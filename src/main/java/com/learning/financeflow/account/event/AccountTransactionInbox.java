package com.learning.financeflow.account.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_transaction_inbox")
public class AccountTransactionInbox {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "transaction_version", nullable = false)
    private long transactionVersion;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected AccountTransactionInbox() {
    }

    public AccountTransactionInbox(
            UUID eventId,
            Long transactionId,
            long transactionVersion,
            String eventType,
            Instant processedAt) {
        this.eventId = eventId;
        this.transactionId = transactionId;
        this.transactionVersion = transactionVersion;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }
}
