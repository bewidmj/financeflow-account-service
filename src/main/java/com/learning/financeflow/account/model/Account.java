package com.learning.financeflow.account.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Account() {
    }

    public Account(
            UUID id,
            UUID userId,
            String name,
            AccountType accountType,
            String currency,
            BigDecimal openingBalance,
            Instant now) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.accountType = accountType;
        this.currency = currency;
        this.openingBalance = openingBalance;
        this.currentBalance = openingBalance;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDetails(String name, AccountType accountType, Instant now) {
        ensureActive();
        this.name = name;
        this.accountType = accountType;
        this.updatedAt = now;
    }

    public void debit(BigDecimal amount, Instant now) {
        ensureActive();
        if (currentBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient account balance");
        }
        currentBalance = currentBalance.subtract(amount);
        updatedAt = now;
    }

    public void credit(BigDecimal amount, Instant now) {
        ensureActive();
        currentBalance = currentBalance.add(amount);
        updatedAt = now;
    }

    public void close(Instant now) {
        ensureActive();
        if (currentBalance.signum() != 0) {
            throw new IllegalStateException("Account balance must be zero before closing");
        }
        status = AccountStatus.CLOSED;
        updatedAt = now;
    }

    private void ensureActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is closed");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
