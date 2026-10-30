package com.learning.financeflow.account.api;

import com.learning.financeflow.account.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull AccountType accountType) {
}
