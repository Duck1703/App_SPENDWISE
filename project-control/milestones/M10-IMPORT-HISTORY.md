# M10 — Server-Backed Import History

```
STATUS: DELIVERED (pending Human review)
M10_HUMAN_AUTHORIZATION: EXPLICIT — Human authorized "M10 — Server-Backed
  Import History" as a large delivery block including a bounded backend
  write for REQ-BE-004 (`GET /api/imports` batch list).
BASELINE: ac46d6e (post-M9-remediation)
BACKEND: D:\spendwise @ 401e99c "feat: add owner-scoped import batch
  list endpoint (REQ-BE-004)" — bounded write, imports package only,
  no schema change. User-owned README/docs left uncommitted there.
```

## Human decisions (authoritative)

- `M10 = Server-backed import history` — the single user outcome.
- Backend write authorized for REQ-BE-004 only (read path; no
  mutations, no schema migration, no other backend surface).

## User outcome

A signed-in user opens **More → Import history** and sees the
server-truth list of their imported statements — filename, import
date, account, and outcome counts — newest first. A row opens the
full per-batch report (the same `ImportBatchSummaryResponse` the
one-shot result screen renders). Empty history is a real state with
an "Import a statement" escape; foreign/unknown batch ids read as
NotFound, never forbidden.

## Backend contract delivered (REQ-BE-004)

- `GET /api/imports` — authenticated (`/api/**` default), owner-scoped
  **in the query** (`findAllByUserIdOrderByImportedAtDesc` — never
  filtered after the fact, R24/R25), ordered by persisted
  `importedAt` DESC.
- Response: `List<ImportBatchListItem>` —
  `{batchId, accountId, fileName, importedAt, successCount,
  duplicateCount, failedCount}` — **no row payloads**; empty → `200 []`.
- Counts come from one grouped query
  (`ImportRowRepository.countByBatchIdIn`) — no N+1, no per-batch row
  hydration.
- Existing `GET /api/imports/{id}` unchanged — the detail contract.
- Backend tests: `ImportHistoryListTest` — empty→`200 []`, 401,
  ownership isolation, ordering, grouped counts, no `rows` key.

## Architecture decisions (M10-local)

- **Summary ≠ report**: `ImportBatchSummary` (no `rows`) models the
  list contract; `ImportBatch` stays the detail/upload report shape.
  `totalCount` derives from the three counts — the wire has no
  `totalCount` field.
- **Latest-wins loads** (`loadSerial`) + session-generation guard in
  the repository — a stale-generation result is discarded, never
  published (same contract as Recurring list).
- **Read-only surface** — no delete/re-run exists on the backend;
  the screens expose none.
- **Account names are display-only context** — a lookup failure
  degrades to "Unknown account", never blocks the list.

## Scope exclusions

Import pointer store, this-device-only history, batch delete,
re-import/re-run actions, pagination (`GET /api/imports` is
unpaged — same honest bound as the transaction list), FX, dates,
mutations, refresh token.

## IA / UX

`More → Import history` row (between "Import statement" and
Recurring; `MoreRow` pattern) → `ImportHistoryRoute` → row tap →
`ImportHistoryDetailRoute(batchId)`. List: `SwHeader` +
pull-to-refresh + `SwCard` rows (icon · filename ·
`date · account · counts` · chevron). Detail: filename + meta,
three outcome tiles (same visual language as the import result
screen — `ResultTile`/`ImportRowLine` reused as `internal`), full
per-row report. Empty state offers "Import a statement" → `ImportRoute`.

## Quality gates (M10 acceptance)

- Backend: `ImportHistoryListTest` (5) + full suite green
  (pre-existing `RecurringSchedulingJobTest` timing flake noted,
  unrelated to imports).
- Contract tests (MockWebServer): `list`/`detail` paths, summary
  decode incl. unknown keys, `[]` → empty, 401→Expired, 404.
- Repository tests: summary/report mapping, empty history,
  network/404 mapping, dead-generation discard.
- ViewModel tests: state machine, refresh-error banner, latest-wins,
  session resume, account-name degradation.
- `testDebugUnitTest`, `lintDebug`, detekt, `assembleRelease`,
  serializer scan (new `@Serializable` routes), connected suite on
  the physical Pixel 4 XL if available.

## Quality-gate evidence

- **Backend (D:\spendwise @ 401e99c)**: `ImportHistoryListTest` 5/5 —
  empty→`200 []`, unauthenticated→401, ownership isolation, newest-first,
  grouped counts, no `rows` key. Full suite: 285 tests, 1 failure —
  `RecurringSchedulingJobTest` timing flake, identical at the 280-test
  pre-change baseline; unrelated to imports.
- **JVM**: `testDebugUnitTest` 489/489 (46 files), 0 failures — +24 M10
  tests (contract, repository, fakes, both ViewModels, route
  serialization).
- **Static**: lint clean; detekt 210 files, 0 smells; `assembleDebug`,
  `assembleRelease` (unsigned) and `compileDebugAndroidTestKotlin` green.
- **Connected**: 158/158 on `Spendwise_M8_Acceptance` AVD (API 36),
  incl. `ImportHistoryScreenTest` 4/4 (loading/empty/content+tap/
  network-error/back). Note: the physical Pixel 4 XL (99041FFBA006F0)
  was locked at run time — secure keyguard stopped every activity before
  `setContent`, a device state issue, not an app regression; `wm
  dismiss-keyguard` cannot pass a PIN. Rerun on the emulator was clean.
  One earlier run reported an `ActivityScenario` DESTROYED-timeout flake
  in `AccountsScreenTest.rowClick_opensThatAccount`; the isolated rerun
  passed 10/10.
- **Manifest/serializer**: no manifest change; new `@Serializable`
  route objects covered by generic keep rules + `AppRoutesTest`
  round-trips.

## Status

Delivered pending Human review — all M10 gates green on emulator;
physical-device run pending unlock (see connected note above).
