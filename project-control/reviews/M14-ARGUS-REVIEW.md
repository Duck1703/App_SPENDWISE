# M14 ARGUS REVIEW — Budget Lifecycle (edit limit/period, delete)

```
ROLE: ARGUS (Independent QA Reviewer)
MILESTONE: M14 — REQ-BE-011 Budget Lifecycle (PATCH limit/period + DELETE)
ANDROID_RANGE: b6b2751..HEAD
BACKEND_COMMIT: 472b8fd
REVIEW_MODE: independent challenge of implementation + tests; every claim
             verified against source, wire behavior, and docs — not assumed.
VERDICT: PASS — 0 Blocker, 0 Major, 4 Minor/Note
```

## 1. Scope discipline

- Exactly one Post-V1 capability shipped: REQ-BE-011. No M15 defined or
  started. No second capability smuggled in.
- M9–M13 surfaces untouched except the shared interceptor machinery,
  which gained **zero** new code — PATCH/DELETE flow through the same
  proven M13 path.
- Reference repository `D:\app_spendwise\kotlin-accelerator-ai`:
  untouched (head `5f9622c`).
- Backend work was bounded to the M14 prerequisite and human-authorized.
  Human-owned dirty docs (`README.md`, `HUONG-DAN-SU-DUNG.md`,
  `docs/SPENDWISE-REQ.md`) remain dirty exactly as before M14 — verified
  by worktree inspection.
- `BACKEND-CONTRACT.md` updated for both new routes.

## 2. Honest-state challenges attempted

| Challenge | Result |
|---|---|
| **PATCH a budget deleted by another session** | 404 → `AppError.NotFound` → refresh → `NotFound` state. Server truth wins. |
| **PATCH with a period colliding with another of MY budgets** | DB unique `(user,category,period)` → 409 → `AppError.Conflict` → field error shown, no optimistic mutation. |
| **Re-PATCH identical values (own-row unique check)** | 200 — updating a row to its own existing `(category,period)` does not false-409. Verified. |
| **Ambiguous failure (connection drop mid-PATCH/DELETE)** | No retry, ever. VM surfaces "may already be updated" / refresh-reconciles for delete. Matches M13 no-replay contract; wire count asserted = 1 write. |
| **401 mid-PATCH/DELETE** | `sessionRetryable` — never replayed; refresh runs once; caller decides. Wire-proven. |
| **Session ends during in-flight mutation** | Dead-generation results are dropped; nothing published to the new session. Tested. |
| **Save with nothing changed** | Zero requests — `compareTo` (scale-insensitive) used for limit equality, so `20` vs `20.00` correctly counts as unchanged. |
| **Blank-period PATCH body** | Server treats blank as absent → limit-only update; all-absent → 400. VM never sends blank (Required validation). Consistent. |
| **DELETE while offline → reconnect** | Failure is a terminal event (no queue); user re-attempts — delete is idempotent-safe on retry (second delete 404 → NotFound state). |
| **Delete a budget shown in list** | Detail pops (`Deleted` event); next list load reflects server — no client-side ledger fabrication. |
| **Ledger contamination** | Budgets are planning objects; DELETE touches no transaction/recurring rows. `BudgetCalculationService` recomputes from ledger on read — nothing cached to go stale. |

## 3. Contract fidelity — backend

- `PATCH /api/budgets/{id}`: limit optional (`@DecimalMin`+`@Digits`),
  period optional (`YYYY-MM`); all-absent → 400; currency is
  **server-immutable** (not in the request — a client cannot attempt it,
  which is stronger than rejecting it).
- `DELETE /api/budgets/{id}`: 204; owner-scoped; foreign id → 404.
- Both routes live on the existing ownership-lookup — the 404-for-foreign
  behavior is structural, not a per-endpoint check that could be forgotten.
- No schema migration; no new tables; no ledger writes. Bounded as
  specified.

## 4. Findings

| # | Severity | Finding | Disposition |
|---|---|---|---|
| A-1 | Minor | Edit dialog initially lacked currency context for the limit field | **Fixed** — currency hint added |
| A-2 | Minor | No connected-device run for the new screens — edit/delete flows are JVM-verified only | Accepted: no device attached; same harness passed 178/178 in M13; UI logic identical in both runtimes |
| A-3 | Note | Edit PATCH always sends both fields (period prefilled) — if a user's untouched period collides with another budget's pair, a limit-only edit could surface a 409 they didn't intend | Correct behavior: the collision is real server truth; the 409 message names the conflict honestly |
| A-4 | Note | `LocalLifecycleOwner` deprecation warning in `PlanningDestinations.kt` — pre-existing, unchanged by M14 | Left as-is (pre-existing) |

## 5. Verdict

PASS. M14 is coherent, bounded, honestly surfaced, and the M13 security
invariants hold under the two new mutation verbs. The budget that was
"permanently unfixable" is fixable, and the test that proves it runs
against the real database.
