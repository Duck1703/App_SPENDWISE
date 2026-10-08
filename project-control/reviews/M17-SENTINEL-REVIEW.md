# M17 SENTINEL REVIEW — Persisted Transaction Descriptions

```
ROLE: SENTINEL (Test Engineer)
MILESTONE: M17 — REQ-BE-010 Persisted Transaction Descriptions
ANDROID_RANGE: f304c5b..working tree (governance + vertical slice)
BACKEND_RANGE: ca5b380..9c123f7 (bounded prerequisite, human-authorized)
REVIEW_MODE: adversarial self-audit per workflow contract; all evidence
             reproduced from live gates, not assumed.
VERDICT: PASS — 0 Blocker, 0 Major, 1 Minor (fixed), 1 Note
```

## 1. Test evidence (re-executed for this review)

| Gate | Result |
|---|---|
| Backend focused M17 | `TransactionDescriptionContractTest` **10/10** + `CsvExportTest` **2/2** — real MockMvc + Postgres |
| Backend full regression (`mvn test`, env `SPENDWISE_DB_PASSWORD` + `SPENDWISE_JWT_SECRET` from `.env.local`) | **393/393**, 0 failures, 0 errors, 0 skipped (383 M16 baseline → +10) — surefire-reports count, not exit code |
| Flyway | `Successfully validated 12 migrations`; V12 additive `ALTER TABLE … ADD description VARCHAR(255)` applied at boot, schema up to date |
| Android JVM full regression (`testDebugUnitTest`, fresh) | **657/657**, 0 failures, 0 skipped (653 M16 baseline → +4) — XML report count, not exit code |
| Android lint (`lint`) | PASS — **0 Error/Fatal**, 22 warnings (pre-existing surfaces; none in M17 files) |
| Detekt | **0 code smells** (227 files analyzed) |
| Debug / Release builds | PASS; `assembleRelease` green incl. `lintVitalRelease` + `minifyReleaseWithR8` |
| R8 serializer audit | `TransactionDto`, `TransactionDto$$serializer`, `Companion`/`serializer()` kept unobfuscated in `mapping.txt` — same preservation pattern as M14–M16 DTOs |
| Merged manifest / backup rules | release merged manifest keeps `allowBackup` + `dataExtractionRules` + `fullBackupContent` — unchanged from accepted baseline |
| Instrumentation compile | `compileDebugAndroidTestKotlin` green |
| Pixel 4 XL connected suite (run 1) | 194 tests, **1 failure — test-harness defect found & fixed in this block** (see MINOR-1); focused re-run of the class: **8/8 PASS** |
| Install + launch (physical, explicit `adb -s`) | `app-debug.apk` installed; `com.spendwise.android/.MainActivity` confirmed `topResumedActivity` |

## 2. Requirement → test mapping (REQ-BE-010)

Backend (`TransactionDescriptionContractTest`, 10 tests + `CsvExportTest`):

| Acceptance surface | Covering test |
|---|---|
| Persist on manual create + return on create/list/page | `createdDescriptionPersistsAndReturnsOnCreateListAndPage` — asserts the row and all three read surfaces by transaction id |
| Omitted/blank → null | `absentAndBlankDescriptionsPersistNull` — no field and `"   "` both store NULL → wire null |
| Whitespace trimmed | `surroundingWhitespaceIsTrimmed` |
| 255 boundary / 256 rejected | `boundary255PersistsAnd256IsRejected` — 255 persists, 256 → 400 |
| Special chars + CSV escaping | `specialCharactersPersistVerbatimAndExportEscapesThem` — commas/quotes/newlines persist verbatim; export quotes per RFC4180 |
| Rule engine still consumes the text; explicit category wins | `ruleEngineStillCategorizesFromTheSamePersistedText` |
| Import statement text persists; overlong truncates, never fails the row | `importedStatementTextPersistsAndOverlongTextTruncates` |
| Recurring/transfer rows carry no note | `generatedAndTransferRowsCarryNoDescription` |
| Owner isolation | `aForeignUserNeverSeesAnotherUsersNote` |
| Auth boundary | `unauthenticatedCreateIsRejected` |
| CSV header/rows | `CsvExportTest` — header `…,sourceRef,description`; row values + quoting |

Android:

| Acceptance surface | Covering test |
|---|---|
| Request DTO serializes `description`; omitted when unset | `TransactionsServiceContractTest` — exact wire body asserted; `create omits optional fields entirely when unset` |
| Response DTO parses `description`; tolerates absence | `list parses the persisted description and tolerates its absence` (new) |
| Repository passes it through; blank → omitted | `TransactionRepositoryTest` create-call assertions |
| ViewModel submits captured note | `CreateTransactionViewModelTest` |
| Client input bound = server bound | `note input is bounded at the server's 255` — `take(255)` |
| Persisted note reaches detail content | `TransactionDetailViewModelTest.a persisted note reaches detail content while absent stays null` |
| Detail renders note when present | `TransactionDetailScreenTest.content_note_showsThePersistedDescriptionWhenPresent` |
| Detail hides note when null | `content_note_isHiddenWhenDescriptionIsNull` |
| 255-char note renders whole (block layout, not truncated side-row) | `content_note_rendersAMaxLengthDescriptionWhole` (new) |

## 3. Invariants verified adversarially

- **No fabricated truth** — detail renders only `transaction.description` from the server response; no client-side synthesis, aggregation, or caching layer touched.
- **Ledger untouched** — the field is read-only metadata; no balance, stats, or reconciliation invariant reads it (verified: no aggregate query references `description`; reconciliation warnings in the run are the pre-existing routine fixture noise).
- **Unbounded import text cannot fail a row** — entity `normalizeDescription` caps at the column; contract test proves >255 statement text truncates rather than erroring (a real regression caught during implementation — fixed before commit).
- **Blank never stored** — both ends converge to null: client omits `isBlank`, entity normalizes `isBlank → null`. Whitespace-only notes cannot round-trip as empty UI blocks (detail also defends with `takeIf { it.isNotBlank() }`).
- **Backward compatibility** — response field is additive/nullable; `list tolerates unknown future fields` and the new absence-tolerance test prove old rows (NULL) parse fine.
- **CSV contract change is additive-only** — trailing column through existing `field()` quoting; header + row shape asserted.
- **No new mutation surface** — no transaction PATCH/PUT/DELETE added; the ledger stays append-only. Session-generation, no-replay, and ambiguous-outcome paths untouched (no mutation code was modified).
- **Serializer survives R8** — obfuscation would have silently broken the new field on release; mapping verified by name.

## 4. Findings

### MINOR-1 (found & fixed in this block): Compose harness called `setContent` twice

`content_note_showsWhenPresent_hidesWhenNull` set content for the
present-description case and then set content again for the null case —
`createAndroidComposeRule` permits one `setContent` per test.
`IllegalStateException: ComponentActivity has already set content`.
**Fix:** split into `…ShowsThePersistedDescriptionWhenPresent` and
`…IsHiddenWhenDescriptionIsNull`; class re-run **8/8 PASS** on the Pixel.

### MINOR-2 (found & fixed in this block): hypothetical blank string would render an empty note block

Detail rendered any non-null `description`; the server normalizes blank →
null so `""` cannot arrive today, but the UI had no defense if a future
server anomaly emitted one. **Fix:** `description?.takeIf { it.isNotBlank() }`
— one line, zero risk.

### NOTE-1: input silently truncates at 255

`onDescriptionChange` does `take(MAX_DESCRIPTION_LENGTH)` — chars past 255
are dropped with no counter. Consistent with every other `MAX_*` client
guard in the codebase (same convention, no counter anywhere); the server
bound can never be reached on the wire. Documented, not a defect.

## 5. Unresolved defects

None. No backend gaps — the persisted contract behaves as specified against
the real backend (10/10 contract suite incl. import-cap, recurring/transfer
nulls, and owner isolation).

`READY_FOR_ARGUS`: **YES**
