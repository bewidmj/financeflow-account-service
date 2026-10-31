package com.learning.financeflow.account.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreditCardPaymentCommand(
        UUID eventId,
        UUID paymentId,
        UUID cardId,
        UUID userId,
        UUID sourceAccountId,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        Instant requestedAt) {
}
