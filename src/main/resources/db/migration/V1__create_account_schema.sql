CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    opening_balance NUMERIC(19, 2) NOT NULL,
    current_balance NUMERIC(19, 2) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_accounts_type
        CHECK (account_type IN ('SAVINGS', 'CURRENT', 'CASH', 'WALLET', 'CREDIT_CARD')),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT chk_accounts_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_accounts_balances CHECK (opening_balance >= 0 AND current_balance >= 0),
    CONSTRAINT uq_accounts_id_user UNIQUE (id, user_id)
);

CREATE INDEX idx_accounts_user_status ON accounts (user_id, status, created_at DESC);

CREATE TABLE account_transfers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    source_account_id UUID NOT NULL,
    destination_account_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(12) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_transfers_amount CHECK (amount > 0),
    CONSTRAINT chk_transfers_distinct_accounts CHECK (source_account_id <> destination_account_id),
    CONSTRAINT chk_transfers_status CHECK (status = 'COMPLETED'),
    CONSTRAINT fk_transfer_source_owner
        FOREIGN KEY (source_account_id, user_id) REFERENCES accounts (id, user_id),
    CONSTRAINT fk_transfer_destination_owner
        FOREIGN KEY (destination_account_id, user_id) REFERENCES accounts (id, user_id),
    CONSTRAINT uq_transfer_user_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_transfers_user_created ON account_transfers (user_id, created_at DESC);
CREATE INDEX idx_transfers_source_created ON account_transfers (source_account_id, created_at DESC);
CREATE INDEX idx_transfers_destination_created ON account_transfers (destination_account_id, created_at DESC);

CREATE TABLE account_ledger_entries (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts (id),
    reference_id UUID NOT NULL,
    entry_type VARCHAR(6) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_ledger_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_amount CHECK (amount > 0)
);

CREATE INDEX idx_ledger_account_created ON account_ledger_entries (account_id, created_at DESC, id DESC);
CREATE INDEX idx_ledger_reference ON account_ledger_entries (reference_id);

CREATE FUNCTION reject_ledger_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'account ledger entries are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_account_ledger_immutable
    BEFORE UPDATE OR DELETE ON account_ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();
