# M17 EXTERNAL AUDIT — Persisted Transaction Descriptions (REQ-BE-010)

Independent external audit of the M17 delivery. Executed as a falsification
task: internal SENTINEL/ARGUS PASS verdicts were treated as claims, not proof.
Two MAJOR defects and two MINOR defects were found that all internal reviews
missed; all were remediated in-task and re-verified on final source.

```
INITIAL_VERDICT=FINDINGS (0 Blocker / 2 Major / 2 Minor / 3 Note)
FINAL_VERDICT=ACCEPTED (all findings remediated, all gates re-green)
```

## 1. Starting checkpoints

| Field | Value |
|---|---|
| Android base | `63fef64` (M16-accepted) |
| Android delivery HEAD | `25db8abfe43ce2148f06c3630c92459582084cba` |
| Backend base | `ca5b380` |
| Backend delivery HEAD | `9c123f7ede7a1d9e39a88177a44c97b73e64537a` |
| Android clean before | YES |
| Backend pre-existing dirty | `M README.md`, `?? HUONG-DAN-SU-DUNG.md`, `?? docs/SPENDWISE-REQ.md` (human-owned — untouched throughout) |
| M16 canonical status | COMPLETE — `M16_EXTERNAL_AUDIT_FINAL=ACCEPTED`, `M16_HUMAN_APPROVED=YES`; `@Version` + V11 + 409 handler verified intact at audit time |

## 2. Contract & migration

| Check | Result |
|---|---|
| REQ-BE-010 alignment | PASS — nullable `description` persisted, returned on create/list/page, exported in CSV; note is metadata, never touches ledger math |
| V12 fresh DB | PASS — context boot runs Flyway through V12 on every test |
| V11→V12 upgrade | PASS — `ALTER TABLE … ADD COLUMN … VARCHAR(255)` (nullable) is purely additive; existing rows read NULL |
| Existing financial rows preserved | YES — no data rewrite, no index added |
| `description` nullable | YES — column and field both nullable |
| Max length | 255 UTF-16 code units (entity cap; request `@Size(255)` counts units too — consistent) |

## 3. Normalization

| Check | Result |
|---|---|
| blank → null | PASS (`isBlank` → null; also tabs/newline-only) |
| strip | PASS — edges stripped, internal whitespace preserved |
| 255 boundary | PASS — exactly 255 units persisted verbatim |
| 256+ request | 400 via `@Size` (request path) |
| 256+ import text | capped at entity — surrogate-safe after remediation |
| Unicode boundary | **FIXED** — was MAJOR-1 |
| Vietnamese + emoji round-trip | PASS — `Cà phê sáng ☕ … 😊` persisted and returned verbatim |

## 4. Findings (frozen before consulting internal reviews)

### MAJOR-1 — `substring(0,255)` split UTF-16 surrogate pairs

`Transaction.normalizeDescription` capped unbounded import text with
`trimmed.substring(0, 255)`. Java indexes UTF-16 code units: a 254-ASCII +
emoji statement line (256 units) truncated to a string ending in a **lone high
surrogate** — malformed Unicode. Reproduced with a failing test: the persisted
row read back `y`×254 + an unpaired surrogate (255 units). Postgres tolerated
it on this instance; on stricter encodings the insert fails — precisely the
row-failure the cap existed to prevent. **Fix:** surrogate-aware cap (drop the
orphaned pair half) + drop any dangling trailing surrogate from malformed
client payloads. Regression: `truncationNeverSplitsASurrogatePair`.

### MAJOR-2 — CSV formula injection (CWE-1236)

`CsvExportService.field` quoted commas/quotes/newlines but had no formula
neutralization. `description` is user/statement-controlled text: `=1+1`,
`+SUM(…)`, `-2+3`, `@NOW()` exported verbatim — evaluated as formulas when the
export is opened in Excel/Sheets/LibreOffice. Reproduced with a failing test.
**Fix:** export-layer apostrophe prefix when the first non-whitespace char is
`= + - @`; persisted values unchanged. Only `description` can carry free text
— every other column is a server-formatted token that cannot start with a
trigger char. Regression: `formulaLeadingDescriptionsAreNeutralizedInExportButPersistedVerbatim`.

### MINOR-1 — Android `take(255)` surrogate boundary

`CreateTransactionViewModel.onDescriptionChange` used `value.take(255)` —
254 ASCII + one emoji produced a wire string ending in a lone high surrogate.
**Fix:** `cappedDescriptionLength()` drops the orphaned half. Regression:
`note cap never leaves a dangling surrogate half`.

### MINOR-2 — Flaky single-flight storm test (test integrity)

`SessionRefreshContractTest.concurrent GETs that all 401 collapse into a
single refresh` relied on MockWebServer FIFO ordering: enqueued
[401,401,refresh,200,200] assumed both initial reads consumed both 401s
before any retry fired. Under another interleaving, a retry bearing the
**fresh** token consumes a queued 401 → the authenticator legitimately renews
again (a 401 on the *current* token is a real reason to refresh) → 2 refreshes
→ assertion failure. The production single-flight machinery (ReentrantLock +
`AlreadyAlive`) is correct — the test's ordering assumption was not. Failed
twice in full-suite runs, passed in isolation. **Fix:** header-keyed
`Dispatcher` — stale bearer always 401, fresh always 200, refresh counted
directly. Deterministic under every interleaving; the invariant is now
actually proven rather than order-lucked.

### MINOR-3 — Stale comment

`TransactionCreationCommand` Javadoc still claimed `description` was "never
persisted" — false after M17. Updated.

### Notes (accepted, not defects)

- `@Size(255)` counts UTF-16 units while Postgres `VARCHAR(255)` counts code
  points — the request bound is strictly more conservative for supplementary
  characters. Consistent interpretation: the product limit is 255 UTF-16
  code units, which is what Android enforces too.
- `isBlank()`/`strip()` use `Character.isWhitespace` — an NBSP-only note
  persists as invisible text. Pre-existing Java semantic shared by every
  string field; out of M17 scope.
- No app-side length counter regression: the create screen still caps input
  at the constant and shows the field counter.

## 5. Independent verification results

| Surface | Evidence |
|---|---|
| Create persistence | `createdDescriptionPersistsAndReturnsOnCreateListAndPage` + DB read-back |
| Unpaged/paged list | `description` present on both; page metadata unchanged |
| Owner isolation | stranger list empty, export contains no foreign note (`aForeignUserNeverSeesAnotherUsersNote`) |
| Unauthenticated | 401 on create |
| Rule engine | unchanged — engine receives the raw request value; manual category precedence untouched; `ruleEngineStillCategorizesFromTheSamePersistedText` |
| Import | `row.description()` flows through the entity normalizer; sourceRef fingerprint uses parse-time text — dedup unchanged (`descriptionDoesNotChangeImportDuplicateIdentity`) |
| Generated/transfer rows | recurring `TransactionCreationCommand` and both transfer legs pass `null` description — verified in source + contract test |
| Balance isolation | description is never read by `applyEffect`; amounts/currency/balance paths untouched by the diff |
| M13 no-replay | `create transaction 401 after transmission is never replayed` green; POSTs are not authenticator-retried |
| Session generation | `AccountRepositoryConcurrencyTest` stale-response/generation tests green; the field is passive data on the same guarded objects |
| M15 pagination | `GET /api/transactions/page` shape unchanged + description field; ordering `occurredOn DESC, id DESC` intact |
| M16 locking | `@Version` + `OptimisticLockingFailureException` → 409 handler verified in source; recurring suite green |

## 6. Comparison with internal reviews

| Internal claim | Independent result |
|---|---|
| SENTINEL PASS (0B/0M open) | **Refuted** — 2 Majors + flaky-test MINOR missed (no Unicode/security falsification performed) |
| ARGUS PASS (0B/0M open) | **Refuted** — same two Majors missed; ARGUS's own findings (contract-doc drift A-1/A-2) were correctly fixed |
| Handoff gate claims | Reproduced where re-run: backend 397/397, JVM 658/658, physical 196/196 ×2 — on *remediated* source |

Net-new external findings: MAJOR-1, MAJOR-2, MINOR-1, MINOR-2. Internal
findings confirmed as properly remediated (doc drift, Compose harness,
blank-string guard, CSV header/order tests).

## 7. Final gates (on remediated source)

| Gate | Result |
|---|---|
| Backend targeted (contract) | 14/14 |
| Backend full `mvn test` | **397/397**, 0 f/e/s |
| Backend `mvn package` | PASS — `spendwise-0.1.0-SNAPSHOT.jar` |
| Flyway | PASS — V1–V12 applied, context boots |
| Android JVM `testDebugUnitTest --rerun-tasks` | **658/658**, 0 f/e |
| lint | PASS — 0 errors, 22 warnings (pre-existing surface) |
| detekt | PASS — 0 smells (227 files) |
| assembleDebug | PASS |
| compileDebugAndroidTestKotlin | PASS |
| assembleRelease (R8) | PASS — `lintVitalRelease` green |
| Serializer audit | `TransactionDto` + `TransactionDto$$serializer` present in release APK dex |
| Manifest | INTERNET only; `usesCleartextTraffic=false`; `datastore/` excluded from backup + data-extraction |
| Physical Pixel 4 XL (API 33), run 1 | **196/196**, 0 fail/skip |
| Physical Pixel 4 XL, run 2 | **196/196**, 0 fail/skip |
| Install + launch | `app-debug.apk` installed; `MainActivity` top-resumed |

The M17 device flow itself is exercised by the connected suite
(`CreateTransactionScreenTest`, `TransactionDetailScreenTest` — note shown
when present, hidden when null, long-note layout) on the physical Pixel.

## 8. Release artifact

- `app/build/outputs/apk/release/app-release-unsigned.apk`
- Size: 2,454,294 bytes
- SHA-256: `9028b1b02d49d2e9b142f3bb5b9fff6327df8dc011fe1c1daa71d42ff4685568`

## 9. Remediation commits

- Backend `752eac397923454dd8cca680c6a589cd5285deda`
  `fix(transactions): reconcile M17 external audit findings`
- Android `deccaf2099c40dbc909411b7a03783d6030f3a88`
  `fix: reconcile M17 external audit findings`

## 10. Boundaries

- Backend human-owned dirty paths unchanged: `README.md`, `HUONG-DAN-SU-DUNG.md`, `docs/SPENDWISE-REQ.md` — never staged/edited.
- Reference + prototype repos untouched. Private fixtures never accessed.
- No push, no tag, no amend, no destructive git.
- `M18_DEFINED=NO`, `M18_STARTED=NO` throughout.
- `COURSE_UPDATE_EXECUTED=NO`, `COURSE_SYNC_DEFERRED=YES`.
- Course-sync range: `ca5b380..752eac3` — persisted description semantics:
  nullable VARCHAR(255) V12; blank→null, stripped, surrogate-safe 255-unit
  cap; returned on create/list/page; trailing CSV column with CWE-1236
  neutralization; rules still see raw input; recurring/transfer rows NULL.

## 11. Verdict

M17 is **ACCEPTED**. Persisted transaction descriptions are correct and safe
across the full workflow: migration is additive; normalization bounded and
Unicode-safe; rules/import/dedup unaffected; generated rows clean; CSV export
neutralized; owner isolation proven; M13/M15/M16 invariants intact; Android
reflects server truth. Every found defect was remediated and re-verified on
final source — backend 397/397, Android JVM 658/658, physical 196/196 ×2,
release artifact verified.
