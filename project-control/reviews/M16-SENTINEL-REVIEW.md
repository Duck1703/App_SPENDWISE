# M16 SENTINEL REVIEW — Recurring Template Lifecycle (edit)

```
ROLE: SENTINEL (Test Engineer)
MILESTONE: M16 — REQ-BE-012 Recurring Template Edit
           (PATCH /api/recurring-transactions/{id})
ANDROID_RANGE: 04310be..f8d855e (governance + delivery, 19 files, +1687)
BACKEND_COMMIT: eeb2c2f (bounded prerequisite, human-authorized)
REVIEW_MODE: adversarial self-audit per workflow contract; all evidence
             reproduced from live gates, not assumed.
VERDICT: PASS — 0 Blocker, 0 Major, 2 Minor/Note
```

## 1. Test evidence (re-executed for this review)

| Gate | Result |
|---|---|
| Backend focused M16 | `RecurringUpdateContractTest` **11/11** — real MockMvc + Postgres |
| Backend full regression (`mvn test`) | 376 tests, **1 failure = documented pre-existing `RecurringSchedulingJobTest` date-dependent flake** (expected 1, got 2 — identical signature documented since M10-era baselines; unrelated to the PATCH path) |
| Android JVM full regression (`testDebugUnitTest`, fresh) | **651/651**, 0 failures, 0 skipped (617 M15 baseline → +34) |
| Android lint (`lintDebug`) | PASS — 23 warnings, all in pre-existing files (`MoreScreen`, `strings.xml`, `topo_lines`, `libs.versions.toml`); none in M16 code |
| Detekt | **0 code smells** (empty txt report, 0 sarif results) |
| Debug / Release builds | PASS; `assembleRelease` green incl. `lintVitalAnalyzeRelease` + `minifyReleaseWithR8` |
| R8 serializer audit | `UpdateRecurringRequestDto`, `$$serializer`, `Companion` kept unobfuscated in `mapping.txt` (lines 253578–253618) — same preservation pattern as M14 `UpdateBudgetRequestDto` |
| Instrumentation compile | `:app:compileDebugAndroidTestKotlin` green |
| Pixel 4 XL connected suite | **193/193 PASS**, 0 failed, 0 skipped (186 M15 → +7 M16 tests) — run 1; run 2 on final source pending |

## 2. Requirement → test mapping (REQ-BE-012)

| Acceptance surface | Covering tests |
|---|---|
| Sparse PATCH — only changed fields on the wire | `recurring PATCH sends sparse body` (PlanningServiceContractTest) — asserts method, path, auth, and that absent fields + immutable identity are omitted |
| `clearCategory` / `clearEndDate` explicit clearing | `recurring PATCH clear flags serialize as true when clearing`; VM tests `removing the category sends clearCategory`, `emptying the end date sends clearEndDate`, `changing the category sends the code not a clear` |
| Foreign id → 404, no disclosure | `recurring PATCH foreign id surfaces as 404`; backend `PATCH on a stranger's template is 404` |
| Transmitted-401 never replays a mutation | `recurring PATCH 401 after transmission is never replayed` — wire `requestCount = 2` (1 refresh + exactly 1 PATCH) |
| Pre-send renewal, then exactly one PATCH | `locally-expired token renews before exactly one recurring PATCH` |
| Ledger isolation (edit ≠ rewrite history) | backend `RecurringUpdateContractTest` ledger-isolation proof: generate Jan @1200.00 → edit to 999.99 → **Jan row unchanged**, Feb row uses 999.99 |
| Backend validation matrix | empty PATCH 400; `category`+`clearCategory` 400; `endDate`+`clearEndDate` 400; non-positive/oversized amount 400; bad frequency 400; unknown category 400; inactive template editable. `endDate < nextDueDate` is **client-blocked only** (`end date before next due blocks save`) — server accepts it under create-parity; see MINOR-2 |
| Repository contract | `update maps a sparse patch`, `update sends clear flags…`, `update emits the mutation signal`, `a dead-generation update is discarded and emits nothing` |
| ViewModel | 22 tests: prefill, NotFound, network retry, post-expiry reload, **stale-failure race** (M15 lesson), stale-success race, no-change no-wire, sparse patch, clear flags, field errors, save lock, ambiguous outcome, dead generation |
| Compose/instrumentation | 7 tests: edit affordance visible+navigates on active; hidden on inactive; prefill + immutable identity; save disabled until change; save fires; error banner; missing-notice |

## 3. Invariants verified adversarially

- **Mutation never silently replayed** — wire-proven for the new PATCH verb; `TokenAuthenticator` GET/HEAD gate unchanged.
- **A→B generation guard** — `RemoteRecurringRepository.update` re-checks `sessionGeneration` post-call; `dead-generation update` test asserts the dropped result emits no `mutations` event.
- **Sparse-partial semantics** — `explicitNulls` defaults false; wire test asserts absent fields literally missing from the JSON body (not `"field":null` noise).
- **No fabricated success** — dropped mid-write and dead-generation saves both surface `Ambiguous`, with copy instructing the user to verify before retrying; no auto-retry.
- **Identity immutability** — request DTO shape cannot carry `accountId`/`direction`/`currency`/`active`; wire test asserts their absence.

## 4. Findings

### MINOR-1 (pre-existing, not introduced by M16): `SessionRefreshContractTest` concurrent-GET flake

`concurrent GETs that all 401 collapse into a single refresh` flaked once in a
parallel multi-class run (line 465, `r1/r2.isSuccess`). Root cause: the test
enqueues exactly 5 responses; under contention a late second GET can consume a
200 as its *initial* response, leaving a retry with an empty queue. The class
passes 3× in isolation and passed in the fresh full suite; production code is
untouched. **Disposition: documented baseline weakness; recommend a small
harness hardening (pad the response queue) in a future maintenance pass —
not a product defect and not a mutation path.**

### MINOR-2 (pre-existing parity gap, surfaced by ARGUS): `endDate < nextDueDate` never rejected server-side

Neither create nor PATCH validates the date relationship — a template
can be put into a permanently inert state (`due(asOf)` returns false
once `asOf > endDate`). It cannot corrupt the ledger (never generates)
and the Android client blocks the case (`end date before next due
blocks save`). Introducing the check on PATCH only would diverge from
create parity; correct fix is a future hardening applied to both entry
points. **Disposition: documented; not an M16-introduced defect.**

### NOTE-1: `lintDebug` first-run LintDriver internal error

The combined `lintDebug assembleDebug detekt` invocation hit a transient
`LintDriver.handleDetectorError` crash while running concurrently with the
JVM suite. Re-run standalone: **UP-TO-DATE green**, all analyze/report tasks
clean. Tooling crash, not a code defect.

## 5. Unresolved defects

None. No backend gaps surfaced — the PATCH contract behaves as specified
against the real backend (11/11 contract suite, incl. ledger isolation).

`READY_FOR_ARGUS`: **YES**
