# M4 ARGUS Review — Home / Dashboard

Reviewer role: **ARGUS** (independent QA), per
`agent-system/agents/argus.md`. Scope: the full M4 diff — dashboard
remote/domain/repository, `AccountRepository.mutations`, DI wiring,
`HomeViewModel`/`HomeUiState`/`HomeScreen`/`HomeDestination`, shell
navigation, strings, and the M4 test set — reviewed against PRODUCT §17,
P3A §7, BACKEND-CONTRACT §22, and the M1–M3 invariants.

## Verdict

**PASS — 0 Blockers, 0 Majors, 2 Minors (both fixed in-review), 1 noted
decision.** The slice is backend-faithful, section-honest, and clean on
every financial/session invariant. Ready for final gates + milestone
reconciliation.

## Findings

### MINOR-1 — sign-glyph inconsistency in the hero spend figure (FIXED)

`MonthFigure` originally rendered the spend total as
`Money(amount.negate(), …)` → `DecimalFormat` emits hyphen-minus
`-` (U+002D), while `TransactionRow.signedAmountText` uses the
typographic minus `−` (U+2212) app-wide. Fixed — the figure now renders
the positive amount prefixed with the same `−` glyph
(`HomeScreen.MonthFigure`).

### MINOR-2 — whole-screen gating + refresh data loss in `load()` (FIXED)

The first implementation awaited totals+activity inside a single
`Content(...)` construction: (a) the slowest leg gated the carousel —
against §17's per-section loading contract; (b) a refresh-time section
failure could replace last-good figures with an error section — against
the MIN-1 preserved-content rule. Fixed — `load()` now publishes the
carousel immediately with `Loading` sections, runs the two legs
concurrently (`async`), publishes each section as it lands, and a
`Failed` leg during refresh preserves the last-good section under the
`refreshError` banner. A regression test exists
(`accounts refresh failure … inline banner`; preserved-section path
covered by the same mechanism, exercised in the VM suite).

### Noted decision (not a defect)

A session-expired section leg returns `Loading` until resume rather
than preserving last-good figures — the session overlay covers the
screen throughout, so the difference is invisible; the uniform
pending-then-reload contract (EXT-MAJOR-1) is the safer invariant.
Recorded for awareness only.

### Observation — decoded-but-unused DTO members

`DashboardMonthDto` decodes `accounts`, `budgets`, and
`categoryBreakdown` for contract fidelity; Home renders none of them —
the P3A §7 omission is deliberate (budgets = M6, no breakdown card on
Home). Correct, not a gap.

## Checklist

| Check | Result |
| --- | --- |
| Backend contract fidelity — `GET /api/dashboard?month=YYYY-MM`, response record field-for-field vs `8194daa` | PASS |
| §17 hard rule — totals labeled ONLY under uniform account currency; mixed → unavailable state, **zero** dashboard calls | PASS |
| Same-type mixed-currency 400 → honest unavailable (not error) | PASS |
| No currency ever guessed from the response payload (it carries none) | PASS |
| `totalExpense` sign convention — backend positive sum; display-only `−` prefix | PASS |
| Financial correctness — `BigDecimal` only; zero client-side aggregation of balances/totals | PASS |
| Session/privacy — generation-stamped publish guard in `RemoteDashboardRepository`; `SessionEndedException` handled on every leg; `expiredMidLoad` arms resume uniformly | PASS |
| Mutations — transaction AND account creation reload all Home sections | PASS |
| Stale-suppression — monotonic `loadSeq`; superseded loads never publish | PASS |
| State model — Loading/Empty/Content/Error + per-section Loading/Loaded/Unavailable/Failed + `refreshError` banner — complete and honest | PASS |
| Compose — hero `SwHeroPanel`, carousel `LazyRow`, activity `SwCard`+`TransactionRow`, extended→compact FAB preserved | PASS |
| a11y — merged account-card CD reusing `account_row_cd`; merged transaction-row CD; decorative icons nulled | PASS |
| P3A §7 fidelity — hero + carousel + activity only; no breakdown/budget/import UI | PASS |
| Scope — no M5/M6 surfaces, no invented fields/params/endpoints | PASS |
| Navigation — See-all → Transactions tab (saveState/launchSingleTop/restoreState); card → detail; row → detail; FAB → create | PASS |
| DI — `DashboardService` provider + `DashboardRepository` bind, both `@Singleton` | PASS |
| No secrets/PII logged; no `Log`/`println`; no Float/Double on money paths | PASS |
| `home_placeholder` removed cleanly — no dangling references | PASS |

## Quality gates (this review's run)

| Gate | Result |
| --- | --- |
| `testDebugUnitTest` | PASS — 301 tests, 0 failures/errors |
| `compileDebugAndroidTestKotlin` | PASS (compile-only; `adb devices` = 0 — instrumentation not executed) |
| `detekt` | PASS — 0 smells |
| `compileDebugKotlin` + Hilt/KSP | PASS |
