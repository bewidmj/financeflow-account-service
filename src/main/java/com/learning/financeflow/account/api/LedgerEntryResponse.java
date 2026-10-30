package com.learning.financeflow.account.api;

import com.learning.financeflow.account.model.AccountLedgerEntry;
import com.learning.financeflow.account.model.LedgerEntryType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID accountId,
        UUID referenceId,
        LedgerEntryType entryType,
        BigDecimal amount,
        String description,
        Instant createdAt) {

    public static LedgerEntryResponse from(AccountLedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getAccountId(),
                entry.getReferenceId(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getDescription(),
                entry.getCreatedAt());
    }
}
