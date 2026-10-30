package com.learning.financeflow.account.service;

import com.learning.financeflow.account.api.AccountResponse;
import com.learning.financeflow.account.api.BalanceResponse;
import com.learning.financeflow.account.api.CreateAccountRequest;
import com.learning.financeflow.account.api.LedgerEntryResponse;
import com.learning.financeflow.account.api.UpdateAccountRequest;
import com.learning.financeflow.account.exception.AccountServiceException;
import com.learning.financeflow.account.model.Account;
import com.learning.financeflow.account.model.AccountLedgerEntry;
import com.learning.financeflow.account.model.AccountType;
import com.learning.financeflow.account.model.LedgerEntryType;
import com.learning.financeflow.account.repository.AccountLedgerEntryRepository;
import com.learning.financeflow.account.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountLedgerEntryRepository ledgerRepository;

    public AccountService(
            AccountRepository accountRepository,
            AccountLedgerEntryRepository ledgerRepository) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public AccountResponse create(UUID userId, CreateAccountRequest request) {
        Instant now = Instant.now();
        UUID accountId = UUID.randomUUID();
        String currency = request.currency() == null ? "INR" : request.currency();
        Account account = new Account(
                accountId,
                userId,
                request.name().trim(),
                request.accountType(),
                currency,
                request.openingBalance(),
                now);
        accountRepository.save(account);
        if (request.openingBalance().signum() > 0) {
            ledgerRepository.save(new AccountLedgerEntry(
                    UUID.randomUUID(),
                    accountId,
                    accountId,
                    LedgerEntryType.CREDIT,
                    request.openingBalance(),
                    "Opening balance",
                    now));
        }
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(UUID userId) {
        return accountRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID userId, UUID accountId) {
        return AccountResponse.from(getOwnedAccount(userId, accountId));
    }

    @Transactional
    public AccountResponse update(UUID userId, UUID accountId, UpdateAccountRequest request) {
        Account account = getOwnedAccountForUpdate(userId, accountId);
        try {
            account.updateDetails(request.name().trim(), request.accountType(), Instant.now());
        } catch (IllegalStateException exception) {
            throw conflict(exception.getMessage());
        }
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse close(UUID userId, UUID accountId) {
        Account account = getOwnedAccountForUpdate(userId, accountId);
        try {
            account.close(Instant.now());
        } catch (IllegalStateException exception) {
            throw conflict(exception.getMessage());
        }
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(UUID userId, UUID accountId) {
        Account account = getOwnedAccount(userId, accountId);
        return new BalanceResponse(account.getId(), account.getCurrentBalance(), account.getCurrency());
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> transactions(
            UUID userId, UUID accountId, Pageable pageable) {
        getOwnedAccount(userId, accountId);
        return ledgerRepository
                .findAllByAccountIdOrderByCreatedAtDescIdDesc(accountId, pageable)
                .map(LedgerEntryResponse::from);
    }

    Account getOwnedAccount(UUID userId, UUID accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new AccountServiceException(
                        HttpStatus.NOT_FOUND, "Account was not found"));
    }

    private Account getOwnedAccountForUpdate(UUID userId, UUID accountId) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountServiceException(
                        HttpStatus.NOT_FOUND, "Account was not found"));
        if (!account.getUserId().equals(userId)) {
            throw new AccountServiceException(HttpStatus.NOT_FOUND, "Account was not found");
        }
        return account;
    }

    private AccountServiceException conflict(String message) {
        return new AccountServiceException(HttpStatus.CONFLICT, message);
    }
}
