package com.learning.financeflow.account.payment;

import java.util.UUID;

public record ClaimedPaymentResult(
        UUID id,
        UUID paymentId,
        String payload,
        int attemptCount,
        UUID claimToken) {
}
