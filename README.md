# FinanceFlow Account Service

Independent account and transfer microservice for FinanceFlow. It owns its PostgreSQL database and does not read or write Finance Service tables.

## Architecture

- Java 21 and Spring Boot 3.5.5.
- Stateless Spring Security OAuth2 resource server; validates the Identity Service's RS256 JWTs using its JWKS endpoint.
- Account ownership is derived from the verified token's UUID `sub`. The identity service currently issues the `role` claim; the service maps it to Spring `ROLE_*` authorities.
- PostgreSQL schema managed by Flyway. JPA schema auto-update is disabled.
- Balances are stored as `NUMERIC(19,2)` and updated in the same transaction as append-only ledger rows.
- Transfers lock both account rows in UUID order, validate both owners/statuses and currency, then write the transfer and debit/credit ledger entries atomically.
- A database uniqueness constraint scopes idempotency keys to a user. Retrying the same key and payload returns the original transfer; reusing it for different accounts or amount returns `409 Conflict`.
- Finance transaction updates are consumed asynchronously from Kafka. Finance Service owns the transactional outbox; this service owns its consumer inbox and transaction projection.
- Credit-card repayments are consumed as internal Kafka commands. This service owns the source-account debit, payment operation and result outbox in one database transaction.

## Run locally

Requirements: Java 21, Maven 3.9+, PostgreSQL 14+.

Create a database and an application database user using your local PostgreSQL administration workflow. Do not put database passwords, JWT private keys, or other credentials in this repository. Then configure:

```bash
export DATABASE_URL='jdbc:postgresql://localhost:5432/financeflow_accounts'
export DATABASE_USERNAME='your-local-db-user'
export DATABASE_PASSWORD='your-local-db-password'
export JWT_JWKS_URI='http://localhost:8081/.well-known/jwks.json'
export KAFKA_CARD_PAYMENT_COMMAND_TOPIC='financeflow.credit-card-payments.v1'
export KAFKA_CARD_PAYMENT_COMMAND_DLT_TOPIC='financeflow.credit-card-payments.v1.dlt'
export KAFKA_CARD_PAYMENT_RESULT_TOPIC='financeflow.credit-card-payments.result.v1'
```

The JWKS URI above matches the Identity Service's public `/.well-known/jwks.json` endpoint. The default application value is the same local endpoint. Production should set `JWT_JWKS_URI` to the reachable HTTPS endpoint for the deployed Identity Service.

Run the service:

```bash
mvn spring-boot:run
```

It listens on port `8082` by default. Override with `SERVER_PORT`.

Run tests:

```bash
mvn test
```

Unit, MockMvc, and H2 repository tests run without external services. PostgreSQL integration tests use Testcontainers and are skipped automatically if Docker is unavailable.

## Docker

Build the image:

```bash
docker build -t financeflow-account-service .
```

Run it by passing environment variables through your deployment environment:

```bash
docker run --rm -p 8082:8082 \
  -e DATABASE_URL='jdbc:postgresql://host.docker.internal:5432/financeflow_accounts' \
  -e DATABASE_USERNAME='your-local-db-user' \
  -e DATABASE_PASSWORD='your-local-db-password' \
  -e JWT_JWKS_URI='http://host.docker.internal:8081/.well-known/jwks.json' \
  financeflow-account-service
```

The service migrates its own database at startup. Configure database reachability and secret injection in the runtime environment; no credential is baked into the image.

## API

All API routes require `Authorization: Bearer <access-token>` issued by FinanceFlow Identity Service. Swagger UI is available at `/swagger-ui.html`; health is available at `/actuator/health`.

### Accounts

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/accounts` | Create an account and optionally record its opening balance |
| `GET` | `/api/v1/accounts` | List the authenticated user's accounts |
| `GET` | `/api/v1/accounts/{accountId}` | Get account details |
| `PUT` | `/api/v1/accounts/{accountId}` | Update the account name and type |
| `DELETE` | `/api/v1/accounts/{accountId}` | Soft-close a zero-balance account |
| `GET` | `/api/v1/accounts/{accountId}/balance` | Get current balance and currency |
| `GET` | `/api/v1/accounts/{accountId}/transactions?page=0&size=20` | Get immutable account ledger entries |

Supported account types are `SAVINGS`, `CURRENT`, `CASH`, `WALLET`, and `CREDIT_CARD`. Currency defaults to `INR`. Currency and opening balance cannot be changed after account creation. An account must have a zero balance before it can be closed.

Create an account:

```bash
curl -X POST http://localhost:8082/api/v1/accounts \
  -H 'Authorization: Bearer <access-token>' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "HDFC Savings",
    "accountType": "SAVINGS",
    "currency": "INR",
    "openingBalance": "25000.00"
  }'
```

### Transfers

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/transfers` | Transfer between two same-owner, same-currency active accounts |
| `GET` | `/api/v1/transfers?page=0&size=20` | List the authenticated user's transfers |
| `GET` | `/api/v1/transfers/{transferId}` | Get a transfer |

Every transfer requires a caller-generated `Idempotency-Key` header. The API accepts decimal strings for amounts so clients can avoid binary floating-point representation.

```bash
curl -X POST http://localhost:8082/api/v1/transfers \
  -H 'Authorization: Bearer <access-token>' \
  -H 'Idempotency-Key: 2ef2165d-715b-40da-a681-91d4d627e90d' \
  -H 'Content-Type: application/json' \
  -d '{
    "sourceAccountId": "<source-account-uuid>",
    "destinationAccountId": "<destination-account-uuid>",
    "amount": "10000.00",
    "description": "Monthly savings"
  }'
```

Amounts are positive decimal values with at most two fractional digits. Currency conversion is not supported. An account-history entry's `referenceId` identifies the associated account (opening balance), transfer, or source Finance event; Finance transaction rows additionally expose their source transaction ID and version.

## Database schema

Flyway migrations create:

- `accounts`: UUID account, owner UUID, name, type, currency, opening/current balances, status, timestamps, optimistic version. Indexed by owner/status/time.
- `account_transfers`: UUID transfer, owner UUID, source/destination accounts, amount, currency, status, idempotency key, description, timestamp. Enforces distinct accounts and unique `(user_id, idempotency_key)`.
- `account_ledger_entries`: UUID immutable debit/credit rows, account UUID, reference UUID, amount, description, timestamp, and optional source transaction/event metadata. The PostgreSQL migration also installs a trigger preventing update/delete.
- `V2__transaction_event_inbox_and_ledger_sources.sql` adds the transaction source metadata, `account_transaction_inbox` (unique event ID and version), and `account_transaction_state` (latest snapshot used to reverse changes).
- `V3__credit_card_payment_saga.sql` creates `account_credit_card_payments` and its result outbox. The operation and source-account debit ledger row commit atomically; the outbox publishes an idempotent completion or rejection result.

Foreign keys target only tables owned by this service. Financial amounts use `NUMERIC(19,2)`; floating-point database types are not used.

## Frontend integration

The FinanceFlow UI includes account overview, account creation/editing, detail and ledger history, soft-close confirmation, transfer form/history, credit card and repayment pages, and authenticated API clients. Local Vite development proxies `/account-api` to `http://localhost:8082`, separate from the UI's `/accounts` route; override `VITE_ACCOUNT_API_URL` if needed.

## Current boundaries and follow-up

- Existing Finance transactions remain unlinked until an explicit update assigns an account or credit card; no historical balance backfill is performed.
- Finance Service does not directly access this database. Kafka carries event DTOs, and no database tables are shared.
- This initial API does not expose admin cross-user account operations. Admins are authenticated using the existing `role` convention, while account operations remain scoped to the token subject.
- Transfers are synchronous and recorded as `COMPLETED`; failed attempts roll back and are not persisted as transfer rows.
- Credit-card payment commands are internal Kafka messages, not a public Account Service API. The command carries the payment idempotency key and authenticated owner UUID from the Credit Card Service; this service revalidates the source account owner before debiting.

The payment command, dead-letter, and result topics default to
`financeflow.credit-card-payments.v1`,
`financeflow.credit-card-payments.v1.dlt`, and
`financeflow.credit-card-payments.result.v1`. The Credit Card Service and
Account Service must use the same command and result topic environment values.
# Finance transaction events

Account Service consumes the versioned `financeflow.transactions.v1` topic using transaction ID as the Kafka key. It validates `eventVersion`, transaction version, snapshots, account ownership, active status, and INR currency before applying a change. The `account_transaction_inbox` and `account_transaction_state` records are committed atomically with immutable ledger entries and balance updates.

`TransactionCreated` applies the after snapshot, `TransactionUpdated` reverses the prior snapshot and applies the new one, and `TransactionDeleted` appends a reversal without removing historical entries. Duplicate event IDs are no-ops. Version gaps are retried and dead-lettered if they persist. Non-retryable ownership, account-status, currency, and payload failures are sent to `financeflow.transactions.v1.dlt`.

The `V2__transaction_event_inbox_and_ledger_sources.sql` migration adds transaction source metadata to ledger rows and creates the inbox/projection tables. Flyway applies it at service startup. Account Service does not connect to the Finance database.

Set `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`) and keep it consistent with Finance Service. For a local broker and Finance-side operational guidance, see the FinanceFlow README.
