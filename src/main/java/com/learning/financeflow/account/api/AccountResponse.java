package com.learning.financeflow.account.api;

import com.learning.financeflow.account.model.Account;
import com.learning.financeflow.account.model.AccountStatus;
import com.learning.financeflow.account.model.AccountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        AccountType accountType,
        String currency,
        BigDecimal openingBalance,
        BigDecimal currentBalance,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getAccountType(),
                account.getCurrency(),
                account.getOpeningBalance(),
                account.getCurrentBalance(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
