package com.learning.financeflow.account.payment;

import java.time.Instant;
import java.util.UUID;

public record CreditCardPaymentResult(
        UUID eventId,
        UUID paymentId,
        UUID accountPaymentId,
        String status,
        String errorCode,
        Instant processedAt) {
}
