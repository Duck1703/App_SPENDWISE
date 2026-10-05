# M4 External Audit Summary — Home / Dashboard

Independent external audit of the M4 vertical slice, per the overnight
pipeline contract. Audit performed **read-only** on the committed range
before any remediation.

## Audit scope

- **Audited HEAD**: `29d1b623268b4ebcf8235e4276735adf87067f0f`
- **Audited range**: `3658282` (M3 human closeout) .. `29d1b62`
- **Branch**: `main`, worktree clean at audit start.
- **Backend verified at**: `8194daa9959f2d568dc13ab0f211787195efc79d`
  (read-only; `DashboardController`, `DashboardReadService`,
  `StatisticsService`, `DashboardMonthResponse`,
  `CategoryBreakdownItem`, `AccountResponse`,
  `BudgetCalculationResponse` inspected at source).
- **Canonical scope**: ROADMAP M4 — "Home / Dashboard (hero month
  totals, carousel, recent activity, mixed-currency state)". No M5
  leakage found in the diff (no import/budget/recurring/rules UI).

## Findings

### Verdict: `M4_INITIAL_EXTERNAL_AUDIT=FINDINGS`

| ID | Severity | Finding |
| --- | --- | --- |
| EXT-M4-MIN-1 | MINOR | **Collapsed FAB unlabeled.** `ExtendedFloatingActionButton` hides the text when `expanded=false`; the icon carries `contentDescription=null` → an icon-only button with no accessible name. Carried over from the M0 placeholder; a11y floor is the canonical label in both forms. |
| EXT-M4-MIN-2 | MINOR | **Inline actions under the 48dp touch floor + hero figures unmerged.** `See all`, hero retry, and activity retry `TextButton`s use the M3 ~40dp default below `SwSpacing.TouchTargetMin` (48dp); the hero figure row's `−`/amount/code nodes are unmerged, so TalkBack reads the sign as an orphan node. |
| EXT-M4-NOTE-1 | NOTE | A session-expired section leg publishes `Loading` until resume rather than preserving last-good — the session overlay covers the screen; consistent with EXT-MAJOR-1 pending-then-reload. Recorded as design note. |
| EXT-M4-NOTE-2 | NOTE | `month()` default uses device-local `YearMonth.now()` — correct product semantics ("This month" = the user's calendar month); the backend filters on `occurredOn` and echoes the requested `YYYY-MM`. The UTC `Clock` bean is correctly NOT used. |

## Areas audited — all PASS unless finding above

| Area | Evidence |
| --- | --- |
| Canonical scope / no M5 leakage | diff = dashboard slice + Home slice + wiring + tests + docs only |
| Backend contract fidelity | `GET /api/dashboard`, required `?month=` parsed as `YearMonth` `YYYY-MM` (`DomainException`→400 on malformed/missing); response record field-for-field vs `DashboardMonthResponse` — `month,totalIncome,totalExpense,categoryBreakdown[{categoryCode,amount}],accounts[AccountResponse{id,name,type,openingBalance,currency,balance}],budgets[BudgetCalculationResponse{budgetId,category,period,limit,currency,spent,remaining,percentage,status}]` |
| DTO fidelity | `DashboardMonthDto` matches exactly; optional lists default `emptyList()`; `BigDecimalSerializer` on every amount; no silent zero defaults on required fields |
| 400 semantics | `ApiException(400)` no-fields → `AppError.BadRequest` → `MonthTotals.Unavailable` (same-type `CurrencyMismatchException`); `fields{}`-bearing 400 → `Validation` → honest Failed — never a fabricated total |
| Mixed-currency safety | uniform-currency precondition `accounts.map{balance.currency}.distinct().singleOrNull()` — mixed → `Unavailable` **with zero dashboard calls** (VM test asserts `monthCalls.isEmpty()`) |
| Call suppression race | cross-load divergence is covered by `loadSeq` — a load whose dashboard response lands after a newer load publishes is dropped (`seq != loadSeq` → return); within a load the SAME account list drives both carousel and currency check, so they cannot diverge |
| Zero client-side aggregation | no `sumOf`/`reduce`/`fold`/`add` on money paths — only sign presentation on a server figure |
| Three-leg independence | accounts = spine (Error/Empty), totals + activity = independent degradable sections, concurrent `async` fetch, progressive publish |
| Progressive publish privacy | section publishes happen only under `seq == loadSeq` on the newest load; repository guards generation before returning |
| Last-good refresh | accounts-leg failure → `refreshError` banner, content kept; section-leg failure during refresh preserves `Loaded`/`Items` under the banner |
| `loadSeq` stale-suppression | monotonic; superseded load never publishes (`a superseded load never publishes` test) |
| Session-generation (§19) | `RemoteDashboardRepository` checks `sessionGeneration` before returning; dead-generation → `SessionEndedException` → never surfaces; account/txn caches already generation-stamped |
| 401 mid-leg (§20) | every leg arms `expiredMidLoad`; `onVisible()` → full refresh; no stuck skeleton (5 dedicated VM tests: per-leg + mid-load + mid-refresh) |
| Account mutations (§21) | `create` emits AFTER the generation check — dead-session writes cannot signal; emission once per success |
| Transaction mutation integration (§22) | `transactionRepository.mutations` collected → all sections reload; no local recompute |
| FAB semantics (§23) | `derivedStateOf { scrollState.value > 96 }` — scroll-derived, deterministic — **but see MIN-1** |
| Navigation (§24) | typed routes only: account card → `AccountDetailRoute`, row → `TransactionDetailRoute`, See-all → `TransactionsRoute` (saveState/launchSingleTop/restoreState), FAB → `TransactionCreateRoute`, empty → `AccountCreateRoute`; four tabs preserved |
| "This month" copy (§10) | backend filters `occurredOn` within the requested month and echoes it — copy truthful |
| Test quality (§27) | real Retrofit+MockWebServer wire tests; deterministic concurrency via gates/`runCurrent`; 24-scenario VM matrix; 16 stateless Compose tests; no theater |
| Static safety | no `println`/`Log`/`Timber`/BODY logging/`GlobalScope`/Double/Float on the new paths; money stays `BigDecimal` |

## Gate rerun (audited HEAD)

| Gate | Result |
| --- | --- |
| `assembleDebug` | PASS |
| `testDebugUnitTest` | PASS — **301/301** (XML-verified) |
| `lintDebug` | PASS — 0 errors, 20 informational warnings |
| `compileDebugAndroidTestKotlin` | PASS (compile-only; `adb devices` = 0 — `NOT_RUN_NO_DEVICE`) |
| `assembleRelease` | PASS |
| `detekt` | PASS — 0 smells |

## Remediation

MIN-1 and MIN-2 remediated in this audit's remediation pass (close in
scope, low risk): FAB carries a merged `contentDescription` in both
forms; the three inline actions meet the 48dp floor; hero figure rows
carry merged semantics. Regression assertions added to
`HomeScreenTest`.

Post-remediation falsification re-verified: build + full JVM suite +
detekt + androidTest compile all green; no behavior change to state
machine, contract, or financial semantics.

## Final verdict

```
M4_INITIAL_EXTERNAL_AUDIT=FINDINGS (0 blockers, 0 majors, 2 minors)
M4_REMEDIATION=COMPLETE
M4_POST_REMEDIATION=VERIFIED
BLOCKER=0
MAJOR=0
M4_CORE_CORRECTNESS=PASS
M4_FINANCIAL_CORRECTNESS=PASS
M4_SECURITY_PRIVACY=PASS
M4_CONTRACT_FIDELITY=PASS
M4_TEST_EVIDENCE=PASS
M4_EXTERNAL_AUDIT_FINAL_VERDICT=ACCEPTED
```
