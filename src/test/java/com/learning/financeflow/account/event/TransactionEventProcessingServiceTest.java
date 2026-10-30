package com.learning.financeflow.account.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learning.financeflow.account.model.Account;
import com.learning.financeflow.account.model.AccountType;
import com.learning.financeflow.account.repository.AccountLedgerEntryRepository;
import com.learning.financeflow.account.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionEventProcessingServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountLedgerEntryRepository ledgerRepository;
    @Mock
    private AccountTransactionInboxRepository inboxRepository;
    @Mock
    private AccountTransactionStateRepository stateRepository;

    private TransactionEventProcessingService service;
    private Account account;

    @BeforeEach
    void setUp() {
        service = new TransactionEventProcessingService(
                accountRepository, ledgerRepository, inboxRepository, stateRepository);
        account = new Account(
                ACCOUNT_ID,
                USER_ID,
                "Main",
                AccountType.SAVINGS,
                "INR",
                new BigDecimal("1000.00"),
                Instant.parse("2026-01-01T00:00:00Z"));
        when(inboxRepository.existsById(any())).thenReturn(false);
    }

    @Test
    void expenseCreationDebitsTheAccountAndRecordsInboxAtomically() {
        TransactionEvent event = event(
                "TransactionCreated",
                1,
                null,
                snapshot(ACCOUNT_ID, TransactionType.EXPENSE, "200.00"));
        when(stateRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertTrue(service.process(event));

        assertEquals(new BigDecimal("800.00"), account.getCurrentBalance());
        verify(ledgerRepository).save(any());
        verify(stateRepository).save(any(AccountTransactionState.class));
        verify(inboxRepository).save(any(AccountTransactionInbox.class));
    }

    @Test
    void duplicateEventIsIgnoredWithoutChangingBalance() {
        TransactionEvent event = event(
                "TransactionCreated",
                1,
                null,
                snapshot(ACCOUNT_ID, TransactionType.INCOME, "200.00"));
        when(inboxRepository.existsById(event.eventId())).thenReturn(true);

        assertFalse(service.process(event));

        assertEquals(new BigDecimal("1000.00"), account.getCurrentBalance());
        verify(accountRepository, never()).findByIdForUpdate(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void updateReversesPriorImpactThenAppliesNewImpact() {
        TransactionSnapshot oldSnapshot =
                snapshot(ACCOUNT_ID, TransactionType.EXPENSE, "200.00");
        AccountTransactionState state = new AccountTransactionState(
                42L, USER_ID, 1, oldSnapshot, false);
        account.debit(new BigDecimal("200.00"), Instant.now());
        TransactionEvent event = event(
                "TransactionUpdated",
                2,
                oldSnapshot,
                snapshot(ACCOUNT_ID, TransactionType.INCOME, "100.00"));
        when(stateRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(state));
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));

        assertTrue(service.process(event));

        assertEquals(new BigDecimal("1100.00"), account.getCurrentBalance());
        ArgumentCaptor<com.learning.financeflow.account.model.AccountLedgerEntry> entries =
                ArgumentCaptor.forClass(
                        com.learning.financeflow.account.model.AccountLedgerEntry.class);
        org.mockito.Mockito.verify(ledgerRepository, org.mockito.Mockito.times(2)).save(entries.capture());
        assertEquals(0, entries.getAllValues().get(0).getEntrySequence());
        assertEquals(1, entries.getAllValues().get(1).getEntrySequence());
    }

    @Test
    void rejectsAccountsOwnedByAnotherUserWithoutWritingLedger() {
        TransactionEvent event = event(
                "TransactionCreated",
                1,
                null,
                snapshot(ACCOUNT_ID, TransactionType.INCOME, "200.00"));
        when(stateRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());
        Account foreignAccount = new Account(
                ACCOUNT_ID,
                UUID.randomUUID(),
                "Foreign",
                AccountType.SAVINGS,
                "INR",
                BigDecimal.ZERO,
                Instant.now());
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(foreignAccount));

        assertThrows(AccountEventRejectedException.class, () -> service.process(event));

        assertEquals(BigDecimal.ZERO, foreignAccount.getCurrentBalance());
        verify(ledgerRepository, never()).save(any());
        verify(inboxRepository, never()).save(any());
    }

    @Test
    void rejectsOutOfOrderCreationUpdate() {
        TransactionEvent event = event(
                "TransactionUpdated",
                2,
                snapshot(ACCOUNT_ID, TransactionType.EXPENSE, "200.00"),
                snapshot(ACCOUNT_ID, TransactionType.EXPENSE, "250.00"));
        when(stateRepository.findByIdForUpdate(42L)).thenReturn(Optional.empty());

        assertThrows(TransactionVersionGapException.class, () -> service.process(event));

        verify(accountRepository, never()).findByIdForUpdate(any());
        verify(ledgerRepository, never()).save(any());
    }

    private TransactionEvent event(
            String type,
            long version,
            TransactionSnapshot before,
            TransactionSnapshot after) {
        return new TransactionEvent(
                UUID.randomUUID(),
                type,
                1,
                42L,
                version,
                USER_ID,
                before,
                after,
                Instant.now(),
                UUID.randomUUID().toString());
    }

    private TransactionSnapshot snapshot(
            UUID accountId, TransactionType type, String amount) {
        return new TransactionSnapshot(
                accountId,
                type,
                new BigDecimal(amount),
                "INR",
                "Groceries",
                "Weekly shop",
                LocalDate.of(2026, 1, 1));
    }
}
