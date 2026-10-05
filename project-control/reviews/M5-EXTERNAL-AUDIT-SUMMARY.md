# M5 External Audit Summary — CSV / TPBank PDF Import

Independent external audit of the M5 vertical slice, per the overnight
pipeline contract. Audit performed **read-only** on the committed range
before any remediation.

## Audit scope

- **Audited HEAD**: `dd74ba686ae9ae2a115b40665012c201f50854e6`
- **Audited range**: `e325a51` (M4 human closeout) .. `dd74ba6`
- **Branch**: `main`, worktree clean at audit start.
- **Backend verified at**: `8194daa9959f2d568dc13ab0f211787195efc79d`
  (read-only; `ImportController`, `ImportService`,
  `ImportBatchSummaryResponse`, `ImportRowDetail`, `ImportRowStatus`,
  `ApiExceptionHandler`, `StatementParseException` inspected at source).
- **Canonical scope**: ROADMAP M5 — "CSV / TPBank PDF Import
  (select/upload/result)". No M6 leakage found in the diff (no
  budget/recurring/rules surfaces; no new dependencies).

## Findings

### Verdict: `M5_INITIAL_EXTERNAL_AUDIT=FINDINGS`

| ID | Severity | Finding |
| --- | --- | --- |
| EXT-M5-MIN-1 | MINOR | **Pick race — stale `describe` can overwrite a newer selection.** `onFilePicked` launches the provider `describe` without a sequence guard; a slow provider response for pick N can land after pick N+1 and publish the older file. Latest-wins violation (same class as `loadSeq` elsewhere). |
| EXT-M5-MIN-2 | MINOR | **All-duplicates/all-failed banners not row-total-anchored.** Banners keyed on `successCount == 0 && failedCount == 0` / `successCount == 0` rather than `count == totalRows` — a forward-compat `UNKNOWN` row (already modeled client-side) would wrongly trigger "Nothing new to import". |
| EXT-M5-NOTE-1 | NOTE | Provider returning no `DISPLAY_NAME` falls back to `"statement"` (no extension) → extension gate rejects — honest "unsupported file" for a pathological provider; consistent with the server's own extension dispatch on the original name. |
| EXT-M5-NOTE-2 | NOTE | Dead-generation/401 ambiguous banner is dropped on a same-user resume rebuild (form reloads clean) — the banner is advisory-only and the write was never claimed committed; recorded as a design note. |

## Backend contract fidelity — verified at source

| Contract point | Backend truth (`8194daa`) | Client behavior | Verdict |
| --- | --- | --- | --- |
| Endpoint | `POST /api/imports`, `accountId` + `file` as `@RequestParam`s | `@Multipart @POST("api/imports")`, `accountId` as `@Query`, `file` as `@Part` | PASS |
| Multipart part name | `file` | `MultipartBody.Part.createFormData("file", …)` | PASS |
| Original filename | suffix of `getOriginalFilename()` drives format dispatch | provider `DISPLAY_NAME` sent verbatim; no fabricated name | PASS |
| Response | `ImportBatchSummaryResponse{batchId,accountId,fileName,importedAt,successCount,duplicateCount,failedCount,rows[{rowNumber,status,reason,transactionId}]}` — counts derived from rows | field-for-field DTO; `ignoreUnknownKeys`; `status` → `ImportRowStatus.of` with `UNKNOWN` armor; **no invented `totalCount`** (`totalRows = rows.size`) | PASS |
| Structural CSV failure | `CsvStatementParser` `IOException` → generic `@ExceptionHandler(Exception)` → **500** (controller comment claims 400 — backend comment is stale; code truth is 500) | 5xx → `Server` + ambiguous banner | PASS (client never trusts the stale comment) |
| Bad PDF / bad extension | `StatementParseException extends DomainException` → 400 | 400 → `UnsupportedFile` | PASS |
| Foreign `accountId` | `accountService.find` pre-check → 404 | 404 → `AccountUnavailable` | PASS |
| Missing params | `MissingServletRequestParameterException` → 400 | client always sends both | N/A |
| Oversize | `MaxUploadSizeExceededException` → 500 (Boot defaults: 1 MB file / 10 MB request — no override in `application.properties`) | 1 MB client gate + 5xx ambiguous if server still rejects | PASS |
| Per-row failure | recorded `FAILED` row in 201 body, never thrown | status rows rendered verbatim; reasons are server-supplied domain strings | PASS |

## Areas audited — all PASS unless finding above

| Area | Evidence |
| --- | --- |
| Streaming, no full-buffer | `RequestBody.writeTo` → `sink.writeAll(input.source())`; no `readBytes()`/`ByteArrayOutputStream` anywhere on the path |
| No Android-side parsing | no opencsv/pdfbox/itext/tesseract dependency or reference in `app/build.gradle.kts`, `libs.versions.toml`, or `main` sources |
| SAF / ContentResolver | `OpenDocument` launcher; metadata query + stream open on `Dispatchers.IO`; no `takePersistableUriPermission`; no `MANAGE_EXTERNAL_STORAGE`/`READ_MEDIA` |
| Local read-error classification | `toPart` construction failure → `StatementUnreadableException` → `Generic` (non-ambiguous); mid-body `IOException` → `NetworkUnavailable` + ambiguous (byte-0 vs mid-body indistinguishable client-side — conservative-honest) |
| Extension gate | `.csv`/`.pdf`, `lowercase()` — identical semantics to the server's `endsWith` on the original name |
| Size gate | `1_048_576L` = Boot default file cap; `sizeBytes = -1` (unknown) → `contentLength() = -1` → chunked — never rejects an unknown-size file |
| Session generation | `sessionGeneration` captured before POST, checked after response; dead-generation → `SessionEndedException` → form restored with ambiguous banner, `expiredMidLoad` armed |
| 401 | `ErrorInterceptor` → `Session.Expired` → ambiguous banner (bytes may have committed); never auto-replayed; `onVisible()` → reload only |
| Same-user resume | overlay pops → `onVisible` → `retry()` reloads accounts; no POST replay |
| Different-user reset | auth success rebuilds the nav graph → VM disposed; repository caches generation-stamped |
| Non-idempotent write safety | `retryOnConnectionFailure(false)`; no `Retry-After`/`Backoff` anywhere; double-submit guarded by `isUploading` set synchronously before launch (VM test: 1 POST across 3 submits) |
| Invalidation ordering | `accountRepository.invalidate()` → generation check → `notifyLedgerChanged()` — dead-gen writes never signal; no invalidation on failure paths (no pretend-mutation) |
| S12/S13/S14 UI | form / modal processing (no cancel/back, form removed from a11y tree) / result with 3 status tiles + rows-on-demand — P3A-faithful |
| Touch targets / a11y | `TouchTargetMin` (48dp) floors on rows and buttons; merged `contentDescription`s; processing state announces itself |
| Error mapping | structured `ProblemDetail`/`fields{}` preserved in `ApiException`; no raw exception text or message-sniffing in the UI |
| Money correctness | zero `Double`/`Float` on M5 paths; no client arithmetic on counts beyond `rows.size` |
| Boundary preservation | backend `8194daa` and reference `5f9622c` untouched; 30-file diff is entirely inside `spendwise-android` |
| Static safety | no `println`/`Log` on new paths; OkHttp logger is HEADERS-level, DEBUG-only, `Authorization`/`Cookie` redacted — filename/body bytes never logged |

## Remediation

MIN-1: `pickSeq` monotonic counter — every `onFilePicked`/`onFileCleared`
invalidates in-flight describes; publishes re-check against the newest
`Form`. Regression test: `out-of-order pick completions keep the latest
pick`.

MIN-2: banners now compare `duplicateCount == totalRows` /
`failedCount == totalRows` (with `totalRows == 0` still the empty
banner) — an `UNKNOWN` row can no longer trigger a false
all-duplicate/all-failed claim.

## Post-remediation falsification

- `ImportViewModelTest` — **20/20** PASS (new race test green).
- `ImportRepositoryTest`, `ImportsServiceContractTest`,
  `AppRoutesTest`, `compileDebugAndroidTestKotlin` — all PASS.
- No state-machine, contract, or financial-semantics change — both
  fixes are local ordering/accuracy corrections.

## Final verdict

```
M5_INITIAL_EXTERNAL_AUDIT=FINDINGS (0 blockers, 0 majors, 2 minors)
M5_REMEDIATION=COMPLETE
M5_POST_REMEDIATION=VERIFIED
BLOCKER=0
MAJOR=0
M5_CONTRACT_FIDELITY=PASS
M5_STREAMING_AND_SAF=PASS
M5_SESSION_PRIVACY=PASS
M5_WRITE_SAFETY=PASS
M5_TEST_EVIDENCE=PASS
M5_EXTERNAL_AUDIT_FINAL_VERDICT=ACCEPTED
```
