# M4 — Home / Dashboard

Status: **DELIVERED — awaiting external-audit gate**
(`M4_STARTED=YES`; `M4_HUMAN_APPROVED=NO`;
`M4_READY_FOR_EXTERNAL_AUDIT=YES`)

## Authorization

M3 closed by the Human closeout `3658282` recording
`M3_HUMAN_APPROVED=YES` and `M4_AUTHORIZED_TO_START=YES` (conditional
overnight authorization, same mechanism as the M2 closeout). M4 scope
per ROADMAP + PRODUCT §17 + P3A §7/§8: Home answers "How much do I
have, and how did this month go?"

## Sources of truth

- [../PRODUCT.md](../PRODUCT.md) §17 (Home/dashboard experience), §16
  (session expiry), §19 (activity rows inherit transaction semantics).
- [../P3A-DESIGN-HANDOFF.md](../P3A-DESIGN-HANDOFF.md) §7 (Home:
  hero → account carousel → recent-transactions panel; category
  breakdown + budget cards **deliberately omitted**; import quick-card
  is M5 scope), §8 (visual tokens).
- [../BACKEND-CONTRACT.md](../BACKEND-CONTRACT.md) §22 + BG-013
  (dashboard endpoint + mixed-currency semantics).
- P3A design intent labels the hero "This month" (production label
  confirmed in the handoff — the server computes month scope).

## Backend contract verified (source, read-only) — `8194daa`

`GET /api/dashboard?month=YYYY-MM` (month required; bad/missing → 400):

```json
{
  "month": "2026-10",
  "totalIncome": 0.00,          // BigDecimal — NO currency field
  "totalExpense": 0.00,         // BigDecimal — NO currency field
  "categoryBreakdown": [{ "categoryCode": "FOOD", "amount": 0.00 }],
  "accounts": [ AccountResponse ],
  "budgets": [ BudgetCalculationResponse ]
}
```

Semantics (verified in `DashboardReadService` + `StatisticsService`):

- Per-request computation; nothing stored server-side.
- `totalIncome`/`totalExpense`: sums per type; **same-type mixed
  currencies → 400** (`CurrencyMismatchException → DomainException`).
  Income-vs-expense mixing can return 200 with unlabeled totals — the
  §17 rule handles this (totals are only displayed under a uniform
  account currency).
- `categoryBreakdown`: categorized EXPENSE rows only, summed, sorted
  by `categoryCode`. **Not rendered** (P3A §7 omission); decoded for
  contract fidelity.
- `accounts`: full `AccountResponse` list — Home reads balances from
  `AccountRepository` instead (session-cached, one source of truth),
  because a 400 returns no body at all and the carousel must survive
  the mixed-currency case.
- `budgets`: `BudgetCalculationResponse{budgetId, category, period,
  limit, currency, spent, remaining, percentage, status∈{WITHIN_BUDGET,
  OVER_BUDGET}}` — month-filtered server-side. **Not rendered**
  (P3A §7 omission; budget management is M6). Decoded for fidelity.
- 401 → session layer; no other error surface beyond 400/401/5xx.

## Scope

- `DashboardService` + `DashboardMonthDto`/`CategoryBreakdownItemDto`/
  `BudgetCalculationDto` (+ `AccountResponse` reuse) — wire-faithful,
  `ignoreUnknownKeys`.
- `DashboardRepository` — `suspend fun month(month: YearMonth =
  YearMonth.now())`; always-fresh (server computes per request), no
  cache, generation-stamped publish (`SessionEndedException` on
  dead-generation, uniform with M3 remediation).
- `AccountRepository.mutations` — `SharedFlow<Unit>` emitted on
  successful `create` (Home needs balance-refresh on account creation;
  also closes the M2 stale-list gap for the Accounts tab).
- `HomeViewModel` + `HomeUiState` + `HomeDestination` + new
  `HomeScreen` implementing the P3A composition:
  - **Hero** (`SwHeroPanel`): "This month" income/expense with the
    account-derived currency label — uniform currency only;
    mixed accounts → the honest "Combined totals unavailable"
    state *without a dashboard call* (the product rule is
    unconditional); dashboard 400 → same honest state; dashboard
    5xx/network → section-scoped retry (never sinks the screen);
    never a cross-account "total balance".
  - **Account carousel**: `LazyRow` of account cards (tile + name +
    balance + currency + chevron → account detail).
  - **Recent activity panel**: first 5 rows of the unfiltered list as
    returned (same order the Transactions screen presents — no
    chronology promised anywhere); "See all" → Transactions tab;
    empty → honest "No activity yet" copy; failure → inline section
    retry, never sinks the screen.
  - **States**: no accounts → "Create your first account"; no
    activity → totals show `0.00` with the currency label; loading →
    skeleton; refresh → pull-to-refresh over all three sections;
    refresh failure → inline banner preserving content; 401/dead-gen
    on any leg → `expiredMidLoad` → `onVisible()` reloads on
    same-user resume.
  - Existing scroll-aware extended↔compact "+ Add transaction" FAB
    retained (AD-019).
- `SpendwiseMainShell`: `HomeDestination` wiring + "See all" →
  Transactions tab navigation.

## Forbidden scope

- No category-breakdown or budget cards on Home (P3A §7 Human-accepted
  omission).
- No import quick-card (M5). No budget/recurring/rules surfaces (M6).
- No client-side month arithmetic, balance recomputation, or
  cross-currency aggregation (INV-012; §17).
- No new endpoints; no edits to backend/reference/prototype.
- No date display — `occurredOn` is still not returned; the month
  label is the only temporal copy.

## Architecture constraints

- Server-authoritative balances; dashboard totals are display-only
  BigDecimal — no client arithmetic beyond pass-through.
- `MonthTotals` display requires the §17 pre-condition
  (uniform account currencies); the client never guesses a label from
  the response — the response carries none.
- Session: same overlay/resume contract as M3 — every in-flight leg
  arms a resume-retry flag; dead-generation never publishes.
- Month semantics: `YearMonth.now()` device-local inside the
  repository — not the UTC `Clock` bean (a UTC month would be wrong
  for non-UTC users near boundaries).

## Acceptance criteria

- [x] Uniform-currency Home shows "This month" income + expense with
  the currency label; figures are the exact server BigDecimals.
- [x] Mixed-currency Home shows the honest totals-unavailable hero
  state and never calls the dashboard endpoint for totals.
- [x] Dashboard 400 → same honest state; dashboard 5xx/network →
  section retry; accounts/activity still render.
- [x] Carousel lists every account with balance + currency; tap →
  account detail.
- [x] Activity shows ≤5 rows as returned; row tap → transaction
  detail; "See all" → Transactions tab; failure → inline retry.
- [x] No accounts → create-account empty state; zero-activity →
  `0.00`-labeled totals.
- [x] Transaction create / account create → Home reloads via
  `mutations` collectors.
- [x] Session expiry on any leg → overlay; same-user resume →
  reload; different-user → clean state (no cross-identity data).
- [x] Pull-to-refresh reloads all sections; failure → inline banner.

## Required tests

- `DashboardServiceContractTest` — path, `?month=` param format,
  full-DTO decode (incl. unknown-key tolerance), 400/401 mapping.
- `DashboardRepositoryTest` — mapping, always-fresh fetch,
  dead-generation `SessionEndedException`, error mapping.
- `AccountRepositoryTest` — `create` emits `mutations`.
- `HomeViewModelTest` — all §17 states: uniform/mixed/400/5xx/
  network hero, empty, no-activity, activity failure non-fatal,
  mutation reloads, expiry mid-each-leg → resume reload (401 +
  dead-generation per EXT-MAJOR-1 pattern), stale-response
  suppression.
- `HomeScreenTest` — stateless Compose: hero states, carousel,
  activity rows, empty state, retry callbacks, semantics/CDs.

## QA criteria

- §17 composition fidelity (incl. the P3A omissions — absence of
  breakdown/budget cards is correct, not a gap).
- Contract fidelity: `?month=` is the only param; no invented fields.
- Financial correctness: zero client-side currency aggregation.
- Session/privacy: generation-stamped publish; mutations-driven
  reload; no cross-identity residue.
- Scope: nothing M5/M6 surfaces (no import affordance, no budget UI).

## Stop conditions

Beyond the global contract: if `GET /api/dashboard` semantics differ
from this brief when re-verified (e.g., response shape drift) →
BACKEND GAP report, stop the affected slice.

## Expected final report

- Implementation handoff (FLUX) — delivered below
- Test report (SENTINEL) → [../reviews/M4-SENTINEL-REVIEW.md](../reviews/M4-SENTINEL-REVIEW.md)
- QA report (ARGUS) → [../reviews/M4-ARGUS-REVIEW.md](../reviews/M4-ARGUS-REVIEW.md)
- ATLAS reconciliation + verdict; human gate required: YES — pending below

## Implementation handoff (FLUX)

Production files:

- `data/remote/DashboardDtos.kt` — `DashboardMonthDto` +
  `CategoryBreakdownItemDto` + `BudgetCalculationDto`, matching the
  `DashboardMonthResponse`/`CategoryBreakdownItem`/
  `BudgetCalculationResponse` records field-for-field; BigDecimal
  serializer on every amount.
- `data/remote/DashboardService.kt` — `GET api/dashboard` with the
  single `?month=YYYY-MM` param.
- `core/model/DashboardMonth.kt` — domain model; carries **no**
  currency (the wire carries none — nothing can guess one).
- `data/repository/DashboardRepository.kt` — always-fresh fetch,
  `YearMonth.now()` device-local default, `ApiException`/`IOException`
  mapping, generation-stamped publish guard
  (`SessionEndedException`).
- `data/repository/AccountRepository.kt` — added `mutations`
  `SharedFlow` emitted once per successful `create`.
- `feature/home/` — `HomeUiState` (screen × per-section state model),
  `HomeViewModel` (three independent legs, monotonic `loadSeq`
  stale-suppression, concurrent section fetches, last-good
  preservation on refresh leg-failures, `expiredMidLoad` resume
  contract on 401 + dead-generation), `HomeScreen` (`SwHeroPanel`
  hero + `LazyRow` carousel + `SwCard` activity panel, extended→compact
  FAB preserved), `HomeDestination`.
- `navigation/SpendwiseMainShell.kt` — Home wired to account/txn
  detail, create-account, create-transaction, and See-all →
  Transactions tab.
- `core/network/NetworkModule.kt` + `data/DataModule.kt` — service
  provider + repository bind (`@Singleton`).
- `res/values/strings.xml` — Home section; `home_placeholder` removed.

Design decisions (resolved during implementation):

- §17 progressive publish — the carousel renders the moment accounts
  land; totals/activity publish per-leg rather than gating the screen
  on the slowest section.
- Refresh keeps last-good figures under the indicator; a leg failure
  mid-refresh preserves the section and reports via `refreshError`
  (MIN-1 rule) — a first-load failure still yields the honest
  per-section retry.
- Spend figure displays with the app-wide `−` (U+2212) glyph —
  consistent with transaction rows (not `DecimalFormat`'s hyphen).

## External-audit gate

```
M4_SENTINEL=PASS (0 blockers)
M4_ARGUS=PASS (0 blockers, 0 majors, 2 minors — both fixed in-review)
M4_JVM_TESTS=301/301 (43 new/updated M4 tests)
M4_CONNECTED_INSTRUMENTATION=NOT_RUN_NO_DEVICE (adb devices = 0; androidTest compile-verified)
M4_GATES=assembleDebug+testDebugUnitTest+lintDebug+compileDebugAndroidTestKotlin+assembleRelease+detekt: PASS
M4_LINT=20 informational warnings, 0 errors
M4_READY_FOR_EXTERNAL_AUDIT=YES
M4_HUMAN_APPROVED=NO
M5_STARTED=NO
SAFE_TO_AUDIT=YES
```

## Scope exclusions honored

No category-breakdown card, no budget card, no import quick-card
(M5), no recurring/rules UI (M6), no cross-account balance
aggregation, no currency conversion, no invented dashboard fields or
params, no new dependencies. Backend `8194daa` unchanged; reference
`5f9622c` unchanged; prototype untouched.

## Human gate

`M4_EXTERNAL_AUDIT_VERDICT=PENDING` — awaiting the external audit and
the human closeout that authorizes M5.
