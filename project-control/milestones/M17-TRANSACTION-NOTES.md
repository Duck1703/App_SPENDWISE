# M17 — Transaction Notes (persisted description)

```
STATUS: READY_FOR_EXTERNAL_AUDIT
M16_COMPLETE=YES
M17_DEFINED=YES
M17_STARTED=YES
M17_TECHNICAL_COMPLETE=YES
M17_READY_FOR_EXTERNAL_AUDIT=YES
M17_EXTERNAL_AUDIT=NOT_RUN (out of scope for this delivery block)
M17_HUMAN_APPROVED=NO
M17_HUMAN_AUTHORIZATION=EXPLICIT (M17 delivery block)
CANONICAL_REQUIREMENT=REQ-BE-010 (persisted transaction
  description/notes)
TRANSACTION_NOTES=DELIVERED_TECHNICAL
M18_DEFINED=NO
M18_STARTED=NO
SPENDWISE_STATUS=M17_READY_FOR_EXTERNAL_AUDIT
NEXT_ACTION=ONE_LARGE_INDEPENDENT_EXTERNAL_M17_AUDIT
BASELINE: Android 63fef64 (M16-accepted) · Backend ca5b380
DELIVERY: Backend ca5b380..9c123f7 · Android f304c5b..HEAD
COURSE_SYNC_DEFERRED=YES (backend range recorded below)
```

## Selection rationale (decision record)

Candidates re-evaluated against CURRENT canonical sources
(`PRODUCT.md` §41 BE register, `POST-V1-BACKEND-REQUIREMENTS.md`,
`POST-V1-CAPABILITY-MATRIX.md`, `ROADMAP.md` — M9–M16 removed from
candidacy) and the actual backend surface (`ca5b380`).

| Candidate | Verdict |
|---|---|
| **Persisted transaction description (REQ-BE-010)** | **SELECTED**: the only canonical REQ left that is both user-visible and fully derivable. `description` is already accepted on `CreateTransactionRequest` (`@Size(max=255)`) and already sent by the Android create form as a categorization hint — the wire field exists end-to-end; the requirement is to *persist* it and return it. Real user value ("what was this charge for?"), additive nullable `VARCHAR(255)` column (same bounded-migration discipline as M16's V11), unlocks honest transaction search later (M15 rejected `q` as near-fake precisely because no searchable text is persisted). Score: priority 4 + value 4 + backend 5 + android 5 + testability 3 + leverage 3 + physical 2 − ambiguity 1 − security 1 − schema 1 − history 0 − scope 1 = **22**. |
| Transaction search | Rejected again, honestly: M15's own verdict stands — no persisted searchable text exists; `q` over `sourceRef` technical keys or category/type duplicates existing filters = near-fake. BE-010 is the strict prerequisite; landing notes first makes a future search milestone honest. |
| REQ-BE-005 transaction correction | Rejected: register requires an explicit product decision (PATCH vs reversal); financial-history ambiguity without canonical resolution. |
| REQ-BE-006 account edit/archive | Rejected: "define archived-account semantics" unresolved — transactions/transfers/budgets/recurring all reference accounts; inventing archive semantics is out of bounds. |
| Categorization rule edit | Rejected: same PATCH shape as M16 but lower user value (rules are set-once); priority/conflict semantics add ambiguity. ~15. |
| REQ-BE-010 "needs product decision" (M16 table) | Re-resolved: the M16 candidate table flagged ambiguity — "the hint silently becomes ledger text". On re-derivation the canonical REQ itself answers it: persist *the same accepted field*; the UI copy is updated to declare both uses explicitly ("saved with the transaction; also used to auto-categorize"), so nothing is silent. Every §41 derivation item resolves from existing conventions (see Semantics below). |
| FX converter tool | Rejected: product-gated (V1 never-mix-currencies), display-only provider, low value, external dependency. |
| Profile management | Rejected: PRODUCT explicitly excludes new auth surfaces from near-term milestones. |
| REQ-BE-008 multipart / REQ-BE-009 hardening | Rejected: trivial config / low user-visible value; not milestone-sized. |
| Recurring pause/resume/skip | Rejected: not in the canonical register; undefined semantics. |

## User outcome

When creating a transaction the user can type an optional free-text
note (≤255 chars). It is persisted server-side, returned on every
transaction read, shown on the transaction detail screen, and carried
in the CSV ledger export. The same text continues to feed the
categorization rules engine exactly as the hint does today.

## Why the "hint becomes ledger text" question is resolved, not deferred

REQ-BE-010 says literally: "`description` column on `Transaction` +
request/response fields (currently accepted but dropped after
rule-matching)". The requirement is to persist the field the user
already types — not to invent a second field. The honesty fix is in
the UI copy: the field's label/support text now says it is **saved
with the transaction and used to auto-categorize** — the dual use is
declared, not silent. Introducing a separate `note` field would
duplicate the wire surface and confuse which text feeds rules.

## Backend contract (bounded M17 prerequisite)

No new endpoint. Two changes:

1. `POST /api/transactions` — `description` (existing `@Size(max=255)`
   request field) is now **persisted** instead of dropped after
   rule-matching. Still optional; still feeds `RuleEngine.categorize`
   unchanged.
2. `TransactionResponse.description` — new nullable field on every
   transaction read (list, page, detail-by-filter). `null` for rows
   created without a note and for all pre-M17 rows.

Semantics (all derived from existing conventions):

- Length: `@Size(max=255)` (already on the request) — the column is
  `VARCHAR(255)`; >255 → 400 via existing Bean Validation.
- Blank: whitespace-only normalized to `null` (stored trimmed), like
  every other optional text field; empty is never persisted.
- Schema: `V12__transaction_description.sql` — `ALTER TABLE
  transactions ADD COLUMN description VARCHAR(255)` — additive,
  nullable, zero default, existing rows untouched.
- Legacy rows: `NULL` → wire `null` → UI hides the note row.
- Imports: `ImportRowProcessor` already passes the parsed statement
  description into `TransactionCreationCommand` (it feeds rules +
  sourceRef identity today) — it now persists too. That is the honest
  REQ-BE-010 reading: the column is `description`, not `userNote`, and
  statement text is legitimate descriptive data (owner-scoped, same
  dual-use as the manual field). Statement text longer than 255 is
  truncated at the entity so a long line can't fail the row
  (request-side input is already `@Size(255)`-bounded).
- Recurring-generated rows: `NULL` (templates have no description
  field — out of scope).
- Transfer legs: `NULL` (`CreateTransferRequest` has no description).
- Edit: none — correcting a note is REQ-BE-005 territory (still
  gated); description is set at creation only.
- CSV export (`GET /api/transactions/export`): gains a `description`
  column — the export is the canonical ledger surface; a persisted
  field omitted there contradicts the BE-001 "export proves the data
  exists" principle. Existing RFC-4180 `field()` quoting already
  handles commas/quotes/newlines.
- Sanitization: plain-text render only (Compose `Text`, CSV-escaped
  output); no HTML surface; no logging of body content.
- Owner scope: unchanged — every read path is already
  `findAllByUserId*`; a foreign user cannot see another's note.
- Errors: 400 >255 chars; 401 unauthenticated; 404 unchanged.
- M13: no new mutation class — POST replay rules already enforced
  (no auto-replay on transmitted-401); reads are GET.
- M16 optimistic-lock baseline: `RecurringTransaction.@Version`
  untouched; Transaction is not a versioned aggregate and gains no
  mutable-field endpoint — no interaction.

## Android scope

- `TransactionDto.description` (response side, nullable).
- Domain `Transaction.description` propagated.
- Create form: existing `hint` field reframed — label "Note",
  supporting text "Saved with the transaction. Also used to
  auto-categorize." (≤255 char bound enforced client-side as
  `MAX_DESCRIPTION_LENGTH`).
- `TransactionRepository.create`: `hint` param renamed `description`
  for honesty (same wire field).
- `TransactionDetailScreen`: "Note" block shown only when non-blank
  (server emits `null` for absent notes; the blank guard defends the
  hypothetical `""`).
- List rows unchanged (detail is the display surface; no redesign).
- No new screens/routes — additive to existing create + detail flows.

## Session-refresh implications

No new request classes: description rides existing POST (mutation —
no replay, unchanged) and GET reads (refresh-retry allowed,
unchanged). Detail/list rendering is pure read data — generation
guards already in place.

## Privacy / ownership

Notes are owner data like every other transaction field; all reads
are owner-scoped server-side; no cross-user surface. A→B: repository
generation guards drop late-A results — unchanged behavior, tests
cover the detail path. No note content in logs (bodies are never
logged; logging is HEADERS-only DEBUG-gated).

## Financial-authority constraints

A note is **not** financial truth: it never participates in balance,
amount, category, or date computation. It is user-authored metadata
persisted verbatim. Category resolution still flows explicit
category first, rule engine second — unchanged.

## Non-goals

- No `PATCH`/`DELETE` on transactions (REQ-BE-005 — needs product
  decision).
- No description on recurring templates, transfers, or import rows.
- No server-side search/`q` param (unblocked by this, but is its own
  milestone).
- No list-row note preview; no note editing UI.
- No M18 work. No course synchronization.

## Test strategy

Backend (`TransactionDescriptionContractTest`, MockMvc + Postgres —
existing conventions): persist-and-return round trip; absent → null;
blank → null; 255 boundary; 256 → 400; commas/quotes/newlines
persisted verbatim + CSV-escaped; legacy (null-column) row → wire
null; recurring/import/transfer-created rows → null; rule
categorization still uses the text; owner-scoped reads (foreign user
sees nothing); unauthenticated 401.

Android: MockWebServer wire tests (request field, response parsing,
null handling); repository mapping tests; create-VM tests (bound at
255, blank → omitted wire field); detail-VM/screen tests (note
rendered when present, hidden when null); A→B generation-guard on
detail path; Compose detail test.

## Gates

JVM fresh suite; lint/detekt/debug; release R8 + serializer audit;
physical Pixel 4 XL connected suite ×2 on final source; install +
launch; Sentinel + Argus reviews with in-task remediation; external
audit is OUT OF SCOPE for this delivery (stops at
`M17_READY_FOR_EXTERNAL_AUDIT=YES`).

## Course sync

Deferred. Backend production delta is `ca5b380..9c123f7` (single commit:
entity field + V12 + response + mapper + service + CSV + tests) plus the
contract summary in this file and `BACKEND-CONTRACT.md`.
`COURSE_UPDATE_EXECUTED=NO`.

## Delivery record (final)

### Backend diff — `ca5b380..9c123f7` (8 files, +307/−9)

- `Transaction.java` — `@Column(length=255) description`; entity
  `normalizeDescription` (blank→null, strip, cap 255); 9-arg ctor;
  8-arg delegates null; `description()` accessor.
- `V12__transaction_description.sql` — additive nullable
  `ALTER TABLE transactions ADD COLUMN description VARCHAR(255)`.
- `TransactionCreationService` — passes `command.description()`.
- `TransactionResponse` / `TransactionMapper` — field added to every
  read surface (create/list/page/export paths share the record).
- `CsvExportService` — trailing `description` column via existing
  RFC4180 `field()` quoting; header now
  `occurredOn,type,amount,currency,category,sourceRef,description`.
- Tests — `TransactionDescriptionContractTest` (10, new);
  `CsvExportTest` header/row updates.

### Android diff — `f304c5b..` (slice commit `445ffb0` + review fixes)

- `TransactionDtos.kt` / `Transaction.kt` — nullable `description`.
- `TransactionRepository.create(hint→description)`; blank → omitted.
- `TransactionsUiState` / `CreateTransactionViewModel` — rename;
  `MAX_DESCRIPTION_LENGTH=255` input bound (server parity).
- `CreateTransactionScreen` + `TransactionDestinations` + `strings.xml`
  — "Note (optional)", support text declares both uses.
- `TransactionDetailScreen` — full-width Note block when non-blank.
- Tests — service wire (body + parse + absence), repository, create-VM
  (+255 bound), detail-VM (note reaches content), detail screen
  (shown/hidden/255-whole), fakes.
- Docs — `BACKEND-CONTRACT.md` (6 sites), `PRODUCT.md` (§6, matrix,
  BE-010), `POST-V1-BACKEND-REQUIREMENTS.md`, capability matrix.

### Gate evidence (final source)

| Gate | Result |
|---|---|
| Backend regression (`mvn test`) | **393/393**, 0 f/e/s (383 + 10) |
| Android JVM (`testDebugUnitTest`) | **657/657**, 0 f/e/s (653 + 4) |
| lint | 0 Error/Fatal, 22 warnings (pre-existing) |
| detekt | 0 smells (227 files) |
| assembleDebug / assembleRelease+R8 | PASS; `TransactionDto$$serializer`+Companion kept in mapping.txt |
| Merged manifest / backup | unchanged from accepted baseline |
| Pixel 4 XL `connectedDebugAndroidTest` ×2 | **196/196** both runs (193 + 3) |
| Install + launch (physical) | `MainActivity` top-resumed |
| SENTINEL | PASS — 0B/0M open; 1 minor fixed, 1 note |
| ARGUS | PASS — 0B/0M open; 1 major + 1 minor remediated, 3 notes |

### Findings remediated in this block

1. `TransactionDescriptionContractTest` same-date ordering assumption —
   matched rows by id (ordering is `occurredOn DESC, id DESC`).
2. `CsvExportTest` pre-existing header literal — updated to the new
   additive schema.
3. Import-path >255 statement text would have hit `VARCHAR(255)` —
   capped at the entity before any row could fail.
4. Compose harness `setContent` twice — split into two tests.
5. Detail note blank-string defense (`takeIf { it.isNotBlank() }`).
6. `BACKEND-CONTRACT.md` + `PRODUCT.md` + register/matrix drift —
   reconciled (ARGUS A-1/A-2).

### Non-events verified

- No M18 work. No course sync. No push, no tag. Backend human-owned
  paths (`README.md`, `HUONG-DAN-SU-DUNG.md`, `docs/SPENDWISE-REQ.md`)
  untouched. Reference repo untouched.
