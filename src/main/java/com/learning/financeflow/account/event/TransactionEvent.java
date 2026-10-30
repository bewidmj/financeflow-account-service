package com.learning.financeflow.account.event;

import java.time.Instant;
import java.util.UUID;

public record TransactionEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Long transactionId,
        long transactionVersion,
        UUID userId,
        TransactionSnapshot before,
        TransactionSnapshot after,
        Instant occurredAt,
        String correlationId) {
}
