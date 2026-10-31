CREATE TABLE account_credit_card_payments (
    payment_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    card_id UUID NOT NULL,
    source_account_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(12) NOT NULL,
    error_code VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_card_payment_amount CHECK (amount > 0),
    CONSTRAINT chk_card_payment_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_card_payment_status CHECK (status IN ('COMPLETED', 'FAILED')),
    CONSTRAINT uq_card_payment_user_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_account_card_payments_source_created
    ON account_credit_card_payments (source_account_id, created_at DESC);

CREATE TABLE account_credit_card_payment_outbox (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES account_credit_card_payments (payment_id),
    event_type VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claimed_until TIMESTAMPTZ,
    claim_token UUID,
    last_error VARCHAR(4000),
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT chk_card_payment_outbox_status
        CHECK (status IN ('PENDING', 'IN_FLIGHT', 'RETRY', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_account_card_payment_outbox_pending
    ON account_credit_card_payment_outbox (status, next_attempt_at, created_at);
