package com.learning.financeflow.account.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.financeflow.account.model.Account;
import com.learning.financeflow.account.model.AccountStatus;
import com.learning.financeflow.account.model.AccountType;
import com.learning.financeflow.account.model.AccountLedgerEntry;
import com.learning.financeflow.account.model.LedgerEntryType;
import com.learning.financeflow.account.repository.AccountLedgerEntryRepository;
import com.learning.financeflow.account.repository.AccountRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditCardPaymentCommandProcessor {

    private final AccountRepository accountRepository;
    private final AccountLedgerEntryRepository ledgerRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CreditCardPaymentCommandProcessor(
            AccountRepository accountRepository,
            AccountLedgerEntryRepository ledgerRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void process(CreditCardPaymentCommand command) {
        validate(command);
        ExistingOperation existing = findOperation(command.paymentId());
        if (existing != null) {
            if (!existing.matches(command)) {
                throw new InvalidCreditCardPaymentCommandException(
                        "Payment ID was reused with different payment details");
            }
            return;
        }

        Instant now = Instant.now();
        Account account = accountRepository.findByIdForUpdate(command.sourceAccountId())
                .orElse(null);
        String failure = rejectionCode(account, command);
        if (failure == null) {
            try {
                account.debit(command.amount(), now);
            } catch (IllegalStateException exception) {
                failure = "INSUFFICIENT_FUNDS";
            }
        }

        String status = failure == null ? "COMPLETED" : "FAILED";
        if (failure == null) {
            ledgerRepository.save(new AccountLedgerEntry(
                    UUID.randomUUID(),
                    account.getId(),
                    command.paymentId(),
                    LedgerEntryType.DEBIT,
                    command.amount(),
                    "Credit card repayment",
                    now));
        }
        saveOperation(command, status, failure, now);
        saveResultOutbox(command, status, failure, now);
    }

    private String rejectionCode(Account account, CreditCardPaymentCommand command) {
        if (account == null
                || !account.getUserId().equals(command.userId())
                || account.getStatus() != AccountStatus.ACTIVE
                || account.getAccountType() == AccountType.CREDIT_CARD) {
            return "SOURCE_ACCOUNT_UNAVAILABLE";
        }
        if (!account.getCurrency().equals(command.currency())) {
            return "CURRENCY_MISMATCH";
        }
        if (account.getCurrentBalance().compareTo(command.amount()) < 0) {
            return "INSUFFICIENT_FUNDS";
        }
        return null;
    }

    private void validate(CreditCardPaymentCommand command) {
        if (command == null
                || command.eventId() == null
                || command.paymentId() == null
                || command.cardId() == null
                || command.userId() == null
                || command.sourceAccountId() == null
                || command.amount() == null
                || command.amount().signum() <= 0
                || command.currency() == null
                || !command.currency().matches("[A-Z]{3}")
                || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 128
                || command.requestedAt() == null) {
            throw new InvalidCreditCardPaymentCommandException(
                    "Invalid credit card payment command");
        }
    }

    private ExistingOperation findOperation(UUID paymentId) {
        List<ExistingOperation> operations = jdbcTemplate.query(
                """
                SELECT user_id, card_id, source_account_id, amount, currency, idempotency_key
                FROM account_credit_card_payments
                WHERE payment_id = ?
                """,
                (rs, rowNum) -> new ExistingOperation(
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("card_id", UUID.class),
                        rs.getObject("source_account_id", UUID.class),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("idempotency_key")),
                paymentId);
        return operations.isEmpty() ? null : operations.getFirst();
    }

    private void saveOperation(
            CreditCardPaymentCommand command, String status, String errorCode, Instant now) {
        jdbcTemplate.update(
                """
                INSERT INTO account_credit_card_payments (
                    payment_id, user_id, card_id, source_account_id, amount, currency,
                    idempotency_key, status, error_code, created_at, processed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                command.paymentId(),
                command.userId(),
                command.cardId(),
                command.sourceAccountId(),
                command.amount(),
                command.currency(),
                command.idempotencyKey(),
                status,
                errorCode,
                Timestamp.from(command.requestedAt()),
                Timestamp.from(now));
    }

    private void saveResultOutbox(
            CreditCardPaymentCommand command, String status, String errorCode, Instant now) {
        CreditCardPaymentResult result = new CreditCardPaymentResult(
                UUID.randomUUID(),
                command.paymentId(),
                status.equals("COMPLETED") ? command.paymentId() : null,
                status,
                errorCode,
                now);
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO account_credit_card_payment_outbox (
                        id, payment_id, event_type, payload, status, attempt_count,
                        next_attempt_at, created_at
                    ) VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?)
                    """,
                    UUID.randomUUID(),
                    command.paymentId(),
                    "CreditCardPaymentResult",
                    objectMapper.writeValueAsString(result),
                    Timestamp.from(now),
                    Timestamp.from(now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize credit card payment result", exception);
        }
    }

    private record ExistingOperation(
            UUID userId,
            UUID cardId,
            UUID sourceAccountId,
            BigDecimal amount,
            String currency,
            String idempotencyKey) {

        private boolean matches(CreditCardPaymentCommand command) {
            return userId.equals(command.userId())
                    && cardId.equals(command.cardId())
                    && sourceAccountId.equals(command.sourceAccountId())
                    && amount.compareTo(command.amount()) == 0
                    && currency.equals(command.currency())
                    && idempotencyKey.equals(command.idempotencyKey());
        }
    }
}
