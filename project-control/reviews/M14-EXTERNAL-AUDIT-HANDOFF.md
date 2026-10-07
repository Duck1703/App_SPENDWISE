# M14 EXTERNAL-AUDIT HANDOFF — Budget Lifecycle

```
MILESTONE: M14 — REQ-BE-011 Budget Lifecycle
           (PATCH /api/budgets/{id} limit+period · DELETE /api/budgets/{id})
ANDROID_DELIVERY: 1412860 · governance b0036c6 · baseline b6b2751
BACKEND_DELIVERY: 472b8fd · baseline da147b8
REFERENCE_REPO: 5f9622c (unchanged, read-only honored)

M13_COMPLETE=YES
M14_TECHNICAL_COMPLETE=YES
M14_READY_FOR_EXTERNAL_AUDIT=YES
M14_HUMAN_APPROVED=NO
M15_DEFINED=NO
M15_STARTED=NO
SPENDWISE_STATUS=M14_READY_FOR_EXTERNAL_AUDIT
NEXT_ACTION=ONE_LARGE_INDEPENDENT_EXTERNAL_M14_AUDIT
```

## 1. Scope audit — exactly one capability

Delivered: REQ-BE-011 only — edit budget limit/period, delete budget,
full Android vertical slice. Nothing else. M9 export / M10 import /
M11 dates / M12 transfers / M13 refresh were removed from candidacy and
are untouched except the shared M13 interceptor path, which gained zero
new code. No M15 defined or started. The PRODUCT.md defect — "a wrong
current-month budget is permanently unfixable" — is the shipped fix;
a real-Postgres test proves delete-then-recreate succeeds.

## 2. Backend evidence

- New endpoints: `PATCH` + `DELETE /api/budgets/{id}` — owner-scoped;
  foreign/missing id → 404; validation 400; `(user,category,period)`
  collision → 409 via existing `DataIntegrityViolation` mapping; no
  schema migration; no ledger writes; currency/category immutable.
- `BudgetLifecycleTest` **13/13** (real MockMvc + Postgres): field
  updates, empty/invalid payloads, collision, idempotent re-PATCH,
  stranger update/delete 404 with row-state verification, delete across
  all read surfaces, delete-then-recreate.
- Full backend regression: **343 tests, 1 failure** =
  `RecurringSchedulingJobTest.firingTheScheduledMethodTwiceForTheSamePeriodCreatesExactlyOneTransaction`
  (expected 1, got 2) — documented pre-existing timing flake, proven
  byte-identical to the accepted M12 baseline in M13; unrelated to
  budgets.
- `BACKEND-CONTRACT.md` updated for both routes.

## 3. Android evidence

| Gate | Result |
|---|---|
| JVM regression `testDebugUnitTest` | **592/592**, 0 skipped (570 → +22) |
| lint / detekt | clean / **0 smells** |
| `assembleDebug` / `assembleRelease` (minified R8) | pass |
| Connected Pixel 4 XL | **N/A — no device attached** during the block (`adb devices -l` empty at every check). Not fabricated. M13's 178/178 run validated the identical harness; new flows are JVM-covered |
| Release APK | `app/build/outputs/apk/release/app-release-unsigned.apk`, **2,431,038 B**, SHA-256 `abb52304b72b7e5cc23434567f5e0b0d4d0fac392a3d1f528160946e8eb966d6` |
| Serializers | **44 `@Serializable`** in remote DTOs; `UpdateBudgetRequestDto` + `BudgetDto` verified kept in release `mapping.txt` |
| Manifest | `INTERNET` only; no new permissions; datastore excluded from backup + device-transfer rules; no cleartext |

New M14 tests (22): PATCH absent-field omission (wire body asserted to
NOT contain untouched keys — `encodeDefaults` leak-proof), PATCH 409/404,
DELETE path + 204/404, transmitted-PATCH/DELETE-401 **never replayed**
(wire count = 1 write + 1 refresh), repository generation-race drops,
ViewModel edit validation / save+reload / conflict / ambiguous / delete
event, Compose edit+delete flows.

## 4. Security invariants preserved

M13 contract holds for both new verbs — verified at the wire:

- Locally-expired token → pre-send refresh, then the write transmits
  once with the fresh bearer.
- Transmitted PATCH/DELETE that 401s → `sessionRetryable`; **never
  replayed** (server sees exactly one write).
- Refresh rejection → `Session.Expired`, store wiped.
- Ambiguous mutation failure → surfaced honestly; no silent retry, no
  queue. Delete reconnects honestly: refresh reconciles to server truth
  (live → Content, gone → NotFound).
- Session generation guards drop dead-generation results — a mutation
  landing across a logout/login cannot surface in the new session.

## 5. Reviews

| Reviewer | Verdict | Findings |
|---|---|---|
| SENTINEL | **PASS** | 0 Blocker / 0 Major / 3 Minor-Note — S-1 (dialog currency context) **remediated in-milestone**; S-2 connected N/A; S-3 backend flake note |
| ARGUS | **PASS** | 0 Blocker / 0 Major / 4 Minor-Note — A-1 same currency fix; A-2 connected N/A accepted; A-3 collision-on-prefilled-period = honest 409 (correct); A-4 pre-existing deprecation |

Remediation status: **all blockers/majors remediated (zero found)**;
the single actionable minor fixed and all gates re-run green after the
fix (JVM 592/592, detekt 0, release rebuilt — SHA above is the
post-fix artifact).

## 6. Per-milestone regression status (M0–M13 preserved)

M0–M8: V1 externally accepted baseline, unchanged. M9: COMPLETE (external
audit + gates). M10: COMPLETE (JVM 493/493, connected 158/158×2). M11:
COMPLETE, human-approved (JVM 503/503, connected 162/162×2). M12: COMPLETE,
human-approved (JVM 533/533, connected 174/174×2). M13: COMPLETE,
human-approved (JVM 570/570, connected 178/178×2). All M0–M13 tests still
pass inside the 592 — nothing regressed.

## 7. Worktree / repository state

- Android `spendwise-android`: clean at `1412860` (+ this handoff
  commit); governance `b0036c6`; baseline `b6b2751`.
- Backend `spendwise`: `472b8fd` on `da147b8`; worktree carries ONLY the
  3 pre-existing human-owned dirty docs (`README.md`,
  `HUONG-DAN-SU-DUNG.md`, `docs/SPENDWISE-REQ.md`) — untouched by M14.
- Reference `kotlin-accelerator-ai`: `5f9622c`, unchanged.
- No push performed. No tags created.

## 8. What the external auditor should attack first

1. `BudgetDetailViewModel.saveEdit` — PATCH failure modes
   (409 conflict copy, ambiguous, session-retryable).
2. `BudgetService.updateBudget` — empty-patch, collision, stranger-row
   paths.
3. `SessionRefreshContractTest` M14 cases — replay the wire assertions.
4. Delete-then-recreate backend test — the PRODUCT.md defect claim.
5. Adversarial: PATCH `period` into a collision while `limit` unchanged —
   confirm the 409 copy is honest.
