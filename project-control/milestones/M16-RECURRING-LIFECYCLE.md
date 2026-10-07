# M16 — Recurring Template Lifecycle (edit)

```
STATUS: COMPLETE
M15_COMPLETE=YES
M16_DEFINED=YES
M16_STARTED=YES
M16_TECHNICAL_COMPLETE=YES
M16_READY_FOR_EXTERNAL_AUDIT=YES
M16_EXTERNAL_AUDIT_FINAL=ACCEPTED
M16_HUMAN_APPROVED=YES
M16_HUMAN_AUTHORIZATION=EXPLICIT (M16 delivery block)
CANONICAL_REQUIREMENT=REQ-BE-012 (new register entry —
  recurring template edit; lifecycle completion, M14 precedent)
RECURRING_LIFECYCLE_EDIT=APPROVED
M17_DEFINED=NO
M17_STARTED=NO
SPENDWISE_STATUS=M16_COMPLETE
NEXT_ACTION=HUMAN_M17_DECISION
BASELINE: Android 102ced2 (M15-accepted) · Backend ef08539
```

## Selection rationale (decision record)

Candidates re-evaluated against CURRENT canonical sources
(`PRODUCT.md` §26–§31, `POST-V1-BACKEND-REQUIREMENTS.md`,
`POST-V1-CAPABILITY-MATRIX.md`, `ROADMAP.md` — M9–M15 removed from
candidacy) and the actual backend surface (`ef08539`: PATCH exists
only on `/api/budgets/{id}`; recurring has POST/GET/GET{id}/DELETE{id}
with **no edit surface**).

| Candidate | Verdict |
|---|---|
| **Recurring template edit (REQ-BE-012)** | **SELECTED**: completes the lifecycle the Human already approved twice (V1 create/list/deactivate; M14 budget PATCH precedent). Real user pain — amounts/dates/categories of recurring items drift ("rent went up"); today the only fix is deactivate + recreate. Template is a planning object (like budgets), never ledger — editing affects only future generation (`RecurringGenerationService` reads template fields at fire time); generated rows are untouched, preserving append-only history and PRODUCT §27's "history stays". Clean semantics, no schema change, bounded backend diff. Score: priority 3 + value 4 + backend 4 + android 4 + testability 3 + leverage 2 + physical 2 − ambiguity 1 − security 1 − schema 0 − scope 1 = **19**. |
| Categorization rule edit | Rejected: same PATCH shape but lower user value (rules are set-once); priority/conflict semantics add ambiguity. ~15. |
| REQ-BE-005 transaction correction | Rejected: register requires an explicit product decision (PATCH vs reversal); financial-history ambiguity without canonical resolution. |
| REQ-BE-006 account edit/archive | Rejected: "define archived-account semantics" is an unresolved product decision; rename-only would ship half the requirement. |
| REQ-BE-010 persisted description | Rejected: schema change + unresolved semantics — the field is currently a rule-matching **hint**; persisting it would silently turn existing hint input into displayed ledger text (§32 guard). Needs product decision first. |
| Transaction search | Rejected (again, honestly): no persisted searchable text exists (`description` is never persisted, PRODUCT §94); a server `q` over `sourceRef` technical keys would be near-fake search. Unblocked only by BE-010. |
| FX converter tool | Rejected: display-only placeholder provider (`available:false` live state); product-gated, low value, external dependency on the physical gate. |
| Profile management | Rejected: PRODUCT explicitly excludes new auth surfaces from near-term milestones. |
| REQ-BE-008 multipart / REQ-BE-009 hardening | Rejected: trivial config / low user-visible value; not milestone-sized. |

## User outcome

On a recurring template's detail screen the user can edit amount,
category (incl. clearing to uncategorized), frequency
(WEEKLY/MONTHLY), next due date, and end date (incl. removing it).
Account, direction, currency, and active state are immutable — same
immutability discipline as M14 (currency/category there; identity
fields here). Edits apply to future generation only; already-generated
ledger rows are never rewritten.

## Backend contract (bounded M16 prerequisite)

`PATCH /api/recurring-transactions/{id}` → 200
`RecurringTransactionResponse` (full updated template).

- Request `UpdateRecurringTransactionRequest` — every field optional;
  **at least one present → else 400** (M14 `UpdateBudgetRequest`
  discipline, enforced in the service):
  - `amount` — `@DecimalMin(>0) @Digits(17,2)`;
  - `category` — category code (resolved like create; unknown → 400);
  - `clearCategory` — explicit boolean; sets category to null.
    `category` + `clearCategory` both present → 400 (absent-vs-null
    ambiguity made explicit);
  - `frequency` — `WEEKLY`|`MONTHLY` enum name (invalid → 400 via the
    existing mapper helper);
  - `nextDueDate` — ISO `LocalDate`; any date allowed (parity with
    create — a past date triggers the documented catch-up loop);
  - `endDate` — ISO `LocalDate`;
  - `clearEndDate` — explicit boolean; `endDate` + `clearEndDate`
    both present → 400.
- Ownership: `findByIdAndUserId`; foreign/missing → empty → **404**
  (non-disclosure parity with every other owner-scoped lookup).
- Immutable: `account`, `direction`, `currency` (account-locked,
  D14 mitigation), `active` (DELETE remains the only active-state
  transition; no reactivation invented), `userId`.
- Editing a deactivated template: allowed (harmless correction;
  template stays inactive).
- Errors: 400 validation/DomainException, 401 unauthenticated,
  404 foreign/missing, 200 success. No 409 class (no unique
  constraint on template fields).
- No schema migration — all columns already exist.
- M13: PATCH is a non-idempotent mutation → the Android authenticator
  already refuses post-401 replay (GET/HEAD only); a wire-level
  transmitted-PATCH-401 test proves request count = 1.

## Android scope

- `RecurringService.update` (PATCH) + `UpdateRecurringRequestDto`.
- `RecurringRepository.update(...)` — generation-guarded, emits
  `mutations` on success (list + detail surfaces reload).
- `EditRecurringViewModel` + `EditRecurringState`: loads the template
  (`findById`), prefills the form; immutable account/direction/
  currency shown read-only; save builds a **sparse** patch (only
  changed fields; category removed → `clearCategory`, endDate
  emptied → `clearEndDate`); no changes → save disabled.
- `EditRecurringScreen` + `RecurringEditRoute(templateId)` nested
  under the existing planning graph; detail gains an "Edit"
  affordance (active templates only — editing a deactivated template
  has no future effect and is not surfaced).
- `RecurringDetailViewModel` observes `mutations` so a saved edit
  refreshes the detail content while it sits on the back stack.
- Write safety: submit lock, no auto-retry, ambiguous-outcome copy —
  the established mutation contract.

## Session-refresh implications

PATCH rides the existing stack: pre-send renewal, single-flight
refresh, 401 → mutation NOT replayed (TokenAuthenticator returns
null for non-GET/HEAD) → caller sees retryable state. Wire test
asserts PATCH count = 1 under transmitted-401, and 1 refresh + 1
PATCH under known-expired-access.

## Privacy / ownership

Owner derivation server-side only; no client userId. Foreign id is
indistinguishable from missing (404). A→B: repository re-checks
`sessionGeneration` post-call; late-A publish dropped.

## Financial-authority constraints

Template ≠ ledger. The generated-row write path
(`RecurringGenerationService`) is untouched; `sourceRef` keys stay
`RECURRING:{templateId}:{dueDate}` — existing rows are never
mutated, rescoped, or deleted. No balance math on the client.

## Non-goals

- No reactivation endpoint (`active` stays DELETE-only, PRODUCT §27).
- No rule/budget/account/transaction edits (separate candidates).
- No scheduler change, no catch-up policy change, no schema change.
- No M17 work. No course synchronization.

## Test strategy

Backend contract tests (`RecurringUpdateContractTest`, MockMvc +
Postgres): field updates, clear flags, ≥1-field 400, conflict pairs
400, validation 400s, foreign 404, inactive editable, generated-row
isolation (create → fire → edit → verify row unchanged), response
shape. Android: MockWebServer wire tests (method/path/body/401
non-replay count), repository tests (generation guard, mutations
emit, error mapping), ViewModel tests (prefill, sparse patch,
stale-load races incl. stale **failure** — M15 lesson), Compose
tests (edit affordance, form, save, errors), instrumentation
(physical edit flow). No vacuous coverage.

## Gates

JVM fresh suite; lint/detekt/debug; release R8 + serializer audit;
physical Pixel 4 XL connected suite ×2 on final source; install +
launch; Sentinel + Argus reviews with in-task remediation; external
audit is OUT OF SCOPE for this delivery (stops at
`M16_READY_FOR_EXTERNAL_AUDIT=YES`).

## Course sync

Deferred. Backend production delta will be recorded as
`ef08539..<M16 backend head>` + contract summary for a later
synchronization pass. `COURSE_UPDATE_EXECUTED=NO`.

## Final status (delivery-block close)

```
M16_TECHNICAL_COMPLETE=YES
M16_READY_FOR_EXTERNAL_AUDIT=YES
M16_EXTERNAL_AUDIT_FINAL=ACCEPTED
M16_HUMAN_APPROVED=YES
M16_STATUS=COMPLETE
M17_DEFINED=NO
M17_STARTED=NO
PUSH_PERFORMED=NO
TAG_CREATED=NO
COURSE_UPDATE_EXECUTED=NO
```

Backend `eeb2c2f` (bounded prerequisite, human-authorized — 6 files,
`com.spendwise.recurring` only). Android `f8d855e` (19 files).
Gates: backend focused 11/11; backend full 376 tests / 1 documented
pre-existing `RecurringSchedulingJobTest` flake; Android JVM 651/651
fresh; lint PASS (0 errors, pre-existing warnings only); detekt 0
smells; debug+release builds green; R8 serializer audit green;
physical Pixel 4 XL 193/193 ×2 on final source; installed + launched.
Reviews: [reviews/M16-SENTINEL-REVIEW.md](../reviews/M16-SENTINEL-REVIEW.md)
PASS, [reviews/M16-ARGUS-REVIEW.md](../reviews/M16-ARGUS-REVIEW.md)
PASS. Handoff:
[reviews/M16-EXTERNAL-AUDIT-HANDOFF.md](../reviews/M16-EXTERNAL-AUDIT-HANDOFF.md).

External audit: [reviews/M16-EXTERNAL-AUDIT.md](../reviews/M16-EXTERNAL-AUDIT.md)
— **ACCEPTED** after in-task remediation of 1 Major (recurring entity
optimistic locking, `133fe18` backend) + bounded minors (test-integrity
falsifications `133fe18`, scheduler clock-flake fix `ca5b380`, fake
seam + server-truth test, contract-ledger row). Post-remediation gates:
backend 383/383, Android JVM 653/653, lint 0 errors, detekt 0 smells,
debug+release+R8 green, serializer/manifest/backup audit green,
physical Pixel 4 XL 193/193 ×4 total (early ×2 + final-source ×2),
installed + launched.
