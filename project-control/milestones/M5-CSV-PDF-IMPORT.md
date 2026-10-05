# M5 — CSV / TPBank PDF Import

Status: **READY FOR EXTERNAL AUDIT**
(`M5_STARTED=YES`; `M5_TECHNICAL_COMPLETE=YES`;
`M5_READY_FOR_EXTERNAL_AUDIT=YES`; `M5_HUMAN_APPROVED=NO`)

## Authorization

M4 closed by the conditional-overnight closeout `e325a51` recording
`M4_HUMAN_APPROVED=YES` and `M5_AUTHORIZED_TO_START=YES` after the
independent M4 audit passed its gate conditions
(`BLOCKER=0`, `MAJOR=0`, all five correctness axes PASS).

M5 scope per ROADMAP + PRODUCT §23–25 + P3A §5–6:
**CSV / TPBank PDF Import (select/upload/result)** — the full
vertical slice: choose account → choose file → format truth →
synchronous upload → one-shot result.

## Sources of truth

- [../PRODUCT.md](../PRODUCT.md) §23 (import entry + flow), §24
  (formats: exact CSV schema + TPBank machine-readable PDF), §25
  (result semantics), §16 (session expiry), §52/PD-012
  (outcome-ambiguous interruption).
- [../P3A-DESIGN-HANDOFF.md](../P3A-DESIGN-HANDOFF.md) §5 (Home
  secondary entry — the import quick-card deferred from M4), §6
  (More primary entry; choose → file → processing → result; calm
  duplicates; generic PDF-reject copy).
- [../BACKEND-CONTRACT.md](../BACKEND-CONTRACT.md) §16 (import
  endpoints + failure surface + the structural-CSV 500 drift note).

## Backend contract verified (source, read-only) — `8194daa`

`POST /api/imports?accountId=<UUID>` (`ImportController`):

- `multipart/form-data`, one required `file` part; `accountId` as
  **query** param.
- Success: **201** + `Location: /api/imports/{batchId}` +
  `ImportBatchSummaryResponse` `{batchId, accountId, fileName,
  importedAt, successCount, duplicateCount, failedCount,
  rows[{rowNumber, status∈{OK,DUPLICATE,FAILED}, reason,
  transactionId}]}`.
- Format dispatched on the original filename's lower-cased
  extension — `.csv` / `.pdf` only (extension must survive in the
  multipart filename).
- Foreign `accountId` → **404** (ownership check before import).
- Unsupported extension / parse failure → **400**; structural CSV
  failure / oversize / mid-loop crash → **500** (drift documented in
  §16 — controller comments claim 400; the client treats the 5xx as
  outcome-ambiguous, never assumes 400).
- **Synchronous**: the server parses + commits the file before
  answering; mid-loop failure can partially commit — re-upload of the
  same file is safe (server dedups by `sourceRef`).
- `GET /api/imports/{id}` exists (owner-scoped report) — **not used**:
  the one-shot result is the report; there is no batch list.
- Spring Boot default multipart caps (no config override at
  `8194daa`): **1 MB per file**, 10 MB per request → oversize → 500.

## Scope

- `data/remote/ImportDtos.kt` + `ImportsService.kt` — wire-faithful
  `ImportBatchSummaryDto`/`ImportRowDto`, `@Multipart POST
  api/imports` + `@Query("accountId")` + `@Part file`,
  `ignoreUnknownKeys` tolerant.
- `core/model/ImportBatch.kt` — `ImportBatch`/`ImportRow`/
  `ImportRowStatus{OK,DUPLICATE,FAILED,UNKNOWN}` (`of()` — unknown
  future statuses degrade, never crash).
- `data/repository/ImportRepository.kt` — `ImportRepository`
  (`describe`/`upload`), `RemoteImportRepository`,
  `StatementStore`/`ContentResolverStatementStore` (the
  ContentResolver seam — `describe` on `Dispatchers.IO`, streamed
  `writeTo`, `filename=` preserved, `contentLength` from the picker),
  `StatementUnreadableException` (local read failure — never claimed
  ambiguous), generation guard + account invalidation +
  `notifyLedgerChanged` on success.
- `TransactionRepository.notifyLedgerChanged()` — emits the existing
  `mutations` signal for server-side writes outside `create` (imports
  refresh Home activity + Transactions through the same path).
- `feature/imports/` — `ImportUiState` (`Loading`/`Empty`/`Form`
  /`Result`/`Error` + `ImportError`), `ImportViewModel` (account load,
  extension + 1 MB client gates, double-submit guard, ambiguous-
  outcome honesty, `expiredMidLoad`/`onVisible` resume), `ImportScreen`
  (S12 form + format-truth block, S13 swapped-in modal processing
  surface, S14 counts-first result), `ImportDestination` (SAF
  `OpenDocument` CSV/PDF/octet-stream, `BackHandler` while uploading,
  `ON_RESUME` re-load).
- `navigation/` — `ImportRoute` (pushed over the preserved shell),
  `SpendwiseMainShell` + `MainFlow` wiring.
- `feature/more/` — the primary entry row (P3A §6).
- `feature/home/` — `ImportQuickCard` secondary entry (P3A §5, the
  M4-deferred card).
- `res/values/strings.xml` — the import copy block.

## Design decisions (resolved during implementation)

- **One destination, three surfaces**: choose → processing → result is
  one pushed route with an in-VM state machine — S13 replaces the
  form entirely (`isUploading`), so no underlying control stays
  reachable and system back is blocked via `BackHandler`.
- **No GET report call**: `POST` already returns the full report;
  `GET /api/imports/{id}` is left unused (no history screen exists).
- **Ambiguous-outcome honesty (PD-012)**: network drop / 5xx /
  session-expired / dead-generation during upload restore the form
  with the ambiguous notice instead of claiming failure — a manual
  re-upload is safe by server dedup; the client **never auto-retries**
  the non-idempotent POST (transport `retryOnConnectionFailure` is
  off app-wide too).
- **Client gates mirror the server**: extension check (`.csv`/`.pdf`
  on the display name) + the real 1 MB file cap — honest, local
  reasons, before any byte leaves.
- **`android.net.Uri` removed from all shared signatures** — the
  `SelectedStatement.uri` is the string form; the seam parses it
  back, so repository + ViewModel stay plain-JVM testable (no
  Robolectric, no `Uri` mocking).
- Unknown row statuses decode to `UNKNOWN` and never land in the
  Imported tile (counted in file-total only).

## Forbidden scope

- No OCR, scans, photos, password-protected, or arbitrary-bank PDFs —
  the copy says so verbatim.
- No import history/batch-list screen (no backend list endpoint).
- No progress percentage (the contract gives none — indeterminate
  only).
- No client-side CSV/PDF parsing, no client ledger writes, no balance
  math — the server is the sole importer.
- No auto-retry of the upload; no cancel affordance mid-upload.
- No M6 surfaces (recurring, budgets, rules).

## Acceptance criteria

- [x] `POST /api/imports?accountId=` + multipart `file` with the
  original filename — wire-verified via MockWebServer.
- [x] Account picker (first preselected, currency shown); zero
  accounts → create-account state.
- [x] File picker (SAF); extension + size gates honest, client-side.
- [x] Format-truth block: exact CSV header (no spaces, no BOM) +
  TPBank-PDF-only truth (no scans/OCR/password/foreign banks).
- [x] Synchronous processing surface — modal, indeterminate, no back
  or cancel, a11y-announced.
- [x] Result: file-name + `Rows in file: N` + Imported/Already
  imported/Couldn't import tiles + failure reasons; all-duplicates →
  "Nothing new to import"; all-failed → format guidance; empty file →
  honest zero state.
- [x] `Done` returns to the origin; account cache invalidated +
  ledger mutation signal → Home/Transactions reload.
- [x] 400→unsupported-file, 404→account-unavailable, 5xx/network →
  form restored with the ambiguous banner; local read failure →
  generic, never ambiguous.
- [x] Session expiry mid-flow → overlay + restored form; same-user
  resume → account reload, never a replay; dead generation →
  `SessionEndedException`, nothing published.
- [x] Double-submit can never issue a second POST.
- [x] Unknown row status → `UNKNOWN`, never an Imported count.

## Required tests

- `ImportsServiceContractTest` (8) — path/query/part-name/filename/
  201 decode/unknown-keys/empty report/400/404/500/401-no-retry.
- `ImportRepositoryTest` (9) — mapping, UNKNOWN fallback, invalidation
  + ledger signal, 400/404/500 mapping, dead-generation
  `SessionEndedException`, describe delegation, unreadable →
  `StatementUnreadableException`, malformed `importedAt` → Unknown.
- `ImportViewModelTest` (18) — load states, file gates, submit flow,
  double-submit guard, all error mappings, ambiguous honesty, session
  expiry + dead-generation resume.
- `ImportScreenTest` (13, androidTest) — S12/S13/S14 states, callbacks,
  merged-CD tiles, all-duplicates/all-failed/empty/UNKNOWN states.
- `MoreScreenTest` + `HomeScreenTest` — entry-row callbacks.
- `AppRoutesTest` — `ImportRoute` serialization.

## QA criteria

- Contract fidelity: `?accountId=` is the only param; `file` is the
  only part; filename preserved; no invented fields/endpoints/retries.
- Financial correctness: zero client arithmetic — counts are the
  server's; no balances computed; no currency assumptions.
- Session/privacy: generation guard on the one publish point; no
  stale-identity result; resumed state never replays.
- P3A fidelity: More primary + Home secondary entries; the
  §23–25 copy constraints (calm duplicates, generic PDF-reject,
  blocking process, counts-first result).

## Stop conditions

Beyond the global contract: if `POST /api/imports` semantics differ
when re-verified (response shape, status map, sync behavior) →
BACKEND GAP report, stop the affected slice.

## External-audit gate

```
M5_SENTINEL=PASS (0 blockers — see reviews/M5-SENTINEL-REVIEW.md)
M5_ARGUS=PASS (0 blockers, 0 majors — see reviews/M5-ARGUS-REVIEW.md)
M5_JVM_TESTS=338/338 (37 M5-scoped: 8 contract + 9 repo + 18 VM + 1 route + 1 navigation-adjacent)
M5_CONNECTED_INSTRUMENTATION=NOT_RUN_NO_DEVICE (adb devices = 0; androidTest compile-verified, 14 new Compose tests)
M5_GATES=assembleDebug+testDebugUnitTest+lintDebug+compileDebugAndroidTestKotlin+assembleRelease+detekt: PASS
M5_LINT=informational warnings only, 0 errors
M5_DETEKT=0 code smells
M5_READY_FOR_EXTERNAL_AUDIT=YES
M5_HUMAN_APPROVED=NO
M6_STARTED=NO
SAFE_TO_AUDIT=YES
```

## Human gate

`M5_EXTERNAL_AUDIT_VERDICT=ACCEPTED` — independent external audit ran on
`dd74ba6` (see [reviews/M5-EXTERNAL-AUDIT-SUMMARY.md](../reviews/M5-EXTERNAL-AUDIT-SUMMARY.md)):
0 blockers / 0 majors / 2 minors (pick race, banner anchoring) —
remediated at `0db8b3a` and post-remediation falsification verified
(20/20 import VM tests, contract/repo/route suites green, detekt clean).
Human-approved conditional closeout authorized M6 to proceed;
`M5_HUMAN_APPROVED` remains `NO` until THE HUMAN signs off.

## Scope exclusions honored

No import history, no OCR/scan/foreign-bank claims, no progress %,
no cancel/back mid-upload, no auto-retry, no client-side parsing,
no balance arithmetic, no M6 surfaces, no new dependencies, no
backend/reference/prototype edits (backend `8194daa`, reference
`5f9622c` unchanged).
