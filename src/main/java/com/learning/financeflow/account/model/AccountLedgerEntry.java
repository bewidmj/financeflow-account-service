package com.learning.financeflow.account.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_ledger_entries")
public class AccountLedgerEntry {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 6)
    private LedgerEntryType entryType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "source_transaction_id")
    private Long sourceTransactionId;

    @Column(name = "source_transaction_version")
    private Long sourceTransactionVersion;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "entry_sequence")
    private Integer entrySequence;

    protected AccountLedgerEntry() {
    }

    public AccountLedgerEntry(
            UUID id,
            UUID accountId,
            UUID referenceId,
            LedgerEntryType entryType,
            BigDecimal amount,
            String description,
            Instant createdAt) {
        this(id, accountId, referenceId, entryType, amount, description, createdAt,
                null, null, null, null);
    }

    public AccountLedgerEntry(
            UUID id,
            UUID accountId,
            UUID referenceId,
            LedgerEntryType entryType,
            BigDecimal amount,
            String description,
            Instant createdAt,
            Long sourceTransactionId,
            Long sourceTransactionVersion,
            UUID sourceEventId,
            Integer entrySequence) {
        this.id = id;
        this.accountId = accountId;
        this.referenceId = referenceId;
        this.entryType = entryType;
        this.amount = amount;
        this.description = description;
        this.createdAt = createdAt;
        this.sourceTransactionId = sourceTransactionId;
        this.sourceTransactionVersion = sourceTransactionVersion;
        this.sourceEventId = sourceEventId;
        this.entrySequence = entrySequence;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getSourceTransactionId() {
        return sourceTransactionId;
    }

    public Long getSourceTransactionVersion() {
        return sourceTransactionVersion;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public Integer getEntrySequence() {
        return entrySequence;
    }
}
