# M5 SENTINEL Review — CSV / TPBank PDF Import

Reviewer role: **SENTINEL** (test engineer / verification), per
`agent-system/agents/sentinel.md`. Scope: correctness, contract
fidelity, test coverage, session/security integration,
financial-correctness invariants, P3A §5–6 fidelity, scope
discipline, quality gates for the M5 import vertical slice.

## Verified contract (source-checked)

Backend `D:\spendwise` HEAD `8194daa` (read-only). `ImportController`,
`ImportReportController`, `ImportService`, `ImportRowProcessor`,
`ImportBatchSummaryResponse`, `ImportRowDetail`, `ImportRowStatus`:

| Endpoint | Success | Failure surface |
| --- | --- | --- |
| `POST /api/imports?accountId=UUID` multipart `file` | 201 + `Location` + `ImportBatchSummaryResponse` | 400 (unsupported extension / parse failure); 404 (foreign account); 500 (structural CSV / oversize / mid-loop — the documented drift); 401 |
| `GET /api/imports/{id}` | 200 report (owner-scoped) | — (not used by the client) |

Wire shape verified field-for-field: `{batchId, accountId, fileName,
importedAt, successCount, duplicateCount, failedCount,
rows[{rowNumber, status, reason, transactionId}]}` — **no
`totalCount`** (the client derives it from `rows.size`), statuses
`OK|DUPLICATE|FAILED`, CSV row numbers are physical line numbers with
data starting at line 2. Extension dispatch is on the original
filename, lower-cased — the multipart `filename=` must carry `.csv`
or `.pdf` through. No multipart config at `8194daa` → Boot defaults:
**1 MB file / 10 MB request** caps.

## Correctness checklist

| Check | Result |
| --- | --- |
| `POST api/imports` path + `?accountId=` is the only query param | PASS (`upload POSTs api-imports …` asserts `queryParameterNames == {accountId}`) |
| Multipart `file` part + original filename reach the wire | PASS (recorded body contains `name="file"; filename="october statement.pdf"`) |
| Bearer token propagates through the real `AuthInterceptor` | PASS (`Bearer live-token` asserted) |
| 201 full-response decode: ids, timestamps, three counts, row list | PASS (`upload decodes the 201 report exactly`) |
| Unknown response fields + header-only empty report tolerated | PASS (`unknown fields and a header-only empty report decode`) |
| 400/404/500 → `ApiException` with the real status | PASS (three dedicated contract tests) |
| 401 → `Session.Expired`, **one** request on the wire — the POST never replays | PASS (`401 transitions … never auto-retries` + `requestCount == 1`) |
| DTO→domain mapping incl. `Instant` parse + per-row status enum | PASS (repo `upload maps the report`) |
| Unknown status string → `UNKNOWN`, never a crash or an Imported count | PASS (repo + screen tests) |
| Malformed `importedAt` → `AppError.Unknown`, never a raw defect | PASS (`malformed importedAt … Unknown`) |
| Success → account `invalidate()` + `notifyLedgerChanged` | PASS (`success invalidates the account cache and signals the ledger`) |
| Failure → **no** ledger signal | PASS (`400 maps to BadRequest` asserts `notifyLedgerChangedCalls == 0`) |
| Dead generation → `SessionEndedException`, no publish, no signal | PASS (`a dead generation throws … never signals the ledger`) |
| `describe`/`toPart` behind `StatementStore` — `android.net.Uri` outside the shared layer | PASS (string URI through VM/repo; seam parses it back) |
| Local unreadable file → `StatementUnreadableException`, zero wire hits | PASS (`unreadable picked file … never reaches the wire`) |

## Session / cross-user privacy

| Check | Result |
| --- | --- |
| Generation captured before the network call; checked before publish | PASS (repo `upload` — same pattern as `create()`) |
| Dead-generation upload → `SessionEndedException`, form restored + resume armed | PASS (VM `dead-generation upload restores the form`) |
| 401 mid-upload → `Session.Expired` + restored form + ambiguous notice + `expiredMidLoad` | PASS (VM `session-expired mid-upload restores …`) |
| Same-user resume → `onVisible()` re-runs the account load only — never an upload replay | PASS (`onVisible` asserts `calls == 1` after resume) |
| Session-expired account load → stays `Loading` under the overlay; resume retries | PASS (`session-expired account load arms the resume retry`) |
| Result state carries no identity data beyond the server batch — nothing client-persisted | PASS (stateless result; sign-out destroys the VM state with the graph entry) |

## Financial correctness

| Check | Result |
| --- | --- |
| Zero client-side arithmetic anywhere in the slice | PASS — counts come straight from `successCount`/`duplicateCount`/`failedCount`; `totalRows = rows.size`; no `BigDecimal` appears in the feature (there is nothing to compute) |
| No client ledger writes, balance math, or currency assumptions | PASS — the server is the sole importer; `accountRepository.invalidate()` + the ledger signal are the only "math-adjacent" steps |
| Currency-mismatch rows surface as server `FAILED` rows with reasons | PASS (result renders `Row 4 — currency mismatch`) |

## State-machine coverage (VM)

| State/transition | Test |
| --- | --- |
| accounts → `Form` (first preselected) | `loads the form with the first account preselected` |
| accounts empty → `Empty` | `empty accounts produce the create-first state` |
| accounts fail → `Error(Network)` + `retry()` recovers | `account load failure maps to the error state` |
| `.exe` pick → `UnsupportedFile`, file stays null | extension gate test |
| `.csv` (uppercase) pick accepted | same test (`Statement.CSV`) |
| `>1 MB` → `FileTooLarge`, zero upload calls | `an oversized file is rejected client-side` |
| `==1 MB` boundary accepted | `a file exactly at the cap is accepted` |
| clear-file preserves the chosen account | `clearing the file resets the pick` |
| submit → `Result` once | `submit lands on the result and posts once` |
| double-submit gated → one POST | `double-submit while uploading …` (gated `uploadResponder`) |
| submit without file → no-op | `submit without a file is a no-op` |
| 400 → `UnsupportedFile`, not ambiguous | `400 surfaces as unsupported-file` |
| 404 → `AccountUnavailable`, not ambiguous | `404 surfaces as account-unavailable` |
| 500 → `Server` + ambiguous, form restored | `500 restores the form …` |
| network drop → `Network` + ambiguous, form restored | `network drop restores the form …` |
| unreadable file → `Generic`, **not** ambiguous | `an unreadable file surfaces generic` |
| failed describe keeps the form usable | `the failed pick keeps the form usable` |
| expiry/dead-gen mid-upload → restored + resume | two dedicated tests |

## Compose coverage (androidTest — compile-verified, no device)

S12 form (picker, format block, disabled/enabled submit, account
dropdown via the field CD), S13 (merged processing CD + unreachable
form + absent Back), S14 (filename, `Rows in file: 3`, three merged-CD
tiles, failure reasons, `Done` callback), all-duplicates (calm
"Nothing new to import"), all-failed ("Nothing could be imported" +
reasons), empty-file zero state, `Empty`/`Error` states, UNKNOWN-row
not Imported. Entry points: More row (CD) + Home quick-card (CD +
≥48dp assertion) — callbacks asserted.

## Quality gates (fresh run on the final diff)

```
BASELINE_UNIT_TESTS=301 (M4 closeout, XML-verified)
FINAL_UNIT_TESTS_EXECUTED=338
UNIT_PASS=338
UNIT_FAIL=0
UNIT_SKIPPED=0
M5_CONTRACT_TESTS=PASS (8/8 MockWebServer)
M5_REPOSITORY_TESTS=PASS (9/9)
M5_VIEWMODEL_TESTS=PASS (18/18)
M5_COMPOSE_TESTS=PASS (compile-verified — no device)
M5_NAVIGATION_TESTS=PASS (AppRoutesTest incl. ImportRoute)
M5_SESSION_GENERATION_TESTS=PASS (dead-gen + 401 mid-upload + resume)
M0_REGRESSION=PASS, M1…M4_REGRESSION=PASS (338/338 suite-wide)
assembleDebug=PASS, testDebugUnitTest=PASS, lintDebug=PASS
compileDebugAndroidTestKotlin=PASS, assembleRelease=PASS, detekt=PASS (0 smells)
CONNECTED_DEVICE=NO
CONNECTED_TESTS=NOT_RUN_NO_DEVICE
REAL_BACKEND_E2E=NOT_RUN
```

## Findings

None — **0 blockers, 0 majors, 0 minors** at close. (One
in-scope note for ARGUS: the ambiguous-notice banner is dropped when
a same-user resume rebuilds the form — the ledger reloads through the
normal visible lifecycle, so no data is stale; the copy tradeoff is
documented in the milestone.)

## Verdict

`M5_SENTINEL=PASS` — the slice is contract-faithful, session-safe,
financially inert (nothing computed client-side), fully wired through
real interceptors, and covered at wire/repo/VM/screen levels.
