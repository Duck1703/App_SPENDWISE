# M6 ARGUS Review — Planning (Recurring, Budgets, Categorization Rules)

Reviewer role: **ARGUS** (independent QA), per
`agent-system/agents/argus.md`. Independent of FLUX/SENTINEL — findings
from a fresh read of the diff, the backend sources at `8194daa`, the
P3A spec, PRODUCT §30, and the session/financial invariants. Focus:
blockers, majors, minors, notes across requirement fidelity, backend
assumptions, privacy, financial correctness, scope discipline.

## Verdict

```
BLOCKER=0
MAJOR=0
MINOR=0
NOTE=3
M6_ARGUS=PASS
```

## Independent re-verification (not trusting prior reports)

| Claim checked | Finding |
| --- | --- |
| Endpoint set | VERIFIED — `BudgetController` (POST, GET list, GET `{id}`, GET `{id}/calculation`), `RecurringTransactionController` (POST, GET list, GET `{id}`, DELETE `{id}` → 200+body), `CategorizationRuleController` (POST, GET list, GET `{id}`, DELETE `{id}` → 200+body) at `8194daa`. Nothing else invented — no edit/delete for budgets, no reorder, no enable endpoint used. |
| Request records | VERIFIED field-for-field — `CreateBudgetRequest{category,period,limit:BigDecimal,currency}`, `CreateRecurringTransactionRequest{accountId:UUID,direction,amount:BigDecimal,currency,category?,frequency,nextDueDate:LocalDate,endDate?}`, `CreateCategorizationRuleRequest{conditionType,conditionValue:≤255,targetCategory:≤32,priority:Int,enabled?}`. Client DTOs match exactly. |
| Response records | VERIFIED — `BudgetResponse`, `BudgetCalculationResponse{budgetId,category,period,limit,currency,spent,remaining,percentage,status}`, `RecurringTransactionResponse{…,active}`, `CategorizationRuleResponse{…,enabled}` all match the client DTOs field-for-field incl. nullability. |
| `enabled` on rule create | VERIFIED DEAD — `CategorizationRuleMapper.toDomain` calls the 4-arg ctor and **never reads `request.enabled()`**; new rules are always enabled server-side. The client never sending `enabled` (S22) is not just safe — it's the only honest choice; the field is ignored. |
| 409 semantics | VERIFIED — both duplicate budget (category, period) and duplicate rule (user, priority) surface as `DataIntegrityViolationException` → 409 through `ApiExceptionHandler`; client maps both to `Conflict`. |
| Rule list ordering | VERIFIED — `findAllByUserIdOrderByPriorityAsc`; the client renders verbatim, no re-sort (test proves out-of-order input passes through). |
| Soft-delete | VERIFIED — `rule.disable()` flips a boolean, `RecurringTransaction.deactivate()` likewise; DELETE 200 bodies are decoded and drive the in-place row flip. |
| Currency lock (recurring) | VERIFIED — VM submits `account.balance.currency`, never a user-entered code (PRODUCT §158 mitigation). |
| Financial correctness | VERIFIED — no client aggregation; `spent/remaining/percentage/status` rendered verbatim; positive amounts + `+`/`−` display prefix; `BigDecimal`-exact client validation mirrors `@DecimalMin`/`@Digits(17,2)`. The single `toFloat` at `BudgetDetailScreen:163` is progress-bar geometry over the server `percentage` (clamped 0..1); the rendered text uses the raw `BigDecimal` — display-only, inert. |
| Session/privacy | VERIFIED — generation capture+check on every repo call; dead-gen → `SessionEndedException` (no publish, no mutation emit); 401 → `Session.Expired` via the real interceptor; resume reloads via `onVisible`, never replays a write; `mutations` flow only fires post-generation-check. |
| Boundary discipline | VERIFIED — `git status` shows only `spendwise-android` changes; no backend/reference edits; diff confined to planning feature + wiring + tests + review docs. |
| Design-system fidelity | VERIFIED — rows ≥72dp (`heightIn`), `SwHeader/SwCard/SwStateBlock` reused, merged `clearAndSetSemantics` row CDs, confirm dialogs on destructive actions. |
| Regression | VERIFIED — 419/419 suite (was 338 at M5 close → +81 M6 tests); M0–M5 untouched and green. |

## Findings

None open. Three notes, all in-scope honesty observations, not
defects:

### NOTE-1 — budget list shows limit only, not spent

`GET /api/budgets` returns `{id,category,period,limit,currency}` — no
spent figure; fetching N calculations would be N+1 chatter the backend
doesn't offer. The list correctly shows category + period + limit;
spent/remaining live on the detail screen where the calculation
endpoint serves them. Faithful to the contract, not an omission.

### NOTE-2 — dead-generation deactivate/disable shows a generic action error

When a deactivate response lands after session death, the repo throws
`SessionEndedException` and the detail VM surfaces `actionError=
Generic` on the still-active row — honest (the outcome is
unconfirmable), the write never replays, and the next visible-lifecycle
load corrects the row. Consistent with the ambiguous-outcome doctrine;
the create forms use the stronger `Ambiguous` copy because a create is
non-idempotent, while deactivate is effectively idempotent (a
deactivated template stays deactivated).

### NOTE-3 — `CreateRecurringViewModel` maps bare 400 → `AccountUnavailable`

A 400 without `fields{}` on recurring create can only be a
foreign/gone `accountId` per the controller's validation order
(`@NotBlank` violations arrive as `fields{}` 400s; account ownership is
the next failure). The VM refreshes the picker so the account drops
out. Correct and honest — recorded for traceability.

## Verdict

`M6_ARGUS=PASS` — `BLOCKER=0`, `MAJOR=0`, `MINOR=0`. The slice builds
the right thing: three backend-supported surfaces, no invented
capability, honest `409`/`400`/`401` handling, soft-delete semantics
respected, server-derived finances untouched, and the full M0–M5
regression green.
