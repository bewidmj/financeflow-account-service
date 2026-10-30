package com.learning.financeflow.account.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull UUID sourceAccountId,
        @NotNull UUID destinationAccountId,
        @NotNull @DecimalMin(value = "0.01")
                @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Size(max = 500) String description) {
}
