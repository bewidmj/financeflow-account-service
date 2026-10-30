package com.learning.financeflow.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learning.financeflow.account.api.AccountResponse;
import com.learning.financeflow.account.api.CreateAccountRequest;
import com.learning.financeflow.account.api.UpdateAccountRequest;
import com.learning.financeflow.account.exception.AccountServiceException;
import com.learning.financeflow.account.model.Account;
import com.learning.financeflow.account.model.AccountType;
import com.learning.financeflow.account.repository.AccountLedgerEntryRepository;
import com.learning.financeflow.account.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountLedgerEntryRepository ledgerRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void createsAccountWithDefaultCurrencyAndOpeningLedgerEntry() {
        UUID userId = UUID.randomUUID();
        CreateAccountRequest request = new CreateAccountRequest(
                "Primary savings", AccountType.SAVINGS, null, new BigDecimal("125.50"));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse result = accountService.create(userId, request);

        assertEquals("INR", result.currency());
        assertEquals(new BigDecimal("125.50"), result.currentBalance());
        verify(ledgerRepository).save(any());
    }

    @Test
    void cannotReadAnotherUsersAccount() {
        UUID accountId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(accountRepository.findByIdAndUserId(accountId, userId))
                .thenReturn(Optional.empty());

        AccountServiceException exception = assertThrows(
                AccountServiceException.class,
                () -> accountService.get(userId, accountId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void rejectsClosingAccountWithNonzeroBalance() {
        UUID userId = UUID.randomUUID();
        Account account = new Account(
                UUID.randomUUID(),
                userId,
                "Savings",
                AccountType.SAVINGS,
                "INR",
                new BigDecimal("1.00"),
                Instant.now());
        when(accountRepository.findByIdForUpdate(account.getId()))
                .thenReturn(Optional.of(account));

        AccountServiceException exception = assertThrows(
                AccountServiceException.class,
                () -> accountService.close(userId, account.getId()));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void updatesOwnedAccountDetails() {
        UUID userId = UUID.randomUUID();
        Account account = new Account(
                UUID.randomUUID(),
                userId,
                "Old name",
                AccountType.SAVINGS,
                "INR",
                BigDecimal.ZERO,
                Instant.now());
        when(accountRepository.findByIdForUpdate(account.getId()))
                .thenReturn(Optional.of(account));

        AccountResponse result = accountService.update(
                userId, account.getId(), new UpdateAccountRequest("Everyday", AccountType.CURRENT));

        assertEquals("Everyday", result.name());
        assertEquals(AccountType.CURRENT, result.accountType());
    }
}
