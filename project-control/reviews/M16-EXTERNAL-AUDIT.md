# M16 EXTERNAL AUDIT — Recurring Template Lifecycle (edit, REQ-BE-012)

```
M16_EXTERNAL_AUDIT_FINAL=ACCEPTED
M16_HUMAN_APPROVED=YES
M16_STATUS=COMPLETE
M17_DEFINED=NO
M17_STARTED=NO
COURSE_UPDATE_EXECUTED=NO
```

One continuous independent audit block. Initial verdict was derived
from source + product docs + live falsification BEFORE the prior
Sentinel/Argus/handoff documents were read (see §Verdict timeline).
Every number below was re-executed, not adopted from the handoff.

## Checkpoints verified

| Repo | Expected | Actual | State |
|---|---|---|---|
| Android `D:\app_spendwise\spendwise-android` | `83c0d29` (M16 reviews+handoff) | `83c0d2912347e57115d6ad98a75d7f0e85c6fd69` | clean at precheck |
| Backend `D:\spendwise` | `eeb2c2f` (M16 PATCH) | `eeb2c2f37f57e888edec2536634590233df35ad8` | 3 known human-owned dirty paths only (`README.md` M, `HUONG-DAN-SU-DUNG.md` ??, `docs/SPENDWISE-REQ.md` ??) — preserved byte-for-byte throughout |
| Reference `D:\app_spendwise\kotlin-accelerator-ai` | read-only | untouched | verified post-audit |

## M15 canonical closeout

Verified in `project-control/milestones/M15-TRANSACTION-HISTORY.md`,
`project-control/reviews/M15-EXTERNAL-AUDIT.md`, and commit `102ced2`:
`M15_EXTERNAL_AUDIT_FINAL=ACCEPTED`, `M15_HUMAN_APPROVED=YES`,
`M15_STATUS=COMPLETE`. M15 remains canonically closed.

## REQ-BE-012 re-derivation (product → source, not reviews)

Canonical text (`project-control/POST-V1-BACKEND-REQUIREMENTS.md`):
`PATCH /api/recurring-transactions/{id}` edits a recurring template's
future-generation inputs (amount, category incl. clearing, frequency,
next due date, end date incl. removal). Generated rows never rewritten.
PRODUCT.md §27 carries no `endDate ≥ nextDueDate` invariant — the
entity's own exhaustion semantics (`advanceNextDueDate` can push
`nextDueDate` past `endDate` as the normal terminal state) make such an
invariant internally inconsistent. Implemented contract matches.

## Backend diff audit — `ef08539..eeb2c2f` (6 files, all `com.spendwise.recurring`)

| File | Verdict |
|---|---|
| `RecurringTransaction.java` | Entity mutators only; **finding F-1: no `@Version`** → remediated |
| `RecurringTransactionController.java` | `PATCH` → service → `ResponseEntity.ok(response)`; JWT-gated |
| `RecurringTransactionMapper.java` | frequency string→enum helper, invalid → 400 |
| `RecurringTransactionService.java` | `findByIdAndUserId` → 404 foreign/missing; sparse apply; conflict-pair 400s; clear-flag semantics; account/direction/active not in request type |
| `UpdateRecurringTransactionRequest.java` | record, all-optional, `hasField` ≥1 rule, `@DecimalMin`/`@Digits` amount |
| `RecurringUpdateContractTest.java` | 11 original tests — substantive (live Postgres + MockMvc; real ledger-isolation proof); gaps noted below |

No collateral outside `com.spendwise.recurring`. Confirmed by
`git diff --name-only`.

## Android diff audit — `102ced2..83c0d29` (17 source files + docs)

All production hunks verified. Highlights:

- `RecurringDtos.kt` — `UpdateRecurringRequestDto` all-nullable +
  defaults; relies on `explicitNulls=false`; `clearCategory`/
  `clearEndDate` default false (omitted from wire when false — verified
  wire-level via backend sparse tests reading raw JSON).
- `RecurringService.kt` — `@PATCH("{id}")`, no retry annotations.
- `RecurringRepository.kt` — `sessionGeneration` captured pre-call,
  re-checked post-call; dead generation → `SessionEndedException` →
  Ambiguous, no `_mutations` emit (A→B stale-publish impossible).
- `EditRecurringViewModel.kt` — snapshot (`form`/`amount`/`dates`/
  `template`) captured before launch; `loadSerial` guards stale
  success+failure; save lock; no auto-retry of mutations; server field
  errors mapped per-field; ambiguous-outcome copy.
- `EditRecurringScreen.kt` — scrollable, `imePadding`, ≥48dp targets,
  immutable-identity card with currency context, enum-bounded frequency
  segmented control, client blocks `endDate < nextDueDate`.
- `PlanningDestinations.kt` / `AppRoutes.kt` / `MainFlow.kt` —
  `RecurringEditRoute(templateId)` serializable; Saved-event →
  `popBackStack` → detail reloads via `mutations` (server truth).
- `RecurringDetailScreen.kt` — Edit affordance inside `if
  (template.active)` only.
- Diff is additive; create/deactivate paths untouched.

## Falsification battery (all executed against live code, not reviewed)

Backend (`RecurringUpdateContractTest`, now 17 tests + 1 lock test —
all new cases written by this audit, all green):

| Claim | Falsification |
|---|---|
| Sparse patch | `eachSingleFieldPatchPreservesEveryOtherField` — 5 single-field PATCHes, every other field re-read unchanged |
| Omitted-field preservation | same test + `updatesAmountOnlyAndPreservesEveryOtherField` |
| Category set/clear | `clearFlagsNullOutCategoryAndEndDate` (+ explicit-false flags preserve: `explicitFalseClearFlagsPreserveNullableFields`) |
| End-date set/clear | same pair |
| Conflict pairs | `valuePlusItsClearFlagIsRejected` — `category`+`clearCategory`, `endDate`+`clearEndDate` → 400 |
| Identity smuggling | `identityFieldsSentInTheBodyAreNeverApplied` — JSON carrying `accountId`/`direction`/`currency`/`active`/`userId`/`id` keys → 200, all unchanged (fixes the original test that never sent the fields) |
| Owner-scoped 404 | `foreignOrMissingTemplateIsNotFound` — foreign id and random id indistinguishable |
| Validation | `rejectsInvalidAmountFrequencyAndCategory`, `malformedAndOversizedInputsAreRejected` (negative, >17-integer digits, bad enum, malformed date, malformed JSON) |
| Empty patch | `emptyPatchIsRejected` → 400 |
| Atomicity | `aMultiFieldPatchIsAtomicWhenOneFieldIsInvalid` — mixed valid+invalid → 400, nothing applied |
| Inactive editable | `editDoesNotReactivateADeactivatedTemplate` — 200, `active` stays false |
| Ledger isolation | `editChangesOnlyFutureGenerationAndNeverRewritesLedgerRows` — generate → PATCH → generated row byte-identical, next generation uses edited values |
| Optimistic lock | `aStaleTemplateWriteFailsInsteadOfRevertingACommittedEdit` (`RecurringOptimisticLockTest`) — dual-EntityManager stale save → `OptimisticLockingFailureException` → 409 via existing handler |

Note: one initial hypothesis was falsified in the auditor's favor —
`{"amount":1e3}` is a legal JSON number (1000.00, scale −3, passes
`@Digits`), correctly accepted. Removed from the suite; recorded here
so the test list isn't padded.

Android (JVM): existing 22 VM tests verified substantive (wire-count
assertions, stale-response races, session guards). Audit additions:

- `a field edited mid-save cannot contaminate the sent snapshot` —
  mutates form between `save()` and repo completion; captured snapshot
  wins.
- `a mutations emission reloads detail to server truth` — fake repo
  gained the missing `emitMutation()` seam (other fakes already had
  it); detail VM now proven to refresh from repository state on
  mutation signal, not trust local echoes.

Physical falsifications from the handoff list: sparse wire bytes
(backend tests assert full-field preservation, which transitively
proves omission), non-replay (M13 wire tests assert exactly 1 PATCH +
1 refresh under transmitted-401), ledger isolation (live test above),
foreign-404 non-disclosure (same response body shape for foreign and
missing), `explicitNulls=false` (sparse-preservation tests require it).

## Concurrency analysis — the finding both prior reviews missed

`RecurringGenerationService.generateDue` runs the scheduler catch-up
loop and saves the whole template entity; M16 widened the entity's
write surface from 1 to 6 mutable fields. Without `@Version`, a
scheduler save interleaving with a PATCH silently reverts the committed
edit — the user got a 200 but the next generated row carries pre-edit
financial values. `UNIQUE (user_id, source_ref)` (V10) +
`existsByUserIdAndSourceRef` prevent duplicate ledger rows, but dedup
does not prevent the lost update. The codebase's own convention —
`@Version` on `Account` (V2) + `OptimisticLockingFailureException →
409` handler — defines this as a real defect class with a bounded fix.

Both Sentinel and Argus marked the scheduler out of scope because "M16
adds no scheduler code" — true, but the invariant is about the shared
entity, not the scheduler's code. **MAJOR, remediated in-task.**

## Findings and dispositions

| # | Sev | Finding | Disposition |
|---|---|---|---|
| F-1 | MAJOR | `RecurringTransaction` lacked `@Version` — scheduler whole-entity save could silently revert a committed PATCH | FIXED `133fe18`: `@Version` + `V11__recurring_version.sql` + `RecurringOptimisticLockTest`; 409 via existing handler |
| F-2 | MINOR | `immutableIdentityFieldsStayPut` never sent the smuggled fields it claimed to verify | FIXED `133fe18`: `identityFieldsSentInTheBodyAreNeverApplied` sends them |
| F-3 | MINOR | No single-field sparse / date-validation / malformed-JSON / atomicity / false-flag proofs | FIXED `133fe18`: 6 new falsification tests |
| F-4 | MINOR | `FakeRecurringRepository` lacked `emitMutation` seam; mutations→detail-refresh untested | FIXED (Android): seam + `a mutations emission reloads detail to server truth` |
| F-5 | MINOR (pre-existing) | `RecurringSchedulingJobTest` clock-drift flake — fixture due Sept 1 vs `LocalDate.now()` Oct 8 → 2 occurrences not 1 | FIXED `ca5b380`: clock-relative fixture; 5/5 green; full suite 383/383 |
| F-6 | MINOR (documented) | `endDate < nextDueDate` unvalidated server-side | NOT A DEFECT: inert state, zero ledger risk; exhaustion legitimately produces `nextDueDate > endDate`; no product invariant; client blocks. Parity note carried forward for a future both-entry-points hardening |
| F-7 | MINOR | `BACKEND-CONTRACT.md` endpoint ledger missing PATCH row; `RecurringDtos.kt` file header stale | FIXED: row + field-format entry added; KDoc updated |

Pre-existing notes carried (unchanged, re-verified): M13
`SessionRefreshContractTest` concurrent-GET timing window; lint
warnings (22, all pre-existing categories, none in M16 code).

## Gate evidence — re-executed on post-remediation final source

| Gate | Result |
|---|---|
| Backend focused | `RecurringUpdateContractTest` 17/17 + `RecurringOptimisticLockTest` 1/1 + `RecurringSchedulingJobTest` 5/5 |
| Backend full regression | **383 tests, 0 failures, 0 errors, 0 skipped** (376 baseline + 7 new) |
| Android JVM fresh | **653 tests, 0 failures, 0 skipped** (651 → +2 audit tests) |
| `lintDebug` | 0 errors, 22 warnings (pre-existing files only) |
| `detekt` | 0 code smells |
| `assembleDebug` / `assembleRelease` | BUILD SUCCESSFUL (R8 + lintVital green) |
| R8 serializer audit | `UpdateRecurringRequestDto`, `clearCategory`, `clearEndDate` present in release dex |
| Manifest/backup | `allowBackup=true` with `datastore/` excluded (cloud-backup, device-transfer, full-backup); exports correct |
| Logging | HEADERS-only, DEBUG-gated, `Authorization`/`Cookie`/`Set-Cookie` redacted, no bodies |
| Physical Pixel 4 XL (99041FFBA006F0) | early: **193/193 ×2**; final source: **193/193 ×2** — 0 fail, 0 skip both pairs |
| Install + launch | `installDebug` OK; `MainActivity` topResumed on 99041FFBA006F0 |

## Test-integrity audit

No vacuous tests accepted. `RecurringOptimisticLockTest` fails loudly
without `@Version` (stale write silently commits). The clock-relative
scheduler fix kept the assertion strength (exactly-1 + second-tick
dedup). Android additions assert real state transitions and wire
counts. The one bogus hypothesis (`1e3`) was removed rather than
asserted green.

## Verdict timeline

1. **Initial external verdict (frozen before reading prior reviews)**:
   `FINDINGS` — 1 Major (F-1) + minors; lean ACCEPTED after bounded
   remediation. Basis: independent diff audit + falsification +
   concurrency analysis.
2. Prior `M16-SENTINEL-REVIEW.md` / `M16-ARGUS-REVIEW.md` /
   `M16-EXTERNAL-AUDIT-HANDOFF.md` read only after freezing. Both
   reviews PASSed with 0 Major; the handoff's falsify list was followed
   but did not include the shared-entity lost update. F-1 is the
   audit's independent value-add.
3. All Blocker/Major remediated in-task; low-risk minors fixed; every
   gate re-run on final source.
4. **Final: `M16_EXTERNAL_AUDIT_FINAL=ACCEPTED`.**

## Final git state

```
Backend  D:\spendwise                     HEAD ca5b38031cc23964b4545d4597e5e99eef2ab9a5
         eeb2c2f delivery + 133fe18 audit remediation + ca5b380 flake fix
         dirty: README.md (M), HUONG-DAN-SU-DUNG.md (??), docs/SPENDWISE-REQ.md (??) — human-owned, untouched
Android  D:\app_spendwise\spendwise-android  see HEAD commit below (audit closeout)
Reference D:\app_spendwise\kotlin-accelerator-ai  5f9622c59ce602ce33c98ee633ba799f903b67e1 — untouched
```

Course sync deferred (`COURSE_UPDATE_EXECUTED=NO`,
`COURSE_SYNC_DEFERRED=YES`). No M17 defined or implemented.
