# M4 SENTINEL Review — Home / Dashboard

Reviewer role: **SENTINEL** (test engineer / verification), per
`agent-system/agents/sentinel.md`. Scope: correctness, contract fidelity,
test coverage, session/security integration, financial-correctness
invariants, P3A §7 fidelity, scope discipline, quality gates for the M4
Home vertical slice.

## Verified contract (source-checked)

Backend `D:\spendwise` HEAD `8194daa` (read-only).
`DashboardController` / `DashboardReadService` / `StatisticsService`:

| Endpoint | Success | Failure surface |
| --- | --- | --- |
| `GET /api/dashboard?month=YYYY-MM` | 200 `DashboardMonthResponse` | 400 (missing/malformed `month`); 400 `CurrencyMismatchException` (same-type mixed currency); 401 |

Wire shape (`DashboardMonthResponse` record): `{month: "YYYY-MM",
totalIncome: BigDecimal, totalExpense: BigDecimal,
categoryBreakdown[{categoryCode, amount}], accounts[AccountResponse],
budgets[BudgetCalculationResponse]}` — verified field-for-field at
`8194daa`. `totalIncome`/`totalExpense` are **positive** raw sums filtered
by `occurredOn` inside the month; `month` is echoed from the requested
`YearMonth`; `requireSingleCurrency` throws per type-bucket (the 400).

**No currency field exists on any total** — the §17 labeling rule
derives display currency from uniform account currency, never from the
payload. `accounts`/`budgets`/`categoryBreakdown` are decoded for
contract fidelity and deliberately unused by Home (P3A §7 omission).

## Correctness checklist

| Check | Result |
| --- | --- |
| DTO field names/types match `DashboardMonthResponse`, `CategoryBreakdownItem`, `BudgetCalculationResponse`, `AccountResponse` exactly | PASS (`DashboardServiceContractTest` decodes every member) |
| `?month=YYYY-MM` is the only query param — nothing invented | PASS (`month sends exactly one query param`) |
| `MonthTotals.Loaded` only under uniform account currency; mixed → `Unavailable` with **zero dashboard calls** | PASS (`mixed currencies … no dashboard call`) |
| Dashboard 400 (`CurrencyMismatchException`) → `AppError.BadRequest` → honest `Unavailable` hero | PASS (contract 400 test + VM `dashboard 400 … unavailable`) |
| `totalExpense` rendered as the signed spend figure (−) — backend sums positive | PASS (backend `sumAmounts` positive; hero inverts for display only) |
| Dashboard fetch is always-fresh — no session caching | PASS (`month is always-fresh — no session caching`) |
| Default month derives from device-local `YearMonth.now()` — the UTC `Clock` bean is never used | PASS (`default month derives from the device-local clock`) |
| `YYYY-MM` serialization of explicit months | PASS (`explicit month serializes to YYYY-MM`) |
| Dead-generation publish → `SessionEndedException`, never surfaces | PASS (`response landing after a generation boundary throws SessionEndedException`) |
| `io`/5xx/400/401 error mapping through `toAppError` | PASS (repo tests per code) |
| Cancelled fetch propagates `CancellationException` unmangled | PASS |
| `accounts` spine: empty → `Empty`, network → `Error(Network)`, server → `Error(Server)` | PASS (VM tests) |
| Refresh failure over live content → inline banner, content kept (MIN-1) — **including per-section last-good preservation** | PASS (`accounts refresh failure … inline banner`; sections preserve `Loaded`/`Items` under `refreshError`) |
| Activity renders ≤5 rows of the unfiltered list — no params ever sent | PASS (`activity renders the first five rows …`) |
| Activity/hero failures degrade their section only | PASS (both VM tests) |
| `retryTotals`/`retryActivity` re-run their leg only | PASS (call-count assertions) |
| Transaction **and** account mutations reload all sections | PASS (both mutation tests) |
| `refresh()` bypasses the account cache (`refresh=true`) | PASS (`[false, true]` call capture) |
| Superseded loads never publish — monotonic `loadSeq` | PASS (`a superseded load never publishes`) |
| Session expiry mid-**any** leg (accounts/dashboard/activity) → pending section + `onVisible()` resume reload — 401 **and** dead-generation paths | PASS (5 dedicated VM tests) |
| EXT-MAJOR-1: `SessionEndedException` releases `isRefreshing` and arms resume reload | PASS (`dead-generation mid-refresh clears isRefreshing …`) |
| `AccountRepository.create` emits one `mutations` signal | PASS (`create emits one mutation signal`) |
| P3A §7 fidelity: hero + carousel + activity only — no breakdown/budget/import UI | PASS (screen renders no such nodes; strings contain none) |
| a11y: merged account-card CD, transaction-row CD, ≥48dp targets | PASS (Compose assertions on merged CDs) |

## Test coverage (JVM: 301 passing, 0 failures — Gradle-verified)

New/updated M4 suites (+43 over the post-remediation M3 run of 258):

| Suite | Tests | What it proves |
| --- | --- | --- |
| `DashboardServiceContractTest` | 7 | wire path, `?month=` format, full-DTO decode, unknown-key tolerance, 400/401 through real interceptors |
| `DashboardRepositoryTest` | 10 | mapping, always-fresh, month derivation + format, error mapping, dead-generation, cancellation |
| `AccountRepositoryTest` (+1) | 1 new | `create` → `mutations` emission |
| `HomeViewModelTest` | 24 | every §17 state, section independence, mutations, races, all session-expiry legs, resume contract |
| `HomeScreenTest` (androidTest) | 16 | stateless Compose: hero states, carousel semantics/CDs, activity rows, empty/error, all callbacks |

## Quality gates (this run)

| Gate | Result |
| --- | --- |
| `testDebugUnitTest` | PASS — 301 tests, 0 failures/errors |
| `compileDebugAndroidTestKotlin` | PASS (compile-verified; `adb devices` = 0 — instrumentation not executed) |
| `detekt` | PASS — 0 smells |
| `compileDebugKotlin` (incl. Hilt/KSP graph) | PASS |

## Findings during verification

| Severity | Finding | Disposition |
| --- | --- | --- |
| MINOR (test-side) | `FakeDashboardRepository.monthResult` default recursively called the fake's own `month()` — infinite recursion | Fixed — companion factory invoked explicitly |
| MINOR (test-side) | Buffered `MutableSharedFlow.tryEmit` drops emissions before collector attach — flaky-by-construction test | Fixed — collector attached via `runCurrent()` before `create` |
| MINOR (test-side) | `backgroundScope.async` dispatch ordering skipped the generation read before the boundary | Fixed — `runCurrent()` parks the call on the gate first |
| MINOR (prod) | `load()` gated the whole screen on the slowest section | Improved — progressive per-section publish + concurrent legs |
| MINOR (prod) | Refresh-time section failure could replace last-good figures with an error section | Fixed — last-good preserved under `refreshError` banner (MIN-1 rule) |

## Scope check

- Nothing M5/M6: no import affordance, no budget/category-breakdown UI,
  no recurring/rules surfaces.
- No invented backend fields or params.
- `Money`/`BigDecimal` only — `grep -rn "Double\|Float"` on the new files
  is clean.
- Backend and reference repos untouched (verified via `git status`).

## Verdict

**PASS** — M4 Home vertical slice meets the brief's required-test matrix
with genuine tests (no theater); all gates green; ready for ARGUS.
