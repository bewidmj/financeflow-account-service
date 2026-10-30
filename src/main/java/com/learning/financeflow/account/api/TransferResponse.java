package com.learning.financeflow.account.api;

import com.learning.financeflow.account.model.AccountTransfer;
import com.learning.financeflow.account.model.TransferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        TransferStatus status,
        String description,
        Instant createdAt) {

    public static TransferResponse from(AccountTransfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getSourceAccountId(),
                transfer.getDestinationAccountId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getStatus(),
                transfer.getDescription(),
                transfer.getCreatedAt());
    }
}
