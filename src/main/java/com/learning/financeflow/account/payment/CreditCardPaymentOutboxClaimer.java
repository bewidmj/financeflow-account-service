package com.learning.financeflow.account.payment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CreditCardPaymentOutboxClaimer {

    private static final int MAX_ATTEMPTS = 12;

    private final JdbcTemplate jdbcTemplate;

    public CreditCardPaymentOutboxClaimer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<ClaimedPaymentResult> claim(int batchSize) {
        jdbcTemplate.update("""
                UPDATE account_credit_card_payment_outbox
                SET status = 'FAILED',
                    last_error = COALESCE(last_error, 'Claim lease expired after maximum attempts'),
                    claimed_until = NULL,
                    claim_token = NULL
                WHERE status = 'IN_FLIGHT'
                  AND claimed_until < CURRENT_TIMESTAMP
                  AND attempt_count >= ?
                """, MAX_ATTEMPTS);
        UUID claimToken = UUID.randomUUID();
        return jdbcTemplate.query("""
                UPDATE account_credit_card_payment_outbox AS outbox
                SET status = 'IN_FLIGHT',
                    claim_token = ?,
                    claimed_until = CURRENT_TIMESTAMP + INTERVAL '60 seconds',
                    attempt_count = attempt_count + 1
                WHERE outbox.id IN (
                    SELECT candidate.id
                    FROM account_credit_card_payment_outbox candidate
                    WHERE (
                        candidate.status IN ('PENDING', 'RETRY')
                        AND candidate.next_attempt_at <= CURRENT_TIMESTAMP
                    ) OR (
                        candidate.status = 'IN_FLIGHT'
                        AND candidate.claimed_until < CURRENT_TIMESTAMP
                        AND candidate.attempt_count < ?
                    )
                    ORDER BY candidate.created_at, candidate.id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING outbox.id, outbox.payment_id, outbox.payload, outbox.attempt_count
                """,
                (rs, rowNum) -> map(rs, claimToken),
                claimToken,
                MAX_ATTEMPTS,
                batchSize);
    }

    @Transactional
    public boolean markPublished(UUID id, UUID claimToken, Instant publishedAt) {
        return jdbcTemplate.update("""
                UPDATE account_credit_card_payment_outbox
                SET status = 'PUBLISHED', published_at = ?, claimed_until = NULL,
                    claim_token = NULL, last_error = NULL
                WHERE id = ? AND status = 'IN_FLIGHT' AND claim_token = ?
                """, Timestamp.from(publishedAt), id, claimToken) == 1;
    }

    @Transactional
    public void markFailed(ClaimedPaymentResult result, String error) {
        boolean exhausted = result.attemptCount() >= MAX_ATTEMPTS;
        long delaySeconds = Math.min(3600, 1L << Math.min(result.attemptCount() - 1, 12));
        String message = error == null ? "Unknown Kafka publishing failure" : error;
        jdbcTemplate.update("""
                UPDATE account_credit_card_payment_outbox
                SET status = ?,
                    next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                    claimed_until = NULL, claim_token = NULL, last_error = ?
                WHERE id = ? AND status = 'IN_FLIGHT' AND claim_token = ?
                """,
                exhausted ? "FAILED" : "RETRY",
                delaySeconds,
                message.substring(0, Math.min(message.length(), 4000)),
                result.id(),
                result.claimToken());
    }

    private ClaimedPaymentResult map(ResultSet rs, UUID claimToken) throws SQLException {
        return new ClaimedPaymentResult(
                rs.getObject("id", UUID.class),
                rs.getObject("payment_id", UUID.class),
                rs.getString("payload"),
                rs.getInt("attempt_count"),
                claimToken);
    }
}
