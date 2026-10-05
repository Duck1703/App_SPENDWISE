# M9 Sentinel Review — Ledger Export

```
VERDICT: PASS (0 blocker / 0 major / 3 minor notes)
SCOPE: app diff f1f8e3e..working-tree — TransactionsService.export,
  LedgerExportRepository + ExportFileStore + ContentResolverExportStore,
  ExportLedger{UiState,ViewModel,Screen,Destination}, LedgerExportRoute,
  More entry, strings, fakes + tests.
BACKEND: 8194daa re-verified — GET /api/transactions/export,
  text/csv + attachment disposition, accountId optional, foreign→404.
```

## Contract fidelity

- Path/method/query verified end-to-end by MockWebServer contract
  tests: `GET /api/transactions/export`, `accountId` **omitted**
  (asserted `query == null`) for all-accounts, exact value otherwise;
  bearer propagated through the real Auth/Error interceptors; 401 →
  `Session.Expired`; 404/500 → `ApiException` → `AppError`.
- Media-type honesty: a non-`text/csv` 2xx body is rejected as
  `Unknown` and never saved — no HTML error page masquerading as CSV.

## Streaming / byte integrity

- `@Streaming` + `Response<ResponseBody>`; single
  `byteStream()→file.outputStream()` copy; destination copy streams
  `file→ContentResolver.openOutputStream`. No `.string()`/`.bytes()`/
  `readBytes()` on the artifact path anywhere.
- Byte-identity proven on JVM (UTF-8 Vietnamese, commas, quotes, CRLF,
  big decimals) AND on-device via `ContentResolverExportStoreTest`
  (real `file://` destination write).

## Temp file & cleanup

- `cacheDir/ledger-export/` — app-private, excluded from Auto Backup
  (cacheDir is never backed up), not media-indexed.
- Delete on: save success, save failure, picker cancel, generation
  boundary (repository), VM `onCleared`, **and** orphan sweep on screen
  entry (process-death case — fixed during review: `discardOrphans`
  was exposed-but-uncalled; now invoked in `init`).

## Filename

- `Content-Disposition` parsed defensively: `filename*=`/quoted/bare
  forms; basename-only (traversal stripped); control chars removed;
  `.csv` enforced; 100-char bound; `spendwise-ledger.csv` fallback —
  10 unit cases green.

## Session / privacy

- Generation captured pre-request; a response landing after a sign-in
  boundary is deleted + `SessionEndedException` — test-proven
  (sign-in-to-user-B mid-flight → artifact discarded, never published).
- Cancelled/left-screen artifact never survives: modal phases +
  `onCleared` delete.
- No CSV content logging anywhere; only `sizeBytes`/suggested name
  surface to the VM.

## Error honesty

- `ExportSaveException` distinguishes *destination-write* failure from
  download failure — the UI says "Couldn't save the file", never
  claims export success before the copy completes.
- 404 scope error surfaces honestly; no silent fallback to all-accounts.
- GET is side-effect-free: explicit retry only, no auto-replay.

## Findings

| Sev | Item | Disposition |
| --- | --- | --- |
| MINOR | `sizeBytes` carried but not displayed | Accepted — informational field, may serve a later size hint |
| MINOR | `runBlocking` in `onCleared` | Accepted — bounded metadata delete on teardown; documented |
| MINOR | Download phase not user-cancellable | Consistent with M5 import's modal-transit contract |
| NOTE | Save-request relaunch on rotation while picker open | Correct-by-design: system picker dies with the activity, artifact survives in the VM |

## Regression

M0–M8 untouched except additive `More` row + route registration;
462/462 JVM, 153/153 connected (Pixel 4 XL, API 33), release R8 clean,
28/28 serializers (new route is `data object` — correctly needs none),
manifest: INTERNET only, no storage permission, 0 exported-component
changes.
