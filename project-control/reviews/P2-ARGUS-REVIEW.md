# P2 Argus Review

Independent review of `project-control/BACKEND-CONTRACT.md` against the
backend source at `D:\spendwise` HEAD `8194daa9959f2d568dc13ab0f211787195efc79d`.

ARGUS_REVIEW_MODE=ISOLATED (separate read-only review agent with no access to
the drafting context).

## Scope

Full adversarial re-verification of the contract document: independent
endpoint enumeration, DTO field checks, status-code claims, auth/JWT claims,
money/date/precision claims, validation coverage, internal-vs-public
classification, PDF support claims, dedup semantics, drift completeness,
gap-labeling, privacy, and evidence-anchor accuracy.

## Evidence sampled

- All 12 controllers and all request/response records re-read.
- `ApiExceptionHandler`, `SecurityConfig`, `NimbusTokenIssuer`,
  `TokenBlacklist`, `TransactionCreationService`, `TransferService`,
  `RuleEngine`, `BudgetCalculationService`, `RecurringGenerationService`,
  `StatisticsService`, all CSV/PDF parsers + layout detectors,
  `RowIdentityService`, `SourceRefNormalizer`, all 10 Flyway migrations,
  `application.properties`, `pom.xml`, committed docs.
- Independent handler enumeration via mapping-annotation grep:
  **26 handlers / 12 controllers — matches the document**.
- All cited line anchors spot-checked (`Money.java:30`, `Money.java:9-10`,
  `TransactionCreationService` L52-55/L85-98, `ImportController.java:20-22`,
  `RecurringTransactionService.java:31-32`, `StatisticsService.java:62-63`) —
  all correct.
- Absence claims grep-verified (no `@Json*`, no CORS, no multipart config,
  `app.fx.api-key` unread, no `insufficient`/`overdraft`, no `created_at`).
- 89 test files counted — matches the document; representative tests verified
  by name.

## Endpoint coverage challenge

All 26 inventory rows checked: methods, paths, parameters, request/response
types, success codes, auth flags — **accurate**. The absent-endpoint list
(no PUT/PATCH, no transfer endpoint, no transaction detail/update/delete,
no import list, no refresh/`/me`, no statistics HTTP) — **verified**.
Framework-default items (8080, 1 MB multipart, empty-body 401) correctly
labeled FRAMEWORK_DEFAULT_INFERENCE.

## Contract findings

| ID | Severity | Claim | Evidence | Problem | Required correction | Disposition |
|---|---|---|---|---|---|---|
| A-001 | MAJOR | §20 said `userId`/`sourceKey`/`ImportRow.id`/`batchId` are returned in the import response | `ImportBatchSummaryResponse`/`ImportRowDetail` lack those fields | Wrong response-shape facts | Mark as stored-not-returned | **FIXED** |
| A-002 | MAJOR | "Mixed-currency months → 400" | `requireSingleCurrency` is per-aggregation (`StatisticsService.java:62-63`); income-USD + expense-VND → 200 with unlabeled ambiguous totals; no currency fields in response | Falsifiable; hides ambiguous totals | Per-aggregation semantics + missing currency fields | **FIXED** (§2, §5, §22, §34, BG-013, D16) |
| A-003 | MAJOR | §33 matrix omitted budgets, CSV export, FX, batch-detail rows | all are real public endpoints | Capability matrix incomplete | Add rows | **FIXED** |
| A-004 | MAJOR | §23 implied currency-match applies everywhere | recurring create never validates currency vs account; generation then throws every run and skips remaining owners | Missed backend defect with client impact | Record defect + qualify claim | **FIXED** (§15, §23, D14) |
| A-005 | MINOR | split-across-pages cited `TpbankMultiPageImportTest` | test covers multi-page rows but no fixture splits a row mid-break | Verification overclaim | Relabel as parser-tolerated, not exercised | **FIXED** |
| A-006 | MINOR | entry point `SpendwiseApplication` | class is `SpendwiseApp` | wrong symbol | rename | **FIXED** |
| A-007 | MINOR | §27 grouped `CsvExportController` under non-`ResponseEntity` returns | it returns `ResponseEntity<String>` | real drift is `text/csv` only | reword | **FIXED** |
| A-008 | MINOR | "201s add Location" | `register` 201 has none | over-broad | qualify | **FIXED** |
| A-009 | MINOR | rules list order "priority asc, id tiebreak" | `IdAsc` exists only on the engine's enabled query | wrong repository claim | scope tiebreak to engine | **FIXED** |
| A-010 | MINOR | "no doc-vs-source drift" | `demo-script.md` implies `SPENDWISE_FX_API_KEY` enables a live provider (never read); `architecture-notes.md` ADR-002 cites `createdAt`/`TIMESTAMPTZ` columns that don't exist | overstated | list as D15 | **FIXED** |
| A-011 | MINOR | dangling "§39" ref for mixed-currency conventions | no drift row covered it | broken reference | add D16 | **FIXED** |
| A-012 | MINOR | §30 listed `ImportRowProcessor` as `@Transactional` | `processRow` is deliberately not `@Transactional`; boundaries are `create()`/`record()`; list also omitted non-ledger writers | imprecise | reword/enumerate | **FIXED** |
| A-013 | MINOR | `ConversionResponse`/`BudgetResponse`/`CategorizationRuleResponse` never enumerated; `from`/`to`→`base`/`target` rename undocumented | DTO records | missing field lists + naming trap | enumerate | **FIXED** |
| A-014 | MINOR | recurring `endDate` semantics unrecorded | `isDue` drops missed occurrences before `endDate`; `endDate < nextDueDate` unvalidated | omission | add note | **FIXED** |
| A-015 | NOTE | tier-2 `sourceRef` can exceed `VARCHAR(255)` on long descriptions → misleading `FAILED` reason | fingerprint construction + constraint translation | edge case | one line | **FIXED** |
| A-016 | NOTE | non-UUID `{id}` → 400 (not 404) | `MethodArgumentTypeMismatchException` mapping | worth explicit row | add | **FIXED** |
| A-017 | NOTE | `Category.valueOf` → 500 claim unreachable over HTTP | only `CsvTransactionStore` calls it and catches | edge unreachable | reword | **FIXED** |
| A-018 | NOTE | §41.8 read as design advice | mild leakage vs "does not decide" mandate | soften | **FIXED** (capability-absent phrasing) |
| A-019 | NOTE | BG-002 scope omitted budgets/rules update | §28 covers them but the gap row didn't | scope gap | expand row | **FIXED** |
| A-020 | NOTE | "all repository queries are `…ByUserId`" over-broad; §35 called `openingBalance` "server-computed" | `import_rows` is batchId-scoped; schedulers scan cross-user; `openingBalance` echoes request | over-claims | scope + reword | **FIXED** |

### Spot-verified correct (sample)

- Endpoint inventory: all 26 rows and the absent-endpoint list.
- Auth: permitAll set, HS256, `jti`/`sub=UUID`/`roles`/`iat`/`exp`, 15-min
  default, no iss/aud, in-memory blacklist, no refresh.
- Money: scale-2 `HALF_UP`, positive-only, `NUMERIC(19,2)`, `@Digits(17,2)`,
  open ISO-4217 string, currency-match pre-write, JSON-number wire.
- `occurredOn`/`description` absent from request and response; `now()` stamp;
  `occurredOn` only in CSV export.
- Import: exact CSV header, signed amounts, no BOM strip, 2-based CSV vs
  1-based PDF `rowNumber`, `IOException` → 500 vs comment claim (D1),
  TPBank 4-marker detection, synthetic layout, failure modes → 400.
- Dedup: tier-1/tier-2 shapes, `OCC:n`, legacy probe, `uq_txn_source_ref`,
  per-account scoping, re-import → DUPLICATE.
- Drift: `enabled`-ignored (D5), recurring-foreign-400 (D2), `app.fx.api-key`
  unread (D12), FX bare DTO (D8).
- Errors: ProblemDetail shapes, swallowed messages, `fields` map, empty-body
  404, 409s, 415/405, catch-all 500, `SourceRefConflictException` bypass.
- `Category.name()` = code; DELETE → 200+body; 89 test files.

## Mobile capability challenge

The §33 matrix initially omitted budgets, CSV export, FX conversion, and
import-batch detail (A-003 — fixed). All prompt-required rows are present and
correctly classified. Transfer correctly shown as BACKEND_GAP (not defect) —
matches `docs/defense-audit.md` self-documentation.

## Privacy review

No real customer data, account numbers, balances, private references, raw
bank-PDF content, or private filesystem paths appear in the contract.
`private-bank-fixtures` was never accessed. Synthetic test names are the only
fixtures referenced. PASS.

## Boundary review

BACKEND_MODIFIED=NO
REFERENCE_MODIFIED=NO
ANDROID_CODE_CREATED=NO

## Verdict

**PASS after reconciliation** — initial verdict PARTIAL (0 BLOCKER, 4 MAJOR,
10 MINOR, 6 NOTE); all 20 findings reconciled FIXED in the contract. Remaining
substance stands verified: endpoint inventory, auth model, money/precision,
dedup, import boundary, error model, and the gap/drift registers.
