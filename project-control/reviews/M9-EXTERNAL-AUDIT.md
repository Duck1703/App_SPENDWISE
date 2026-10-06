# M9 External Audit — Ledger Export

```
AUDITED_RANGE: 43f7a6b7507c0135ba0f493df07e9c0385ca30a7..406ed8b030b375fedab4ea5556616d3ceb95cea6
  (+ remediation on top, see §Findings)
BACKEND: D:\spendwise @ 8194daa9959f2d568dc13ab0f211787195efc79d (unchanged, source-verified)
INITIAL_VERDICT: FINDINGS (0 blocker / 1 major / 1 minor / notes)
FINAL_VERDICT: ACCEPTED after bounded remediation — all gates green.
METHOD: independent source review, falsification attempts, fresh gate
  runs — Sentinel/Argus read only AFTER the initial verdict.
```

## Backend contract (independently derived from source)

`CsvExportController` @8194daa: `GET /api/transactions/export`,
`@RequestParam(required=false) UUID accountId` — omitted →
`exportForUser(ownerId)`; present → `accountService.find(id, ownerId)`
(`findByIdAndUserId`, owner-scoped) empty → `404`. `200 text/csv`,
`Content-Disposition: attachment; filename="transactions.csv"`.
`CsvExportService` columns: `occurredOn,type,amount,currency,category,sourceRef`,
`toPlainString()`, RFC4180 quoting, `\n` endings, `@Transactional(readOnly)`.
**CONTRACT_MATCH=YES — every claimed property verified in source.**

## Diff classification (26 files — all reviewed, none unexplained)

NETWORK(1) REPOSITORY(1) VIEWMODEL/UI(4) NAV(3) MORE(2) SHARED_INFRA
(DataModule, strings) TEST(9) GOVERNANCE(4). No scope leakage: no
import history/pointers, FX, dates, mutations, deep links, M10.

## Falsification results

- **Streaming**: `@Streaming`+`Response<ResponseBody>`; artifact path is
  `byteStream().use{ copyTo(file) }` → `file.inputStream →
  ContentResolver.openOutputStream`. No `string()/bytes()/readBytes()`.
  3 MB synthetic body streams byte-identical (added regression).
- **Byte identity**: UTF-8/CRLF/quotes/decimals — asserted at temp-file
  AND destination level, JVM + device.
- **CSV opacity**: zero parsing/domain mapping — `occurredOn` never
  surfaced (greps + full file reads).
- **Filename**: traversal/dot/control/overlong/missing-ext all fail
  safe to `spendwise-ledger.csv` (10 unit cases); suggested name is
  never a filesystem path (`createTempFile` random names).
- **401/404/500/network**: interceptor-thrown `ApiException` → mapped
  AppError; 401 flips `Session.Expired`; non-`text/csv` 2xx rejected.
- **SAF**: `CreateDocument("text/csv")`, one-shot `SaveRequest` keyed
  effect, cancel = silent cleanup, success only after completed copy.

## Findings (all remediated or deferred with rationale)

### EXT-M9-MAJOR-1 — Orphan sweep could delete a live artifact

`ContentResolverExportStore.discardOrphans()` unconditionally deleted
every file in `cacheDir/ledger-export/`; invoked on every VM init.
`MoreRow` is a plain `clickable` and `navigate(LedgerExportRoute)` uses
no `launchSingleTop` → a double push stacks two live ViewModels → the
second's entry sweep could delete the first's in-flight or
ready-for-save artifact (user's export silently disappears → spurious
save failure). Also: files were unregistered for their entire
streaming duration.

**FIX**: live-artifact registry in `ContentResolverExportStore` —
`createTempFile`+registration atomic under `synchronized(liveArtifacts)`;
sweep deletes only non-registered files under the same lock; `discard`
unregisters. Regression: `discardOrphans_never_deletes_a_live_artifact`
(androidTest, real store). **RESOLVED.**

### EXT-M9-MINOR-1 — `save()` not generation-bound

A ready artifact wasn't bound to its producing session generation;
same-identity resume (stack preserved by design) could save a
pre-expiry artifact — spec prefers conservative discard. Cross-identity
was already unreachable (`popUpTo(0)` + `onCleared` delete).

**FIX**: `boundGeneration` bound at download; `save()` rejects +
deletes + `SessionEndedException` on mismatch. Regression:
`save rejects an artifact from a dead session generation`. **RESOLVED.**

### EXT-M9-NOTE-1 — `onDestinationChosen` missing phase guard

**FIX**: early return unless `Phase.AwaitingDestination`; regression
`a second destination callback while saving is ignored`. **RESOLVED.**

### Deferred / informational

| Sev | Item | Disposition |
| --- | --- | --- |
| MINOR | Account-list failure blocks even all-accounts export (list isn't a contract prerequisite) | DEFERRED — retry path exists; UX-only |
| NOTE | `filename*=` value used literally (no percent-decode) | safe: suggested name only |
| NOTE | Unicode format chars (U+202E) not stripped | display-spoof surface only; no path use |
| NOTE | `sizeBytes` unused; `runBlocking` in `onCleared`; indeterminate progress | consistent with conventions |

## Evidence (all independently re-run post-remediation)

- `testDebugUnitTest`: **465/465** (462 + 3 new regressions).
- Connected Pixel 4 XL (API 33): **RUN#1 154/154, RUN#2 154/154**
  (153 + new live-artifact test). Post-run: no Spendwise package
  installed → zero residual artifacts possible.
- `lintDebug` PASS, `detekt` PASS, `assembleDebug` PASS,
  `assembleRelease` PASS (fresh APK `da37bd01…f7a2d90`, 2199378 B).
- Serializers: source-derived 28 required (21 wire + 7 data-routes);
  release DEX contains 28 — **0 missing**. `LedgerExportRoute` is
  `data object` → correctly none.
- Merged release manifest: `INTERNET` + synthetic receiver perm only;
  `usesCleartextTraffic=false`; backup rules present; `cacheDir` is
  framework-excluded from backup regardless.
- Internal-review comparison: Sentinel confirmed except it missed the
  sweep race it itself introduced the fix for; Argus UX claims all
  verified.
