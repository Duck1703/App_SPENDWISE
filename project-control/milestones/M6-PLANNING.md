# M6 — Planning (recurring, budgets, categorization rules)

Status: **COMPLETE — conditionally approved via authorized pipeline gate**
Starting SHA: `5c0f344f4ae4685fea6887f639a81778dfe1d224`

## Authorized scope

ROADMAP M6 — "Planning (recurring, budgets, categorization rules)".
Canonical screens (PRODUCT §30, P3A secondary fidelity):

| Screen | Purpose | Endpoint |
| --- | --- | --- |
| S15 Recurring list | manage templates | `GET /api/recurring-transactions` |
| S16 Recurring create | new template | `POST /api/recurring-transactions` |
| S17 Recurring detail | view + deactivate | `DELETE /api/recurring-transactions/{id}` (200 + `active=false`) |
| S18 Budgets list | monthly budgets | `GET /api/budgets` |
| S19 Budget create | new budget | `POST /api/budgets` (400/409) |
| S20 Budget detail | spend vs limit | `GET /api/budgets/{id}/calculation` |
| S21 Rules list | auto-categorization rules | `GET /api/categorization-rules` |
| S22 Rule create | new rule | `POST /api/categorization-rules` (400/409) |
| S23 Rule detail | view + disable | `DELETE /api/categorization-rules/{id}` (200 + `enabled=false`) |

Entry points: three More rows beneath Import (P3A §3 More inventory).

## Source-of-truth evidence (backend `8194daa`, read-only)

- `BudgetController`/`BudgetService`/`BudgetCalculationService` —
  create `{category(code), period "YYYY-MM", limit >0 @Digits(17,2),
  currency}` → 201 `BudgetResponse{id,category,period,limit,currency}`;
  list; getById 200/404; calculation → `{budgetId,category,period,
  limit,currency,spent,remaining,percentage(2dp HALF_UP),status∈
  {WITHIN_BUDGET,OVER_BUDGET}}` — all derived server-side, never
  persisted. Duplicate (user,category,period) → 409. Unknown category →
  400. Malformed period → 400. **No update/delete exists.**
- `RecurringTransactionController`/`Service` — create `{accountId,
  direction INCOME|EXPENSE, amount >0 @Digits(17,2), currency,
  category?, frequency WEEKLY|MONTHLY, nextDueDate ISO date,
  endDate?}` → 201 full response incl. `active`; list (active+inactive);
  getById 200/404; `DELETE` deactivates → 200 + body. Foreign/unknown
  account → **400** (DomainException — not 404). No currency-vs-account
  check server-side → PRODUCT §158 defect mitigation: **client locks
  currency to the chosen account's currency**. Deactivation never
  touches generated transactions. No reactivate endpoint.
- `CategorizationRuleController`/`Service` — create `{conditionType
  CONTAINS|EQUALS, conditionValue ≤255, targetCategory ≤32 code,
  priority Int, enabled?}` → 201; list ordered priority ASC; getById
  200/404; `DELETE` disables → 200 + body (`enabled=false`). Duplicate
  (user,priority) → 409. Unknown category → 400; invalid conditionType
  → 400. S22 note: `enabled` is not sent on create (server default).
- Generation is server-owned (`RecurringGenerationService` — scheduled
  sweep, `RECURRING:` sourceRefs, advance-after-commit); the client
  never computes due dates, occurrences, or spend.

## Design decisions

- `feature/planning/` package; three repository boundaries
  (`BudgetRepository`, `RecurringRepository`, `RuleRepository`), each
  generation-stamped via `sessionManager.sessionGeneration`, each with
  a `mutations` SharedFlow so create/deactivate/disable reload lists.
- Recurring create: currency fixed to `account.balance.currency` —
  field is read-only display (defect mitigation, not a picker).
- Budget create: period field `YYYY-MM` (default = device-local current
  month, matching the dashboard's month semantics); amount +
  currency — currency options = distinct account currencies when
  available (spend only counts same-currency expense server-side), else
  the open ISO set already used by account create.
- Budget detail: `GET /{id}/calculation` is the sole source for
  spent/remaining/percentage/status — no client arithmetic; BE-011
  ("no budget edit/delete — a wrong current-month budget is
  unfixable") surfaced as an honest info note.
- Rules: `enabled` never sent on create; disabled rows render as
  inactive (`enabled=false`); disable is confirm-dialog soft-delete.
- All writes: `isSubmitting` lock set synchronously; no auto-retry;
  ambiguous outcomes (network/5xx/dead-generation/401) surface honest
  copy; same-user resume reloads, different-user resets via generation.

## Stop conditions

Beyond the global contract: if any planning endpoint semantics differ
on re-verification (shape, status codes, soft-delete behavior) →
BACKEND GAP report, stop the affected slice.

## External-audit gate

Independent external audit performed on `5c0f344..efd34c3` — see
`reviews/M6-EXTERNAL-AUDIT-SUMMARY.md`. Fresh gate run executed at
audit time on the committed tree (not carried from the delivery
report).

```
M6_SENTINEL=PASS (0 blockers — see reviews/M6-SENTINEL-REVIEW.md)
M6_ARGUS=PASS (0 blockers, 0 majors — see reviews/M6-ARGUS-REVIEW.md)
M6_EXTERNAL_AUDIT=PASS (0 blockers, 0 majors, 0 minors — 3 notes)
M6_JVM_TESTS=419/419 verified fresh at audit (86 M6-scoped: 14 contract + 23 repo + 38 VM + 18 Compose compile-verified + 9 route)
M6_CONNECTED_INSTRUMENTATION=NOT_RUN_NO_DEVICE (adb devices = 0 at audit time; androidTest compile-verified)
M6_GATES=assembleDebug+testDebugUnitTest+lintDebug+compileDebugAndroidTestKotlin+assembleRelease+detekt: PASS (re-verified at audit)
M6_LINT=informational warnings only, 0 errors (re-verified)
M6_DETEKT=0 code smells (re-verified)
M6_CORE_CORRECTNESS=PASS
M6_FINANCIAL_CORRECTNESS=PASS
M6_SECURITY_PRIVACY=PASS
M6_CONTRACT_FIDELITY=PASS
M6_TEST_EVIDENCE=PASS
M6_READY_FOR_EXTERNAL_AUDIT=YES
M6_HUMAN_APPROVED=NO (remains NO until THE HUMAN signs off — see Human gate)
M6_COMPLETE=YES (conditionally closed — all gate conditions met)
M7_STARTED=YES (authorized by conditional gate)
SAFE_TO_AUDIT=YES
```

## Human gate

`M6_EXTERNAL_AUDIT_VERDICT=ACCEPTED` — independent external audit ran on
`efd34c3` (see [reviews/M6-EXTERNAL-AUDIT-SUMMARY.md](../reviews/M6-EXTERNAL-AUDIT-SUMMARY.md)):
0 blockers / 0 majors / 0 minors / 3 notes (dead `initial` param +
no serial guard on detail VMs — matches M2/M3 precedent; Compose test
count 18 vs reported 20; `MoreScreen` root not scrollable —
pre-existing pattern). No remediation required. Human-approved
conditional closeout authorized M7 to proceed; `M6_HUMAN_APPROVED`
remains `NO` until THE HUMAN signs off.

## In-review defect reconciliation

One defect surfaced by the SENTINEL test build-up and fixed before
close: `CreateRuleViewModel.submit` shadowed the 409→`priorityError`
mapping behind a nested state write (found by `409 duplicate priority
lands on the priority field`; fixed in `CreateRuleViewModel.kt`).
Full suite re-run after the fix: 419/419 green.

## Scope exclusions honored

No budget edit/delete UI (BE-011 surfaced as an honest read-only note),
no `enabled` sent on rule create (server ignores it — mapper reads the
4-arg ctor), no client-side spent/remaining/percentage recomputation,
no client-side due-date/generation logic, no unsupported capability
teasers, no new dependencies, no backend/reference/prototype edits
(backend `8194daa`, reference unchanged).
