# M2 — Accounts + Design-System Core

Status: **HUMAN_APPROVED — COMPLETE**
(`M2_HUMAN_APPROVED=YES`; M3 authorized)

## Authorization

M1 accepted by external audit and Human-approved
(`M1_HUMAN_APPROVED=YES`, `M2_AUTHORIZED_TO_START=YES` recorded in
`efc3a61`), which opened M2 per `ROADMAP.md`.

## Backend contract verified (source of truth, read-only)

Backend `D:\spendwise` HEAD `8194daa`:

| Endpoint | Success | Failure surface |
| --- | --- | --- |
| `POST /api/accounts` `{name, type, openingBalance, currency}` | 201 `AccountResponse` | 400 + `fields{}`; 400 w/o fields (invalid enum / domain error); 401 |
| `GET /api/accounts` | 200 `AccountResponse[]` | 401 |
| `GET /api/accounts/{id}` | 200 `AccountResponse` | 404 empty body (missing/foreign); 401 |

`AccountResponse` = `{id, name, type, openingBalance, currency, balance}`;
`type ∈ {CASH, BANK}`; `balance` is the server-stored value. Validation:
name `@NotBlank @Size(255)`; type `@NotNull` enum; openingBalance
`@NotNull @DecimalMin("0.00") @Digits(17,2)`; currency `@NotBlank`.
No edit/delete/archive endpoints exist — none implemented.

## Implemented

- **Domain**: `Account`/`AccountType` (`UNKNOWN` open-set defense).
- **Remote**: `AccountsService` (list/get/create), `AccountDto`,
  `CreateAccountRequestDto` — `BigDecimalSerializer` on money fields.
- **Repository**: `RemoteAccountRepository` — `AppErrorException`-only
  failure surface; session-scoped in-memory cache dropped on every
  `cacheInvalidations` signal; create/detail merge into cache.
- **DI**: `CoroutineModule` (`@ApplicationScope`), `AccountsService`
  provider, repository binding.
- **ViewModels**: `AccountsViewModel`, `CreateAccountViewModel`,
  `AccountDetailViewModel` — state machines for Loading/Empty/Content/
  NotFound/Error; SessionExpired mid-load retry via `onVisible`;
  submit-lock release on expiry; client mirrors backend validation.
- **UI**: Accounts tab (S06: hero, mixed-currency banner, rows, empty,
  error states), Create (S07: label-bound fields, CASH/BANK segmented
  control, open ISO-4217 dropdown, decimal input), Detail (S08 header:
  hero + type/currency/opening fields — transaction section is M3).
- **Design-system core**: `SwHeader/SwCard/SwBanner/SwStateBlock/
  SwSkeletonRows/SwNetworkError/SwServerError`, `SwHeroPanel/
  AccountTile/MoneyText`, `SwTextField/SwSegmentedControl/
  SwDropdownField`, `MoneyFormat` (locale grouping, ISO fraction
  digits, BigDecimal-only). P3A tokens: brand gradients, mint/blue
  tiles, topo motif, 52dp targets, merged row semantics, heading +
  live-region a11y.
- **Navigation**: `AccountCreateRoute`, `AccountDetailRoute(accountId)`
  pushed on the outer graph; expiry-overlay predicate generalized
  (fires over pushed screens, idempotent — no duplicate overlays).
- **Assets**: Manrope variable font (OFL), `coin_3d` illustration,
  Lucide icon vectors, third-party notices.

## Tests

`testDebugUnitTest`: **152/152 PASS** — wire contract (7), repository
(12), ViewModels (31), money format/model (7), route serialization (1),
plus the full M1 regression set (88). androidTest: 20 new Compose cases
across the three screens + 1 nav regression — compile-verified; **no
device** (`adb devices` empty), instrumentation not run. Real-backend
E2E: not run (no local backend instance provisioned for the milestone).

## Quality gates

`assembleDebug` PASS · `lintDebug` PASS (0 errors; 20 informational
warnings — dependency versions, decorative VectorRaster note,
pre-existing `mipmap-anydpi-v26`) · `compileDebugAndroidTestKotlin`
PASS · `assembleRelease` PASS · detekt **0 findings**.

## Reviews

- Sentinel: [reviews/M2-SENTINEL-REVIEW.md](../reviews/M2-SENTINEL-REVIEW.md) — PASS.
- Argus: [reviews/M2-ARGUS-REVIEW.md](../reviews/M2-ARGUS-REVIEW.md) —
  0 blockers, 0 majors, 4 minors (accepted), 5 notes. Reconciled.

## Scope exclusions honored

No transactions (incl. detail-screen history — deferred to M3), no
dashboard, import, budgets, recurring, or rules. No Room/WorkManager/
extra modules. No new dependencies (only assets + stdlib/JDK APIs).
Backend, reference, prototype untouched; private fixtures never read.

## Boundary preservation

- Backend: HEAD `8194daa` unchanged (pre-existing user-owned dirty docs
  only — untouched).
- Reference `D:\app_spendwise\kotlin-accelerator-ai`: HEAD `5f9622c`
  unchanged.
- Prototype `D:\app_spendwise\spendwise-ui-prototype`: unchanged
  (read-only design source).

## Human gate

`M2_EXTERNAL_AUDIT_ORIGINAL_VERDICT=FINDINGS`
(`BLOCKER=0`, `MAJOR=1` — EXT-MAJOR-1 session-cache isolation was
timing-dependent). Remediation `db09826` resolved EXT-MAJOR-1 via the
generation-stamped `SessionCache` + synchronous `SessionManager`
generation counter, plus MIN-1/MIN-2/MIN-4 and EXT-MIN-1..5;
verification recorded in `bc02a3b` and
[reviews/M2-EXTERNAL-AUDIT-SUMMARY.md](../reviews/M2-EXTERNAL-AUDIT-SUMMARY.md).

`M2_EXT_MAJOR_1=RESOLVED`, `M2_POST_REMEDIATION_VERIFICATION=PASS`,
`M2_SECURITY_PRIVACY=PASS`, `M2_FINAL_JVM_TESTS=171/171`,
`M2_CONNECTED_INSTRUMENTATION=NOT_RUN_NO_DEVICE`,
`M2_HUMAN_APPROVED=YES`, `M2_STATUS=COMPLETE`,
`M3_AUTHORIZED_TO_START=YES`.
Next action: **M3 — Transactions**.
