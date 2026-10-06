# Spendwise Backend Contract for Android

Produced by **P2 — Spendwise Backend Contract Audit**. Every claim is grounded in
backend source at the audited commit. Claims are tagged **VERIFIED** (direct
code/config/test evidence), **INFERRED** (strongly implied by verified code),
**NOT_EVIDENCED** (nothing found), or **FRAMEWORK_DEFAULT_INFERENCE** (behavior
follows a Spring Boot default that project config does not override).

This document records **what exists now**. It does not propose backend changes
and does not decide Android architecture (P4).

## 1. Audit identity

| Field | Value |
|---|---|
| Backend path | `D:\spendwise` |
| Branch | `main` |
| HEAD | `8194daa9959f2d568dc13ab0f211787195efc79d` |
| Audit date | 2026-10-04 |
| Audit mode | Static, read-only source audit. No builds, tests, runtime, HTTP calls, migrations, or Docker were executed. |
| Source precedence | Controllers → DTOs/serialization → exception handlers → services → repositories/migrations → tests → `application.properties` → clean committed docs → comments. The three uncommitted user docs (`README.md`, `HUONG-DAN-SU-DUNG.md`, `docs/SPENDWISE-REQ.md`) were excluded as non-authoritative and were not modified. |
| Limitations | Runtime behavior (actual wire bytes, real vendor FX responses, JDBC/JPA runtime details, clock/tz of the host) was not executed; framework defaults are labeled. |

## 2. Executive summary

1. The backend is a **JWT-secured, multi-user Spring Boot 3.5.16 REST API**
   (single Maven module, feature-packaged `com.spendwise`, Postgres + Flyway
   V1–V10). Registration and login exist; all `/api/**` other than
   `register`/`login` require a Bearer token. **VERIFIED** — `SecurityConfig.java`.
2. **26 HTTP handlers across 12 controllers.** Everything returns JSON except
   one CSV export. There is **no PUT/PATCH anywhere**; DELETE exists only as
   soft-deactivation on rules and recurring templates. **VERIFIED**.
3. **Transfers are implemented but have no HTTP endpoint** — `TransferService`
   is `@Transactional`, two-legged, atomic, and internally consistent; nothing
   can reach it over the wire. **VERIFIED**; the backend's own committed
   `docs/defense-audit.md` acknowledges this as intended for this build.
4. **Money is `BigDecimal` scale-2 HALF_UP + ISO-4217 currency string**, always
   positive with direction carried by `TransactionType`
   (`INCOME/EXPENSE/TRANSFER_IN/TRANSFER_OUT`). Wire amount is a JSON number.
   Cross-currency operations are rejected, never converted. **VERIFIED**.
5. **Balances are stored columns** (`accounts.balance_amount/balance_currency`)
   mutated inside the same DB transaction as each ledger insert via one
   canonical writer: `TransactionCreationService.create()`. The ledger is
   append-only: **no transaction update, delete, or reversal exists.** VERIFIED.
6. **Transaction `occurredOn` is now a first-class wire field** — **M11 /
   REQ-BE-001 DELIVERED** (backend `bc6e0bb`). `CreateTransactionRequest`
   accepts an optional ISO `yyyy-MM-dd` `occurredOn` (omission preserves the
   pre-M11 contract: the server stamps `LocalDate.now()`); far-future dates
   beyond tomorrow are rejected at the manual-create boundary.
   `TransactionResponse` always returns the persisted `occurredOn`.
   (Pre-M11 snapshot: no `occurredOn` on either DTO — visible only in CSV
   export. — VERIFIED at audit HEAD.)
7. **`description` is never persisted or returned** — it exists only as
   rule-engine input on manual create and on imported rows. **VERIFIED**.
8. **Import is synchronous**: `POST /api/imports` (multipart `file` +
   `accountId` param) parses CSV or PDF, dedups per row, and returns a full
   row-level report in the 201 body. **VERIFIED**.
9. **CSV** accepts exactly `occurredOn,amount,currency,description,sourceTxnId`
   with signed amounts. **PDF** supports the TPBank statement layout
   (multi-page verified in tests) plus one synthetic test layout; every other
   layout → 400. No OCR; encrypted/image-only PDFs → 400. **VERIFIED**.
10. **Dedup is a two-tier deterministic `sourceRef` scheme** enforced by
    `UNIQUE(user_id, source_ref)`; re-imports produce `DUPLICATE` rows, not
    errors, and are idempotent. **VERIFIED**.
11. **No pagination, no date-range filter, no search.** `GET /api/transactions`
    returns the caller's entire ledger (optionally filtered by
    `accountId`/`type`/`category`). **VERIFIED**.
12. **Error model is RFC-7807 `ProblemDetail`** (PARTIALLY_TYPED): status +
    fixed title + sanitized detail; bean-validation errors add a `fields` map.
    404s have an empty body. Domain messages are deliberately swallowed, so
    clients cannot distinguish most 400 causes programmatically. **VERIFIED**.
13. **Dashboard exists**: `GET /api/dashboard?month=YYYY-MM` returns
    income/expense totals, category breakdown, accounts, and budget
    calculations, all computed per request. Currency checks are
    **per-aggregation**: same-type mixed currencies → 400, but
    income-in-USD + expense-in-VND returns 200 with unlabeled,
    currency-ambiguous totals (response carries no currency fields).
    **VERIFIED**.
14. **Recurring transactions** (WEEKLY/MONTHLY) are managed by a 15-min
    scheduler that generates ledger rows through the same canonical write
    path with deterministic idempotent sourceRefs. **VERIFIED**.
15. **Notable gaps for Android**: no transfer endpoint, no edit/delete on
    transactions/accounts/budgets, no refresh token (15-min JWT only), no
    pagination, no server-side date filtering/search, no import batch list,
    multipart limited to ~1 MB by framework defaults. See §38.

## 3. Backend topology relevant to API

Single Maven module, Spring Boot 3.5.16 (`pom.xml`), Java 17. Entry point:
`SpendwiseApp` under `com.spendwise`. Packaging is **feature-based**:

| Package | Role | Key classes |
|---|---|---|
| `account` | accounts, transactions, transfers, balances | `AccountController`, `TransactionController`, `Account`, `Transaction`, `AccountService`, `TransactionService`, `TransactionCreationService`, `TransferService`, `BalanceReconciliationService` |
| `auth` | identity + JWT | `AuthController`, `SecurityConfig`, `AuthService`, `AppUserService`, `NimbusTokenIssuer`, `TokenBlacklist`, `CurrentUser` |
| `budget` | monthly category budgets | `BudgetController`, `Budget`, `BudgetService`, `BudgetCalculationService` |
| `category` | fixed reference categories | `CategoryController`, `Category`, `CategoryService` (cached) |
| `dashboard` | monthly summary read model | `DashboardController`, `DashboardReadService` |
| `fx` | display-only currency conversion | `FxController`, `ExchangeRateClient` (WebClient) |
| `imports` | statement import + export | `ImportController`, `ImportReportController`, `CsvExportController`, `ImportService`, `CsvStatementParser`, `PdfStatementParser`, `TpbankStatementLayout`, `RowIdentityService`, `ImportRowProcessor/Recorder`, `ImportBatch`, `ImportRow` |
| `recurring` | recurring templates | `RecurringTransactionController`, `RecurringTransactionService`, `RecurringGenerationService` |
| `rule` | auto-categorization rules | `CategorizationRuleController`, `CategorizationRule`, `RuleEngine` |
| `scheduling` | periodic jobs | `RecurringSchedulingJob`, `ReconciliationJob`, `SchedulingConfig` |
| `shared` | cross-cutting | `Money`, `ApiExceptionHandler`, `TraceIdFilter`, `AsyncConfig`, `CacheConfig`, `DomainException` family |
| `statistics` | internal analytics | `StatisticsService`, `AdvancedStatisticsService` (no HTTP) |

Persistence: Spring Data JPA + Flyway (`db/migration/V1–V10`),
`ddl-auto=validate`. IDs are application-generated UUIDs (no `@GeneratedValue`
anywhere — VERIFIED by grep). Migrations define client-relevant constraints:
`CHECK (amount > 0)`, `NUMERIC(19,2)`, `UNIQUE(user_id, source_ref)` (V10),
`UNIQUE(user_id, category_code, period)` budgets (V6),
`UNIQUE(user_id, priority)` rules (V8), `user_id NOT NULL` on accounts/
transactions (V4), `occurred_on DATE` (V5), `transfer_ref VARCHAR(36)` (V3).

## 4. Global HTTP contract

| Property | Contract | Evidence / tag |
|---|---|---|
| Port | 8080 | `application.properties` (absent) — FRAMEWORK_DEFAULT_INFERENCE |
| Context path | none; paths are absolute | FRAMEWORK_DEFAULT_INFERENCE |
| API prefix | `/api` on all business endpoints | every `@RequestMapping` — VERIFIED |
| Versioning | none | VERIFIED absence |
| Serialization | Jackson defaults: `LocalDate`/`Instant` → ISO-8601 strings; enums → `name()`; `BigDecimal` → JSON number; nulls included | no `ObjectMapper`/`@Json*` anywhere in main (grep) — VERIFIED |
| Content types | `application/json` default; `multipart/form-data` on import; `text/csv` on export | controllers — VERIFIED |
| CORS | none configured (browser cross-origin blocked; Android unaffected) | no `cors()`/`WebMvcConfigurer` — FRAMEWORK_DEFAULT_INFERENCE |
| CSRF | disabled (stateless Bearer API) | `SecurityConfig.java` — VERIFIED |
| Multipart limits | not configured → Boot defaults `max-file-size=1MB`, `max-request-size=10MB`; exceeded → `MultipartException` → generic 500 | `application.properties` (absent) + `ApiExceptionHandler` — FRAMEWORK_DEFAULT_INFERENCE |
| Compression | not configured | FRAMEWORK_DEFAULT_INFERENCE |
| Trace | `X-Trace-Id` request header echoed or generated; MDC `traceId` in logs | `shared/TraceIdFilter.java` — VERIFIED |
| Docs | springdoc OpenAPI: `/v3/api-docs`, `/swagger-ui.html` (permitAll) | `application.properties`, `SecurityConfig` — VERIFIED |
| Health | `GET /actuator/health` permitAll, `show-details=always`; `/actuator/metrics` requires JWT | `application.properties`, `SecurityConfig` — VERIFIED |
| Error surface | one `@RestControllerAdvice` (`ApiExceptionHandler`) → RFC-7807 `ProblemDetail` | `shared/ApiExceptionHandler.java` — VERIFIED |

`LOCAL_CONTRACT_SHAPE`: `http://<host>:8080/api/...`, Bearer JWT required,
JSON in/out. The emulator-host reachability issue is an Android environment
concern, not a backend contract.

## 5. Endpoint inventory

All endpoints require `Authorization: Bearer <JWT>` unless marked permitAll.
`404` rows mean the controller returns `ResponseEntity.notFound()` (empty
body). Sources: controller files in §42.

| Area | Method | Path | Request | Response | Success | Errors | Auth | Evidence |
|---|---|---|---|---|---|---|---|---|
| Auth | POST | `/api/auth/register` | `RegisterRequest` | `RegisterResponse` | 201 | 400 (validation; duplicate username) | permitAll | `AuthController.java` |
| Auth | POST | `/api/auth/login` | `LoginRequest` | `AuthResponse` | 200 | 400, 401 | permitAll | `AuthController.java` |
| Auth | POST | `/api/auth/logout` | — (JWT principal) | empty | 204 | 401 no-token | JWT | `AuthController.java` |
| Account | POST | `/api/accounts` | `CreateAccountRequest` | `AccountResponse` + `Location` | 201 | 400 | JWT | `AccountController.java` |
| Account | GET | `/api/accounts` | — | `List<AccountResponse>` | 200 | 401 | JWT | `AccountController.java` |
| Account | GET | `/api/accounts/{id}` | path `UUID id` | `AccountResponse` | 200 | 404 foreign/unknown, 401 | JWT | `AccountController.java` |
| Transaction | POST | `/api/transactions` | `CreateTransactionRequest` (+ optional `occurredOn` — **M11 / REQ-BE-001 delivered**) | `TransactionResponse` + `Location` | 201 | 400, 404 unknown/foreign `accountId` | JWT | `TransactionController.java` |
| Transaction | GET | `/api/transactions` | `?accountId?&type?&category?` | `List<TransactionResponse>` (incl. `occurredOn` — **M11 / REQ-BE-001 delivered**) | 200 | 400 bad enum/UUID, 404 foreign `accountId` | JWT | `TransactionController.java` |
| Transaction | GET | `/api/transactions/export` | `?accountId?` | `text/csv` file | 200 | 404 foreign `accountId` | JWT | `CsvExportController.java` |
| Category | GET | `/api/categories` | — | `List<CategoryResponse>` | 200 | 401 | JWT | `CategoryController.java` |
| Rules | POST | `/api/categorization-rules` | `CreateCategorizationRuleRequest` | `CategorizationRuleResponse` + `Location` | 201 | 400, 409 duplicate priority | JWT | `CategorizationRuleController.java` |
| Rules | GET | `/api/categorization-rules` | — | `List<CategorizationRuleResponse>` (priority asc) | 200 | 401 | JWT | `CategorizationRuleController.java` |
| Rules | GET | `/api/categorization-rules/{id}` | path `UUID id` | `CategorizationRuleResponse` | 200 | 404 | JWT | `CategorizationRuleController.java` |
| Rules | DELETE | `/api/categorization-rules/{id}` | path `UUID id` | `CategorizationRuleResponse` (disabled) | 200 | 404 | JWT | `CategorizationRuleController.java` |
| Budget | POST | `/api/budgets` | `CreateBudgetRequest` | `BudgetResponse` + `Location` | 201 | 400, 409 duplicate (user,category,period) | JWT | `BudgetController.java` |
| Budget | GET | `/api/budgets` | — | `List<BudgetResponse>` | 200 | 401 | JWT | `BudgetController.java` |
| Budget | GET | `/api/budgets/{id}` | path `UUID id` | `BudgetResponse` | 200 | 404 | JWT | `BudgetController.java` |
| Budget | GET | `/api/budgets/{id}/calculation` | path `UUID id` | `BudgetCalculationResponse` | 200 | 404 | JWT | `BudgetController.java` |
| Recurring | POST | `/api/recurring-transactions` | `CreateRecurringTransactionRequest` | `RecurringTransactionResponse` + `Location` | 201 | 400 (incl. unknown/foreign `accountId` — drift, §39) | JWT | `RecurringTransactionController.java` |
| Recurring | GET | `/api/recurring-transactions` | — | `List<RecurringTransactionResponse>` | 200 | 401 | JWT | `RecurringTransactionController.java` |
| Recurring | GET | `/api/recurring-transactions/{id}` | path `UUID id` | `RecurringTransactionResponse` | 200 | 404 | JWT | `RecurringTransactionController.java` |
| Recurring | DELETE | `/api/recurring-transactions/{id}` | path `UUID id` | `RecurringTransactionResponse` (deactivated) | 200 | 404 | JWT | `RecurringTransactionController.java` |
| Dashboard | GET | `/api/dashboard` | `?month=YYYY-MM` (required) | `DashboardMonthResponse` | 200 | 400 bad/missing month; 400 same-type mixed-currency aggregation | JWT | `DashboardController.java` |
| Import | POST | `/api/imports` | `?accountId=UUID` + multipart part `file` | `ImportBatchSummaryResponse` + `Location` | 201 | 400 bad file/type/PDF, 404 foreign `accountId`, 500 oversized/malformed-CSV | JWT | `ImportController.java` |
| Import | GET | `/api/imports` | — | `List<ImportBatchListItem>` (summaries, newest first, no rows) | 200 (`[]` when empty) | 401 | JWT | `ImportReportController.java` — **M10 / REQ-BE-004 delivered** |
| Import | GET | `/api/imports/{id}` | path `UUID id` | `ImportBatchSummaryResponse` | 200 | 404 | JWT | `ImportReportController.java` |
| FX | GET | `/api/fx/convert` | `?amount&from&to` (all required) | `ConversionResponse` | 200 | in-body degradation (`available:false`) | JWT | `FxController.java` |

**Absent by verification** (grep over all mapping annotations): no
PUT/PATCH anywhere; no `POST /api/transfers`; no `GET /api/transactions/{id}`;
no `/api/auth/refresh` or `/api/users/me`;
no statistics HTTP surface. (`GET /api/imports` batch list **landed in M10**
— REQ-BE-004.)

## 6. Authentication and ownership

| Question | Answer | Evidence |
|---|---|---|
| DOES ANDROID NEED TO SEND AUTH CREDENTIALS TODAY? | **YES** — Bearer JWT on every `/api/**` endpoint except `register`/`login` | `SecurityConfig.java` |
| IS DATA MULTI-USER ISOLATED TODAY? | **YES** — `user_id` on every owned table; all user-facing repository queries are `…ByUserId`/`…AndUserId` (exceptions by design: `import_rows` is batchId-scoped behind the owner-scoped batch lookup; scheduler scans are cross-user); foreign IDs → 404 | `Transaction.java` denormalized `userId`; `*Repository` methods; `NotFoundNotForbiddenR24Test` |
| DOES BACKEND HAVE A USER DOMAIN MODEL? | **YES** — `AppUser` (`users` table: `id`, unique `username`, `passwordHash`, `roles`); no email field | `AppUser.java`, `V4__user_identity.sql` |
| IS MOBILE LOGIN CURRENTLY POSSIBLE? | **YES** — `POST /api/auth/login` returns a usable Bearer token | `AuthController.java`, `SecurityConfigTest` |

Token mechanics — **VERIFIED** (`NimbusTokenIssuer.java`, `SecurityConfig.java`):

- HS256 HMAC over `${SPENDWISE_JWT_SECRET}` (no default; required env var).
- Claims: `jti` (UUID), `sub` = user UUID string, `roles`, `iat`, `exp`.
- `exp = iat + app.jwt.expiration-minutes` (**default 15 minutes**).
- No `iss`/`aud` validation; signature + expiry + blacklist only.
- `POST /api/auth/logout` adds `jti` to an in-memory `TokenBlacklist`
  (unbounded, lost on restart — documented limitation).
- **No refresh token, no `/me`** — expiry forces a fresh login.
- `CurrentUser` resolves `authentication.getName()` → `UUID.fromString` —
  the single ownership choke point (never client-supplied IDs).
- `roles` → `ROLE_*` authorities exist but **no endpoint checks roles**
  (`anyRequest().authenticated()` is the only rule).
- Passwords: delegating encoder (BCrypt), hashed at registration.
- Bearer-auth failures inside the filter chain produce Spring Security's
  default 401 (empty body / `WWW-Authenticate`), **not** ProblemDetail —
  INFERRED (framework behavior outside `@RestControllerAdvice`).

**Auth request/response DTOs:**

| DTO | Fields | Validation | Success |
|---|---|---|---|
| `RegisterRequest` | `username`, `password` | `@NotBlank @Size(max=255)`; `@NotBlank @Size(min=8,max=255)` | 201 `RegisterResponse{id, username}`; duplicate → 400 |
| `LoginRequest` | `username`, `password` | both `@NotBlank` | 200 `AuthResponse{accessToken, expiresAt}` |
| — | logout | JWT `jti` | 204 empty |

## 7. Account contract

**Endpoints:** POST/GET-list/GET-by-id only (§5). **No update, delete, or
archive endpoints exist.** — VERIFIED.

`CreateAccountRequest` (`account/CreateAccountRequest.java`):

| Field | Wire type | Required | Validation | Meaning |
|---|---|---|---|---|
| `name` | string | yes | `@NotBlank @Size(max=255)` | display name |
| `type` | string | yes | `@NotNull`, enum `AccountType` | `CASH` or `BANK` |
| `openingBalance` | number | yes | `@NotNull @DecimalMin("0.00") @Digits(17,2)` | starting balance; **zero allowed** |
| `currency` | string | yes | `@NotBlank`; ISO-4217 checked at domain layer | fixed account currency |

`AccountResponse` (`account/AccountResponse.java`):
`{id, name, type, openingBalance, currency, balance}` — `currency` is the
opening currency; `balance` is the current stored balance. **VERIFIED** —
`AccountMapper.toResponse`.

Semantics:

- **IDs**: server-generated `UUID` (`Account` ctor). No client ID input.
- **Types**: `CASH`, `BANK` only — closed enum.
- **Balance**: stored `balance_amount`/`balance_currency` columns initialized
  to opening values, mutated per transaction (§12). `NUMERIC(19,2)` — V1/V2.
- **Concurrency**: `@Version` optimistic lock → 409 on lost update.
- **No duplicate-name or per-user uniqueness** — duplicate names legal.
- **No active/archived flag, no enabled state.**
- **Negative balances are legal** — no floor check anywhere (grep: no
  "insufficient"/"overdraft") — VERIFIED.
- **Ordering**: `findAllByUserId` with no `Sort` → unspecified DB order —
  VERIFIED.
- **Error cases**: foreign/unknown `{id}` → 404 empty body;
  `Currency.getInstance` failure → `DomainException` → 400.

## 8. Money and currency contract

| Aspect | Contract | Evidence |
|---|---|---|
| Java type | `BigDecimal` in `shared/Money` + `java.util.Currency` | `Money.java` |
| **Wire representation** | **JSON number** (Jackson BigDecimal default); currency is a JSON string | no `@JsonFormat`/`@JsonSerialize` anywhere — VERIFIED |
| Scale/rounding | normalized to **scale 2, `HALF_UP`** in the `Money` ctor — single normalization point | `Money.java:30` |
| Precision bound | `@Digits(integer=17, fraction=2)` on all money request fields; DB `NUMERIC(19,2)` | DTOs; V1 |
| Sign convention | **amounts are always positive** (`signum() > 0` enforced); direction lives in `TransactionType` (`INCOME`/`EXPENSE`/`TRANSFER_IN`/`TRANSFER_OUT`) | `Transaction.java`, `Account.requireAmount` |
| Zero | transactions: rejected (`amount must be positive`); account opening: `0.00` allowed | DTOs, `Account` |
| Currency type | **open ISO-4217 string** (`Currency.getInstance`), **not** a closed enum; invalid code → `DomainException` → 400 | `Money.parseCurrency` |
| Account currency | fixed at creation; every transaction on that account must match or `CurrencyMismatchException` → 400 | `Account.apply*`, `TransactionCreationService` |
| Transfer currency | must match **both** accounts (single `Money` applied to both legs); otherwise rollback | `TransferService` |
| Conversion | **none in the ledger**; `/api/fx/convert` is display-only and never persists | `FxController`, `FxNeverTouchesLedgerR4R19Test` |
| Default currency | `Money(String)` one-arg ctor defaults USD (internal convenience only; all wire paths require explicit `currency` — `@NotBlank`) | `Money.java:9-10` |
| Multi-currency effects | accounts may hold different currencies, but `StatisticsService` refuses mixed-currency aggregation → dashboard 400 for mixed months; budget calc silently **excludes** mismatched-currency rows | `StatisticsService`, `BudgetCalculationService` |

## 9. Date/time contract

| Field / input | Java type | Wire form | Required | Notes |
|---|---|---|---|---|
| `occurredOn` (transaction) | `LocalDate` | `"yyyy-MM-dd"` | optional on create, **always returned on reads** | **M11 / REQ-BE-001 delivered** (backend `bc6e0bb`): optional on `CreateTransactionRequest` (omission → `LocalDate.now()`; > tomorrow → 400), required on `TransactionResponse` (column NOT NULL). Pre-M11: CSV-export-only | `Transaction.java`, `TransactionMapper`, `CsvExportService`, `TransactionResponse` |
| `importedAt` (ImportBatch) | `Instant` | ISO-8601 | server-generated | UTC instant — VERIFIED |
| `expiresAt` (AuthResponse) | `Instant` | ISO-8601 | server-generated | token expiry — VERIFIED |
| `nextDueDate`, `endDate` (recurring) | `LocalDate` | `"yyyy-MM-dd"` | required / optional | `CreateRecurringTransactionRequest` |
| `month` (dashboard param) | parsed to `YearMonth` | `"YYYY-MM"` string | required | malformed → 400 |
| `period` (budget) | `BudgetPeriod` VO | `"YYYY-MM"` string | required | malformed → 400 |
| Recurring generated `occurredOn` | `LocalDate` | — | server | generated row carries the **due date**, not generation date | `RecurringGenerationService` |
| CSV `occurredOn` column | `LocalDate` | `"yyyy-MM-dd"` | required per row | `LocalDate.parse` |

**TIMEZONE_POLICY_NOT_EVIDENCED** for economic dates: `occurredOn` is a
zone-less `LocalDate`; "today" is the **server host's local date**
(`LocalDate.now()`), not explicitly UTC — relevant for clients in other
timezones. `Instant` fields are unambiguous UTC. — VERIFIED (absence of tz
config) + FRAMEWORK_DEFAULT_INFERENCE.

## 10. Identifier contract

| Resource | Wire ID type | Source |
|---|---|---|
| Account | `UUID` (JSON string) | server-generated in entity ctor — VERIFIED |
| Transaction | `UUID` | server-generated; `TransactionCreationCommand.transactionId` exists internally but is **not exposed** on `CreateTransactionRequest` — VERIFIED |
| User | `UUID` (`sub` claim, `RegisterResponse.id`) | server-generated |
| Budget | `UUID` | server-generated |
| Recurring template | `UUID` | server-generated |
| Categorization rule | `UUID` | server-generated |
| Import batch | `UUID` | server-generated |
| Category | `String` code (≤32) — **the PK itself is the wire value** | `Category.java` — VERIFIED |
| Transfer | **no transfer ID** — legs linked by `transferRef` (internal string); `TransferResult.transferRef` never leaves the server (no endpoint) | `TransferService` — VERIFIED |
| External source ref | `sourceRef` string (≤255): `MANUAL:<uuid>`, `IMPORT:SRC:<accountId>:<bankTxnId>`/`IMPORT:<acct>\|…\|OCC:n`, `RECURRING:<templateId>:<dueDate>`, `TRANSFER:<ref>:OUT/IN` | `TransactionMapper`, `RowIdentityService`, `RecurringGenerationService`, `TransferService` — VERIFIED |

Do not conflate: **database `id`** (UUID, path param) vs **`sourceRef`**
(dedup/provenance identity on the wire) vs **`transferRef`** (internal leg
link, not returned).

## 11. Transaction contract

`CreateTransactionRequest` (`account/CreateTransactionRequest.java`):

| Field | Wire type | Required | Validation | Meaning |
|---|---|---|---|---|
| `accountId` | string (UUID) | yes | `@NotNull` + owner-scoped existence → 404 | target account |
| `type` | string | yes | `@NotNull`, enum | `INCOME`/`EXPENSE`/`TRANSFER_IN`/`TRANSFER_OUT` (transfer types rejected by service → 400) |
| `amount` | number | yes | `@NotNull @DecimalMin(value="0.00", inclusive=false) @Digits(17,2)` | strictly positive |
| `currency` | string | yes | `@NotBlank`; must equal account currency → 400 | ISO-4217 |
| `category` | string | no | optional; unknown code → 400 (`categoryRepository.findById` in service) | category code |
| `description` | string | no | `@Size(max=255)` | **rule-engine input only — never persisted or returned** |
| `occurredOn` | string (ISO `yyyy-MM-dd`) | **no — optional** | `LocalDate`; dates beyond tomorrow rejected at the manual-create boundary (`TransactionMapper`) | **M11 / REQ-BE-001 delivered** — omission stamps server `LocalDate.now()` exactly as pre-M11 |

`TransactionResponse` (`account/TransactionResponse.java`):
`{id, accountId, type, amount, currency, category, sourceRef, occurredOn}` —
**`occurredOn` returned on every read (M11 / REQ-BE-001 delivered)**;
`transferRef` and `description` remain not returned.

Semantics:

- **Ledger is append-only**: no update, no delete, no detail endpoint; the
  only reads are `GET /api/transactions` (list + filters) and CSV export.
- `GET /api/transactions` filters: `accountId` (UUID; foreign → 404), `type`
  (enum; bad value → 400), `category` (code; unknown value → **empty list,
  no error** — `TransactionService.search` in-memory match). No date, text,
  amount, or sourceRef filters. — VERIFIED.
- `category` on request = code (e.g. `FOOD`); `category` on response = the
  same string (`Category.name()` returns the code — `Category.java`). — VERIFIED.
- `sourceRef` is set by the server (`MANUAL:<uuid>` for manual creates) and
  is returned — clients can detect import vs manual vs recurring provenance
  by its prefixes (§10). — VERIFIED.
- `transferRef` exists on the entity but is **not serialized**; transfer legs
  look like ordinary `TRANSFER_IN`/`TRANSFER_OUT` transactions on the wire.
- `userId` is denormalized onto every transaction (V4) — ownership scoping.
- Immutability is enforced by absence of mutators and endpoints — VERIFIED.

## 12. Canonical transaction write path

**All transaction creation — manual, imported, recurring, and transfer legs —
funnels through `TransactionCreationService.create(command, ownerId)`
(`@Transactional`).** — VERIFIED (`TransactionCreationService.java`; static
guard test `ImportNormalizationTest` forbids direct `transactionRepository.save`
or `account.apply*` calls inside `com.spendwise.imports`).

Sequence inside one DB transaction:

1. Validate command: `sourceRef`, `account`, `type` required; `TRANSFER_IN/OUT`
   rejected unless `transferRef` present → REST callers cannot mint transfer
   legs (`L52-55`).
2. Resolve account owner-scoped (`findByIdAndUserId`) → `DomainException` → 400.
3. Category: explicit code validated; if absent →
   `RuleEngine.categorize(ownerId, description)` consulted once (explicit
   category always wins).
4. `applyEffect` → `Account.applyIncome/Expense/TransferIn/Out` mutates the
   in-memory balance; **currency mismatch throws before any write**.
5. `entityManager.persist` + `flush` — `uq_txn_source_ref` violation →
   `SourceRefConflictException`; other PK conflicts → "transaction id already
   exists" (`L85-98`).
6. `accountRepository.save(account)` persists the new balance (`@Version` bump).

Callers: `TransactionController.create` (manual), `ImportRowProcessor`
(each row in its own `@Transactional` — a failed row never rolls back siblings),
`RecurringGenerationService` (occurrence + `nextDueDate` advance in one tx),
`TransferService` (two legs in one outer tx). — all VERIFIED.

## 13. Transfer contract

**There is no transfer endpoint.** `TransferService` is reachable only from
Java/tests — confirmed by controller inventory and reference grep. The
backend's committed `docs/defense-audit.md` acknowledges the missing REST
surface as intended for this build.

Domain semantics — **VERIFIED** (`TransferService.java`):

| Question | Answer |
|---|---|
| IS_TRANSFER_ONE_RESOURCE_OR_TWO_TRANSACTIONS? | **TWO TRANSACTIONS** — `TRANSFER_OUT` on source + `TRANSFER_IN` on destination, linked by a shared `transferRef` (a random UUID string per call); `sourceRef`s `TRANSFER:<ref>:OUT`/`:IN`. |
| IS_TRANSFER_CREATION_ATOMIC? | **VERIFIED** — `@Transactional`; both legs go through `TransactionCreationService`; any leg-2 failure rolls back leg 1 including the balance update (proven by `IntegrationSuiteTest`). |
| DOES_TOTAL_WEALTH_CHANGE? | **NO** — identical `Money` subtracted/added on two accounts; cross-currency is **rejected** (mismatch → rollback), never converted. |
| CAN_ANDROID_CREATE_TRANSFER_WITH_ONE_API_CALL? | **NO — BACKEND_GAP** (BG-001). |

Other domain rules: `sourceAccountId != destinationAccountId` required;
amount strictly positive; both accounts must belong to the caller;
no insufficient-funds check (source may go negative); no idempotency key
(fresh `transferRef` per call); no update/delete (ledger immutable anyway).

## 14. Category and auto-categorization contract

**Categories — read-only reference data:**

- `GET /api/categories` → `[{"value":"FOOD"},…]` — 5 fixed codes:
  `FOOD, TRANSPORT, HOUSING, ENTERTAINMENT, SALARY` (V1 seed). — VERIFIED.
- **No CRUD** — no create/update/delete anywhere; no user-defined categories,
  no hierarchy, no income/expense flag. `code` is the only field — VERIFIED.
- List is `@Cacheable("categories")`, process-local `ConcurrentMapCacheManager`.
- `Category.valueOf` mimics enum semantics and can throw `IllegalArgumentException`;
  over HTTP this is **unreachable** (services use `findById` + guard; only the
  non-HTTP `CsvTransactionStore` calls `valueOf`, and it catches it) — VERIFIED.

**Auto-categorization rules — user-owned, PUBLIC_API:**

- Endpoints: create/list(priority-asc)/get/DELETE-as-disable (200 + body).
- `CreateCategorizationRuleRequest{conditionType, conditionValue, targetCategory,
  priority, enabled}`:
  - `conditionType` — `CONTAINS` or `EQUALS` (invalid → 400)
  - `conditionValue` — `@NotBlank @Size(max=255)`
  - `targetCategory` — `@NotBlank @Size(max=32)`; unknown code → 400; rule rows
    survive category deletion via `ON DELETE SET NULL` → `targetCategory:null`
  - `priority` — `@NotNull Integer`; `UNIQUE(user_id, priority)` → 409 on clash
  - `enabled` — **accepted but silently ignored; entity always persists true** —
    VERIFIED drift (`CategorizationRuleMapper` never reads it).
- **Execution timing**: inside `TransactionCreationService` only when no
  explicit category was supplied — for **both manual creates and every
  imported row** (`ImportRowProcessor` passes `categoryCode=null` +
  `row.description()`). First enabled rule in priority order wins; disabled
  and orphaned (null target) rules skipped. — VERIFIED (`RuleEngine.java`,
  `ImportRuleApplicationTest`).
- **No re-run/backfill endpoint** — rules never retro-apply.
- Uncategorized transactions are legal everywhere (nullable `category`).

## 15. Recurring/subscription contract

**PUBLIC_API** endpoints: create / list / get / **DELETE = deactivate**
(200 + body; generated transactions untouched). — VERIFIED.

`CreateRecurringTransactionRequest`:

| Field | Wire | Required | Validation |
|---|---|---|---|
| `accountId` | UUID | yes | `@NotNull`; unknown/**foreign** → `DomainException` → **400** (not 404 — drift §39) |
| `direction` | string | yes | `@NotBlank`; entity accepts `INCOME`/`EXPENSE` only → 400 otherwise |
| `amount` | number | yes | `@NotNull @DecimalMin(0.00,excl) @Digits(17,2)` |
| `currency` | string | yes | `@NotBlank` |
| `category` | string | no | unknown code → 400 |
| `frequency` | string | yes | `WEEKLY` or `MONTHLY` → 400 otherwise |
| `nextDueDate` | `LocalDate` | yes | `@NotNull`, ISO date |
| `endDate` | `LocalDate` | no | optional; `isDue` returns false once `asOf > endDate`, so occurrences missed **before** `endDate` are silently dropped (never caught up); `endDate < nextDueDate` is unvalidated at create |

**Recurring currency defect (drift D14):** the template's `currency` is
**never validated against the account's currency** — a mismatched template
creates 201, then every generation run throws `CurrencyMismatchException`
inside `create()`, rolls back that owner, and skips the remaining owners in
that run — a permanent, silent poison of the scheduler for that template.

`RecurringTransactionResponse`: `{id, accountId, direction, amount, currency,
category, frequency, nextDueDate, endDate, active}` — VERIFIED.

Generation — **INTERNAL_ONLY** (no manual-trigger endpoint; committed
`docs/demo-script.md` states "by design"):

- `RecurringSchedulingJob` — `@Scheduled(fixedDelay)` default **900 000 ms**;
  per-owner fan-out, single scheduler thread, **no distributed lock**
  (single-instance assumption, documented).
- `RecurringGenerationService.generateDue(ownerId, asOf)` — `@Transactional`
  per owner; catch-up `while (isDue)` loop materializes every overdue
  occurrence; each occurrence goes through `TransactionCreationService` with
  `occurredOn = dueDate` and deterministic
  `sourceRef = "RECURRING:<templateId>:<dueDate>"` → `existsByUserIdAndSourceRef`
  + `uq_txn_source_ref` make re-runs no-ops. Occurrence insert and
  `nextDueDate` advance are atomic (mid-generation failure → nothing —
  `RecurringGenerationFailureTest`). — VERIFIED.
- Deactivation stops future generation; history is preserved.
- "Subscriptions" as a distinct concept: **NOT_PRESENT** — recurring templates
  are the only mechanism.

## 16. Import API contract

| Aspect | Contract | Evidence |
|---|---|---|
| Endpoint | `POST /api/imports` | `ImportController.java` — VERIFIED |
| Content type | `multipart/form-data` | `@RequestParam MultipartFile` — VERIFIED |
| Parts | `file` (multipart part, **required**) + `accountId` (`@RequestParam`, required) | `ImportController.upload` — VERIFIED |
| Filename | dispatch is on `getOriginalFilename()` lower-cased extension: `.csv`/`.pdf`; anything else → `StatementParseException` → 400 | `ImportService.importFile` — VERIFIED |
| MIME/size checks | none custom; Boot default ~1 MB file cap → 500 if exceeded | FRAMEWORK_DEFAULT_INFERENCE |
| Foreign `accountId` | 404 (controller pre-check) | `ImportController` — VERIFIED |
| Synchrony | **SYNCHRONOUS** — the whole file is parsed and all rows processed before the 201 returns; no 202/pending contract; only a fire-and-forget `@Async` log line | `ImportService`, `BatchNotificationService` — VERIFIED |
| Success | 201 + `Location: /api/imports/{batchId}` + `ImportBatchSummaryResponse` | VERIFIED |
| `GET /api/imports/{id}` | same response re-served (owner-scoped → 404) | `ImportReportController` — VERIFIED |
| `GET /api/imports` (M10, REQ-BE-004) | `List<ImportBatchListItem>` — `{batchId, accountId, fileName, importedAt, successCount, duplicateCount, failedCount}` — owner-scoped in the query, ordered `importedAt` DESC, **no row payloads**; empty history → `200 []`; counts from one grouped row query (no N+1) | `ImportReportController.list`, `ImportReportService.listForUser`, `ImportHistoryListTest` — VERIFIED |

`ImportBatchSummaryResponse`:
`{batchId, accountId, fileName, importedAt, successCount, duplicateCount,
failedCount, rows:[{rowNumber, status("OK"|"DUPLICATE"|"FAILED"), reason,
transactionId}]}`. No `totalCount` (use `rows.size()`).

Failure matrix (§24 has the error shapes):

| Condition | HTTP |
|---|---|
| unsupported extension / no filename | 400 |
| corrupt, encrypted, image-only, unknown-layout, zero-row PDF | 400 |
| TPBank page missing header / columns out of order | 400 |
| **structural CSV failure (header/columns/quoting/BOM)** | **500 — drift (§39); controller comment claims 400** |
| missing `file` part / oversized upload | 500 (unhandled) |
| mid-loop unexpected exception | 500 + **partially committed batch** |
| per-row bad content / duplicate | 201 with row status |

## 17. CSV import contract

`CsvStatementParser` — VERIFIED:

- **Header (exact, case/space-sensitive)**:
  `occurredOn,amount,currency,description,sourceTxnId` — any deviation →
  `IOException("CSV header is invalid")` → whole upload fails **before a
  batch exists**.
- Delimiter `,`; UTF-8; **BOM not stripped** (a BOM corrupts the header check);
  CRLF tolerated; RFC-4180-ish quoting (`""` escapes, `"` only at field start).
- Columns:
  - `occurredOn` — `yyyy-MM-dd` (`LocalDate.parse`)
  - `amount` — **signed**: `<0 → EXPENSE`, `>0 → INCOME`, `0 → FAILED row`;
    `BigDecimal`-parseable incl. exponent notation; HALF_UP scale-2 via `Money`
  - `currency` — ISO-4217; **must equal the account's currency** or the row
    fails (`CurrencyMismatchException` inside `create()`)
  - `description` — free text; **never persisted**; feeds `RuleEngine`
  - `sourceTxnId` — optional; blank → tier-2 identity (§19)
- Failure model: **structural** errors (header, column count, quoting) are
  all-or-nothing `IOException` → **500** (no handler — drift §39); **content**
  errors (date/amount/currency) are per-row `FAILED`, siblings unaffected.
- `rowNumber` = physical file line, **2-based** (header = line 1).
- Header-only file → empty batch → 201 with all-zero counts.
- **Export ≠ import**: `CsvExportService` header is
  `occurredOn,type,amount,currency,category,sourceRef` — a different schema
  that cannot be re-imported — VERIFIED.

## 18. PDF / TPBank support boundary

`PdfStatementParser` + `TpbankStatementLayout` + `PositionedTextExtractor`
(PDFBox 3.0.8) — VERIFIED:

| Capability | Status |
|---|---|
| Machine-readable (text-layer) PDF | SUPPORTED (if a layout matches) |
| TPBank account statement layout | SUPPORTED — requires all 4 markers: `ACCOUNT STATEMENT`, `Beginning balance`, `Closing balance`, `Tien Phong Commercial Joint Stock Bank` |
| TPBank multi-page | SUPPORTED — per-page re-anchoring, page-footer skipping (`TpbankMultiPageImportTest`); an open row is carried across page breaks **by parser construction** — tolerated, not exercised by fixtures/tests; a page missing the column header fails the statement |
| Synthetic test layout (`SYNTHETIC BANK STATEMENT` marker, `Date \| Reference \| Description \| Debit \| Credit` + `Currency:` line) | SUPPORTED — an intentionally generic fixture layout |
| Scanned / image-only PDF | UNSUPPORTED → 400 `"statement PDF contains no extractable text"` |
| Encrypted PDF | UNSUPPORTED → 400 `"statement PDF is encrypted or password-protected"` (no password flow) |
| Corrupt / not-a-PDF | UNSUPPORTED → 400 `"statement PDF could not be read"` |
| Other banks / arbitrary PDFs | UNSUPPORTED → 400 `"unsupported PDF statement layout"` |
| OCR | **NOT_PRESENT** — no OCR dependency (PDFBox only) |
| Row dates | `dd/MM/yyyy` → ISO; amounts `1,234.56` style; Debit→negative, Credit→positive; reference cell → `sourceTxnId` |
| `rowNumber` | sequential transaction index, **1-based** (≠ CSV's 2-based — drift §39) |

**Android import UX may promise only**: TPBank machine-readable statements
(incl. multi-page) and the backend's CSV schema — nothing else.

## 19. Import deduplication contract

`RowIdentityService` (fresh instance per batch) + `SourceRefNormalizer` +
`uq_txn_source_ref UNIQUE(user_id, source_ref)` — VERIFIED:

- **Tier-1** (row carries `sourceTxnId`):
  `IMPORT:SRC:<accountId>:<NORMALIZED_TXNID>` — normalization = trim +
  collapse whitespace + uppercase. **Format-agnostic**: the same bank
  transaction imported via CSV and later via PDF dedups
  (`CrossSourceDedupTest`). Scoped per account — same ref on another account
  does not collide.
- **Tier-2** (no bank ref): fingerprint
  `IMPORT:<accountId>|<occurredOn>|<TYPE>|<absAmount-scale2>|<NORMALIZED_DESC>|OCC:<n>`
  where `n` is the in-batch occurrence counter — N identical rows in one
  file get N distinct identities; a re-import in any order dedups all
  (`ReImportIdempotencyTest`).
- **Legacy probe**: tier-1 rows first check the pre-upgrade shape
  `IMPORT:SRC:CSV:<accountId>:<txnId>` (`existsByUserIdAndSourceRef`) →
  DUPLICATE for old-format history.
- **Enforcement point**: the DB unique constraint is the race-safe arbiter —
  `TransactionCreationService` translates a `uq_txn_source_ref` violation
  into `SourceRefConflictException`, which `ImportRowProcessor` catches →
  row `status=DUPLICATE` (counted, `transactionId=null`, never an error).
- Manual `MANUAL:<uuid>`, recurring `RECURRING:…`, and transfer
  `TRANSFER:…` sourceRefs live in the **same unique namespace**, so the
  constraint protects every write path.
- Re-importing the same file → every row `DUPLICATE`, zero new transactions —
  import is **DEDUP_PROTECTED**, effectively idempotent.
- **Edge**: tier-2 fingerprints carry the full normalized description; a long
  description can push `sourceRef` past `VARCHAR(255)` → the row lands
  `FAILED` with the misleading generic message (constraint violation on a
  different column is reported as "transaction id already exists").

## 20. Import provenance

| Item | Stored | Returned to client |
|---|---|---|
| `ImportBatch`: `id`, `userId`, `accountId`, `fileName` (original client filename), `sourceKey` (`"CSV"`/`"PDF"`), `importedAt` (`Instant`) | all | `batchId`, `accountId`, `fileName`, `importedAt` only — **`userId` and `sourceKey` are stored but never returned** |
| `ImportRow`: `id`, `batchId`, `rowNumber`, `status`, `reason` (≤500), `transactionId` (nullable; OK ⇒ required) | all | `rowNumber`, `status`, `reason`, `transactionId` only — **`id`/`batchId` not returned** |
| Transaction ↔ batch linkage | `import_rows.transaction_id` | `transactionId` per row |
| Origin on the transaction itself | `sourceRef` prefix (`IMPORT:*` vs `MANUAL:*` vs `RECURRING:*` vs `TRANSFER:*`) | `TransactionResponse.sourceRef` |
| Batch status/lifecycle field | **none** — counts derived at read | — |
| Raw parser output / description / sourceTxnId | not persisted on `ImportRow` | not returned |
| Batch list | `findAllByUserId` exists but is dead code | **no endpoint** |

## 21. Pagination/filter/sort

| Endpoint | Pagination | Filters | Sort |
|---|---|---|---|
| `GET /api/accounts` | none — full list | — | unspecified (JPA default) |
| `GET /api/transactions` | **none — full list** | `accountId`, `type`, `category` (code) only | unspecified (fetch order) |
| `GET /api/transactions/export` | none | `accountId` | unspecified |
| `GET /api/categories` | none | — | `code` asc (fixed) |
| `GET /api/categorization-rules` | none | — | `priority` asc (fixed; the `id` tiebreak exists only in the RuleEngine's enabled-only query) |
| `GET /api/budgets` | none | — | unspecified |
| `GET /api/recurring-transactions` | none | — | unspecified |
| `GET /api/imports/{id}` | n/a (single) | — | rows in `rowNumber` order |

`TransactionService.pageByAccount` + repository `Pageable` methods exist but
**no controller exposes them — dead code**. — VERIFIED. No date-range,
full-text search, amount, sourceRef, or sort query parameters exist
anywhere. `GET /api/dashboard` requires `month` (a temporal filter, not
pagination).

## 22. Dashboard/summary capabilities

`GET /api/dashboard?month=YYYY-MM` — **PUBLIC_API** — returns
`DashboardMonthResponse{month, totalIncome, totalExpense,
categoryBreakdown[{categoryCode, amount}], accounts[AccountResponse],
budgets[BudgetCalculationResponse]}` — VERIFIED
(`DashboardController`, `DashboardReadService`):

- All figures computed at request time from the caller's rows — nothing
  stored or cached.
- `month` required; malformed → 400 (`DomainException`), missing → 400.
- `categoryBreakdown` covers EXPENSE transactions only; uncategorized rows
  excluded (`StatisticsService.spendingByCategory`).
- `accounts` = the caller's current account list (live balances).
- `budgets` filtered to the requested period;
  `BudgetCalculationResponse{budgetId, category, period, limit, currency,
  spent, remaining, percentage, status}` where `status ∈
  {WITHIN_BUDGET, OVER_BUDGET}` (no warning band).
- **Currency mixing is checked per aggregation, not per month**
  (`StatisticsService.requireSingleCurrency` runs inside `totalIncome`,
  `totalExpense`, `spendingByCategory` — each sees one type's rows only).
  Mixing currencies *within one type* (two INCOME currencies) → 400.
  **Income in one currency + expense in another → 200** with a
  `totalIncome` in currency A and `totalExpense`/breakdown in currency B —
  and neither `DashboardMonthResponse` nor `CategoryBreakdownItem` carries
  currency fields, so the ambiguity is **invisible on the wire** — VERIFIED
  (`StatisticsService.java:62-63`, `DashboardReadService`).
- Budget calculation instead **silently excludes** mismatched-currency rows —
  a third convention (§39 D16).
- Not exposed: total wealth across accounts (compose from `accounts[]`),
  recent-transactions list, trends, recurring summary — **INTERNAL_ONLY**
  (`AdvancedStatisticsService.monthlyTrend`, `topCategories` — deliberately
  no HTTP) or **NOT_PRESENT**.

Budget management endpoints (create/list/get/`{id}/calculation`) are
PUBLIC_API — §5. Budget scope = category + month only (no account scope);
`UNIQUE(user, category, period)` → 409 on duplicate. Spent is derived at
read; `percentage` scale-2 HALF_UP; `OVER_BUDGET` iff `spent > limit`.

**Remaining public response DTOs** — VERIFIED:

- `BudgetResponse{id, category, period, limit, currency}`
- `CategorizationRuleResponse{id, conditionType, conditionValue,
  targetCategory (nullable), priority, enabled}`
- `ConversionResponse{base, target, amount, convertedAmount (nullable),
  rate (nullable), available}` — **naming trap**: the request uses
  `?amount&from&to` while the response echoes them as `base`/`target`.
  `from == to` (case-insensitive) short-circuits to `rate:1,
  available:true`; missing params → 400; provider failure → 200 with
  `available:false` + null `convertedAmount`/`rate`.

## 23. Validation contract

**BEAN_VALIDATION** (jakarta, on request records — `@Valid` at controllers):

| DTO | Constraints |
|---|---|
| `RegisterRequest` | `username @NotBlank @Size(max=255)`; `password @NotBlank @Size(min=8,max=255)` |
| `LoginRequest` | both `@NotBlank` |
| `CreateAccountRequest` | `name @NotBlank @Size(255)`; `type @NotNull`; `openingBalance @NotNull @DecimalMin("0.00") @Digits(17,2)`; `currency @NotBlank` |
| `CreateTransactionRequest` | `accountId @NotNull`; `type @NotNull`; `amount @NotNull @DecimalMin(0.00,excl) @Digits(17,2)`; `currency @NotBlank`; `description @Size(255)` |
| `CreateBudgetRequest` | `category @NotBlank`; `period @NotBlank`; `limit @NotNull @DecimalMin(0.00,excl) @Digits(17,2)`; `currency @NotBlank` |
| `CreateRecurringTransactionRequest` | `accountId @NotNull`; `direction @NotBlank`; `amount` as above; `currency @NotBlank`; `frequency @NotBlank`; `nextDueDate @NotNull` |
| `CreateCategorizationRuleRequest` | `conditionType @NotBlank`; `conditionValue @NotBlank @Size(255)`; `targetCategory @NotBlank @Size(32)`; `priority @NotNull` |

**DOMAIN_VALIDATION** (service layer — all `DomainException` → 400 unless
marked): account exists/owned; source≠destination (transfer); transfer types
require `transferRef`; amount signum > 0; currency ISO-4217; **currency-vs-
account match enforced on transaction create, import rows, and transfer legs
— but NOT on `POST /api/recurring-transactions`** (drift D14); category code
exists; `frequency`/`direction` enum survival; `month`/`period` `YYYY-MM`
shape; CSV/PDF parse rules; `sourceRef` required; enum `valueOf` on `type`
param (framework 400 on bad value).

## 24. Error contract

`shared/ApiExceptionHandler` (`@RestControllerAdvice`) → **RFC-7807
`ProblemDetail`** bodies: `{type≈about:blank, title, status, detail[,fields]}`.

| Trigger | Status | Title / detail | Extra |
|---|---|---|---|
| `DomainException` (+ `InvalidAmountException`, `CurrencyMismatchException`, `StatementParseException`) | 400 | `Invalid request` / `Request is invalid.` | **exception message swallowed — indistinguishable 400s** |
| `MethodArgumentNotValidException` (bean validation) | 400 | same | `fields: {field: message}` — first error per field |
| malformed JSON / bad param type / missing param | 400 | same | — |
| `AuthenticationException` | 401 | `Unauthorized` / `Invalid credentials.` | — |
| `OptimisticLockingFailureException` | 409 | `Conflict` / "changed by another request. Reload and try again." | — |
| `DataIntegrityViolationException` | 409 | `Conflict` / "conflicts with an existing record." | — |
| unsupported media type | 415 | `Invalid request` | — |
| wrong HTTP method | 405 | `Invalid request` | — |
| `ResponseEntity.notFound()` | 404 | **empty body** (no ProblemDetail) | drift §39 |
| non-UUID `{id}` path param | 400 | `Invalid request` (type-mismatch) — **not** 404 | §23 |
| Bearer failure in filter chain | 401 | empty body + `WWW-Authenticate` | FRAMEWORK_DEFAULT_INFERENCE |
| anything else (incl. `IOException`, `MultipartException`, `MaxUploadSizeExceededException`, `IllegalArgumentException`) | 500 | `Unexpected error` | sanitized |

**Classification: PARTIALLY_TYPED.** Clients can branch reliably on HTTP
status and (for 400 validation) the `fields` map; they **cannot**
programmatically distinguish 400 causes ("account is required" vs "category
is invalid" vs "month must be shaped YYYY-MM" all share one body) — no error
codes exist. `SourceRefConflictException` intentionally bypasses this chain
(caught inside the import processor).

## 25. Nullability and optionality

Client-visible response nullability — VERIFIED from DTO records:

| DTO field | Optionality |
|---|---|
| `TransactionResponse.category` | **nullable** (uncategorized) |
| `TransactionResponse.sourceRef` | present (server-assigned; DB `NOT NULL`) |
| `RecurringTransactionResponse.category` | nullable |
| `RecurringTransactionResponse.endDate` | nullable |
| `CategorizationRuleResponse.targetCategory` | **nullable** (ON DELETE SET NULL) |
| `ConversionResponse.convertedAmount`, `.rate` | **nullable** when `available:false` |
| `ImportRowDetail.reason` | present on `DUPLICATE`/`FAILED`, null on `OK` |
| `ImportRowDetail.transactionId` | null on `DUPLICATE`/`FAILED` |
| `AuthResponse.expiresAt` | present |
| All `*Response` IDs / counts / dates listed | server-generated, present |

Request-side required-vs-optional is covered in §7/§11/§15/§23 tables.
`CreateCategorizationRuleRequest.enabled` is optional-but-ignored (drift §39).

## 26. Enum wire contract

| Enum | Exact wire values | Where | Unknown-value behavior |
|---|---|---|---|
| `AccountType` | `CASH`, `BANK` | request body, response, query-safe | `HttpMessageNotReadable` → 400 |
| `TransactionType` | `INCOME`, `EXPENSE`, `TRANSFER_IN`, `TRANSFER_OUT` | request body (`type`), `?type=` param, response | request/query → 400; transfer types pass parsing then service-reject → 400 |
| `ImportRowStatus` | `OK`, `DUPLICATE`, `FAILED` | `ImportRowDetail.status` (string) | response-only |
| `RecurringFrequency` | `WEEKLY`, `MONTHLY` | recurring request/response (as strings) | invalid → 400 |
| `BudgetStatus` | `WITHIN_BUDGET`, `OVER_BUDGET` | `BudgetCalculationResponse.status` | response-only |
| `RuleConditionType` | `CONTAINS`, `EQUALS` | rule request/response (as strings) | invalid → 400 |
| Recurring `direction` | `INCOME`, `EXPENSE` only | request/response (string) | `TRANSFER_*` → 400 |
| Category codes | `FOOD`, `TRANSPORT`, `HOUSING`, `ENTERTAINMENT`, `SALARY` | `CategoryResponse.value`, `category` fields | **not an enum** — DB-seeded set; unknown → 400 (body) or `[]` (`?category=` param) |

All enums serialize as `name()` strings; request parsing is **case-sensitive**
(framework default). Enum evolution: adding a constant is wire-compatible for
readers that tolerate unknown strings — Android should map defensively.

## 27. Response-shape conventions

- Success bodies are **bare DTOs or `List<DTO>`** — no envelope, no `Page`,
  no `data:`/`meta:` wrapper. All 201s **except `register`** add `Location`
  headers.
- `ResponseEntity<T>` everywhere; the drifts are payload-side (§39):
  `FxController` returns a bare `ConversionResponse` (no `ResponseEntity`),
  and `CsvExportController` returns `ResponseEntity<String>` with a
  `text/csv` payload rather than JSON.
- 404s = **empty body**; 204 on logout; DELETE-as-deactivate returns **200 +
  the updated resource body** (not 204) — consistent across rules/recurring.
- No endpoint returns a JPA entity — VERIFIED.

## 28. Update/delete semantics

| Resource | Update | Delete | Actual mechanism |
|---|---|---|---|
| Account | **absent** | **absent** | create + read only |
| Transaction | **absent** | **absent** | append-only ledger; no reversal API either |
| Transfer | absent | absent | (domain only; no endpoint at all) |
| Budget | **absent** | **absent** | create + read + calculation |
| Category | absent | absent | fixed reference data |
| Categorization rule | **absent** (no PUT/PATCH; `enabled` in request ignored) | **soft only** | `DELETE /{id}` → `enabled=false`, 200 + body |
| Recurring template | **absent** | **soft only** | `DELETE /{id}` → `active=false`, 200 + body; history preserved |
| Import batch | n/a | absent | history retained; rows `ON DELETE` only via batch |
| User | absent | absent | — |

## 29. Idempotency

| Operation | Class | Mechanism |
|---|---|---|
| Create account | NOT_IDEMPOTENT | duplicates allowed |
| Create transaction | NOT_IDEMPOTENT | server-generated `MANUAL:<uuid>` sourceRef; a retried POST creates a second row — **no client idempotency key exists** |
| Transfer | NOT_IDEMPOTENT | fresh `transferRef` per call (domain only anyway) |
| Import file | **DEDUP_PROTECTED** | deterministic row sourceRefs + `uq_txn_source_ref` → re-import yields `DUPLICATE` rows, not errors; safe to retry |
| Recurring generation | DEDUP_PROTECTED | deterministic `RECURRING:<id>:<date>` sourceRefs |
| Create budget | partially protected | `UNIQUE(user,category,period)` → 409 on duplicate — constraint, not a retry key |
| Create rule | partially protected | `UNIQUE(user,priority)` → 409 |
| Register | NOT_IDEMPOTENT | duplicate username → 400 |
| Logout | naturally idempotent | jti blacklist |

**No idempotency-key header or request field exists anywhere** — VERIFIED.
Safe-retry strategy for non-import writes is a P3/P4 product question
(e.g., search-then-create by sourceRef is not possible — sourceRef isn't
client-settable on manual creates).

## 30. Financial transaction/concurrency semantics

- `@Transactional` on the financial write paths: `TransactionCreationService.create`,
  `TransferService.transfer`, `RecurringGenerationService.generateDue` (per owner),
  `ImportRowRecorder.record`, `AccountService.create`, plus non-ledger writers
  `BudgetService`, `CategorizationRuleService`, `RecurringTransactionService`,
  `AppUserService`. `ImportRowProcessor.processRow` itself is deliberately **not**
  `@Transactional` — each row's atomicity comes from `create()`/`record()` — VERIFIED.
- `ImportService.importFile` is **deliberately NOT `@Transactional`** — batch
  metadata + each row commit independently → partial batches can persist on
  crash (no failure marker on `ImportBatch`).
- **Balance is a stored projection** mutated in the same tx as the insert;
  `BalanceReconciliationService` + `ReconciliationJob` (900 s fixedDelay)
  recompute and **log drift only — never repair** (all-users scan, scheduler
  only).
- `@Version` on `Account` → optimistic-lock 409; `transactions` table has no
  version (immutable anyway).
- `Account.apply*` methods are `synchronized` (defense in depth within the tx).
- Schedulers run on a **single thread, no ShedLock** — single-instance
  deployment assumption (documented).
- No index on `transactions(user_id)`/`(account_id)`/`occurred_on`/
  `transfer_ref` — list/search/dashboard scans are unindexed (V1–V10). Not a
  client contract issue but noted for growth expectations.

## 31. Client connectivity baseline

| Item | Value |
|---|---|
| Local base | `http://localhost:8080` (framework default; no `server.port`) |
| API base path | `/api` |
| TLS | **none configured** — plain HTTP in dev; no `server.ssl.*` |
| Auth header | `Authorization: Bearer <HS256 JWT>` |
| Content types | `application/json`; `multipart/form-data` (import); `text/csv` (export) |
| Docs | `http://localhost:8080/swagger-ui.html`, `/v3/api-docs` (permitAll) |
| Health | `GET /actuator/health` (permitAll) |
| Required env to run backend | `SPENDWISE_JWT_SECRET` (≥32 bytes), `SPENDWISE_DB_URL` (default `jdbc:postgresql://localhost:55432/spendwise`), `SPENDWISE_DB_USERNAME` (`spendwise_app`), `SPENDWISE_DB_PASSWORD` |
| Optional env | FX provider (`app.fx.*`, default `http://localhost:0` — deliberately no vendor), scheduler delays, async pool sizes |
| CORS | none — Android unaffected |
| Deployment | `Dockerfile` + `docker-compose.yml` exist (not audited for contract) |

## 32. Representative contract traces

**TRACE A — ACCOUNT CREATE**
`POST /api/accounts` + Bearer → security chain (JWT decode via
`BlacklistAwareJwtDecoder`, roles→authorities) → `AccountController.create`
→ `@Valid` on `CreateAccountRequest` (bean validation → 400+`fields` on
failure) → `CurrentUser.id()` (sub→UUID) → `AccountMapper.toDomain` →
`Account` ctor (UUID id; invariant checks) → `AccountService.create` →
`accountRepository.save` → `AccountMapper.toResponse` → **201 + Location**.

**TRACE B — MANUAL TRANSACTION CREATE**
`POST /api/transactions` → `TransactionController.create` → `@Valid` →
`accountService.find(accountId, ownerId)` → **404 if foreign/unknown** →
`TransactionMapper.toCommand` (money built; `sourceRef="MANUAL:"+uuid`) →
`TransactionCreationService.create` (`@Transactional`: validate → resolve
category/RuleEngine → `account.applyIncome/Expense` (currency match or 400)
→ persist+flush (`uq_txn_source_ref` guard) → `accountRepository.save`) →
`TransactionMapper.toResponse` → **201 + Location**. Response has no
`occurredOn`/`description` despite them being stored/used.

**TRACE C — TRANSFER (domain only — NOT REACHABLE)**
*(no HTTP request exists)* `TransferService.transfer(command, ownerId)`
(`@Transactional`) → validations (ids differ, amount>0, both accounts owned)
→ `transferRef=uuid` → leg1 `TransactionCreationService.create`(TRANSFER_OUT,
`TRANSFER:<ref>:OUT`) → leg2 create (TRANSFER_IN, `TRANSFER:<ref>:IN`) →
`TransferResult` (never serialized). Atomicity verified by
`IntegrationSuiteTest` (currency-failure rolls back both legs).
**Android equivalent today: BACKEND_GAP — must be surfaced as unavailable.**

**TRACE D — FILE IMPORT**
`POST /api/imports?accountId=<uuid>` multipart `file` → controller ownership
pre-check (**404**) → temp file → `ImportService.importFile(ownerId,
accountId, originalName, path)` → extension dispatch → `CsvStatementParser`
(exact-header; `IOException` on structural failure → **500**) or
`PdfStatementParser` (synthetic-marker or `TpbankStatementLayout.looksLike`
4-marker gate; else `StatementParseException` → **400**) → `ImportBatch`
saved (`fileName`,`sourceKey`,`importedAt`) → per row (`ImportRowProcessor`,
own tx): date/amount/currency parse → sign→type → `RowIdentityService.identify`
→ legacy-ref probe → `TransactionCreationService.create` (**same canonical
path as TRACE B** — RuleEngine fires on row `description`) → outcomes →
`ImportRowRecorder` (OK / DUPLICATE on `SourceRefConflictException` / FAILED
on `DomainException`) → `BatchNotificationService` (async log) →
`ImportReportService.find` → **201 `ImportBatchSummaryResponse`**.

**ERROR TRACE — validation & failure shapes**
`POST /api/transactions {"amount":0}` → `MethodArgumentNotValidException` →
**400** `{type:"about:blank",title:"Invalid request",status:400,
detail:"Request is invalid.",fields:{"amount":"must be greater than 0.00"}}`.
`POST /api/imports` with `x.docx` → `StatementParseException` → **400** same
body shape, no `fields` (indistinguishable cause). Malformed CSV header →
`IOException` → **500 `{title:"Unexpected error"}`** — drift vs the
controller's own comment claiming 400.

## 33. Mobile capability matrix

| Capability | Status | Evidence / impact |
|---|---|---|
| list accounts | SUPPORTED_NOW | `GET /api/accounts` |
| create account | SUPPORTED_NOW | `POST /api/accounts` |
| edit account | **BACKEND_GAP** | no PUT/PATCH — BG-002 |
| delete/archive account | **BACKEND_GAP** | no endpoint — BG-002 |
| account balances | SUPPORTED_NOW | `balance` in `AccountResponse`; server-owned |
| list transactions | SUPPORTED_WITH_LIMITATIONS | unbounded list, no sort/date filter — BG-005/013 |
| transaction detail | **BACKEND_GAP** | list-only; `occurredOn` now returned (M11/REQ-BE-001); `description`/`transferRef` still not — BG-004/BG-014 |
| create expense | SUPPORTED_NOW | `type=EXPENSE` |
| create income | SUPPORTED_NOW | `type=INCOME` |
| edit transaction | **BACKEND_GAP** | append-only — BG-002 |
| delete transaction | **BACKEND_GAP** | append-only — BG-002 |
| transfer | **BACKEND_GAP** | domain exists, no endpoint — BG-001 |
| categories | SUPPORTED_WITH_LIMITATIONS | fixed 5, read-only, no user categories |
| category assignment | SUPPORTED_NOW | optional `category` on create; cannot be changed after |
| auto categorization | SUPPORTED_NOW | rules run on manual create + imports; no re-run |
| CSV import | SUPPORTED_NOW | strict schema; synchronous |
| PDF import | SUPPORTED_WITH_LIMITATIONS | TPBank + synthetic layouts only |
| TPBank multi-page PDF | SUPPORTED_NOW | verified in tests |
| import result | SUPPORTED_NOW | full row-level 201 report |
| import duplicate reporting | SUPPORTED_NOW | per-row `DUPLICATE` + counts |
| import batch detail | SUPPORTED_NOW | `GET /api/imports/{id}` re-serves the report |
| import batch history | **BACKEND_GAP** | no batch list endpoint — BG-007 |
| budgets | SUPPORTED_NOW | create/list/get/calculation; no update/delete |
| CSV export | SUPPORTED_WITH_LIMITATIONS | `GET /api/transactions/export`; schema ≠ import schema (not re-importable) |
| FX conversion | SUPPORTED_WITH_LIMITATIONS | display-only; placeholder provider → usually `available:false` — BG-018 |
| recurring | SUPPORTED_NOW | WEEKLY/MONTHLY; create/list/get/deactivate |
| subscriptions | **NOT_EVIDENCED** | no distinct subscription concept |
| dashboard summary | SUPPORTED_WITH_LIMITATIONS | single month param; mixed-currency → 400 |
| filtering | SUPPORTED_WITH_LIMITATIONS | accountId/type/category only |
| pagination | **BACKEND_GAP** | none anywhere — BG-005 |
| authentication | SUPPORTED_WITH_LIMITATIONS | register/login/logout; 15-min token, no refresh — BG-006 |
| multi-user ownership | SUPPORTED_NOW | owner-scoped; 404-not-403 |
| offline sync support | **NOT_EVIDENCED** | no sync/change-feed/idempotency-key contract |
| server-side search | **BACKEND_GAP** | no text/date search — BG-013 |

## 34. Android contract risk matrix

| Area | Current backend behavior | Android risk | Severity | P3/P4 impact |
|---|---|---|---|---|
| No transfer endpoint | domain-complete, HTTP-absent | transfer UX undeliverable | MAJOR | P3 scope call; P4 design may stub domain-side adapter |
| Append-only ledger | no edit/delete | correction UX must be compensating entries or absent | MAJOR | P3 product decision |
| No pagination | unbounded `List` responses | unbounded payloads; local paging only | MAJOR | P4 data-layer strategy |
| 15-min JWT, no refresh | expiry → re-login | session UX friction; token handling | MAJOR | P3 auth UX; P4 networking/auth |
| `occurredOn` not settable/returned | server-stamped today; absent from API | back-dating & date display impossible via API | MAJOR | P3 feature cut or gap escalation |
| `description` not persisted | write-only rule fuel | no memo/notes feature | MINOR→MAJOR | P3 scope |
| Swallowed error messages | one generic 400 body | thin error UX; map by `fields`/status only | MAJOR | P4 typed-error mapping |
| Synchronous import | request blocks until done | ANR risk on large files; no progress | MAJOR | P3 import UX (blocking dialog); P4 coroutine discipline |
| No import batch list | only `GET /api/imports/{id}` | must persist batchIds locally | MINOR | P4 storage |
| Mixed-currency dashboard semantics | same-type mixing → 400; income-vs-expense mixing → 200 with unlabeled mixed totals | dashboard totals can be silently currency-ambiguous | MINOR→MAJOR | P3 display decision |
| Empty-body 404 vs ProblemDetail | two error shapes | error parser must handle both | MINOR | P4 error handling |
| Malformed CSV → 500 | drift vs comment | misreported failure | MINOR | P3 messaging |
| Enum sets | closed strings | forward-compat mapping needed | MINOR | P4 sealed-enum strategy |
| No CORS | none needed | none for Android | NOTE | — |
| Multipart 1 MB default | unconfigured | large PDFs → 500 | MINOR | P3 import limits messaging |
| No idempotency key | server-side refs only | retry duplication on txns | MAJOR | P4 retry policy |
| Token blacklist in-memory | restart clears | security detail, not client-visible | NOTE | — |

## 35. Safe client assumptions

Android may encode these at the audited version — all VERIFIED:

- Base path `/api`, JSON bodies, Bearer JWT on all endpoints except
  `register`/`login`; `sub` = user UUID.
- `RegisterRequest`/`LoginRequest`/`AuthResponse{accessToken, expiresAt}`
  field names; 201 on register, 200 on login, 204 on logout.
- UUID IDs for accounts, transactions, budgets, rules, recurring, batches.
- `AccountType ∈ {CASH, BANK}`; `TransactionType ∈ {INCOME, EXPENSE,
  TRANSFER_IN, TRANSFER_OUT}`.
- Amount = JSON number, positive, ≤ 2 decimals (scale-2 HALF_UP);
  currency = ISO-4217 3-letter string; both always present together.
- `balance` on `AccountResponse` is server-maintained — never reconcile
  client-side; `openingBalance` echoes the create request.
- Transaction create → 201; account foreign/unknown → 404 empty body.
- Import = multipart `file` + `accountId` query param; synchronous 201 with
  `ImportBatchSummaryResponse` row report; duplicates are rows, not errors.
- CSV header contract `occurredOn,amount,currency,description,sourceTxnId`;
  signed amounts; ISO dates.
- PDF import succeeds only for TPBank-family layouts (4 markers) or the
  synthetic layout.
- `GET /api/dashboard?month=YYYY-MM` shape and the 400 on bad/missing month.
- `GET /api/categories` → `[{value}]`, exactly the 5 seeded codes.
- Recurring: `direction ∈ {INCOME, EXPENSE}`, `frequency ∈ {WEEKLY, MONTHLY}`,
  deactivate = DELETE → 200 + body.
- Error bodies: ProblemDetail for MVC errors; `fields` present only on
  bean-validation 400; 404/401(filter) have empty bodies.
- `X-Trace-Id` echo header exists.
- All lists are unpaginated — code for unbounded arrays.
- `sourceRef` prefixes `MANUAL:`, `IMPORT:`, `RECURRING:`, `TRANSFER:` carry
  provenance meaning.

## 36. Unsafe client assumptions

Do **not** encode — evidence-backed concerns:

- **Assuming a transfer endpoint exists** — it does not (BG-001).
- **Assuming pagination, `Pageable` params, or sort controls** — dead code
  only; do not send `page`/`size`/`sort`.
- **Assuming `occurredOn` is settable or even returned** — it is neither on
  the REST API (export CSV only).
- **Parsing `detail`/`title` strings** — domain messages are deliberately
  swallowed; there are no error codes. Only `status`, `title` (fixed set),
  and `fields` are machine-usable.
- **Assuming arbitrary bank PDFs or OCR work** — TPBank + synthetic layouts
  only; everything else → 400.
- **Assuming CSV failures behave like PDF failures** — structural CSV → 500,
  not 400.
- **Assuming enum sets never grow** — defensive mapping required.
- **Duplicating balance/dedup/categorization/recurring logic client-side** —
  all server-owned (§37); local computation would diverge.
- **Assuming refresh tokens, `/me`, or long-lived sessions** — none exist;
  15-min tokens only.
- **Assuming a transaction detail endpoint** — none; enrich UX from list
  fields only.
- **Assuming update/delete will appear** — append-only is the shipped design.
- **Assuming `enabled` on rule-create does anything** — silently ignored.
- **Assuming `?category=` validation** — unknown codes return `[]` silently.
- **Assuming multi-currency aggregation works** — dashboard 400s on mixed
  months.
- **Assuming import batches are listable** — only `GET /api/imports/{id}`;
  persist batchIds locally if history UX is needed.
- **Assuming timezone-aware dates** — `LocalDate.now()` is server-local
  (TIMEZONE_POLICY_NOT_EVIDENCED).

## 37. Server-owned business rules

Android must not reimplement these as source of truth (UX-level
pre-validation is fine, backend remains authoritative):

- **Balance mutation** — `Account.apply*` inside the canonical tx; `balance`
  is read-only for clients.
- **Transaction creation invariants** — `TransactionCreationService`
  (positive amount, currency match, sourceRef requirement, dedup).
- **Transfer double-entry** — leg pairing, `transferRef`, atomicity
  (`TransferService`).
- **Import dedup identity** — `RowIdentityService` tiers + `uq_txn_source_ref`.
- **Auto-categorization** — `RuleEngine` priority matching at creation time.
- **Recurring materialization** — `RecurringGenerationService` + scheduler.
- **Statement parsing** — `CsvStatementParser`, `PdfStatementParser`,
  `TpbankStatementLayout` interpretation.
- **Budget calculation** — `BudgetCalculationService` (spent/remaining/
  percentage/status, currency filtering).
- **Statistics** — `StatisticsService` single-currency aggregation.
- **Ownership scoping** — `CurrentUser` + owner-scoped queries.
- **Token issuance/revocation** — `NimbusTokenIssuer`, `TokenBlacklist`.

## 38. Backend capability gaps

Severity: **BLOCKER** (cannot ship without backend change) / **MAJOR**
(materially degrades a planned capability) / **MINOR** (degraded or
workaround exists) / **NOTE** (awareness only). A gap is a capability gap,
not a defect. Decision owner is THE HUMAN for all.

| ID | Capability | Current evidence | Missing contract | Android impact | Blocks | Severity | Earliest stage |
|---|---|---|---|---|---|---|---|
| BG-001 | transfer | `TransferService` internal | POST/GET transfer resource | transfer UX impossible | M-transfers | **MAJOR** | P3 |
| BG-002 | edit/delete on txns, accounts, budgets; update on rules | absent | mutation endpoints + semantics | no correction UX; compensating entries only; budgets/rules can't be edited | ledger UX | **MAJOR** | P3 |
| BG-003 | transaction date | ~~`occurredOn` internal/export-only~~ | ~~request field + response field~~ | ~~no back-dating; date hidden in UI~~ | entry UX | ~~**MAJOR**~~ **RESOLVED — M11/REQ-BE-001 delivered** (backend `bc6e0bb`): optional `occurredOn` on create, always on reads | ~~P3~~ |
| BG-004 | description/memo | write-only | persisted + returned field | no notes display | detail UX | MINOR | P3 |
| BG-005 | pagination | absent (dead Pageable) | page/size or cursor contract | unbounded payloads | scale UX | **MAJOR** | P4 |
| BG-006 | session | 15-min JWT, no refresh/`/me` | refresh or longer TTL contract | forced re-login UX | session UX | **MAJOR** | P3 |
| BG-007 | import history | `/{id}` only | batch list endpoint | client must keep IDs | import history UX | MINOR | P3 |
| BG-008 | idempotent creates | server-gen refs only | idempotency-key support | retry duplicates | reliability | **MAJOR** | P4 |
| BG-009 | upload size | Boot 1 MB default | configured limits | larger PDFs → 500 | import UX | MINOR | P3 |
| BG-010 | typed errors | swallowed messages | error `code` field | shallow error UX | error UX | **MAJOR** | P4 |
| BG-011 | search/filter | acct/type/cat only | date-range + text search | limited discovery UX | filter UX | **MAJOR** | P3 |
| BG-012 | negative balance | allowed unconditionally | optional policy/warning field | none unless product wants guardrails | — | NOTE | P3 |
| BG-013 | multi-currency stats | same-type mixing → 400; income/expense mixing → 200 with unlabeled ambiguous totals (no currency fields in response) | per-currency or converted totals + currency labels | mixed-currency users get 400s or ambiguous totals | dashboard UX | MINOR | P3 |
| BG-014 | transaction detail | list only | `GET /{id}` or richer list fields | detail screens limited | detail UX | MINOR | P3 |
| BG-015 | 404 shape | empty body | uniform error body | dual error parsing | error UX | MINOR | P4 |
| BG-016 | batch status | none | lifecycle field | can't detect partial batches | import UX | MINOR | P4 |
| BG-017 | offline/sync | absent | change-feed/ETag contract | offline design unsupported | offline | NOTE | P4 |
| BG-018 | FX provider | `localhost:0` placeholder | real provider config | conversion usually `available:false` | fx UX | MINOR | P3 |
| BG-019 | blacklist persistence | in-memory | distributed revocation | none client-visible | — | NOTE | — |
| BG-020 | user categories | fixed 5 | category CRUD | limited categorization UX | category UX | MINOR | P3 |

## 39. Backend contract drift

Inconsistencies inside the contract itself (source-vs-source / source-vs-
comment / convention-vs-convention). Committed docs (`docs/defense-audit.md`,
`docs/demo-script.md`) were found **materially consistent** with source —
they even document the missing transfer endpoint; minor doc-side
inaccuracies are listed as D15.

| # | Drift | Evidence |
|---|---|---|
| D1 | **`ImportController` comment claims structural CSV IOExceptions "become 400s" — actual result is generic 500** (no `IOException` handler); PDF equivalents are 400. | `ImportController.java:20-22` vs `ApiExceptionHandler.java` catch-all |
| D2 | **Foreign `accountId` → 404 everywhere except `POST /api/recurring-transactions` → 400.** | `TransactionController`/`ImportController`/`CsvExportController` vs `RecurringTransactionService.java:31-32` |
| D3 | **404s are empty-bodied while every other error is ProblemDetail** — two error shapes. | controllers' `notFound().build()` vs advice |
| D4 | **Duplicate register → 400** but all other uniqueness violations → 409. | `AppUserService` vs `DataIntegrityViolationException` handler |
| D5 | **`enabled` accepted on rule create but silently ignored** — always persists `true`. | `CreateCategorizationRuleRequest` vs `CategorizationRuleMapper`/`CategorizationRule` |
| D6 | **`?category=` filter is unvalidated** (unknown → `[]`) while body `category` is validated → 400. | `TransactionService.search` vs `TransactionCreationService.category` |
| D7 | **`occurredOn` stored + exported to CSV but absent from `TransactionResponse`** and unsettable. | `Transaction.java`, `CsvExportService` vs `TransactionResponse` |
| D8 | **`FxController` returns a bare DTO** (no `ResponseEntity`) and its params are unvalidated — inconsistent with the house style. | `FxController.java` |
| D9 | **DELETE returns 200 + body** (soft-deactivate) rather than 204 — consistent internally, but unusual for clients. | rule + recurring controllers |
| D10 | **`rowNumber` base differs**: CSV counts file lines (2-based), PDF counts transactions (1-based) — same field, two semantics. | `CsvStatementParser` vs `PdfStatementParser` |
| D11 | **Export CSV schema ≠ import CSV schema** — `transactions.csv` cannot be re-imported (has `type`, unsigned amounts, lacks `description`/`sourceTxnId`). | `CsvExportService` vs `CsvStatementParser` |
| D12 | `app.fx.api-key` is declared in config but **never read or sent**. | `application.properties` vs `ExchangeRateClient` |
| D13 | Roles are minted into JWTs (`roles`→`ROLE_*`) but **never checked** anywhere. | `NimbusTokenIssuer`/`SecurityConfig` vs endpoint set |
| D14 | **Recurring template currency is not validated against the account** — create succeeds (201), then generation fails every run and skips subsequent owners. Every other write path validates. | `RecurringTransactionService.create`/`RecurringTransactionMapper` vs `TransactionCreationService`/`Money.requireSameCurrency` |
| D15 | **Committed docs have minor inaccuracies**: `docs/demo-script.md` presents `SPENDWISE_FX_API_KEY` as enabling a live provider (the property is never read — D12); `docs/architecture-notes.md` ADR-002 cites `createdAt`/`TIMESTAMPTZ` columns that do not exist (`imported_at` is `TIMESTAMP`, no `created_at` anywhere). | `docs/demo-script.md`, `docs/architecture-notes.md` vs `ExchangeRateClient`, V9, source grep |
| D16 | **Three conventions for currency mixing**: ledger ops reject (400), dashboard per-aggregation (same-type→400, cross-type→200 ambiguous), budget calc silently excludes mismatched rows. | `Money`/`StatisticsService`/`BudgetCalculationService` |

## 40. Inputs required by P3 Product Blueprint

P3_PRODUCT_INPUTS — product questions now constrained by backend reality
(decisions belong to P3, not made here):

1. Login/register UX exists to build on; **session expiry is 15 minutes with
   no refresh** — P3 must define session-expiry UX and re-auth flows (BG-006).
2. **Single-user-per-credential model** is real; multi-user isolation is
   enforced — no shared/family mode exists.
3. **Accounts are create-and-view only** — V1 account UX is limited to
   creation + listing + live balances (BG-002).
4. **Transactions are append-only** — V1 entry UX is create + list; correction
   strategy (hide? compensate? gap?) is a P3 product decision (BG-002).
5. **Transfer feature is undeliverable today** — P3 must choose: exclude
   transfer UX, fake it as paired manual entries (visible type would be
   EXPENSE/INCOME, not TRANSFER_* — service rejects transfer types), or
   escalate a backend gap (BG-001).
6. **Manual entries are always dated "today"** — P3 must decide if that's
   acceptable for V1 or requires escalation (BG-003).
7. **Dashboard is server-backed per month** — the screen can be real, but
   mixed-currency months 400 and there's no trend/comparison data (BG-013).
8. **Import is fully synchronous with row-level results** — P3 designs a
   blocking-style import UX with a rich result screen; no progress API
   exists; batch history requires local persistence (BG-007).
9. **Only TPBank PDFs + the CSV schema can be promised** in upload UX
   copy; error guidance must match the 400-for-PDF vs 500-for-CSV reality.
10. **Filtering is account/type/category only** — no search, no date ranges;
    V1 browse UX must live inside those bounds (BG-011).
11. **Unpaginated lists** — list screens must handle entire datasets locally
    (BG-005); fine for V1 volumes, a P4 scaling note.
12. **Categories are fixed at 5** — no user categories or icons beyond a
    fixed map (BG-020).
13. **Recurring is supported end-to-end** — create/manage/deactivate UX is
    feasible; note 15-min generation lag.
14. **FX conversion is display-only** and usually unavailable (placeholder
    provider) — promise nothing (BG-018).
15. **Errors are thin** — generic 400 body; only validation `fields` are
    field-targeted (BG-010).

## 41. Inputs required by P4 Android Architecture

P2_ARCHITECTURE_INPUT — constraints for future design (not decisions):

1. Typed-error mapping must handle **two error surfaces** (ProblemDetail +
   empty-body 404/401) and a **single generic 400 detail** — a `fields` map
   is the only structured signal (BG-010/015).
2. DTO→domain→UI mapping must tolerate: nullable `category`/`endDate`/
   `targetCategory`/`reason`/`transactionId`/`convertedAmount`; enum-string
   fields with unknown values; `BigDecimal` money (JSON number) —
   scale-2 discipline.
3. File upload needs multipart (`file` + `accountId` query param) with
   size-limit awareness (~1 MB) and a **blocking synchronous** call
   (BG-005/009).
4. **No pagination contract** — local in-memory/persisted list strategy;
   don't build around `Page` wire types (BG-005).
5. **Auth architecture deferred but constrained**: Bearer JWT, 15-min TTL,
   no refresh — session persistence + re-login strategy is required (BG-006).
6. **Backend is source of truth for balances** — no client-side balance math;
   optimistic UI only on top of returned `balance` fields (§37).
7. Import result is a **rich finite state machine input** (OK/DUPLICATE/
   FAILED rows + counts) — a result-screen state model, not an async poller
   (BG-007/016).
8. Transfer must be treated as **capability-absent** at the wire — the
   domain exists server-side without an endpoint, so any transfer UX is a
   P3 scope decision, not an assumption (BG-001).
9. **Append-only ledger** shapes local caching: transactions are immutable —
   cache-friendly, no invalidation complexity (BG-002).
10. Retry/idempotency strategy must be client-side (e.g., queue-and-reconcile
    via `sourceRef` presence) — server offers no key (BG-008).
11. Enum forward-compatibility: unknown enum strings must not crash deser
    (open enums / safe parsing).
12. Timezone honesty: `LocalDate` fields are server-local-day semantics;
    `Instant` fields are UTC — distinct handling (§9).

## 42. Evidence index

All under `D:\spendwise\src\main\java\com\spendwise\` unless noted. Each was
read/grepped during P2 — none cited unseen.

| Evidence | Proves |
|---|---|
| `pom.xml` | Spring Boot 3.5.16, JPA/Security/resource-server/WebFlux/PDFBox/springdoc deps |
| `src/main/resources/application.properties` | env-driven config, JWT secret/expiry, scheduler delays, multipart absence, actuator/springdoc exposure |
| `db/migration/V1–V10` | `NUMERIC(19,2)`, `amount>0` check, `user_id` columns, `occurred_on`, `transfer_ref`, `uq_txn_source_ref`, budget/rule uniqueness, import tables |
| `auth/SecurityConfig.java` | filter chain, permitAll set, JWT decoder (HS256+blacklist), stateless, roles converter |
| `auth/AuthController.java`, `AuthService`, `AppUserService`, `NimbusTokenIssuer`, `TokenBlacklist`, `CurrentUser`, `AppUser`, DTOs | auth endpoints, claims, logout/blacklist, identity model, register validation |
| `account/AccountController.java`, `AccountService`, `Account`, `AccountType`, `AccountResponse`, `CreateAccountRequest`, `AccountMapper` | account endpoints, fields, validation, stored balance, `@Version`, positive-effect guards |
| `account/TransactionController.java`, `TransactionService`, `Transaction`, `TransactionType`, `TransactionResponse`, `CreateTransactionRequest`, `TransactionMapper`, `TransactionSearchCriteria`, `TransactionRepository` | transaction endpoints, filters, immutability, fields, in-memory search, response omissions |
| `account/TransactionCreationService.java`, `TransactionCreationCommand` | canonical write path, transfer-type guard, rule hookup, `uq_txn_source_ref` translation, occurredOn defaulting |
| `account/TransferService.java`, `TransferCommand`, `TransferResult` | two-leg atomic transfer, internal-only exposure |
| `account/BalanceReconciliationService.java`, `BalanceMismatch` | stored-balance reconciliation is read-only |
| `shared/Money.java`, `DomainException`, `InvalidAmountException`, `CurrencyMismatchException`, `StatementParseException` | money semantics, exception family |
| `shared/ApiExceptionHandler.java` | complete ProblemDetail error map |
| `shared/TraceIdFilter.java`, `AsyncConfig`, `CacheConfig`, `CsvTransactionStore` | tracing, async executor, category cache, legacy non-HTTP utility |
| `category/Category.java`, `CategoryController`, `CategoryService`, `CategoryResponse`, `CategoryRepository` | 5-code fixed set, code-PK, GET-only, caching |
| `rule/CategorizationRuleController.java`, `CategorizationRule`, `CategorizationRuleService`, `CategorizationRuleMapper`, `RuleEngine`, `RuleCondition`, `CategorizationRuleRepository` | rule CRUD surface, fields, unique priority, execution timing, `enabled` drift |
| `recurring/RecurringTransactionController.java`, `RecurringTransaction`, `RecurringTransactionService`, `RecurringTransactionMapper`, `RecurringFrequency`, `RecurringGenerationService`, `CreateRecurringTransactionRequest`, `RecurringTransactionResponse` | recurring endpoints, fields, deactivate, generation + deterministic sourceRef |
| `scheduling/RecurringSchedulingJob.java`, `ReconciliationJob.java`, `SchedulingConfig` | 900 s schedules, per-owner fan-out, no lock |
| `budget/BudgetController.java`, `Budget`, `BudgetService`, `BudgetCalculationService`, `BudgetPeriod`, `BudgetStatus`, DTOs | budget endpoints, scope, derived calculation |
| `dashboard/DashboardController.java`, `DashboardReadService`, `DashboardMonthResponse`, `CategoryBreakdownItem` | month-scoped summary, computed totals |
| `statistics/StatisticsService.java`, `AdvancedStatisticsService.java` | internal-only analytics, single-currency constraint |
| `fx/FxController.java`, `ExchangeRateClient`, `ConversionResponse`, `ExchangeRate`, `FxApiResponse` | display-only conversion, degradation, placeholder provider |
| `imports/ImportController.java`, `ImportService`, `ImportReportController`, `ImportReportService`, `CsvExportController`, `CsvExportService` | import/export endpoints, dispatch, response shape |
| `imports/CsvStatementParser.java`, `ParsedStatementRow` | exact CSV schema, structural vs content failures |
| `imports/PdfStatementParser.java`, `TpbankStatementLayout`, `PositionedTextExtractor` | TPBank + synthetic layouts, multi-page, failure modes |
| `imports/RowIdentityService.java`, `SourceRefNormalizer` | tier-1/tier-2 dedup identities |
| `imports/ImportRowProcessor.java`, `ImportRowRecorder`, `ImportRow`, `ImportRowStatus`, `ImportRowOutcome`, `ImportBatch`, `ImportBatchRepository`, `ImportRowRepository`, `ImportRowDetail`, `ImportBatchSummaryResponse`, `BatchNotificationService` | per-row processing, recording, batch model, response DTOs |
| `src/test/...` (89 files: `IntegrationSuiteTest`, `invariants/*`, `*OwnershipTest`, import suites, `TransferServiceTest`, `RecurringGeneration*Test`, `ApiExceptionHandlerTest`, `SpendwiseHttpApiTest`, etc.) | corroboration for atomicity, dedup, ownership-404, error shapes, layout coverage — **inspected, not executed** |
| `docs/defense-audit.md`, `docs/demo-script.md` (committed) | self-documented gaps (transfer endpoint, manual generation trigger) — consistent with source |
| `docs/architecture-notes.md`, `docs/decisions/spring-boot-pin.md` | config/version context — consistent |

## 43. Audit limitations

P2 is a **static source audit**. P2 did NOT:

- run backend tests (`mvn test` not executed);
- start Spring Boot or hit endpoints over HTTP;
- run a database or Flyway migrations;
- upload real files or parse real statements;
- access `D:\java_spring_2_10_2026\private-bank-fixtures` or any private data;
- inspect `docker-compose`/`Dockerfile` runtime details beyond presence;
- verify actual serialized JSON bytes (wire forms follow Jackson defaults —
  labeled FRAMEWORK_DEFAULT_INFERENCE where material);
- verify runtime timezone of any deployment host;
- treat the three uncommitted user docs as evidence or modify them.

Runtime-only questions left as deferred: actual 401 body emitted by the
security entry point, exact `MaxUploadSizeExceededException` mapping in the
embedded container, and live FX provider behavior with a real `base-url`.
These do not affect the contracts above beyond the labeled inference points.
