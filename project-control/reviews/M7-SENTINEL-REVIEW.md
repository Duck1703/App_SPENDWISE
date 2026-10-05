# M7 SENTINEL Review — Hardening (a11y, perf, error polish, release config)

Reviewer role: **SENTINEL** (test engineer / verification), per
`agent-system/agents/sentinel.md`. Scope: verify the M7 hardening pass
is real, correct, and regression-free across all shipped surfaces —
not a feature slice (canonical scope: ARCHITECTURE §34 row M7).

## Changes under review

| # | Change | Risk surface |
| --- | --- | --- |
| 1 | `proguard-rules.pro` — canonical kotlinx-serialization keeps (`@Serializable` Companion + `serializer()` members, `**$$serializer` names+descriptors, `kotlinx.serialization.json.**` companions) | release-only |
| 2 | `versionName` `0.1.0-m0` → `1.0.0` (V1 surface complete; signing stays Human-owned per Q4) | none |
| 3 | `CategoryChoice` — `clickable` → `selectable(selected, role=RadioButton)` | semantics only |
| 4 | `TransactionsScreen` — grouped card `Column` → lazy `items` + `key={it.id}` + `contentType`, per-row `SwCard` | S09 list render |
| 5 | `AccountDetailScreen` — `Column.verticalScroll` → `LazyColumn`; history rows lazy `items` + keys; `HistorySection` split into `HistoryHeader`/`HistoryError` | S08 render |
| 6 | `ImportScreen.ImportResult` — `Column.verticalScroll` → `LazyColumn`; failures + expanded rows lazy `items` with `"fail-"/"row-"` prefixed keys | S14 render |
| 7 | `MoreScreen` — root `Column` + `verticalScroll` (font-scale clip fix) | S24 scroll |
| 8 | `.imePadding()` on six form scroll containers (create txn/account/budget/recurring/rule, import form) | keyboard overlap |

## Verification evidence

### Release-build serialization — binary-verified, not assumed

- `app-release-unsigned.apk` **before** rules: `$$serializer` string
  occurrences in DEX = **0** → reflective `serializer(Type)` lookup
  (Retrofit converter path) would fail at runtime.
- **After** rules: `$$serializer` occurrences = **28**, and every
  expected name is present verbatim — `ProblemDetailDto$$serializer`
  (`core/network`), all 20 `data/remote` DTO serializers, all 7
  typed-route serializers (`AccountDetailRoute`, `BudgetDetailRoute`,
  `LoginRoute`, `RecurringDetailRoute`, `RuleDetailRoute`,
  `TransactionCreateRoute`, `TransactionDetailRoute`).
- `assembleRelease` + R8: PASS post-change.
- **Regression guard added**: `ReleaseHardeningTest` (3 JVM tests)
  fails if the keep lines are ever removed — the failure mode is
  invisible to compile gates, so the guard is load-bearing.

### Lazy-list conversions — correctness

- `TransactionsScreen`: `items(key={it.id})` — transaction ids are
  unique server ids; `contentType="transaction"` enables pool reuse.
  Row visuals: per-row `SwCard` (same card language as Home carousel);
  row `contentDescription`s unchanged → existing Compose assertions
  still semantically valid.
- `AccountDetailScreen`: `LazyColumn` items keyed (`hero`, `fields`,
  `history-*`, transaction ids, `bottom-space`); account name binding
  preserved; `HistorySection` split is purely structural.
- `ImportScreen`: `rowsExpanded` hoisted to `ImportResult` scope —
  survives item recycling; key prefixes prevent collisions between
  the two row lists sharing the LazyColumn keyspace; failure rows now
  tint `Expense` (error polish — was `Muted`).
- Bounded lists unchanged: accounts list, planning lists, Home
  activity (`take(MAX_ACTIVITY_ROWS)` client cap — verified in
  `HomeViewModel.fetchActivity`).

### a11y additions

- `CategoryChoice` → `Modifier.selectable` provides `selected` state +
  `Role.RadioButton` + click semantics — TalkBack now announces
  selection in the single-choice category picker.
- `imePadding` after `verticalScroll` on all six form surfaces: the
  viewport pads for the IME so the focused field + submit remain
  reachable under edge-to-edge (insets are not auto-applied with
  `enableEdgeToEdge`). Auth forms already had this.
- `MoreScreen` now scrolls — sign-out can no longer clip at large
  font scales.

### Sweeps with no findings (verified, not assumed)

- `collectAsStateWithLifecycle` on **every** state collection (0 raw
  `collectAsState`); Timber `DebugTree` debug-only; zero
  `println`/`Log.`/raw-`e.message` in UI; every `IconButton` labeled
  + ≥48dp; `SwBanner` LiveRegion (Assertive=Error/Polite=Info);
  `SwSkeletonRows` merged loading CD; all empty states scrollable
  inside `PullToRefreshBox`; `BackHandler` consumed on session-expired.
- Backend contract re-verification at `8194daa`: all 12 consumed
  endpoint groups match controller mappings + `@RequestParam` names;
  backend-only surfaces (`/api/fx/convert`, `/api/transactions/export`,
  `/api/import-report`) correctly remain unconsumed.
- Manifest hardening re-verified: `usesCleartextTraffic=false`,
  `datastore/` excluded from cloud-backup + device-transfer (token
  store), `INTERNET` only, exported activity scoped to launcher.

## Gate results

```
testDebugUnitTest=422/422 PASS (was 419 at M6 + 3 hardening guards)
compileDebugKotlin/compileReleaseKotlin=PASS
compileDebugAndroidTestKotlin=PASS (all existing Compose tests compile
  against the new structure — assertions were semantic, not structural)
assembleRelease=PASS (R8 + shrinkResources + new keeps)
connectedAndroidTest=NOT_RUN (adb devices = 0)
M0–M6 REGRESSION=PASS (no ViewModel/repository/contract touched;
  UI-state models unchanged)
```

## Findings

None open. The eight changes are hardening-only: no state-machine,
repository, navigation-graph, or contract change; no new dependencies;
backend/reference untouched.

```
M7_SENTINEL=PASS
BLOCKER=0
MAJOR=0
MINOR=0
```
