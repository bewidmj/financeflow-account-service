package com.learning.financeflow.account.api;

import com.learning.financeflow.account.model.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull AccountType accountType,
        @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a three-letter uppercase code")
                String currency,
        @NotNull @DecimalMin(value = "0.00")
                @Digits(integer = 17, fraction = 2) BigDecimal openingBalance) {
}
