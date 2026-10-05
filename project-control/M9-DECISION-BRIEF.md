# Proposed M9 — Ledger Export & Recent Import Reports

**STATUS: PROPOSED_ONLY — CANONICAL_M9: NO — HUMAN_APPROVAL_REQUIRED: YES**

## Decision summary for THE HUMAN

| Field | Value |
| --- | --- |
| RECOMMENDED_M9 | Ledger Export & Recent Import Reports |
| WHY | Two coherent "More → Data & records" features; **zero backend work**; server-authoritative; ships real value while backend-gated items wait |
| USER_VALUE | HIGH (export = data ownership/backup/spreadsheet) + MED-HIGH (revisit import failures/duplicates) |
| BACKEND_READY | YES — `GET /api/transactions/export` + `GET /api/imports/{id}` verified end-to-end @8194daa |
| ANDROID_READY | YES — SAF mirror exists, import DTO already matches, More surface ready |
| RISK | LOW — all reads; only new element is a bounded local *pointer* store (no financial data) |
| ESTIMATED_SCOPE | MEDIUM (export SMALL alone) |
| WHAT_WILL_NOT_BE_INCLUDED | FX anything, mutations, pagination, date entry, true cross-device import history |
| ALTERNATIVE_1 | Export-only M9 (if local pointers rejected) — smaller, still viable |
| ALTERNATIVE_2 | Wait for REQ-BE-001 dates milestone — higher value but backend-dependent |
| HUMAN_DECISION_REQUIRED | Local batch-pointer retention: YES/NO |

---

## User outcome

- Export the full ledger (or one account) to a CSV file the user chooses
  via the system save dialog — backup, spreadsheet analysis, data
  ownership. No permission prompts.
- Reopen the row-level report of any import performed **on this
  device** — see which rows failed/duplicated and why, after the fact.

## Why this milestone

- Both endpoints are verified complete at source; Android consumes zero
  new contract surface beyond two GETs.
- Coherent IA home: `More → Export data` + `More → Imports` history
  entry (import upload entry already lives there in V1).
- Zero writes → zero ambiguous-write risk; zero financial math →
  zero money-risk; pure additions to the approved V1 baseline.

## Evidence (backend @8194daa)

- `CsvExportController`: `GET /api/transactions/export?accountId=` —
  auth'd, owner-scoped (404 foreign account), `text/csv` +
  `Content-Disposition: attachment`, header
  `occurredOn,type,amount,currency,category,sourceRef`,
  `amount.toPlainString()`, `@Transactional(readOnly)`.
- `ImportReportController`: `GET /api/imports/{id}` — auth'd,
  owner-scoped 404, returns `ImportBatchSummaryResponse`
  (`batchId, accountId, fileName, importedAt, successCount,
  duplicateCount, failedCount, rows[{rowNumber,status,reason,
  transactionId}]`) — the same DTO POST returns; Android's
  `ImportBatchSummaryDto` matches field-for-field.
- **Gap acknowledged**: no `GET /api/imports` list — history
  discoverability requires local batch pointers (decision gate).

## Backend contract

None required. Both endpoints consumed as-is.

## Android scope

- `More` screen: "Export data" entry → export screen (scope toggle:
  all accounts / per-account picker; file name suggestion;
  `CreateDocument("text/csv")` launcher; write streamed `ResponseBody`
  to the returned `Uri`).
- `Imports` section: recent-imports list (bounded, e.g. last 20) →
  `ImportReportScreen` reusing the V1 result-detail UI (rows, statuses,
  reasons) via `GET /api/imports/{id}`.
- New small infra: raw-body service method; `CreateDocument` launcher;
  `ImportHistoryStore` DataStore holding **pointers only**
  (`batchId, fileName, importedAt, accountId` — no amounts, no rows).
- Batch pointer recorded on each successful V1-style upload.

## Explicit exclusions

- No cross-device import history (that's REQ-BE-004, server-side).
- No `transactionId` deep-links (backend has no `GET
  /api/transactions/{id}` — row detail links degrade to nothing).
- No FX, no export filtering beyond `accountId`, no scheduled exports.
- No import re-run/duplicate-batch operations.

## Navigation/IA

`More` stays the only entry point — no fifth tab, no new top-level
destination. Routes: `More → ExportData`, `More → ImportHistory`,
`More → ImportHistory/{batchId}` (typed nav, `@Serializable` routes
per convention).

## Data authority

- CSV bytes are server-generated canonical output — the client never
  assembles export content.
- Pointer store is **not** financial truth: it remembers *which
  batches this device uploaded*, nothing about amounts/balances.
  Honest copy: "Imports on this device" label, never implied global
  history. Cleared on cross-user sign-in (generation guard); bounded
  retention (e.g. 20 entries).

## Session-generation behavior

- Both flows are reads under the normal authenticated path; a 401
  drives the existing `Session.Expired` → reauth-resume flow.
- Pointer store writes happen post-upload inside the active session
  only; store is user-scoped and wiped on user switch.

## Financial correctness

- No client arithmetic anywhere: the CSV is byte-for-byte server
  output; report rows are server-computed counts/statuses.
- BigDecimal untouched — no amounts are even displayed in M9 UI beyond
  what V1 already renders (import report shows statuses, not sums).

## Error behavior

- Export: network failure → honest retry banner (same error-mapping
  pattern as V1); 404 account → "account unavailable"; partial write
  to SAF → delete partial, report failure.
- Report: 404 batch (foreign/deleted) → "report unavailable" empty
  state; list renders empty-honest when no device imports exist.

## Ambiguous-write policy

N/A — milestone is read-only. No automatic replay of anything.

## Accessibility

- File-save flow exposes progress + success via live region; export
  scope control has labeled radio semantics; report rows reuse V1's
  merged-CD row pattern; 48dp targets; money-not-by-color N/A (no new
  money rendering).

## Performance

- Export streamed via `ResponseBody` — no full-string materialization
  on the client; write through `contentResolver` output stream.
- Pointer list is bounded; report detail is one fetch.

## Test strategy

| Layer | Tests |
| --- | --- |
| MockWebServer contract | byte-exact CSV passthrough incl. `Content-Disposition`; `accountId` query param; 404 mapping; import-report DTO parse vs real payload shape |
| Repository | export streams body→sink correctly; report fetch maps DTO→domain incl. UNKNOWN status fallback; pointer store bounded+cleared-on-generation |
| ViewModel | export success/failure state; report load/error/404; pointer list ordering |
| Compose | export screen states; history list; report detail rows reuse existing semantics |
| Navigation | typed routes for the three new destinations |
| Regression | full JVM suite; connected compile; no V1 behavior change |

## Sentinel expectations

Contract tests prove the exact wire shapes; pointer-store isolation
proven cross-user; no secrets/PII in pointers.

## Argus expectations

Independent verification that: CSV never touches client formatting;
pointers hold no financial data; honest "this device" labeling;
401 path preserved.

## Definition of done

- Both flows reachable from More; CSV lands in user-chosen location
  byte-exact; report detail renders server truth incl. failure reasons.
- All V1 gates green (JVM, connected, release, lint, detekt, serializer
  scan); no production V1 behavior regression.

## External-audit gate

M9 ships only after an external audit equivalent to M8's verifies
export byte fidelity + pointer-store privacy boundaries on a
physical device.

---

## Fallback scope (if Human rejects local pointers)

**M9-lite: Ledger Export only.** Same contract minus the import
history items; pointer store deferred until REQ-BE-004 ships a real
list. Remains a coherent (SMALL) milestone.
