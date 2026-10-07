# M14 — Budget Lifecycle (edit + delete)

```
STATUS: EXTERNAL_AUDIT_COMPLETE — BLOCKED_PHYSICAL_RUNTIME.
Independent audit: 0 blocker / 0 major; all contract, safety,
ownership, ledger, collision, atomicity, M13-no-replay, server-truth,
session-privacy, test-integrity, and release gates PROVEN. The single
unmet closeout gate is the mandatory physical-device run (no device
attached during the audit).
M13_COMPLETE=YES
M14_DEFINED=YES
M14_STARTED=YES
M14_TECHNICAL_COMPLETE=YES
M14_READY_FOR_EXTERNAL_AUDIT=YES
M14_EXTERNAL_AUDIT_FINAL=BLOCKED_PHYSICAL_RUNTIME
M14_HUMAN_APPROVED=NO
M14_HUMAN_AUTHORIZATION=EXPLICIT
CANONICAL_REQUIREMENT=REQ-BE-011
M15_DEFINED=NO
M15_STARTED=NO
SPENDWISE_STATUS=M14_BLOCKED_PHYSICAL_RUNTIME
NEXT_ACTION=CONNECT_AND_UNLOCK_SAFE_PHYSICAL_ANDROID_DEVICE_THEN_RUN_COMPLETE_CONNECTED_SUITE_TWICE
BASELINE: Android b6b2751 (M13-accepted) · Backend da147b8
DELIVERY: Android 1412860 (+ governance b0036c6 + checkpoint 097fcf0
  + audit remediation acb807c) · Backend 472b8fd (+ audit tests 4ed4f81)
AUDIT: reviews/M14-EXTERNAL-AUDIT.md
```

## Selection rationale (decision record)

Candidates re-evaluated against CURRENT canonical sources
(`PRODUCT.md` §30/§37, `POST-V1-BACKEND-REQUIREMENTS.md`,
`POST-V1-CAPABILITY-MATRIX.md` — M9–M13 removed from candidacy):

| Candidate | Verdict |
|---|---|
| REQ-BE-005 transaction correction | **Rejected for M14**: spec requires a product decision (PATCH vs void); §16 guards append-only history; §21 offset-entry is already the sanctioned remedy. Financial-history ambiguity = elevated risk without commensurate canonical urgency. |
| REQ-BE-006 account edit/archive | Rejected: archive semantics unspecified ("define archived-account semantics" is a product decision); medium risk, lower user pain. |
| **REQ-BE-011 budget edit/delete** | **SELECTED**: the only remaining candidate PRODUCT.md names as a live user-facing defect — "a wrong current-month budget is permanently unfixable" (§37 BE-011 limitation is surfaced on budget detail). Contract is explicit; budgets are planning objects, not ledger — no financial-history ambiguity; no schema change needed. |
| REQ-BE-007 pagination | Rejected: canonical trigger is "deferred until real ledgers show load pain" — the trigger has not fired; V1 posture (unbounded honest fetch) stands. |
| REQ-BE-008 multipart size | Rejected: trivial config change; canonical doc says "can ride any backend release" — not a milestone. |
| REQ-BE-009 hardening cluster | Rejected: low user-visible value (app already locks currency). |
| REQ-BE-010 persisted description | Rejected: additive schema change whose Product distinction (categorization input vs ledger description) is not yet resolved; medium risk. |
| FX converter / search | Rejected: FX remains placeholder-gated (display-only provider); local text search was rejected for field poverty and remains thin-value even post-M11. |

Scoring (§15): priority 5 + user value 4 + backend readiness 3 +
android readiness 4 + testability 3 + leverage 2 − ambiguity 1 −
security 1 − schema 0 − scope 2 = **17** — highest defensible.

## User outcome

On a budget's detail screen the user can correct the limit and/or the
month, or delete the budget outright. The "permanently unfixable"
limitation copy is replaced by working affordances. Wrong category is
fixed by delete + recreate (canonical REQ scope is amount/period only).

## Backend contract (bounded prerequisite — human-authorized)

Backend `D:\spendwise` at `da147b8` gains exactly two endpoints:

- `PATCH /api/budgets/{id}` — body `{limit?: decimal, period?: "YYYY-MM"}`;
  at least one field required → `200 BudgetResponse`.
  - owner-scoped: foreign/absent id → 404
  - `limit` non-positive or > `Digits(17,2)` → 400; malformed period → 400;
    empty body → 400; `(user, category, period)` collision → 409
    (existing `DataIntegrityViolation` handler; no new error shape)
  - idempotent: replaying the same PATCH yields the same 200
  - `currency`/`category` are immutable (not in REQ-BE-011 scope)
- `DELETE /api/budgets/{id}` — owner-scoped hard delete → `204`;
  missing/foreign → `404`. No schema change (no soft-delete column —
  budgets hold no history; the ledger is untouched).

No new schema, no new error shapes, no auth-surface changes.

## Android scope

- `BudgetsService`: `@PATCH`/`@DELETE` + `UpdateBudgetRequestDto`
  (absent fields omitted from the wire).
- `BudgetRepository.update/delete`: generation-guarded, `mutations`
  emitted on success so list surfaces reload.
- `BudgetDetailViewModel`: edit dialog state (limit + `YYYY-MM`
  inputs, `CreateBudgetState` validation enums reused), `saveEdit`,
  `delete` → `deleted` effect → nav back.
- `BudgetDetailScreen`: Edit + Delete affordances on Content;
  edit dialog; delete confirm dialog (recurring-deactivate pattern);
  the read-only BE-011 banner replaced by the working actions.
- M13 integration: PATCH/DELETE are non-idempotent methods → the
  TokenAuthenticator never replays them; wire-proven in tests.
  DELETE replay → honest 404 surfaces as NotFound, never silently
  claimed success.

## Ownership / privacy

Owner-scoped `findByIdAndUserId` for both mutations — foreign id is
indistinguishable from missing (404, non-disclosure convention R24/25).
Adversarial User A/User B tests mandatory. No client userId authority.

## Error semantics

- 400 → field validation (limit/period) or `AppError.Validation`
- 404 → `AppError.NotFound` (missing, foreign, or already-deleted)
- 409 → `AppError.Conflict` (period collision) — honest conflict copy
- ambiguous transport failure → `AppError.Ambiguous`-class handling:
  PATCH/DELETE are never auto-replayed; UI surfaces "check and retry"

## Non-goals

- No budget category or currency mutation (delete+recreate is the
  canonical remedy per REQ-BE-011 scope).
- No soft-delete/audit column (schema-free).
- No transaction correction, account lifecycle, pagination, search,
  FX, or description work — M15 remains undefined.

## Tests

Backend: update limit / update period / empty-body 400 / invalid limit
400 / malformed period 400 / collision 409 / foreign 404 / missing 404 /
delete 204 + re-GET 404 / cross-user delete 404 / idempotent re-PATCH.

Android: service wire tests (exact path/method/body, absent-field
omission, 400/404/409), repository (generation race, mutations emit,
error mapping), ViewModel (edit validation, save, delete effect,
reload-after-edit), Compose (dialog open/submit/validation, delete
confirm, a11y), connected (edit + delete flow on real Keystore session),
M13 contract (transmitted PATCH/DELETE never replayed; expired access →
pre-send renew then transmit once).

## Gates

Full backend regression (baseline exception: the documented
time-dependent `RecurringSchedulingJobTest` failure only) · Android JVM
· lint · detekt · assembleDebug · physical Pixel connected ×2 (if
attached) · minified release + serializer/manifest audit · Sentinel ·
Argus → external-audit handoff. `M14_READY_FOR_EXTERNAL_AUDIT=YES` only
when all green; `M14_HUMAN_APPROVED=NO` until the independent audit.
