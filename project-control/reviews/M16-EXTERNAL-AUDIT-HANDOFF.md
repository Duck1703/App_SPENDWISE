# M16 EXTERNAL-AUDIT HANDOFF — Recurring Template Lifecycle (edit, REQ-BE-012)

```
M16_READY_FOR_EXTERNAL_AUDIT=YES
M16_HUMAN_APPROVED=NO          (audit gate precedes human approval)
SPENDWISE_STATUS=M16_READY_FOR_EXTERNAL_AUDIT
NEXT_ACTION=ONE_LARGE_INDEPENDENT_EXTERNAL_M16_AUDIT
M17_DEFINED=NO
M17_STARTED=NO
```

The independent M16 external audit has NOT been performed — this
document is the handoff into it. Auditors: treat every number below
as a claim to be falsified, not as evidence.

## Scope delivered

One substantial capability: **REQ-BE-012 — recurring template edit**.
A user can correct a recurring template's future-generation inputs
(amount, category incl. clearing, frequency, next due date, end date
incl. removal) without destroying and recreating it. Edits affect
future generation only; already-generated ledger rows are never
rewritten.

Deliberately NOT in scope: reactivation (`active` stays DELETE-only),
rule/budget/account/transaction edits, scheduler or catch-up policy
changes, schema changes, M17, course sync.

## Baseline and commits

```
Android repo:  D:\app_spendwise\spendwise-android  (branch main)
  M15 head:    102ced278748a29b3824efb5a437ffc213fe3edb
  M16 gov:     04310be  docs: approve M16 recurring template lifecycle
  M16 delivery: f8d855e feat(planning): M16 recurring template edit
  M16 reviews: (this commit — reviews + status)
Backend repo:  D:\spendwise  (branch main)
  M15 head:    ef08539753438a37725533331714915f7e70d9f9
  M16 delivery: eeb2c2f  feat(recurring): M16 template edit PATCH
  Backend diff: exactly 6 files, all com.spendwise.recurring —
                no collateral; human-owned docs (README.md,
                HUONG-DAN-SU-DUNG.md, docs/SPENDWISE-REQ.md) untouched.
```

## Backend contract

`PATCH /api/recurring-transactions/{id}` → 200
`RecurringTransactionResponse` (full updated template)

```
Request UpdateRecurringTransactionRequest — all optional, ≥1 required:
  amount        @DecimalMin(>0) @Digits(17,2)
  category      category code (resolved like create; unknown → 400)
  clearCategory boolean; category+clearCategory → 400; flag alone → null
  frequency     WEEKLY|MONTHLY (invalid → 400 via mapper helper)
  nextDueDate   ISO LocalDate; any date (create parity — past = catch-up)
  endDate       ISO LocalDate; endDate+clearEndDate → 400
  clearEndDate  boolean; flag alone → null
Ownership:  findByIdAndUserId → foreign/missing → 404 (non-disclosure)
Immutable:  account, direction, currency (account-locked), active,
            userId — structurally absent from the request type
Errors:     400 validation/DomainException · 401 unauthenticated ·
            404 foreign/missing · 200 success · no 409 class
Notes:      editing a deactivated template is allowed (harmless —
            no future generation); no schema migration.
```

## Android surface

- `RecurringService.update` (`@PATCH("{id}")`) + sparse
  `UpdateRecurringRequestDto` (all fields nullable/default → omitted
  under `explicitNulls=false`; clear flags serialize only when true).
- `RemoteRecurringRepository.update` — `sessionGeneration` captured
  pre-call, re-checked post-call before `_mutations` emit; dead
  generation → `SessionEndedException` → Ambiguous, no emit.
- `EditRecurringViewModel` — prefill via `findById`; `loadSerial`
  guards stale success AND stale failure; save lock; no mutation
  retry; server field errors → per-field form errors; ambiguous
  outcome copy (verify before retrying).
- `EditRecurringScreen` + `RecurringEditRoute` nested under planning
  graph; immutable identity read-only; detail "Edit" affordance on
  active templates only; detail VM observes `mutations`.
- Session stack unchanged: PATCH never replayed post-401
  (GET/HEAD-only authenticator gate).

## Gate evidence (all re-executed, not assumed)

| Gate | Result |
|---|---|
| Backend focused | `RecurringUpdateContractTest` **11/11** (MockMvc + real Postgres) |
| Backend full regression | **376 tests, 1 failure** — documented pre-existing `RecurringSchedulingJobTest` date-dependent flake (expected 1, got 2; identical signature since M10-era; PATCH path untouched) |
| Android JVM fresh | **651/651**, 0 fail, 0 skip (617 → +34) |
| `lintDebug` | PASS — 23 warnings, all pre-existing files, none in M16 code |
| `detekt` | **0 code smells** |
| `assembleDebug` / `assembleRelease` | PASS (R8 + lintVital green) |
| R8 serializer audit | `UpdateRecurringRequestDto`, `$$serializer`, `Companion` kept unobfuscated (mapping.txt lines 253578–253618) |
| Instrumentation compile | green |
| Physical Pixel 4 XL suite | **193/193 × 2** on final source, 0 fail, 0 skip (186 → +7) |
| Install + launch | `installDebug` + launcher intent → `MainActivity` topResumed, visible, on serial `99041FFBA006F0` |

## Reviews

- `M16-SENTINEL-REVIEW.md` — **PASS**, 0 Blocker / 0 Major /
  1 Minor + 1 Note. Requirement→test mapping table included.
- `M16-ARGUS-REVIEW.md` — **PASS**, 0 Blocker / 0 Major /
  1 Minor + 1 Note. Independent contract re-derivation, diff walk.

## Findings carried into audit (all documented, none hidden)

1. **MINOR — `endDate < nextDueDate` unvalidated server-side** (pre-existing
   parity gap, NOT introduced by M16): create and PATCH alike accept an
   inert template (`due()` false after `endDate`; zero ledger risk).
   Android client blocks the case. Correct fix = harden BOTH entry
   points in a future milestone, not PATCH-only divergence.
2. **NOTE — `SessionRefreshContractTest` concurrent-GET flake**
   (pre-existing): 5-enqueued-response harness has a timing window under
   parallel execution; passes 3× isolated and in the fresh full suite.
   Production auth path untouched.
3. **NOTE — `lintDebug` transient LintDriver crash** on first combined
   run under concurrent load; standalone re-run UP-TO-DATE green.
4. **NOTE — backend full-suite flake** (`RecurringSchedulingJobTest`):
   date-dependent baseline failure, documented across prior milestones;
   M16 adds no scheduler code.

## Regression-risk assessment for the auditor

Low blast radius: backend delta is one new controller method + one
service method + entity mutators + request record + test class — all
in `com.spendwise.recurring`. Android delta is additive (new screen/
VM/route + repository method + DTO); existing create/deactivate paths
untouched. The single semantic risk class — "does edit mutate ledger
history" — is falsified by the live generate→edit→verify contract test.

## What the auditor should falsify

- Sparse-body claim: capture the actual PATCH wire bytes for a
  single-field edit.
- Non-replay claim: force a transmitted-401 on PATCH and count wire
  requests (expect exactly 1 PATCH + 1 refresh).
- Ledger isolation: edit a template between two generated occurrences.
- Foreign-id PATCH → 404 with no disclosure.
- The `endDate < nextDueDate` MINOR: confirm client blocking and decide
  whether server parity-gap is acceptable for this milestone.
- `explicitNulls=false` claim: verify `{"amount":X}` arrives without
  null-valued keys.
- Physical evidence: re-run the connected suite; expect 193/193.
