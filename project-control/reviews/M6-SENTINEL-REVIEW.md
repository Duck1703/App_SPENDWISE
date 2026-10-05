# M6 SENTINEL Review — Planning (Recurring, Budgets, Categorization Rules)

Reviewer role: **SENTINEL** (test engineer / verification), per
`agent-system/agents/sentinel.md`. Scope: correctness, contract
fidelity, test coverage, session/security integration,
financial-correctness invariants, P3A secondary-fidelity pattern
fidelity, scope discipline, quality gates for the M6 planning
vertical slice.

## Verified contract (source-checked)

Backend `D:\spendwise` HEAD `8194daa` (read-only). `BudgetController`,
`RecurringTransactionController`, `CategorizationRuleController`,
`CategoryController` + request/response records.

| Endpoint | Success | Failure surface |
| --- | --- | --- |
| `POST /api/budgets` | 201 + `BudgetResponse` | 400 + `fields{}` (unknown category, malformed `YYYY-MM`, non-positive/over-precision limit); 409 duplicate (category, period); 401 |
| `GET /api/budgets` | 200 `[BudgetResponse]` | 401 |
| `GET /api/budgets/{id}` | 200 `BudgetResponse` (owner-scoped) | 404; 401 |
| `GET /api/budgets/{id}/calculation` | 200 `BudgetCalculationResponse` — `spent/remaining/percentage/status` all server-derived | 404; 401 |
| `POST /api/recurring-transactions` | 201 + `RecurringTransactionResponse` | 400 + `fields{}`; 401 |
| `GET /api/recurring-transactions` | 200 `[…]` | 401 |
| `GET /api/recurring-transactions/{id}` | 200 | 404; 401 |
| `DELETE /api/recurring-transactions/{id}` | 200 + deactivated template in body (soft-deactivate — generated rows untouched, `RECURRING:<id>:<due>` refs unchanged) | 404; 401 |
| `POST /api/categorization-rules` | 201 + `CategorizationRuleResponse` | 400 + `fields{}` (unknown `conditionType`/category, oversize value); 409 duplicate (user, priority); 401 |
| `GET /api/categorization-rules` | 200 `[…]` — ascending-priority order owned by the server | 401 |
| `GET /api/categorization-rules/{id}` | 200 | 404; 401 |
| `DELETE /api/categorization-rules/{id}` | 200 + disabled rule in body (soft-delete; `enabled=false` remains listable) | 404; 401 |

No budget edit/delete endpoint exists (BE-011) — the client surfaces
that honestly and offers no unsupported control.

## Correctness checklist

| Check | Result |
| --- | --- |
| All three list paths + `GET` method + Bearer header | PASS (`budget list decodes and auths`, `recurring list decodes templates`, `rules list decodes server priority order verbatim`) |
| Budget create body: `category/period/limit/currency`, no `id` | PASS (`budget create sends exact body and decodes 201`) |
| `limit` serialized as `BigDecimal` — plain scale, no float noise | PASS (body asserts `"limit":250.5`) |
| `GET {id}/calculation` decodes `spent/remaining/percentage/status` verbatim | PASS (`budget calculation decodes server figures`) |
| 400 + `fields{limit}` → `ApiException(400, fields)` → `Validation` | PASS (`budget 400 fields decode as validation`) |
| 409 duplicate budget → `Conflict` | PASS (`budget 409 surfaces as ApiException conflict`) |
| Recurring create body omits null `category`/`endDate`, ISO dates | PASS (`recurring create omits null optionals and sends ISO dates`) |
| Recurring `DELETE` returns the 200 deactivated body (`active=false`) | PASS (`recurring DELETE returns the deactivated template in the 200 body`) |
| Foreign recurring id → 404 → `NotFound` | PASS (`recurring foreign id surfaces as 404`) |
| Rule create body never sends `enabled` (S22) | PASS (`rule create never sends enabled`) |
| Rule 409 duplicate priority → `Conflict` | PASS (`rule duplicate priority surfaces 409`) |
| Rule `DELETE` returns the 200 disabled body | PASS (`rule DELETE returns the disabled rule`) |
| Authenticated 401 → `Session.Expired` on any planning call | PASS (`401 on a planning call drives Session Expired`) |
| Unknown `status`/`frequency`/`conditionType` armor to `UNKNOWN` — never crash, never guess | PASS (repo tests for all three) |
| Server list order preserved verbatim — no client re-sort | PASS (`list preserves server priority order without re-sorting`) |

## Session / cross-user privacy

| Check | Result |
| --- | --- |
| Generation captured pre-call, re-validated post-response on every repo method | PASS (`RemoteBudgetRepository`, `RemoteRecurringRepository`, `RemoteRuleRepository` — same EXT-MAJOR-1 pattern as M4/M5) |
| Dead-generation list → `SessionEndedException`, nothing published | PASS (three dedicated repo tests) |
| Dead-generation create/disable → no mutation emission | PASS (`a dead-generation create never hits the wire-visible mutation`, `a dead-generation disable never emits`) |
| Dead-generation create response → `Ambiguous` honesty (the row probably exists server-side) | PASS (VM `SessionEndedException → FormError.Ambiguous` in all three create VMs) |
| 401 mid-write → form unlatches, draft preserved, never auto-replayed | PASS (all three create VMs; only `SessionExpired` returns early with `isSubmitting=false`) |
| Same-user resume → user-visible `onVisible()` retry only | PASS (`expiredMidLoad` armed on `SessionEndedException`/`SessionExpired`; `onVisible` reloads — no write replay anywhere) |
| `mutations` refresh keeps list surfaces fresh after create/disable | PASS (repos emit once per success; list VMs collect → `refresh()`) |

## Financial correctness

| Check | Result |
| --- | --- |
| All money `BigDecimal`; no `Double`/`Float` in the slice | PASS (`Money` everywhere; detekt clean) |
| Zero client-side aggregation — `spent/remaining/percentage/status` rendered verbatim | PASS (`BudgetRepository.toDomain` maps fields 1:1; `BudgetDetailScreen` displays them directly) |
| Recurring amount stays positive; direction is a `+`/`−` display prefix | PASS (`templateAmountText` prefixes `MoneyFormat.format(positive)`, repo test asserts `amount.amount == 1200` for EXPENSE) |
| Currency preserved exactly — no conversion, no assumption | PASS (create sends the account's own currency; mismatched-currency exclusion stays server-side) |
| Recurring currency locked to selected account | PASS (VM submit uses `account.balance.currency`; test asserts `USD` for `acc-2`) |
| Limit/amount validation mirrors backend bounds (`>0`, ≤2dp, ≤17int) | PASS (`parseLimit`/`parseAmount` — `BigDecimal`-exact, `TooPrecise`/`TooLarge`/`NotPositive` tested) |
| No backend invention — no edit/delete UI on budgets, no `enabled` field on rule create, no reordering | PASS |

## State-machine coverage (VM)

| State/transition | Test |
| --- | --- |
| list → Content / Empty / Error(Network) + retry | six tests across the three lists |
| session end mid-load → nothing published | three tests (`SessionEndedException` arms resume, state stays) |
| create: required fields before any wire call | three tests |
| create: `amount=0` → NotPositive; `end<next` → EndBeforeNext; `period` malformed → Invalid; `limit` >2dp → TooPrecise; `priority` non-int → Invalid; blank `conditionValue` → Required | six tests |
| double-submit lock → one remote call, one `Created` event | three gated tests |
| ambiguous failure (`NetworkUnavailable`) → `FormError.Ambiguous`, no retry | three tests |
| 409 → `Conflict` (budget form-level; rule field-level on `priority`) | two tests |
| detail 404 → `NotFound` | three tests |
| deactivate → row flips `active=false` in place | PASS |
| disable → rule flips `enabled=false` in place | PASS |

## Compose coverage (androidTest — compile-verified, no device)

S15 empty/content/row-click, S16 form render, S17 deactivate dialog
(confirm required, `Deactivated` notice when inactive, `NotFound`
notice). S18 empty/row-click, S19 form render, S20 `Over budget` +
server figures verbatim + `Budgets can't be edited yet` honesty
notice + NotFound. S21 empty/disabled-shows-`Inactive`/row-click,
S22 form render, S23 disable dialog + inactive notice. `More` rows →
planning callbacks via existing `MoreScreenTest` params (new
`onOpenRecurring/onOpenBudgets/onOpenRules` defaulted — old tests
still compile).

## Quality gates (fresh run on the final diff)

```
BASELINE_UNIT_TESTS=338 (M5 closeout, XML-verified)
FINAL_UNIT_TESTS_EXECUTED=419
UNIT_PASS=419
UNIT_FAIL=0
UNIT_SKIPPED=0
M6_CONTRACT_TESTS=PASS (14/14 MockWebServer — PlanningServiceContractTest)
M6_REPOSITORY_TESTS=PASS (20/20 — Budget/Recurring/Rule RepositoryTest)
M6_VIEWMODEL_TESTS=PASS (26/26 — Recurring/Budget/Rule ViewModelsTest)
M6_COMPOSE_TESTS=PASS (compile-verified — no device)
M6_NAVIGATION_TESTS=PASS (AppRoutesTest — 9 planning routes round-trip)
M6_SESSION_GENERATION_TESTS=PASS (dead-gen list/create/disable + 401)
M0–M5_REGRESSION=PASS (419/419 suite-wide)
assembleDebug=PASS, testDebugUnitTest=PASS, lintDebug=PASS (0 errors)
compileDebugAndroidTestKotlin=PASS, assembleRelease=PASS
detekt=PASS (0 code smells)
CONNECTED_DEVICE=NO
CONNECTED_TESTS=NOT_RUN_NO_DEVICE
REAL_BACKEND_E2E=NOT_RUN
```

## Defect found + fixed during review build-up

`CreateRuleViewModel.submit()` — the 409 arm assigned
`priorityError = Conflict` via a **nested** `_state.value` write inside
the outer `.copy()` argument list; the outer assignment then overwrote
it, so a duplicate priority surfaced as a silent generic state. Found
by the `409 duplicate priority lands on the priority field` test;
fixed by computing `priorityError` in the single outer assignment.
`CreateBudgetViewModel`/`CreateRecurringViewModel` re-audited — both
already use single-assignment form; no other instance of the pattern.

## Findings

None open — **0 blockers, 0 majors, 0 minors** at close. One in-scope
note for ARGUS: `CreateRecurringViewModel` treats a plain 400 as
`AccountUnavailable` (the only 400 path is a foreign/gone account per
the backend contract — ownership is enforced server-side), and
refreshes the picker so the account drops out; documented in the VM.

## Verdict

`M6_SENTINEL=PASS` — the three planning surfaces are contract-faithful
(paths, bodies, 200/201/400+fields/404/409/401 behavior all wired
through real interceptors), session-safe (generation capture + discard
+ mutation gating on every call), financially inert (server figures
verbatim; positive amounts with direction prefix; account-currency
lock), and covered at wire/repo/VM/screen levels with the full M0–M5
suite green.
