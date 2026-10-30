package com.learning.financeflow.account.service;

import com.learning.financeflow.account.api.CreateTransferRequest;
import com.learning.financeflow.account.exception.AccountServiceException;
import com.learning.financeflow.account.model.AccountTransfer;
import com.learning.financeflow.account.repository.AccountTransferRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final AccountTransferRepository transferRepository;
    private final TransferTransactionExecutor transactionExecutor;

    public TransferService(
            AccountTransferRepository transferRepository,
            TransferTransactionExecutor transactionExecutor) {
        this.transferRepository = transferRepository;
        this.transactionExecutor = transactionExecutor;
    }

    public AccountTransfer create(
            UUID userId, String idempotencyKey, CreateTransferRequest request) {
        AccountTransfer previous = transferRepository
                .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElse(null);
        if (previous != null) {
            return replayOrReject(previous, request);
        }
        try {
            return transactionExecutor.execute(userId, idempotencyKey, request);
        } catch (DataIntegrityViolationException exception) {
            AccountTransfer committed = transferRepository
                    .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> exception);
            return replayOrReject(committed, request);
        }
    }

    @Transactional(readOnly = true)
    public AccountTransfer get(UUID userId, UUID transferId) {
        return transferRepository.findByIdAndUserId(transferId, userId)
                .orElseThrow(() -> new AccountServiceException(
                        HttpStatus.NOT_FOUND, "Transfer was not found"));
    }

    @Transactional(readOnly = true)
    public Page<AccountTransfer> list(UUID userId, Pageable pageable) {
        return transferRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
    }

    private AccountTransfer replayOrReject(
            AccountTransfer transfer, CreateTransferRequest request) {
        if (!transfer.matches(
                request.sourceAccountId(), request.destinationAccountId(), request.amount())) {
            throw new AccountServiceException(
                    HttpStatus.CONFLICT,
                    "Idempotency key was already used for a different transfer");
        }
        return transfer;
    }
}
