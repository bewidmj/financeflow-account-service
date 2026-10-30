package com.learning.financeflow.account.service;

import com.learning.financeflow.account.api.CreateTransferRequest;
import com.learning.financeflow.account.exception.AccountServiceException;
import com.learning.financeflow.account.model.Account;
import com.learning.financeflow.account.model.AccountLedgerEntry;
import com.learning.financeflow.account.model.AccountTransfer;
import com.learning.financeflow.account.model.LedgerEntryType;
import com.learning.financeflow.account.repository.AccountLedgerEntryRepository;
import com.learning.financeflow.account.repository.AccountRepository;
import com.learning.financeflow.account.repository.AccountTransferRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferTransactionExecutor {

    private final AccountRepository accountRepository;
    private final AccountTransferRepository transferRepository;
    private final AccountLedgerEntryRepository ledgerRepository;

    public TransferTransactionExecutor(
            AccountRepository accountRepository,
            AccountTransferRepository transferRepository,
            AccountLedgerEntryRepository ledgerRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public AccountTransfer execute(
            UUID userId, String idempotencyKey, CreateTransferRequest request) {
        AccountTransfer previous = transferRepository
                .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElse(null);
        if (previous != null) {
            return replayOrReject(previous, request);
        }
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw conflict("Source and destination accounts must be different");
        }

        List<UUID> lockOrder = new ArrayList<>(
                List.of(request.sourceAccountId(), request.destinationAccountId()));
        lockOrder.sort(UUID::compareTo);
        Account first = lockOwnedAccountForUpdate(lockOrder.get(0), userId);
        Account second = lockOwnedAccountForUpdate(lockOrder.get(1), userId);
        Account source = first.getId().equals(request.sourceAccountId()) ? first : second;
        Account destination = first.getId().equals(request.destinationAccountId()) ? first : second;

        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw conflict("Transfers between different currencies are not supported");
        }

        Instant now = Instant.now();
        try {
            source.debit(request.amount(), now);
            destination.credit(request.amount(), now);
        } catch (IllegalStateException exception) {
            throw conflict(exception.getMessage());
        }

        AccountTransfer transfer = new AccountTransfer(
                UUID.randomUUID(),
                userId,
                source.getId(),
                destination.getId(),
                request.amount(),
                source.getCurrency(),
                idempotencyKey,
                request.description() == null ? null : request.description().trim(),
                now);
        try {
            transferRepository.saveAndFlush(transfer);
        } catch (DataIntegrityViolationException exception) {
            throw exception;
        }

        ledgerRepository.save(new AccountLedgerEntry(
                UUID.randomUUID(),
                source.getId(),
                transfer.getId(),
                LedgerEntryType.DEBIT,
                transfer.getAmount(),
                transfer.getDescription(),
                now));
        ledgerRepository.save(new AccountLedgerEntry(
                UUID.randomUUID(),
                destination.getId(),
                transfer.getId(),
                LedgerEntryType.CREDIT,
                transfer.getAmount(),
                transfer.getDescription(),
                now));
        return transfer;
    }

    private Account lockOwnedAccountForUpdate(UUID accountId, UUID userId) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountServiceException(
                        HttpStatus.NOT_FOUND, "Account was not found"));
        if (!account.getUserId().equals(userId)) {
            throw new AccountServiceException(HttpStatus.NOT_FOUND, "Account was not found");
        }
        return account;
    }

    private AccountTransfer replayOrReject(
            AccountTransfer transfer, CreateTransferRequest request) {
        if (!transfer.matches(
                request.sourceAccountId(), request.destinationAccountId(), request.amount())) {
            throw conflict("Idempotency key was already used for a different transfer");
        }
        return transfer;
    }

    private AccountServiceException conflict(String message) {
        return new AccountServiceException(HttpStatus.CONFLICT, message);
    }
}
