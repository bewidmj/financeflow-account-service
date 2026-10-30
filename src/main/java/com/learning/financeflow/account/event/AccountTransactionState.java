package com.learning.financeflow.account.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "account_transaction_state")
public class AccountTransactionState {

    @Id
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "transaction_version", nullable = false)
    private long transactionVersion;

    @Column(name = "account_id")
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 10)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(length = 500)
    private String description;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    protected AccountTransactionState() {
    }

    public AccountTransactionState(
            Long transactionId,
            UUID userId,
            long transactionVersion,
            TransactionSnapshot snapshot,
            boolean deleted) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.transactionVersion = transactionVersion;
        setSnapshot(snapshot);
        this.deleted = deleted;
    }

    public void advance(
            long transactionVersion, TransactionSnapshot snapshot, boolean deleted) {
        this.transactionVersion = transactionVersion;
        if (snapshot != null) {
            setSnapshot(snapshot);
        }
        this.deleted = deleted;
    }

    public TransactionSnapshot toSnapshot() {
        return new TransactionSnapshot(
                accountId,
                transactionType,
                amount,
                currency,
                category,
                description,
                transactionDate);
    }

    private void setSnapshot(TransactionSnapshot snapshot) {
        this.accountId = snapshot.accountId();
        this.transactionType = snapshot.transactionType();
        this.amount = snapshot.amount();
        this.currency = snapshot.currency();
        this.category = snapshot.category();
        this.description = snapshot.description();
        this.transactionDate = snapshot.transactionDate();
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public long getTransactionVersion() {
        return transactionVersion;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public boolean isDeleted() {
        return deleted;
    }
}
