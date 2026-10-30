package com.learning.financeflow.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learning.financeflow.account.api.CreateTransferRequest;
import com.learning.financeflow.account.exception.AccountServiceException;
import com.learning.financeflow.account.model.Account;
import com.learning.financeflow.account.model.AccountType;
import com.learning.financeflow.account.model.AccountTransfer;
import com.learning.financeflow.account.repository.AccountLedgerEntryRepository;
import com.learning.financeflow.account.repository.AccountRepository;
import com.learning.financeflow.account.repository.AccountTransferRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferTransactionExecutorTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountTransferRepository transferRepository;

    @Mock
    private AccountLedgerEntryRepository ledgerRepository;

    @InjectMocks
    private TransferTransactionExecutor executor;

    @Test
    void debitsAndCreditsAccountsAndWritesBothLedgerSides() {
        UUID userId = UUID.randomUUID();
        Account source = account(userId, "100.00");
        Account destination = account(userId, "10.00");
        when(accountRepository.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(destination.getId()))
                .thenReturn(Optional.of(destination));
        when(transferRepository.findByUserIdAndIdempotencyKey(userId, "transfer-1"))
                .thenReturn(Optional.empty());
        when(transferRepository.saveAndFlush(any(AccountTransfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        executor.execute(
                userId,
                "transfer-1",
                new CreateTransferRequest(
                        source.getId(), destination.getId(), new BigDecimal("25.00"), "Savings"));

        assertEquals(0, source.getCurrentBalance().compareTo(new BigDecimal("75.00")));
        assertEquals(0, destination.getCurrentBalance().compareTo(new BigDecimal("35.00")));
        verify(ledgerRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void insufficientBalanceDoesNotPersistTransferOrLedgerRows() {
        UUID userId = UUID.randomUUID();
        Account source = account(userId, "5.00");
        Account destination = account(userId, "10.00");
        when(accountRepository.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(destination.getId()))
                .thenReturn(Optional.of(destination));
        when(transferRepository.findByUserIdAndIdempotencyKey(userId, "transfer-2"))
                .thenReturn(Optional.empty());

        assertThrows(
                AccountServiceException.class,
                () -> executor.execute(
                        userId,
                        "transfer-2",
                        new CreateTransferRequest(
                                source.getId(),
                                destination.getId(),
                                new BigDecimal("20.00"),
                                null)));

        assertEquals(0, source.getCurrentBalance().compareTo(new BigDecimal("5.00")));
        assertEquals(0, destination.getCurrentBalance().compareTo(new BigDecimal("10.00")));
        verify(transferRepository, never()).saveAndFlush(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void rejectsAccountsWithDifferentCurrencies() {
        UUID userId = UUID.randomUUID();
        Account source = account(userId, "100.00");
        Account destination = new Account(
                UUID.randomUUID(),
                userId,
                "Foreign currency",
                AccountType.SAVINGS,
                "USD",
                BigDecimal.ZERO,
                Instant.now());
        when(accountRepository.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
        when(accountRepository.findByIdForUpdate(destination.getId()))
                .thenReturn(Optional.of(destination));
        when(transferRepository.findByUserIdAndIdempotencyKey(userId, "transfer-3"))
                .thenReturn(Optional.empty());

        assertThrows(
                AccountServiceException.class,
                () -> executor.execute(
                        userId,
                        "transfer-3",
                        new CreateTransferRequest(
                                source.getId(),
                                destination.getId(),
                                new BigDecimal("1.00"),
                                null)));
        verify(transferRepository, never()).saveAndFlush(any());
    }

    private Account account(UUID userId, String openingBalance) {
        return new Account(
                UUID.randomUUID(),
                userId,
                "Account",
                AccountType.SAVINGS,
                "INR",
                new BigDecimal(openingBalance),
                Instant.now());
    }
}
