# M20 — Offsetting Entry (guided transaction correction)

```
STATUS: IN_DELIVERY — external audit NOT run
M19_COMPLETE=YES
M20_DEFINED=YES
M20_STARTED=YES
M20_TECHNICAL_COMPLETE=NO
M20_READY_FOR_EXTERNAL_AUDIT=NO
M20_EXTERNAL_AUDIT=NOT_RUN
M20_HUMAN_APPROVED=NO
M20_HUMAN_AUTHORIZATION=EXPLICIT (M20 delivery block)
CANONICAL_REQUIREMENT=PRODUCT.md §21 (offsetting-entry decision) +
  S10 (transaction detail primary action) + J14 (correction journey).
  No REQ-BE id — zero backend change; rides canonical
  `POST /api/transactions`.
CORRECTION_VIA_OFFSET=DELIVERED
M21_DEFINED=NO
M21_STARTED=NO
SPENDWISE_STATUS=M20_IN_DELIVERY
NEXT_ACTION=M20_IMPLEMENTATION
BASELINE: Android e1f149d (M19-accepted) · Backend 9970c3e (M19-accepted)
COURSE_SYNC_DEFERRED=YES
```

## Selection rationale (decision record)

Candidates re-evaluated against CURRENT canonical sources
(`PRODUCT.md`, `POST-V1-BACKEND-REQUIREMENTS.md`,
`POST-V1-CAPABILITY-MATRIX.md`, `ROADMAP.md`, M9–M19 milestone records,
`P3A-DESIGN-HANDOFF.md`, backend source `9970c3e`, Android `e1f149d`) —
all delivered features removed from candidacy.

Requirement ledger after M19: every REQ-BE-* with a user journey is
DELIVERED except BE-005 (product-gated: the register reserves the
PATCH-vs-void decision to the Human — unchanged since M14), BE-008
(trivial config), BE-009 (mixed hardening cluster).

| Candidate | Scores (priority+value+backend+android+test+leverage+physical) − (ambiguity+security+schema+history+scope) | Verdict |
|---|---|---|
| **§21 offsetting entry** | 4+4+5+4+3+2+2=24 − (1+0+0+1+1)=3 → **21** | **SELECTED**: the last canonical journey without a guided flow (J14). Fully spec'd — prefill fields, verbatim honest copy, TRANSFER_* exclusion, naming rules. Zero backend change; append-only; the sanctioned correction remedy while BE-005 stays gated. P3A's drop was a V1 *scope* cut, not a product rejection (same class as dates/transfers/search, all later delivered). |
| §17 uncategorized Home alert | 3+3+5+5+3+2+2=23 − (1+0+0+0+1)=2 → **21** | Deferred: honest disclosure but no corrective path for existing rows exists (rules apply to future rows only); lower journey value than completing J14. Remains a legitimate follow-up. |
| REQ-BE-005 transaction correction | 5+5+2+4+3+3+2=24 − (4+1+3+4+3)=15 → **9** | Gated: "product decision which" remains unresolved — PATCH rewrites history vs void/reversal is a Human call (rejected in M14–M19 evals for the same reason). M20's offset entry does NOT preempt it — §21 is the sanctioned remedy independent of BE-005. |
| Categorization-rule lifecycle (edit/re-enable/reorder) | 2+3+4+4+3+2+1=19 − (3+0+0+2+2)=7 → **12** | Rejected: canonical journey S21–S23 is complete (create/list/get/disable all shipped); no canonical REQ exists for edit/re-enable — would be invented scope. |
| Recurring pause/skip/resume | 2+3+2+4+3+1+2=17 − (3+0+1+2+2)=8 → **9** | Rejected: not in the canonical register; scheduler semantics would be invented. |
| REQ-BE-008 multipart size | 1+1+5+5+1+0+0=13 − (0+0+0+0+4)=4 → **9** | Rejected: config knob, no user journey — violates "not a tiny milestone". |
| REQ-BE-009 hardening cluster | 3+1+4+1+2+2+0=15 − (3+1+0+1+4)=9 → **6** | Rejected: grab-bag; D14 unreachable via the app; no coherent vertical slice. |
| Profile management | 2+2+0+3+2+2+1=12 − (5+3+3+0+4)=15 → **−3** | Rejected: no `/me` exists; POST_V1; invented scope. |
| FX display conversion | 1+1+5+3+2+0+1=13 − (3+1+0+1+1)=6 → **7** | Rejected: placeholder provider "no real value yet" (PRODUCT §29); product-gated. |
| Category CRUD | — | Rejected: fixed server enum by product decision. |
| Account delete | — | Rejected: excluded from M19 scope; no canonical REQ. |

## Canonical contract (PRODUCT §21 — verbatim semantics)

- On transaction detail for `INCOME`/`EXPENSE` rows only: **"Create
  offsetting entry"** → opens transaction create prefilled with the
  **inverse type**, same account, same amount, same currency, and (if
  set) the same category.
- Creates a real `POST /api/transactions` — balance effect is the exact
  arithmetic inverse; the original stays in history.
- **Honest copy (spec-verbatim)**: "This adds an opposite entry. The
  original stays in history — income and expense totals for the period
  will both be higher than the true position."
- `TRANSFER_*` legs cannot be offset — a single-side offset would
  un-pair the atomic transfer; the action stays hidden on them.
- **Not labeled "undo"/"delete"/"edit"** — presented as a compensating
  entry.
- True edit/delete remains BACKEND_GATED (BE-005) — untouched.

### Bounded M20 decisions (inside spec, documented here)

- **occurredOn** prefill = device-local *today* (the offset lands when
  made — §21 does not pin a date; editable via the M11 date picker).
- **description** = empty (§21 does not specify a note; the user may
  add one. No fabricated "Correction of …" text).
- **Archived source account**: the action is hidden when the row's
  account is archived — consistent with M19 (archived accounts are
  excluded from every new-entry surface; the detail hides Add/Transfer
  the same way). The offset form additionally refuses to proceed if
  the source account resolves to an archived/non-pickable account
  (mid-navigation race) — an honest error state, not a silent retarget
  to another account.
- **UNKNOWN type rows**: action hidden (type flip undefined).
- **Offset-of-offset**: allowed — no link tracking exists or is
  invented (link semantics are BE-005's deferred decision).

## Backend contract

**Zero production change.** The offset uses the canonical
`POST /api/transactions` (`{accountId, type, amount, category?,
description?, occurredOn?}` → `201 TransactionResponse`; `sourceRef =
MANUAL:<uuid>`; owner-scoped 404s; server computes balance).

Backend work in this milestone = **contract-pinning tests only** (the
offset round-trip as a supported composition): expense then equal
income restores the balance; both rows stay visible in list/page/
search/export; original row fields untouched; per-owner isolation.

## Android scope

- `TransactionCreateRoute(accountId, offsetOf: String?)` — typed nav
  arg carries the source transaction id; the VM resolves the source
  from the repository (server-truth, not a nav-arg snapshot).
- `TransactionDetailScreen`: "Create offsetting entry" action on
  eligible rows (INCOME/EXPENSE + active source account); hidden on
  TRANSFER_*/UNKNOWN/archived. Read-only banner copy retained —
  entries still can't be *edited*.
- `TransactionDetailViewModel`: resolve the account object (not just
  the name) so `offsetEligible` reflects `!account.archived`.
- `CreateTransactionViewModel` offset mode: resolve source → prefill
  inverse type / same account / same amount / same category → render
  the §21 honest banner in-form → save through the identical validated
  submit path (submit lock, ambiguous copy, M13 no-replay all
  inherited unchanged). If the source or its account can't be resolved
  to a pickable account → honest `OffsetUnavailable` state.
- Navigation: detail → `TransactionCreateRoute(offsetOf = id)`;
  after save, pop back to detail (existing `onCreated` contract; the
  Transactions tab repaint lands via the repository mutations signal).

## Invariants protected

- Append-only ledger: the offset is a *new* row through the canonical
  creation path (`TransactionCreationService`, `sourceRef=MANUAL:<uuid>`,
  `uq_txn_source_ref`). No historical row is ever modified.
- BR-03 balance projection: server recomputes via signedEffect — the
  client never touches balance math.
- M13: POST is never auto-replayed; ambiguous outcome copy stands.
- M15/M18: the offset row participates in pagination/search/export
  like any manual row.
- M17: description optional; surrogate-safe cap inherited.
- M19: archived-account picker exclusion honored (offset action hidden
  on archived-account rows; no archived id can be targeted).
- Owner isolation: every call is the existing owner-scoped surface.

## Non-goals

- No `PATCH`/`DELETE`/`void` on transactions (BE-005 stays gated).
- No link/back-reference between original and offset row.
- No backend production change, no schema change, no new endpoint.
- No "undo" semantics, no bulk correction, no transfer offset.
- No M21, no course sync.

## Test strategy

Backend (contract pins, real Postgres): offset round-trip — expense
then equal income on one account → balance restored to opening;
original row fields byte-identical; both rows appear in list/page/
search/export; `totalElements` counts both; foreign account create →
404 unchanged.

Android: wire test (offset create body identical to manual create —
no invented fields); VM tests (offset prefill — type flip, account/
amount/category carry; date=today; description empty; TRANSFER_* and
archived-source → ineligible; source-unresolvable → error state;
submit lock; ambiguous); screen tests (action visible/hidden per
eligibility, banner present in offset mode, prefilled values render);
connected tests on physical Pixel.
