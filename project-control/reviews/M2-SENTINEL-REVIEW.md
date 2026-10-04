# M2 SENTINEL Review — Accounts + Design-System Core

Reviewer role: **SENTINEL** (test engineer / verification), per
`agent-system/agents/sentinel.md`. Scope: correctness, contract fidelity,
test coverage, session/security integration, P3A fidelity, scope
discipline, quality gates for the M2 vertical slice.

## Verified contract (source-checked)

Backend `D:\spendwise` HEAD `8194daa` (read-only). `AccountController`:

| Endpoint | Success | Failure surface |
| --- | --- | --- |
| `POST /api/accounts` `{name, type, openingBalance, currency}` | 201 `AccountResponse` | 400 + `fields{name,type,openingBalance,currency}` (bean validation); 400 w/o fields (invalid enum value / domain error); 401 |
| `GET /api/accounts` | 200 `AccountResponse[]` | 401 |
| `GET /api/accounts/{id}` | 200 `AccountResponse` | 404 **empty body** (missing or foreign account); 401 |

Wire shapes (`AccountResponse` record): `{id: UUID, name: String,
type: String, openingBalance: BigDecimal, currency: String,
balance: BigDecimal}`. `type` ∈ `{CASH, BANK}`; `balance` is the stored
server value (`AccountRepository` entity read — no client derivation
possible). Request validation: `@NotBlank @Size(max=255)` name,
`@NotNull` type, `@NotNull @DecimalMin("0.00") @Digits(17,2)`
openingBalance, `@NotBlank` currency. **No edit/delete/archive
endpoints exist** — none declared client-side.

## Correctness checklist

| Check | Result |
| --- | --- |
| DTO field names/types match `AccountResponse`/`CreateAccountRequest` exactly | PASS |
| All three endpoints authenticated via M1 `AuthInterceptor`; no `@PublicEndpoint` | PASS (contract tests assert `Authorization: Bearer` on list/get/create) |
| Authenticated 401 → `Session.Expired` via `ErrorInterceptor` (idempotent push, no duplicate overlay) | PASS (`authenticated 401 on list expires the session`; nav test `expiredTwice_doesNotStackDuplicateOverlays`) |
| Foreign/missing account 404 → `AppError.NotFound` → honest NotFound state (no invented "server error") | PASS (contract + repo + VM tests) |
| `fields{name}`/`fields{openingBalance}` → inline field errors; other `fields{}` keys → generic form error | PASS (VM tests) |
| Client mirrors backend limits — name ≤255, balance ≥0, ≤2 fraction digits, ≤17 integer digits; no invented caps | PASS (initially shipped a 60-char cap caught in self-review → corrected to backend's 255) |
| `BIGDECIMAL_ONLY` — BigDecimal serializer on the wire, BigDecimal domain model, no Float/Double anywhere | PASS |
| Server-authoritative balance — no client ledger, no recomputation | PASS (repository maps `balance` verbatim) |
| Cross-currency aggregation never computed — hero sums only when `distinct(currency).size==1`, else count+currencies + always-visible info banner | PASS |
| Unknown `type` on the wire → `AccountType.UNKNOWN` (open-set rule §15), never a crash | PASS (repo test) |
| Invalid currency code on the wire → `Unknown` error, never crash/partial render | PASS (repo test) |
| Session-scoped cache: `cacheInvalidations` drops all cached accounts on sign-out/expiry/different-identity | PASS (repo test `session invalidation drops the cache so list refetches`) |
| Created account merges into cache — list shows it without refetch | PASS (repo test) |
| Pushed screens (create/detail) sit on the outer graph; expiry overlay fires over them too (guard generalized from shell-only) | PASS |
| Expiry mid-load + same-user resume → screen re-runs the aborted load (`onVisible` hook); draft forms survive | PASS (VM tests) |
| Submit lock released on SessionExpired so the preserved form can resubmit | PASS (VM test) |
| Expired overlay cannot stack (idempotent predicate) | PASS (nav test) |
| a11y: ≥52dp buttons, 44dp+ targets, merged row semantics with full name/type/balance description, heading semantics, live-region banners, bound field labels | PASS |
| No token/body logging added; debug stays headers-only | PASS |

## Test coverage (unit: 152 passing, 0 failures — XML-verified)

New M2 suites:

- **Wire contract** `AccountsServiceContractTest` (7): paths, methods,
  request/response DTO shapes incl. BigDecimal fidelity, bearer header,
  authenticated-401→expiry, 404, `fields{}`, unknown-field tolerance.
- **Repository** `AccountRepositoryTest` (12): cache serve/refresh/miss,
  merge-on-detail, create-append, session invalidation, 404/IO/400-field
  mapping, unknown type, invalid currency.
- **ViewModels** `AccountsViewModelTest` (10),
  `CreateAccountViewModelTest` (15), `AccountDetailViewModelTest` (6):
  state mapping, mixed-currency flag, client validation parity, server
  `fields{}` channels, submit lock, SessionExpired mid-load/mid-submit
  behavior + `onVisible` resume-retry.
- **Formatting/model** `MoneyFormatTest` (7): currency fraction digits,
  locale grouping, HALF_UP rounding, negatives, unknown-code fallback,
  `AccountType.fromWire`.
- **Navigation** `AppRoutesTest` (1) + `SpendwiseAppTest` nav regression
  `expiredTwice_doesNotStackDuplicateOverlays` — pushed-route expiry
  guard.
- **M1 regression** — all 88 M1 unit tests still pass in the same run
  (auth/session untouched by M2 code paths).

Compose (androidTest — **compile-verified only**; `adb devices` empty, no
instrumentation run): `AccountsScreenTest` (8), `CreateAccountScreenTest`
(8), `AccountDetailScreenTest` (3), `SpendwiseAppTest` (+1 nav case = 9).
Screen tests target stateless composables (no Hilt) — they cover states,
copy, row semantics, dropdown options, submit lock, callbacks. The
Hilt-wired destinations are compile-verified; no device exists for a real
nav-to-accounts instrumentation pass (honest gap — same class as M1).

## Gates (this machine)

`assembleDebug` PASS · `testDebugUnitTest` PASS (**152/152**) ·
`lintDebug` PASS (0 errors; 20 warnings — all informational:
dependency-version notices + decorative `topo_lines` VectorRaster note +
pre-existing `mipmap-anydpi-v26` ObsoleteSdkInt) ·
`compileDebugAndroidTestKotlin` PASS · `assembleRelease` PASS ·
detekt **0 findings**.

## Sentinel verdict

```
SENTINEL_VERDICT=PASS
UNIT_TESTS=152
UNIT_FAIL=0
CONTRACT_TESTS=PASS
REPOSITORY_TESTS=PASS
VIEWMODEL_TESTS=PASS
COMPOSE_TESTS=PASS (compile-verified; device run deferred — no device)
M1_REGRESSION=PASS
SECURITY_REVIEW=PASS
SCOPE_CHECK=PASS (accounts only; zero M3+ surface)
```
