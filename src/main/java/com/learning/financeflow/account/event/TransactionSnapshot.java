package com.learning.financeflow.account.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionSnapshot(
        UUID accountId,
        UUID creditCardId,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        String category,
        String description,
        LocalDate transactionDate) {

    public TransactionSnapshot(
            UUID accountId,
            TransactionType transactionType,
            BigDecimal amount,
            String currency,
            String category,
            String description,
            LocalDate transactionDate) {
        this(
                accountId,
                null,
                transactionType,
                amount,
                currency,
                category,
                description,
                transactionDate);
    }
}
