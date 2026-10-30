ALTER TABLE account_ledger_entries
    ADD COLUMN source_transaction_id BIGINT,
    ADD COLUMN source_transaction_version BIGINT,
    ADD COLUMN source_event_id UUID,
    ADD COLUMN entry_sequence INTEGER,
    ADD CONSTRAINT chk_ledger_transaction_source_fields CHECK (
        (source_transaction_id IS NULL
            AND source_transaction_version IS NULL
            AND source_event_id IS NULL
            AND entry_sequence IS NULL)
        OR
        (source_transaction_id IS NOT NULL
            AND source_transaction_version IS NOT NULL
            AND source_event_id IS NOT NULL
            AND entry_sequence IS NOT NULL
            AND source_transaction_version > 0
            AND entry_sequence >= 0)
    );

CREATE UNIQUE INDEX uq_ledger_source_event_sequence
    ON account_ledger_entries (source_event_id, entry_sequence)
    WHERE source_event_id IS NOT NULL;

CREATE INDEX idx_ledger_source_transaction
    ON account_ledger_entries (source_transaction_id, source_transaction_version)
    WHERE source_transaction_id IS NOT NULL;

CREATE TABLE account_transaction_inbox (
    event_id UUID PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    transaction_version BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_account_inbox_transaction_version CHECK (transaction_version > 0)
);

CREATE INDEX idx_account_inbox_transaction_version
    ON account_transaction_inbox (transaction_id, transaction_version);

CREATE TABLE account_transaction_state (
    transaction_id BIGINT PRIMARY KEY,
    user_id UUID NOT NULL,
    transaction_version BIGINT NOT NULL,
    account_id UUID REFERENCES accounts (id),
    transaction_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    category VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    transaction_date DATE NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_account_transaction_state_version CHECK (transaction_version > 0),
    CONSTRAINT chk_account_transaction_state_type CHECK (
        transaction_type IN ('INCOME', 'EXPENSE')
    ),
    CONSTRAINT chk_account_transaction_state_amount CHECK (amount > 0),
    CONSTRAINT chk_account_transaction_state_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_account_transaction_state_user_account
    ON account_transaction_state (user_id, account_id);
