# Accounts and Transfers API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A bank-style REST API (accounts, ledger, transfers, audit) in Java where money is never created or lost, even under concurrency and retries.

**Architecture:** One Spring Boot module, packages by feature (`auth`, `audit`, `account`, `transfer`, `common`). Spring Data JPA with PostgreSQL and Flyway; stateless JWT security using Spring Security's own resource server (no extra JWT library). Transfers lock both accounts in ascending id order inside one transaction; idempotency is a row inserted first under a unique constraint.

**Tech Stack:** Java 21, Spring Boot 3.5.16 (Maven), Spring Security + oauth2-resource-server (Nimbus, HS256), Spring Data JPA, Flyway, PostgreSQL 16, springdoc-openapi-starter-webmvc-ui 2.10.0, JUnit 5, Mockito, Testcontainers (via `spring-boot-testcontainers`), Docker, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-10-cuentas-api-design.md`

## Global Constraints

- Java 21; Spring Boot 3.5.16; Maven; springdoc 2.10.0 (its pom builds on Boot 3.5.16). The machine has no JDK 21: Task 1 installs `openjdk@21` with Homebrew after the user confirms, and builds use `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.
- Base package `com.cuentasapi`; project folder `cuentas_api/`; code, docs and commit messages in English.
- One currency (MXN). Every amount is a `long` in centavos in the API, the entities and the database; never `double` or `BigDecimal`.
- Roles `CLIENTE` and `OPERADOR`. Routes under `/api/v1`. Errors are `application/problem+json` (Spring `ProblemDetail`).
- A customer asking for an account that is not theirs gets 404, never 403. Error statuses: insufficient balance 422, frozen account 409, missing `Idempotency-Key` 400, same key with a different body 422, invalid body 400.
- JWT lifetime 15 minutes. Local work only: no deployment, no video, no `git push` without asking the user.
- Every commit message ends with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- CI must be green before the project counts as done. Docs say what was measured only if a test or command produced it.

## Review Focus

Behaviors the spec implies that no feature test would otherwise pin; each has a test in the task named.

1. A transfer between the same account is rejected with 400, not silently accepted (Task 6).
2. A transfer to a nonexistent account returns 404 and to a frozen target 409, and neither changes any balance (Task 6).
3. Registering the same email with different letter case returns 409, not a second user (Task 2).
4. A huge `size` page parameter is capped at 100, and an amount that is not a whole number of centavos (`1.5`) or exceeds 10,000,000,000 centavos per operation returns 400 (Tasks 4 and 5).
5. An expired or tampered JWT returns 401 with a problem body, not 500 (Task 2).

## File Structure

```
pom.xml, Dockerfile, compose.yaml, .gitignore, .dockerignore, README.md, requests.http
.github/workflows/ci.yml
src/main/resources/application.yml
src/main/resources/db/migration/V1__schema.sql
src/main/java/com/cuentasapi/
  CuentasApiApplication.java
  common/   ApiException.java, ApiExceptionHandler.java, Money.java
  auth/     Role.java, User.java, UserRepository.java, JwtService.java, SecurityConfig.java,
            AuthService.java, AuthController.java, CurrentUser.java, OperatorSeeder.java
  audit/    AuditEntry.java, AuditRepository.java, AuditService.java, AuditController.java
  account/  Account.java, AccountStatus.java, AccountRepository.java, LedgerEntry.java,
            LedgerEntryRepository.java, AccountService.java, AccountController.java
  transfer/ Transfer.java, TransferRepository.java, IdempotencyKey.java,
            IdempotencyRepository.java, TransferService.java, TransferController.java
src/test/java/com/cuentasapi/   (mirrors main; IntegrationTest.java is the shared base)
```

---

### Task 1: Project skeleton, schema and test base

**Files:**
- Create: `pom.xml`, `.gitignore`, `src/main/resources/application.yml`, `src/main/resources/db/migration/V1__schema.sql`, `src/main/java/com/cuentasapi/CuentasApiApplication.java`
- Test: `src/test/java/com/cuentasapi/IntegrationTest.java`, `src/test/java/com/cuentasapi/SchemaTest.java`

**Interfaces:**
- Produces: abstract `IntegrationTest` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Testcontainers`, a static `PostgreSQLContainer<?>` of `postgres:16-alpine` shared by all tests via `@ServiceConnection`, protected `TestRestTemplate rest` and `JdbcTemplate jdbc` autowired, and a `@BeforeEach void clean()` that truncates `audit_log, idempotency_keys, ledger_entries, transfers, accounts, users` with `restart identity cascade`). All later tests extend it.
- Produces: the V1 schema below, which every later task relies on.

- [ ] **Step 1: Install and verify the JDK.** Ask the user to confirm, then run `brew install openjdk@21`. Verify: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home mvn -v` shows `Java version: 21`. Docker Desktop must be running for Testcontainers. Do not change the user's default Java.
- [ ] **Step 2: Write `pom.xml`:** parent `spring-boot-starter-parent` 3.5.16, `java.version` 21, dependencies `web`, `validation`, `data-jpa`, `security`, `oauth2-resource-server`, `flyway-core` plus `flyway-database-postgresql`, `postgresql` (runtime), `springdoc-openapi-starter-webmvc-ui` 2.10.0; test scope `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, `testcontainers` `junit-jupiter` and `postgresql`. `spring-boot-maven-plugin` builds an executable jar.
- [ ] **Step 3: Write `application.yml`:** datasource from env with local defaults (`jdbc:postgresql://localhost:5432/cuentas`, user and password `cuentas`), `spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.open-in-view: false`, `app.jwt.secret` from env `JWT_SECRET` (default a 32+ character dev value, with a startup failure if shorter than 32 bytes), `app.jwt.ttl-minutes: 15`.
- [ ] **Step 4: Write the failing test `SchemaTest`.** `schema_rejects_negative_balance`: insert a user, then inserting an account with `balance = -1` through `jdbc` throws `DataIntegrityViolationException`. `schema_enforces_unique_idempotency_key_per_user`: two rows with the same (user_id, key) throw the same exception.
- [ ] **Step 5: Run to verify it fails.** `mvn -q test -Dtest=SchemaTest` fails (no application class or schema).
- [ ] **Step 6: Write `V1__schema.sql` and `CuentasApiApplication`.** Tables exactly as in the spec: `users(id bigserial pk, email text unique not null, password_hash text not null, role text not null)`; `accounts(id bigserial pk, user_id fk, number text unique not null, balance bigint not null check (balance >= 0), status text not null default 'ACTIVE', version bigint not null default 0)`; `transfers(id bigserial pk, source_account_id fk, target_account_id fk, amount bigint not null check (amount > 0), status text not null, created_at timestamptz not null)`; `ledger_entries(id bigserial pk, account_id fk, transfer_id fk null, type text not null, amount bigint not null check (amount > 0), balance_after bigint not null, created_at timestamptz not null)`; `idempotency_keys(id bigserial pk, user_id fk, key text not null, request_hash text not null, response_status int, response_body text, unique (user_id, key))`; `audit_log(id bigserial pk, actor_id bigint null, action text not null, target text, outcome text not null, created_at timestamptz not null)`. Indexes on `accounts(user_id)`, `ledger_entries(account_id, id)`, `audit_log(created_at)`. Also `create sequence account_number_seq start 100000000001` for account numbers.
- [ ] **Step 7: Run to verify it passes.** `mvn -q test -Dtest=SchemaTest` passes.
- [ ] **Step 8: Commit** `build: add project skeleton, schema and Testcontainers base`.

### Task 2: Errors, authentication and roles

**Files:**
- Create: `common/ApiException.java`, `common/ApiExceptionHandler.java`, `auth/Role.java`, `auth/User.java`, `auth/UserRepository.java`, `auth/JwtService.java`, `auth/SecurityConfig.java`, `auth/AuthService.java`, `auth/AuthController.java`, `auth/CurrentUser.java`, `auth/OperatorSeeder.java`
- Test: `auth/AuthTest.java`

**Interfaces:**
- Produces: `ApiException(HttpStatus status, String title)` with static `notFound(String)`, `conflict(String)`, `unprocessable(String)`, `badRequest(String)`; `ApiExceptionHandler` turns it, `MethodArgumentNotValidException`, `HttpMessageNotReadableException` and `MissingRequestHeaderException` into `ProblemDetail` with the matching status (400 for the last three).
- Produces: `record RegisterRequest(@Email @NotBlank String email, @Size(min = 8, max = 72) String password)`, `record LoginRequest(String email, String password)`, `record TokenResponse(String token)`.
- Produces: `JwtService.issue(User) -> String` (HS256, subject = user id as text, claim `role`, 15 minutes); `CurrentUser.id() -> long` and `CurrentUser.isOperator() -> boolean`, both read from the `Authentication` in the security context.
- Produces: `SecurityConfig` with a stateless filter chain: `/api/v1/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**` and `/actuator/health` open; everything else authenticated; 401 and 403 written as problem JSON; `GET /api/v1/audit`, `POST .../deposits`, `.../freeze`, `.../unfreeze` require role `OPERADOR`, `POST /transfers` and `POST /accounts` require `CLIENTE`. Role comes from the `role` claim with the `ROLE_` prefix.
- Produces: `OperatorSeeder`, an `ApplicationRunner` that creates one `OPERADOR` from env `OPERATOR_EMAIL` and `OPERATOR_PASSWORD` when both are set and the email does not exist (the spec's "startup script"; there is no API to create operators).

- [ ] **Step 1: Write the failing tests in `AuthTest`.** `register_creates_a_cliente_and_login_returns_a_token`; `register_twice_with_different_letter_case_is_409` (`A@x.com` then `a@X.com`; emails are stored lower-case); `short_password_is_400`; `wrong_password_is_401` and `unknown_email_is_401` return the same body; `protected_route_without_token_is_401_problem_json`; `tampered_token_is_401` (flip a character of the signature); `expired_token_is_401` (token built in the test with the same secret and an `exp` in the past); `cliente_cannot_reach_operator_route_403` (`GET /api/v1/audit`); `operator_is_seeded_from_env` (set `OPERATOR_EMAIL` and `OPERATOR_PASSWORD` through `@DynamicPropertySource`, login works, role claim is `OPERADOR`).
- [ ] **Step 2: Run to verify they fail.** `mvn -q test -Dtest=AuthTest`.
- [ ] **Step 3: Implement the classes above.** Passwords hashed with `BCryptPasswordEncoder`. Login failure uses one message for both cases. The `JwtDecoder` is a `NimbusJwtDecoder` with the HS256 secret; the `JwtEncoder` a `NimbusJwtEncoder` over `ImmutableSecret`.
- [ ] **Step 4: Run to verify they pass.** `mvn -q test -Dtest=AuthTest`.
- [ ] **Step 5: Commit** `feat: add problem+json errors, registration, login and role security`.

### Task 3: Audit log

**Files:**
- Create: `audit/AuditEntry.java`, `audit/AuditRepository.java`, `audit/AuditService.java`, `audit/AuditController.java`
- Test: `audit/AuditTest.java`

**Interfaces:**
- Consumes: `CurrentUser`, the `audit_log` table, the Task 2 role rules.
- Produces: `AuditService.record(Long actorId, String action, String target, String outcome)` (runs in the caller's transaction, `Propagation.REQUIRED`) and `AuditService.recordIndependent(...)` with the same parameters in its own transaction (`Propagation.REQUIRES_NEW`), for entries that must survive a rollback; `GET /api/v1/audit?actor={id}&action={text}&page=0&size=20` returning a page of `{id, actorId, action, target, outcome, createdAt}`, newest first. `size` is capped at 100.
- Produces: `PageSizes.cap(int size) -> int` in `common/` (default 20, minimum 1, maximum 100), used by every paged endpoint.

- [ ] **Step 1: Write the failing tests in `AuditTest`.** `registering_and_logging_in_are_audited` (actions `REGISTER` and `LOGIN`, with `outcome` `OK` or `FAILED`; a failed login records the attempt with a null actor); `operator_can_filter_by_action_and_actor`; `size_above_100_is_capped_to_100` (request `size=100000`, the response `size` is 100); `cliente_gets_403`. Wire `AuditService.record` calls into `AuthService` for register and login.
- [ ] **Step 2: Run to verify they fail.** `mvn -q test -Dtest=AuditTest`.
- [ ] **Step 3: Implement** the entity, the repository (`findAll(Specification)` or a JPQL query with optional filters, ordered by `created_at desc, id desc`), the service, the controller and `PageSizes`.
- [ ] **Step 4: Run to verify they pass.** `mvn -q test`, whole suite green.
- [ ] **Step 5: Commit** `feat: add audit log and operator audit endpoint`.

### Task 4: Accounts and ledger reads

**Files:**
- Create: `account/Account.java`, `account/AccountStatus.java`, `account/AccountRepository.java`, `account/LedgerEntry.java`, `account/LedgerEntryRepository.java`, `account/AccountService.java`, `account/AccountController.java`, `common/Money.java`
- Test: `account/AccountTest.java`

**Interfaces:**
- Consumes: `ApiException`, `CurrentUser`, `AuditService`, `PageSizes`.
- Produces: `Account` entity (`@Version long version`, `long balance`, `AccountStatus status`, `Long userId`, `String number`); `AccountRepository.lockAllById(Collection<Long> ids) -> List<Account>` with `@Lock(PESSIMISTIC_WRITE)` and `@Query("select a from Account a where a.id in :ids order by a.id")`.
- Produces: `Money.MAX = 10_000_000_000L` and `Money.requireValid(long amount)` throwing `ApiException.badRequest` unless `1 <= amount <= MAX`.
- Produces: `AccountService.open(long userId) -> Account` (number: the next value of `account_number_seq` as text, 12 digits, fetched with a native query), `get(long id, long userId, boolean operator) -> Account` (404 when not found or not owned and not operator), `list(long userId, boolean operator, Pageable)`, `entries(long accountId, long userId, boolean operator, Pageable)`. Responses are records `AccountResponse(long id, String number, long balance, String status)` and `EntryResponse(long id, String type, long amount, long balanceAfter, Long transferId, Instant createdAt)`.
- Produces: `POST /api/v1/accounts`, `GET /api/v1/accounts`, `GET /api/v1/accounts/{id}`, `GET /api/v1/accounts/{id}/entries` (history newest first, size capped by `PageSizes`).

- [ ] **Step 1: Write the failing tests in `AccountTest`.** `cliente_opens_account_with_zero_balance`; `cliente_lists_only_own_accounts`; `other_customers_account_is_404_not_403`; `operator_sees_every_account`; `nonexistent_account_is_404`; `entries_are_paged_newest_first_and_capped_at_100` (insert entries with `jdbc`); `operator_cannot_open_an_account_403`; `money_validation` as a unit test of `Money.requireValid` for 0, -1, 1, `MAX`, `MAX + 1`.
- [ ] **Step 2: Run to verify they fail.** `mvn -q test -Dtest=AccountTest`.
- [ ] **Step 3: Implement** the classes above; record `ACCOUNT_OPENED` in the audit log.
- [ ] **Step 4: Run to verify they pass.** `mvn -q test`.
- [ ] **Step 5: Commit** `feat: add accounts, ledger history and ownership rules`.

### Task 5: Operator deposits, freeze and unfreeze

**Files:**
- Modify: `account/AccountService.java`, `account/AccountController.java`
- Test: `account/OperatorActionsTest.java`

**Interfaces:**
- Consumes: Task 4 classes.
- Produces: `AccountService.deposit(long accountId, long amount, long operatorId) -> Account` (`@Transactional`, locks the account with `lockAllById(List.of(id))`, `Money.requireValid`, 409 if frozen, adds a `CREDIT` ledger entry with `transfer_id` null and the resulting balance, audits `DEPOSIT`); `freeze(long, long)` and `unfreeze(long, long)` (idempotent, audited as `FREEZE` and `UNFREEZE`). Request body `record DepositRequest(long amount)`.
- Produces: `POST /api/v1/accounts/{id}/deposits`, `/freeze`, `/unfreeze`, operator only.

- [ ] **Step 1: Write the failing tests in `OperatorActionsTest`.** `deposit_credits_balance_and_writes_a_credit_entry`; `deposit_to_frozen_account_is_409`; `deposit_amount_1_5_is_400` (send `{"amount":1.5}`); `deposit_above_max_is_400`; `deposit_zero_or_negative_is_400`; `cliente_cannot_deposit_403`; `freeze_then_unfreeze_changes_status_and_both_are_audited`; `deposit_to_nonexistent_account_is_404`.
- [ ] **Step 2: Run to verify they fail.** `mvn -q test -Dtest=OperatorActionsTest`.
- [ ] **Step 3: Implement.** Jackson must reject fractional amounts for a `long` field: set `spring.jackson.deserialization.accept-float-as-int: false` in `application.yml`.
- [ ] **Step 4: Run to verify they pass.** `mvn -q test`.
- [ ] **Step 5: Commit** `feat: add operator deposits and account freezing`.

### Task 6: Transfers (without idempotency)

**Files:**
- Create: `transfer/Transfer.java`, `transfer/TransferRepository.java`, `transfer/TransferService.java`, `transfer/TransferController.java`
- Test: `transfer/TransferTest.java`

**Interfaces:**
- Consumes: `AccountRepository.lockAllById`, `LedgerEntry`, `Money`, `AuditService`.
- Produces: `record TransferRequest(long sourceAccountId, long targetAccountId, long amount)`; `record TransferResponse(long id, long sourceAccountId, long targetAccountId, long amount, String status, Instant createdAt)`.
- Produces: `TransferService.execute(long userId, TransferRequest) -> TransferResponse`, `@Transactional`, in this order: reject equal accounts (400) and invalid amount (400); `lockAllById(List.of(source, target))` sorted ascending; 404 if either is missing or the source is not the caller's; 409 if either is `FROZEN`; 422 if `source.balance < amount`; insert the `Transfer` (status `COMPLETED`), a `DEBIT` and a `CREDIT` entry (each with `balance_after`), update both balances; audit `TRANSFER` with outcome `OK`. A failed attempt audits `TRANSFER` with outcome `FAILED: <reason>` through `AuditService.recordIndependent` so the audit survives the rollback.
- Produces: `POST /api/v1/transfers` (201), `GET /api/v1/transfers/{id}` (owner of the source account or operator; otherwise 404).

- [ ] **Step 1: Write the failing tests in `TransferTest`.** `transfer_moves_money_and_writes_two_entries` (balances and both ledger rows with correct `balance_after`); `insufficient_balance_is_422_and_changes_nothing`; `same_account_is_400`; `target_does_not_exist_is_404_and_changes_nothing`; `frozen_source_is_409`; `frozen_target_is_409_and_changes_nothing`; `source_account_of_someone_else_is_404`; `amount_zero_negative_or_above_max_is_400`; `failed_transfer_is_audited`; `get_transfer_by_owner_ok_by_stranger_404`.
- [ ] **Step 2: Run to verify they fail.** `mvn -q test -Dtest=TransferTest`.
- [ ] **Step 3: Implement** as specified above.
- [ ] **Step 4: Run to verify they pass.** `mvn -q test`.
- [ ] **Step 5: Commit** `feat: add transfers with ordered row locking`.

### Task 7: Idempotency

**Files:**
- Create: `transfer/IdempotencyKey.java`, `transfer/IdempotencyRepository.java`
- Modify: `transfer/TransferService.java`, `transfer/TransferController.java`
- Test: `transfer/IdempotencyTest.java`

**Interfaces:**
- Consumes: Task 6 `TransferService`.
- Produces: `POST /api/v1/transfers` requires header `Idempotency-Key` (1 to 80 printable characters, else 400). `TransferService.execute` gains parameters `String key` and `String requestHash` (SHA-256 hex of the canonical request: `source:target:amount`).
- Produces: algorithm. In the same transaction, before locking accounts, insert the `idempotency_keys` row (response columns null). A duplicate (user, key) raises a unique violation: roll back, re-read the row in a new transaction; if the hash differs return 422; if the response is stored return its stored status and body unchanged; the stored body is the JSON `TransferResponse`. On success the response is written to the row in the same transaction. Failed transfers (4xx) are not stored, so the same key may be retried after the cause is fixed, and a repeated failed request is evaluated again.

- [ ] **Step 1: Write the failing tests in `IdempotencyTest`.** `missing_key_is_400`; `blank_or_longer_than_80_key_is_400`; `same_key_and_body_returns_the_same_response_and_applies_once` (one transfer row, one debit); `same_key_with_a_different_amount_is_422`; `same_key_for_two_different_users_is_independent`; `key_is_reusable_after_a_failed_attempt` (first call 422 insufficient balance, deposit, second call with the same key succeeds).
- [ ] **Step 2: Run to verify they fail.** `mvn -q test -Dtest=IdempotencyTest`.
- [ ] **Step 3: Implement** as specified above.
- [ ] **Step 4: Run to verify they pass.** `mvn -q test`.
- [ ] **Step 5: Commit** `feat: make transfers idempotent with a unique key per user`.

### Task 8: Concurrency tests and the money invariant

**Files:**
- Test: `transfer/ConcurrencyTest.java`, `common/Invariants.java` (test helper)

**Interfaces:**
- Consumes: the whole API. `Invariants.assertConsistent(JdbcTemplate jdbc, long expectedTotal)` asserts: every account's `balance` equals the sum of its signed ledger entries (`CREDIT` positive, `DEBIT` negative), no balance is negative, and the sum of all balances equals `expectedTotal` (the sum of operator deposits).

- [ ] **Step 1: Write the tests in `ConcurrencyTest`** using an `ExecutorService` and a `CountDownLatch` so all calls start together, through real HTTP calls with `TestRestTemplate`. `fifty_simultaneous_transfers_from_one_account` (balance 10,000; fifty transfers of 300 to one target: exactly 33 succeed, 17 get 422, final source balance 100, target 9,900); `crossed_transfers_do_not_deadlock` (A to B and B to A, 20 each, every call returns 201 or 422 within a 30 second timeout); `same_idempotency_key_in_parallel_applies_once` (20 parallel calls with one key: one 201, the rest 201 with the same body, exactly one transfer row). Each test ends with `Invariants.assertConsistent`.
- [ ] **Step 2: Run them.** `mvn -q test -Dtest=ConcurrencyTest`. Expected after Tasks 6 and 7: PASS. If one fails, the defect is in Tasks 6 or 7: fix it there with a failing test first, not by adjusting the assertion.
- [ ] **Step 3: Run the whole suite 3 times** (`mvn -q test` x3) to look for flaky timing. Expected: green each time.
- [ ] **Step 4: Commit** `test: add concurrency tests and the money invariant`.

### Task 9: Docker, CI, API docs and README

**Files:**
- Create: `Dockerfile`, `.dockerignore`, `compose.yaml`, `.github/workflows/ci.yml`, `requests.http`, `README.md`
- Test: `OpenApiTest.java`

**Interfaces:**
- Consumes: the finished API.
- Produces: `Dockerfile` multi-stage (`eclipse-temurin:21-jdk` builds with `mvn -q -DskipTests package`, `eclipse-temurin:21-jre` runs the jar as a non-root user); `compose.yaml` with `db` (`postgres:16-alpine`, healthcheck) and `api` (waits for `db` healthy, env `JWT_SECRET`, `OPERATOR_EMAIL`, `OPERATOR_PASSWORD` required with no default, published on `127.0.0.1:8080` only).
- Produces: CI job on `ubuntu-latest` with `actions/setup-java` (Temurin 21, Maven cache): `mvn -B verify`, then `docker build .`.

- [ ] **Step 1: Write the failing test `OpenApiTest`.** `api_docs_list_every_endpoint`: `GET /v3/api-docs` returns 200 and its `paths` contain `/api/v1/transfers`, `/api/v1/accounts/{id}/deposits` and `/api/v1/audit`. Add `@Operation` summaries and `@SecurityScheme` (bearer JWT) so Swagger UI can authorize.
- [ ] **Step 2: Run to verify it fails, then implement and verify it passes.** `mvn -q test -Dtest=OpenApiTest`.
- [ ] **Step 3: Write the Docker files, then verify.** `docker compose up -d --build`, then `curl -s localhost:8080/actuator/health` returns `{"status":"UP"}` (add `spring-boot-starter-actuator` and expose only `health`); stop with `docker compose down`. The `docker credential` problem in this shell needs `export PATH="$PATH:/Applications/Docker.app/Contents/Resources/bin"`.
- [ ] **Step 4: Write `requests.http`:** register, login, open two accounts, deposit as operator, transfer with `Idempotency-Key`, the same transfer repeated, history, audit. Run the sequence against the running stack and check the repeat returns the same body.
- [ ] **Step 5: Write `README.md`** in English: what it is, decisions and reasons (centavos, ordered locking, idempotency table, 404 for foreign accounts), a table of the tests that prove each guarantee with the real count from `mvn verify`, known limits (single currency, no refresh tokens, operator seeded by env, local only), how to run it, and an "AI usage" section that states Claude Code was used from a written spec and plan and asks the user to review and complete it before the repo is published.
- [ ] **Step 6: Verify end to end.** `mvn -B verify` green; then, only if the user approves a push, CI green on GitHub. Without a push, say CI is unverified.
- [ ] **Step 7: Commit** `build: add Docker stack, CI workflow, API docs and README`.
