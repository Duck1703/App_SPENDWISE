# M16 ARGUS REVIEW — Recurring Template Lifecycle (edit, REQ-BE-012)

```
ROLE: ARGUS (Independent QA Reviewer)
MILESTONE: M16 — REQ-BE-012 Recurring Template Edit
           (PATCH /api/recurring-transactions/{id})
ANDROID_RANGE: 04310be..f8d855e (governance + delivery)
BACKEND_RANGE: ef08539..eeb2c2f (bounded-write, human-authorized)
REVIEW_MODE: independent adversarial re-derivation — contract re-read
             from canonical sources, backend diff walked file-by-file,
             Android slice walked file-by-file, gate evidence re-verified.
VERDICT: PASS — 0 Blocker, 0 Major, 2 Minor/Note
```

## 1. Independence checks — contract re-derived, not trusted

- `UpdateRecurringTransactionRequest` re-read from source: 7 optional
  fields + 2 clear flags. Field-by-field against the M16 milestone
  contract: amount `@DecimalMin(>0) @Digits(17,2)` ✓, category code ✓,
  `clearCategory`/`clearEndDate` mutual-exclusion semantics ✓,
  `frequency` enum-name via existing mapper helper ✓, `nextDueDate`/
  `endDate` ISO `LocalDate` ✓. **Contract match: EXACT.**
- Service `update()` walked line-by-line: empty-patch → 400 before any
  lookup; conflict pairs → 400 before any lookup; `findByIdAndUserId`
  owner scoping → foreign/missing indistinguishable → controller 404.
  **No disclosure vector.**
- Category resolution is `categoryRepository.findById` — **identical to
  the create path** (line 31). Categories are code-keyed reference data;
  no owner-scoping divergence introduced.
- `updateAmount` rebuilds `Money` with `template.amount().currency()` —
  the request literally cannot carry currency; D14 mitigation holds.
- Identity fields (`account`, `direction`, `currency`, `active`,
  `userId`) have no setters in the request or service path — immutability
  is structural, not documentary.
- Backend diff is exactly 6 files, all in `com.spendwise.recurring` —
  zero collateral. Human-owned dirty docs verified untouched.
- No schema migration (empty V* delta); scheduler and
  `RecurringGenerationService` untouched — `sourceRef` keys unchanged,
  ledger isolation proven by the live generate→edit→verify test.

## 2. Android slice — architecture fidelity

- `RecurringService.update` mirrors `BudgetService.update` (M14) exactly:
  `@PATCH("{id}")`, `UpdateRecurringRequestDto` body, same error surface.
- `RemoteRecurringRepository.update`: pre-call `sessionGeneration`
  capture → service call → **post-call re-check before `_mutations`
  emit** — identical ordering to `deactivate`; a dead-generation write
  emits nothing (`SessionEndedException` → Ambiguous at the VM).
- `UpdateRecurringRequestDto` fields are all nullable/default → sparse
  body under `explicitNulls=false`; clear-flag serialization verified on
  the wire, not just in the VM.
- `EditRecurringViewModel`: `loadSerial` guards BOTH stale success and
  stale **failure** (the M15 regression lesson is applied proactively);
  save lock; no retry of the mutation itself; ambiguous copy instructs
  verify-before-retry; server field errors mapped onto form fields.
- `EditRecurringScreen`: immutable account/direction/currency rendered
  read-only — user cannot attempt what the contract forbids.
- Edit affordance is active-templates-only (deactivated template edits
  have no future effect; not surfaced — milestone §Android scope).
- `RecurringDetailViewModel` observes `mutations` → back-stack detail
  refreshes after save.
- Diff scope: 19 files, all recurring-edit — **no scope creep**; no new
  dependency, no new auth path, no client-side financial derivation.

## 3. Honesty invariants

- No fabricated success: ambiguous outcome surfaces `Ambiguous` error
  with verify-before-retry copy; the PATCH is never auto-retried (M13
  authenticator returns null for non-GET/HEAD — wire-proven for this
  verb specifically, not inherited by assumption).
- Sparse-body test asserts literal absence of keys (`assertFalse(body
  .contains("category"))`) — not "null was sent but tolerated".
- Template ≠ ledger: no edit path touches generated `Transaction` rows;
  backend proves the Jan-row-unchanged/Feb-row-new-amount sequence
  against real Postgres.

## 4. Findings

### MINOR-2 (pre-existing parity gap): `endDate < nextDueDate` unvalidated server-side

Create and PATCH alike accept `endDate` earlier than `nextDueDate` —
the template becomes permanently inert (`due()` false after `endDate`),
no ledger harm. The Android client blocks this case. PATCH-only
validation would break create-parity; the honest fix is a future
hardening of both entry points together. Recorded in Sentinel MINOR-2.

### NOTE-1: `SessionRefreshContractTest` concurrent-GET flake (pre-existing)

Timing-window weakness in the 5-enqueued-response harness under parallel
execution — passes in isolation and in the fresh full suite; production
auth path untouched. Tracked for future harness padding.

## 5. Evidence completeness

Milestone doc, register entry (REQ-BE-012), roadmap row, backend
contract suite (11), Android wire/repo/VM tests (34), instrumentation
(7), Sentinel report — all present and internally consistent.
Physical run 1 of 2 verified (193/193); run 2 on final source pending
at the time of this review (no code changes since run 1 other than
review documents).

## 6. Recommended next action

Physical suite run 2 + install/launch, then ATLAS handoff with
`M16_READY_FOR_EXTERNAL_AUDIT=YES`. The independent external audit is
explicitly **not** performed here.

`VERDICT: PASS`
