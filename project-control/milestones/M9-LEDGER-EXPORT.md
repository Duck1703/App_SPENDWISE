# M9 — Ledger Export

```
STATUS: CURRENT (implementation)
M9_HUMAN_AUTHORIZATION: EXPLICIT — Human approved "Ledger Export" only,
  rejecting the bundled "Recent Import Reports" from M9-DECISION-BRIEF.
BASELINE: cefec23 → 43f7a6b (Post-V1 planning docs)
BACKEND: D:\spendwise @ 8194daa — authoritative, untouched.
```

## Human decisions (authoritative)

- `M9 = Ledger Export` — the single user outcome.
- `LOCAL_IMPORT_POINTER_STORE = REJECTED`
- `THIS_DEVICE_IMPORT_HISTORY = REJECTED`
- `IMPORT_HISTORY = BACKEND_GATED` (needs a list endpoint, REQ-BE-004)
- `FX = DEFERRED`; `FX_PORTFOLIO_TOTALS = FORBIDDEN`
- `TRANSACTION_DATES = BACKEND_CONTRACT_GATED` (BE-001)
- `MUTATIONS = BACKEND_GATED`; `REFRESH_TOKEN = BACKEND_GATED`
- `M10 = NOT_CANONICAL / NOT_STARTED`

## User outcome

A signed-in user opens **More → Ledger Export**, picks a scope —
**All accounts** (default) or one owned account — requests the
server-generated CSV, chooses a destination via the system save
dialog (Storage Access Framework), and gets an honest
success/error outcome. The delivered file is byte-identical to the
server response.

## Backend contract (re-verified @8194daa, `CsvExportController`)

- `GET /api/transactions/export` — authenticated (`/api/**` filter),
  `accountId` optional query (`UUID`; omitted = whole ledger;
  foreign/unknown → 404).
- `200 text/csv`, `Content-Disposition: attachment; filename="transactions.csv"`.
- Columns: `occurredOn,type,amount,currency,category,sourceRef`;
  `amount.toPlainString()`; `@Transactional(readOnly)`.

## Architecture decisions (M9-local ADs)

- **Opaque artifact rule**: the CSV is a server-generated artifact.
  Android never parses it — no dates (BE-001 stays gated), no
  amounts, no domain objects. `occurredOn` inside the file is
  backend-owned data the UI does not understand or surface.
- **Streaming pipeline**: `@Streaming` Retrofit `Response<ResponseBody>`
  → app-private `cacheDir` temp file → `ContentResolver` stream to the
  SAF destination → delete temp. Whole-file buffering forbidden.
- **Filename**: `Content-Disposition` filename used when present and
  safe; sanitized (basename-only, control chars stripped); fallback
  `spendwise-ledger.csv` when absent/malformed.
- **Session/generation safety**: capture `sessionGeneration` before
  the request; a response landing after a generation boundary throws
  `SessionEndedException` — User A's artifact is deleted and never
  published to User B. Temp artifacts deleted on success, picker
  cancel, save failure, generation change, and screen disposal.
- **No permissions**: `CreateDocument("text/csv")` SAF only — no
  storage permissions, no cache outside `cacheDir` (already
  backup-excluded).

## Scope exclusions

Import history/pointers, FX anything, transaction dates, mutations,
transaction-detail deep links (no `GET /api/transactions/{id}`),
pagination, offline export, background downloads, CSV preview/parsing.

## IA / UX

`More → Ledger Export` row (existing `MoreRow` pattern) → pushed
typed route `LedgerExportRoute` over the preserved shell.
Screen: title + explanation, scope dropdown ("All accounts" default),
primary `Export CSV` action, honest loading/error/success banners.
Save cancellation is not an error; success = completed destination
write, not completed download.

## Quality gates (M9 acceptance)

- Contract tests (MockWebServer): path, query omission/value, csv
  body streaming, Content-Disposition, 401/404/500/network mapping.
- Byte-identity test incl. UTF-8/commas/quotes/CRLF.
- Filename sanitization tests; temp-file lifecycle tests;
  session-generation staleness tests; ViewModel + Compose tests.
- `testDebugUnitTest`, `lintDebug`, detekt, `assembleRelease`,
  serializer scan re-derived from source; connected suite on physical
  device if available; Sentinel + Argus reviews; external audit gate.

## Status

TECHNICAL_COMPLETE pending gates below; `M9_READY_FOR_EXTERNAL_AUDIT`
set at closeout; `M9_HUMAN_APPROVED=NO` until external audit + Human.
