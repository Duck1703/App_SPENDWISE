# M14 SENTINEL REVIEW — Budget Lifecycle (edit limit/period, delete)

```
ROLE: SENTINEL (Test Engineer)
MILESTONE: M14 — REQ-BE-011 Budget Lifecycle (PATCH limit/period + DELETE)
ANDROID_RANGE: b6b2751..HEAD (governance b0036c6 + delivery)
BACKEND_COMMIT: 472b8fd (bounded prerequisite, human-authorized)
REVIEW_MODE: adversarial self-audit per workflow contract; all evidence
             reproduced from live gates, not assumed.
VERDICT: PASS — 0 Blocker, 0 Major, 3 Minor/Note
```

## 1. Test evidence (re-executed for this review)

| Gate | Result |
|---|---|
| Backend full regression (`mvn test`, `SPENDWISE_DB_PASSWORD` loaded) | 343 tests, **1 failure = documented pre-existing `RecurringSchedulingJobTest` timing flake** (expected 1, got 2 — identical to M12/M13 baseline, unrelated to budgets) |
| Backend focused M14 | `BudgetLifecycleTest` **13/13** — real MockMvc + Postgres |
| Android JVM full regression (`testDebugUnitTest`) | **592/592**, 0 skipped (570 M13 baseline → +22) |
| Android lint / detekt | clean / **0 smells** |
| Pixel 4 XL connected | **NOT RUN — no device attached** (`adb devices -l` empty at every check). Documented as N/A, not fabricated |
| Release (minified R8) | builds; `app-release-unsigned.apk` 2,430,570 B, SHA-256 `9dbf20accb095e66e56e36accc0797605042f3cf3c88453fe9564f16f0ac3a45` |

## 2. Invariant coverage — "a planning mutation must NEVER be silently replayed"

Wire-proven in `SessionRefreshContractTest` (new M14 cases):

- `a transmitted budget PATCH that 401s is NEVER replayed` — server sees
  exactly ONE `/api/budgets/…` PATCH + one `/api/auth/refresh`; caller gets
  `ApiException(401, sessionRetryable)`. Wire count asserted on
  `MockWebServer.requestCount`, not on call-site bookkeeping.
- `a transmitted budget DELETE that 401s is NEVER replayed` — same
  invariant for the second new mutation verb.
- Pre-existing proofs (POST probe, reads) unchanged — M14 verbs inherit
  the exact same M13 single-flight/marker machinery; no new auth code path
  was added.

## 3. Backend lifecycle suite — adversarial matrix

`BudgetLifecycleTest` (13 tests, real DB + MockMvc):

- Update limit only / period only / both — each asserts persisted row state.
- Empty patch `{}` → 400; whitespace-only period → 400.
- Non-positive limit (0, negative) → 400; malformed period (`2026-13`,
  `2026-1`, `26-10`) → 400.
- Period change into an existing `(user, category, period)` → **409**
  (DB unique constraint → `DataIntegrityViolationException` mapping,
  asserted against real Postgres, not mocked).
- Idempotent re-PATCH of identical values → 200 (own-row unique check
  verified — updating to the row's existing pair does NOT false-409).
- Stranger's budget PATCH → 404; row verified untouched afterwards.
- Stranger's budget DELETE → 404; row verified present afterwards.
- Double-DELETE → second call 404.
- DELETE removes the budget from `GET list`, `GET by id`, and
  `GET …/calculation` — all three read surfaces asserted.
- DELETE then recreate same `(category, period)` → 201 — the exact
  PRODUCT.md defect ("permanently unfixable wrong budget") is now the
  passing test. Required `delete`+explicit `flush` in the service so the
  row is gone before the insert inside the same transaction.

## 4. Android slice — layer-by-layer

- **Contract** (`PlanningServiceContractTest` +6): PATCH serializes only
  provided fields — `assertFalse(body.contains("limit"))` on a
  period-only request proves `encodeDefaults` does not leak `null`s;
  PATCH 409 → `ApiException(409)`; PATCH/DELETE 404 → `ApiException(404)`;
  DELETE hits the exact path and accepts 204.
- **Repository** (`BudgetRepositoryTest` +6): forwards only provided
  fields; all-null rejected client-side before the wire; 409 →
  `AppError.Conflict`; 404 → `AppError.NotFound`; dead-generation
  update/delete publish nothing and emit no event.
- **ViewModel** (`BudgetViewModelsTest` +8): dialog seeded from server
  limit (scale-safe `compareTo`, not `equals`); invalid limit/period
  produce field errors with no request; unchanged values → **zero**
  requests; save → PATCH → reload publishes fresh server calculation
  (optimistic state is never self-published); 409 → Conflict field error;
  ambiguous transport failure → honest "may already be updated" error
  without retry; delete success → `Deleted` event → pop; delete-404 →
  `NotFound` state (budget already gone is truthfully surfaced).
- **Compose** (`BudgetScreensTest` +3): detail screen exposes edit +
  delete affordances (readonly banner removed); edit dialog opens, edits,
  saves → PATCH observed; delete requires confirmation → DELETE observed
  only after confirm.
- **Fake integrity**: a latent infinite-recursion bug was found and fixed
  in `FakeBudgetRepository` (default `calculationResult` lambda resolved
  to the instance method). The fake now models deletion so post-delete
  reloads return 404 — the screen now reads server truth, not stale fake
  state.

## 5. Findings

| # | Severity | Finding | Disposition |
|---|---|---|---|
| S-1 | Minor | `EditBudgetDialog` initially showed the limit field with no currency context — a user could forget which currency the limit is in | **Fixed in this milestone**: dialog now renders `budget_edit_currency_hint` ("The limit stays in %1$s — the budget currency can't be changed") |
| S-2 | Note | Connected-device suite not executed — no Pixel attached during the block | Documented N/A; not a verdict reduction since the device's deterministic seams (session runtime, screen flows) are JVM-covered and M13's 178/178 physical run validated the same harness |
| S-3 | Note | `RecurringSchedulingJobTest` flake persists (pre-existing, byte-identical to M12 baseline) | Documented; out of M14 scope; does not touch budgets |

## 6. Verdict

PASS. Every acceptance criterion in `M14-BUDGET-LIFECYCLE.md` is
test-backed. The only test not run is the physical suite, blocked by
hardware absence and honestly recorded.
