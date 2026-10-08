# M17 EXTERNAL-AUDIT HANDOFF — Persisted Transaction Descriptions (REQ-BE-010)

```
M17_READY_FOR_EXTERNAL_AUDIT=YES
M17_HUMAN_APPROVED=NO          (audit gate precedes human approval)
M17_EXTERNAL_AUDIT=NOT_RUN     (excluded from the delivery block)
SPENDWISE_STATUS=M17_READY_FOR_EXTERNAL_AUDIT
NEXT_ACTION=ONE_LARGE_INDEPENDENT_EXTERNAL_M17_AUDIT
M18_DEFINED=NO
M18_STARTED=NO
```

The independent M17 external audit has NOT been performed — this
document is the handoff into it. Auditors: treat every number below
as a claim to be falsified, not as evidence.

## Scope delivered

One bounded capability: **REQ-BE-010 — persisted transaction
descriptions**. The `description` field the create endpoint always
accepted (`@Size(255)`) and always consumed for rule-matching — then
dropped — is now persisted on `Transaction`, returned on every read
surface (create/list/page), included in CSV export, and displayed on
the Android transaction detail screen. The same text still feeds the
rules engine; explicit category still wins.

Deliberately NOT in scope: transaction edit/delete (REQ-BE-005 stays
product-gated), server search (unblocked by this but a separate
milestone), notes on recurring templates/transfers (generated rows
carry `null` — no statement text exists), list-row note previews,
any mutation endpoint, M18, course sync.

## Baseline and commits

```
Android repo:  D:\app_spendwise\spendwise-android  (branch main)
  M16 head:    63fef6472d7cd371265fe2e215e9e080ad21e8db
  M17 gov:     f304c5b  docs: approve M17 transaction notes
  M17 slice:   445ffb0  feat(transactions): M17 persisted description
  M17 closeout:(this commit — reviews, doc reconciliation, handoff)
Backend repo:  D:\spendwise  (branch main)
  M16 head:    ca5b38031cc23964b4545d4597e5e99eef2ab9a5
  M17 delivery: 9c123f7 feat(transactions): persist create-accepted
                description — REQ-BE-010 (M17)
  Backend diff: exactly 8 files (+307/−9); human-owned docs
                (README.md, HUONG-DAN-SU-DUNG.md, docs/SPENDWISE-REQ.md)
                untouched; course-sync debt recorded as ca5b380..9c123f7.
Reference:     D:\app_spendwise\kotlin-accelerator-ai @ 5f9622c — read-only,
               untouched.
```

## Backend contract (what changed)

No new endpoint. No removed/renamed field. All changes additive:

```
POST /api/transactions
  request:  unchanged — description?: string @Size(255)
  response: TransactionResponse gains `description: string | null`
            (appended record component; null for no-note rows AND all
            pre-M17 rows)
GET /api/transactions (+ /page, any filter combination)
  same TransactionResponse shape — description returned per row
GET /api/transactions/export
  header: occurredOn,type,amount,currency,category,sourceRef
          → occurredOn,type,amount,currency,category,sourceRef,description
          (trailing column, RFC4180 quoting via existing field())
Entity:   transactions.description VARCHAR(255) NULL (V12 — additive,
          no default, no rewrite, no index)
Normalization at the entity boundary:
  null       → null
  blank/ws   → null
  text       → stripped
  >255       → truncated to 255   (import path only; request path is
                                  already @Size(255)-rejected at 400)
Semantics per creation path:
  manual     → user note persisted
  import     → statement row description persisted (capped)
  recurring  → null   transfer legs → null   legacy → null
Rule engine: still receives the RAW request value before persistence —
             categorization input unchanged.
Errors:     400 >255 / bad enum-UUID / unauth 401 / foreign 404 — all
            pre-existing machinery.
```

## Android surface

- `TransactionDto.description` + domain `Transaction.description`
  (nullable; response-side only on the DTO — the request DTO already
  carried the field).
- `TransactionRepository.create(hint → description)` — parameter
  renamed for honesty; `isNotBlank` → omitted from the wire body.
- `CreateTransactionUiState.description` /
  `CreateTransactionViewModel.onDescriptionChange` — total rename;
  input bound `MAX_DESCRIPTION_LENGTH = 255` (server parity).
- `CreateTransactionScreen` — label "Note (optional)", support text
  "Saved with the transaction. Also used to auto-categorize."
- `TransactionDetailScreen` — full-width Note block inside the detail
  card, rendered only when the description is non-blank.
- No new routes/screens; session-generation, no-replay, and
  ambiguous-outcome paths untouched (no mutation code modified).

## Gate evidence (all re-executed on final source, not assumed)

| Gate | Result |
|---|---|
| Backend focused | `TransactionDescriptionContractTest` **10/10** + `CsvExportTest` **2/2** (MockMvc + real Postgres) |
| Backend full regression | **393/393**, 0 failures, 0 errors, 0 skipped (383 → +10; surefire-report count) |
| Flyway | 12 migrations validated; V12 applied at boot |
| Android JVM fresh | **657/657**, 0 fail, 0 skip (653 → +4; XML-report count) |
| `lint` | PASS — 0 Error/Fatal, 22 warnings (all pre-existing) |
| `detekt` | **0 code smells** (227 files) |
| `assembleDebug` / `assembleRelease` | PASS (R8 + lintVital green) |
| R8 serializer audit | `TransactionDto`, `$$serializer`, `Companion.serializer()` kept unobfuscated in `mapping.txt` |
| Merged manifest / backup | unchanged from accepted baseline (allowBackup + dataExtractionRules + fullBackupContent) |
| Physical Pixel 4 XL (`99041FFBA006F0`) | **196/196 × 2** on final source, 0 fail, 0 skip (193 → +3) |
| Install + launch | explicit `adb -s` install + component start → `MainActivity` top-resumed |

## Reviews

- `M17-SENTINEL-REVIEW.md` — **PASS**, 0 Blocker / 0 Major /
  1 Minor (fixed) + 1 Note. Requirement→test mapping included.
- `M17-ARGUS-REVIEW.md` — **PASS**, 0 Blocker / 0 Major open /
  1 Major + 1 Minor remediated in-task + 3 Notes.

## Findings remediated in this block (all documented, none hidden)

1. **Backend — import-path regression caught pre-commit**: statement
   descriptions arrive unbounded; >255 would have hit `VARCHAR(255)`
   and failed the import row. Fixed at the invariant point — entity
   `normalizeDescription` caps at the column; contract test proves
   truncation.
2. **Test — same-date ordering assumption**: new contract test matched
   list rows by index; ordering is `occurredOn DESC, id DESC`.
   Fixed to match by transaction id.
3. **Test — stale CSV header literal**: `CsvExportTest` updated to the
   new additive schema.
4. **Test — Compose harness `setContent` twice** in the new detail
   test → split into shown/hidden cases; physical suite green.
5. **UI — blank-string defense**: detail renders note only when
   non-blank (server emits null today; defense is free).
6. **Docs — contract drift (ARGUS Major)**: `BACKEND-CONTRACT.md`
   documented "never persisted or returned" in 6 places; `PRODUCT.md`,
   requirement register, and capability matrix stale. All reconciled
   with `9c123f7` evidence; BG-004 marked RESOLVED.
7. **Env note (not a defect)**: backend tests need
   `SPENDWISE_DB_PASSWORD` + `SPENDWISE_JWT_SECRET` (sourced from
   `D:\spendwise\.env.local`); first run without them cascaded
   context-load failures — reran green with env.

## Findings carried into audit (documented, none hidden)

1. **NOTE — silent 255-char input truncation**: `take(255)` drops
   tail chars with no counter — consistent with every `MAX_*` guard
   in the app; server bound unreachable on the wire.
2. **NOTE — note is detail-only**: no list-row preview (milestone
   scope); a future milestone may add it.
3. **NOTE — verbatim internal whitespace**: edges stripped, internal
   newlines preserved — intentional "verbatim note" semantics; CSV
   quoting covers them.

## Regression-risk assessment for the auditor

Minimal blast radius: the backend delta is one nullable column, one
record component, one pass-through argument, one CSV column — no
endpoint, no mutation surface, no scheduler/balance/reconciliation
interaction (notes never enter computation). Ledger stays append-only.
The old 8-arg `Transaction` ctor delegates `null` so every test-fixture
and generated-row path defaults correctly. Android delta is a field +
rename + one conditional UI block. Only semantic risk class — "did
persisting change rule matching" — is falsified by the contract test
proving rules still categorize from the same text.

## What the auditor should falsify

- Persistence claim: create with a note → fetch row → `description`
  returned on create response, list, and page surfaces.
- Blank semantics: POST `{"description":"   "}` → stored NULL → wire
  null (not `""`).
- Boundary: 255 persists; 256 → 400.
- Import cap: import a row with >255-char description → row succeeds,
  note truncated (not failed).
- CSV: header + quoting on a note containing `,` `"` `\n`.
- Generated rows: recurring-generated + transfer legs → `description:
  null`.
- Owner isolation: second user's reads never surface the note.
- Client parity: >255 input cannot reach the wire; blank note sends
  no `description` key at all.
- Legacy claim: any pre-M17 row returns `description: null`.
- Physical evidence: re-run the connected suite; expect 196/196.

## Handoff boundary

This delivery ends here. The external audit is the next and only
remaining M17 action; course synchronization (`ca5b380..9c123f7` +
contract summary above) is a separate deferred task. M18 remains
undefined — selecting it is a future Human decision after audit
acceptance.
