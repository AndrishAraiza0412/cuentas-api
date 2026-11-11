# Accounts and transfers API

A bank-style REST API in Java 23 and Spring Boot 3.5. Customers open accounts and move money between them; operators fund, freeze and audit accounts. The point of the project is the part that is easy to get wrong: money is never created or lost, even when requests arrive at the same moment or are retried.

- Roles: `CLIENTE` (own accounts only) and `OPERADOR` (everything, plus deposits, freezing and the audit log).
- Amounts are whole centavos (MXN), stored as `bigint`. There is no floating point anywhere near money.
- Every transfer is one database transaction that writes the transfer, a debit and a credit entry, and both new balances.
- Retrying a transfer with the same `Idempotency-Key` returns the original response and moves the money once.
- Errors the API produces itself (validation, not found, conflict, auth) are `application/problem+json`; an unhandled server error falls back to Spring Boot's default JSON. API docs are generated with OpenAPI (Swagger UI at `/swagger-ui/index.html`).

## Decisions and why

| Decision | Reason |
|---|---|
| Balance column on the account, plus a ledger of entries | It is how most mid-size banking systems work and it exercises transactions and locking. The cost is that the balance is derived data, so a test checks it against the sum of the entries after every concurrency run. A double-entry ledger with no balance column would be purer and about twice the model. |
| `SELECT ... FOR UPDATE` on both accounts, always in ascending id order | Two opposite transfers (A to B and B to A) lock in the same order, so they cannot deadlock. |
| `balance >= 0` also as a database `CHECK` | The service validates first and answers 422; the constraint is the last line of defence if a code path ever forgets. |
| Idempotency key row inserted first, with a unique constraint on (user, key) | A concurrent duplicate waits on the unique index, then fails and replays the stored response, so no lock or cache is needed. A failed transfer rolls its key back, so the key can be retried once the cause is fixed. |
| Someone else's account is a 404, not a 403 | A 403 would reveal that the account exists. |
| Failed transfers are audited after the rollback, outside the transaction | Auditing inside it would need a second database connection per request and could exhaust the pool under load. |
| Spring Security's own JWT support (HS256), no extra JWT library | One less dependency to keep patched. Tokens last 15 minutes. |
| Spring Data JPA with Flyway migrations and `ddl-auto: validate` | The schema is code-reviewed SQL; Hibernate only checks that the entities match it. |
| One operator, created from environment variables at startup | There is deliberately no endpoint that can create an operator. |

## What the tests prove

58 tests, all against a real PostgreSQL started by Testcontainers (no H2).

| Guarantee | Tests |
|---|---|
| Schema rejects negative balances and duplicate idempotency keys | `SchemaTest` (2) |
| Registration, login, role checks; expired, tampered and missing tokens are 401; the same email in a different letter case is a 409; a password over 72 bytes is a 400; `/error` is public so a server error is not reported as 401 | `AuthTest` (11) |
| 20 simultaneous failed logins (more than the 10-connection pool) are all 401 and all audited | `LoginConcurrencyTest` (1) |
| Audit entries for register and login (including failed); filtering; page size capped at 100. Transfer audit rows (including failed ones) are asserted in `TransferTest` | `AuditTest` (5) |
| Ownership (404 for other people's accounts), paging, amount rules | `AccountTest` (7), `MoneyTest` (2) |
| Deposits, freezing, non-integer, string, missing and out-of-range amounts rejected | `OperatorActionsTest` (8) |
| Transfers: correct balances and entries, insufficient funds, same account, missing or frozen accounts, nothing changes on failure; missing fields and string amounts are 400 | `TransferTest` (11) |
| Idempotency: same key and body replays, different body is 422, keys are per user, a failed attempt can be retried | `IdempotencyTest` (6) |
| 50 simultaneous transfers from one account: exactly 33 succeed, 17 get 422, final balance exact | `ConcurrencyTest` (3) |
| 40 crossed transfers (A to B and B to A) finish with no deadlock | `ConcurrencyTest` |
| 20 parallel requests with one idempotency key apply it once | `ConcurrencyTest` |
| OpenAPI document lists the endpoints and the bearer scheme; health endpoint is up | `OpenApiTest` (2) |

After every concurrency test an invariant is checked ([`Invariants.java`](src/test/java/com/cuentasapi/common/Invariants.java)): each balance equals the sum of its ledger entries, none is negative, and the total money equals what operators deposited. To check that these tests can fail, the row lock was removed once and two of the three concurrency tests failed (the third is protected by the unique key instead). The suite was run four times in a row with no flaky result before the review fixes.

## Run it

Requirements: Docker. To build and test outside Docker you also need Maven and a JDK 23.

```bash
cp .env.example .env          # then edit the three values
docker compose up -d --build
curl localhost:8080/actuator/health
```

Open `http://localhost:8080/swagger-ui/index.html`, or use [`requests.http`](requests.http) (VS Code REST Client). The full flow in that file (register, log in, open accounts, deposit, transfer, repeat the transfer, read history and audit) was also run against the Docker stack with `curl`; the repeated transfer returned the same body and moved the money once.

```bash
mvn verify                    # 58 tests; needs Docker running for Testcontainers
```

## Known limits

- One currency, no fees, no scheduled transfers, no refresh tokens, no email, no front end.
- Java 23 is not an LTS release. It is what is installed on the development machine, and local runs, Docker and CI all use it so they match. Java 23 no longer receives security updates, so before any real use move to Java 25 (LTS), which is a version bump.
- The operator is created from `OPERATOR_EMAIL` and `OPERATOR_PASSWORD`; changing the password means changing the user in the database.
- Deposits are not idempotent: an operator who retries a deposit credits it twice.
- A transfer that fails is not stored for idempotency: repeating it re-evaluates it. Only successful transfers replay.
- Local only. It has not been deployed, and the GitHub Actions workflow ([`ci.yml`](.github/workflows/ci.yml)) has not run yet, so CI is unverified until the first push.

## AI usage

This project was built with Claude Code as a coding assistant, working from a written spec and plan in [`docs/superpowers/`](docs/superpowers/). The owner chose the project, the domain, the roles, the money model and the Java version, and approved the design and plan. This section is a draft: the owner should review and rewrite it in their own words before publishing the repository.
