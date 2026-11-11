# Accounts and transfers API: design

Project B of `camino_a_seguir_cv.txt`. A bank-style REST API in Java, built to show the skills that junior Java vacancies in Mexico ask for (banking, fintech, consultancies): Spring Boot, security, transactions, concurrency, database migrations and integration tests against a real database.

## Goal and success criteria

- A REST API where customers open accounts and transfer money between them, and operators fund, freeze and audit accounts.
- Money is never created or lost: after any sequence of requests, including concurrent ones, every account balance equals the sum of its ledger entries and the total money in the system only changes through operator deposits.
- Retrying a transfer with the same `Idempotency-Key` never applies it twice.
- `docker compose up` starts the API and PostgreSQL; CI is green; the README documents decisions, limits and how to try it.

## Constraints

- Local work only: no deployment, no video, no push to GitHub without asking (see the project memory).
- One currency (MXN). Amounts are integers in centavos.
- Java 23 (the Homebrew `openjdk` already installed, which Maven uses; the user chose it over installing 21 so local, Docker and CI all match), Spring Boot 3.5.x, Maven. Java 23 is not an LTS release; the README says so.
- Docs and code in English; the Spanish-speaking user decides in Spanish.

## Out of scope

Several currencies, fees, scheduled transfers, refresh tokens, email, an admin role, a front end, deployment, video.

## Roles

- `CLIENTE`: operates only their own accounts.
- `OPERADOR`: sees every account, deposits, freezes and unfreezes, reads the audit log. Created by a startup script, not by the API.

## Structure

One Maven module `cuentas_api/`, packages by feature:

- `auth`: registration, login, JWT issue and validation.
- `account`: accounts, balances, freeze and unfreeze, ledger history.
- `transfer`: transfers and idempotency.
- `audit`: audit log.
- `common`: error handling and configuration.

## Data (Flyway, one migration per change)

- `users`: id, unique email, password hash, role.
- `accounts`: id, `user_id`, account number, `balance` in centavos, `status` (`ACTIVE` or `FROZEN`), version. A database check enforces `balance >= 0`.
- `ledger_entries`: id, `account_id`, `transfer_id` (nullable for deposits), type (`DEBIT` or `CREDIT`), amount, resulting balance, timestamp.
- `transfers`: id, source account, target account, amount, status, timestamp.
- `idempotency_keys`: key, user, request body hash, stored response; unique on (user, key).
- `audit_log`: actor, action, target, timestamp, outcome.

Decision: the balance is a column on the account plus a ledger of entries (not a double-entry ledger without a balance column). It is what most mid-size banking systems do and it shows transactions and locking. The cost is that the balance is derived data; a test compares it with the sum of entries.

## Endpoints (`/api/v1`, errors as `application/problem+json`)

| Method and path | Who | Effect |
|---|---|---|
| `POST /auth/register` | public | Creates a `CLIENTE` user |
| `POST /auth/login` | public | Returns a 15-minute JWT |
| `POST /accounts` | CLIENTE | Opens an own account with balance 0 |
| `GET /accounts`, `GET /accounts/{id}` | CLIENTE (own), OPERADOR (all) | Reads accounts |
| `GET /accounts/{id}/entries` | same | Paged ledger history |
| `POST /accounts/{id}/deposits` | OPERADOR | Credits an account |
| `POST /accounts/{id}/freeze`, `/unfreeze` | OPERADOR | Changes status |
| `POST /transfers` | CLIENTE | Transfers from an own account; requires `Idempotency-Key` |
| `GET /transfers/{id}` | owner or OPERADOR | Reads a transfer |
| `GET /audit` | OPERADOR | Paged, filterable audit log |

Authorization: the role is checked per route and account ownership in the service. A customer asking for someone else's account gets 404, not 403, so the account's existence is not revealed.

## Transfer flow (`POST /transfers`)

1. Validate the body (amount > 0, different accounts) and the idempotency key.
2. Look up `idempotency_keys`: same key and same body hash returns the stored response; same key with a different body returns 422.
3. Open a transaction and lock both accounts with `SELECT ... FOR UPDATE` in ascending id order; check both are `ACTIVE` and the source belongs to the caller.
4. Check sufficient balance; insert the transfer and two ledger entries; update both balances.
5. Store the response in `idempotency_keys` in the same transaction and write the audit entry.
6. If two requests with the same key arrive together, the unique constraint makes one fail and be handled as a repeat.

Errors: insufficient balance 422, frozen account 409, someone else's account 404, missing key 400, invalid body 400.

## Testing

- Unit tests (JUnit 5, Mockito): pure business rules.
- Integration tests (Testcontainers, PostgreSQL): migrations, role and ownership authorization, error responses.
- Concurrency tests:
  - 50 simultaneous transfers on one account: exact final balance, never negative.
  - Crossed transfers A to B and B to A in parallel: no deadlock.
  - The same `Idempotency-Key` sent in parallel: applied once.
- Invariant checked after the concurrency tests: each balance equals the sum of its entries, and total money is unchanged.

## Delivery

- Docker: multi-stage image (JDK to build, JRE to run); `docker compose up` starts the API and PostgreSQL.
- CI (GitHub Actions): build, run all tests (Testcontainers works on the Ubuntu runner), build the image. Green before the project counts as done.
- OpenAPI with springdoc; a versioned `.http` file of example calls (text, not Postman).
- README with decisions and reasons (money in centavos, ordered locking, idempotency), known limits and how to try it, plus an "AI usage" section written with the user's input.
