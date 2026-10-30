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
- Kafka and an outbox are intentionally not included. They are appropriate when account events are introduced.

## Run locally

Requirements: Java 21, Maven 3.9+, PostgreSQL 14+.

Create a database and an application database user using your local PostgreSQL administration workflow. Do not put database passwords, JWT private keys, or other credentials in this repository. Then configure:

```bash
export DATABASE_URL='jdbc:postgresql://localhost:5432/financeflow_accounts'
export DATABASE_USERNAME='your-local-db-user'
export DATABASE_PASSWORD='your-local-db-password'
export JWT_JWKS_URI='http://localhost:8081/.well-known/jwks.json'
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

Amounts are positive decimal values with at most two fractional digits. Currency conversion is not supported. An account-history entry's `referenceId` identifies the associated account (opening balance) or transfer.

## Database schema

Flyway migration `V1__create_account_schema.sql` creates:

- `accounts`: UUID account, owner UUID, name, type, currency, opening/current balances, status, timestamps, optimistic version. Indexed by owner/status/time.
- `account_transfers`: UUID transfer, owner UUID, source/destination accounts, amount, currency, status, idempotency key, description, timestamp. Enforces distinct accounts and unique `(user_id, idempotency_key)`.
- `account_ledger_entries`: UUID immutable debit/credit rows, account UUID, reference UUID, amount, description, timestamp. The PostgreSQL migration also installs a trigger preventing update/delete.

Foreign keys target only tables owned by this service. Financial amounts use `NUMERIC(19,2)`; floating-point database types are not used.

## Frontend integration

The existing FinanceFlow UI includes account overview, account creation/editing, detail and ledger history, soft-close confirmation, transfer form/history, and an authenticated `/accounts` API client. Local Vite development proxies `/accounts` to `http://localhost:8082`; override `VITE_ACCOUNT_API_URL` if needed.

## Current boundaries and follow-up

- The existing Finance Service's `FinancialTransaction` entries are standalone income/expense records and are not imported into this account ledger. Account history currently includes opening-balance and account-transfer ledger entries only.
- Finance Service does not directly access this database. Future integration should publish account events through a transactional outbox and a broker, rather than sharing database tables.
- This initial API does not expose admin cross-user account operations. Admins are authenticated using the existing `role` convention, while account operations remain scoped to the token subject.
- Transfers are synchronous and recorded as `COMPLETED`; failed attempts roll back and are not persisted as transfer rows.
