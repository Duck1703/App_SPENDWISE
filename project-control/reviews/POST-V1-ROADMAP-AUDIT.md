# Post-V1 Roadmap Audit — independent verification

**STATUS: ANALYSIS — NOT_CANONICAL**

Baseline: Android `cefec23` (V1 approved), backend `8194daa`,
reference `5f9622c`. All backend claims re-derived from Spring source,
not from prior documents.

## Sources audited

- All 12 controllers, all request/response DTOs, `SecurityConfig`,
  `ExchangeRateClient`, `CsvExportService`, `ImportReportService`
  surface, `ImportBatchRepository`, `Transaction` entity,
  `CreateTransactionRequest`/`TransactionResponse`.
- `POST-V1-BACKEND-GATES.md` and `POST-V1-ROADMAP-PROPOSAL.md`
  line-by-line against that source.

## Verifications (falsify-or-confirm)

| Claim | Result |
| --- | --- |
| `GET /api/imports/{id}` exists, owner-scoped | **CONFIRMED** — `ImportReportController`, `currentUser.id()` filter, 404 on miss/foreign |
| `GET /api/transactions/export` exists | **CONFIRMED end-to-end** — `text/csv`, `Content-Disposition: attachment; filename=transactions.csv`, optional `accountId` (404 foreign), header `occurredOn,type,amount,currency,category,sourceRef`, `toPlainString` precision, read-only TX |
| `GET /api/fx/convert` exists | **CONFIRMED with caveats** — live external call (`ExchangeRateClient`, bounded retry, `Optional.empty` on failure → `success:false`, never fabricated); `setScale(2, HALF_UP)`; same-currency short-circuit; **display-only by explicit design comment; no rate timestamp; no caching** |
| "0 PUT/PATCH anywhere" | **CONFIRMED** — but matrix undercounted: 26 mappings, and **2 DELETEs exist** (`recurring/{id}`, `rules/{id}`; both consumed by app) |
| No refresh-token | **CONFIRMED** — register/login/logout only; stateless JWT + `TokenBlacklist` |
| `occurredOn` "unsettable + unreturned" | **CONFIRMED at API level — REFINED**: persisted on the entity, defaults `LocalDate.now()`, set internally by recurring/import, **exported in CSV**. Gap is DTO contract, not data model |
| No import list | **CONFIRMED — REFINED**: `ImportBatchRepository.findAllByUserId` exists but is **never invoked** — BE-004 is a thin service+controller gap |
| Transfers internal-only | **CONFIRMED — REFINED**: `transferRef` column persisted; only the endpoint surface is missing |
| description never persisted | **CONFIRMED** — entity has no column; request field exists for rule-matching only |

## New findings not in prior docs

1. `AdvancedStatisticsService` + `statistics/` package exist **with no
   controller** — latent server capability; `StatisticsService` basic
   path is consumed only by `DashboardReadService`.
2. Export leaks `occurredOn` while the transaction API hides it — a
   contract inconsistency that strengthens REQ-BE-001.
3. FX `setScale(2)` makes the endpoint **unsuitable for portfolio math
   on 0-decimal currencies (e.g. VND)** — additional evidence the
   endpoint ≠ totals feature.
4. Import report rows carry `transactionId` — a post-import "go to the
   created transaction" UX is backend-supported today… except
   `GET /api/transactions/{id}` **does not exist** (transactions have
   no detail endpoint at all — new finding; list-only). Any
   transaction-detail deep-link is itself backend-gated.

## Corrections applied to prior artifacts

- `POST-V1-ROADMAP-PROPOSAL.md` rewritten (capability matrix +
  corrected FX framing + BE-001/BE-004 gap refinement).
- `POST-V1-BACKEND-GATES.md` superseded by `POST-V1-CAPABILITY-MATRIX.md`
  (kept in place as M8-era record; corrections cross-referenced).

## Candidate comparison (qualitative)

| Candidate | User value | Backend | Android reuse | Complexity | Fin risk | Sec risk | Product fit | Disposition |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| CSV ledger export | HIGH (data ownership, backup, spreadsheet) | READY | HIGH (SAF mirror exists) | LOW-MED | NONE | LOW | HIGH | **M9 component** |
| This-device recent import reports | MED-HIGH (revisit failures/dupes) | READY (detail); list absent | HIGH (DTO exists) | MED (new small local store) | NONE | LOW (pointers only) | MED — needs local-pointer decision | **M9 component (decision-gated)** |
| Standalone FX converter | LOW-MED | READY | MED | LOW | LOW (display-only) | LOW | MED — honesty copy burden | Later/optional |
| Transaction detail deep-link | MED | **GATED** (no GET/{id}) | — | — | — | — | HIGH | Backend req |
| BE-001 dates | **HIGHEST** | GATED (thin contract add) | — | — | LOW | LOW | HIGH | First backend-dependent milestone |
| BE-003 transfers | HIGH | GATED (medium) | — | — | MED | MED | HIGH | After dates |
| BE-005/006/011 mutations | HIGH | GATED | — | — | MED | MED | HIGH | Editing cluster |
| BE-002 refresh | MED | GATED (security-sensitive) | — | — | — | MED | MED | Later |
| BE-007 pagination | MED | GATED | — | — | — | — | MED | When ledgers grow |
| BE-008/009/010 | LOW-MED | GATED | — | — | — | — | MED | Backlog |

## Challenge pass (self-falsification)

- *Is export truly ready?* Yes — deterministic, owner-scoped, canonical
  server output; SAF needs no permission. No local aggregation involved.
- *Does import-report-history create local financial truth?* Only
  pointers (batchId/fileName/importedAt/accountId) — no amounts. But it
  IS device-local truth about which batches exist → must be labeled
  "imports on this device" and cleared on cross-user sign-in
  (generation-safe). Human decision recorded.
- *Is bundling coherent?* Export + import reports share the "More →
  data & records" surface and both are pure-read consumers. Coherent;
  combined scope stays one milestone.
- *Safer alternative?* Export-only M9 is the fallback if Human rejects
  local batch pointers — still viable but thin.
- *Any unsafe writes?* None — M9 candidates are all reads.
- *Milestone too trivial?* Combined = MEDIUM; alone, export is SMALL.
- *Did we confuse endpoint-exists with feature-ready?* The import
  detail endpoint alone cannot discover past batches — resolved via
  the local-pointer decision or BE-004.

## Unresolved Human decisions

1. Local batch-pointer retention for import reports (vs waiting for
   REQ-BE-004).
2. Whether backend work is in-scope for this program at all (all
   high-value items after M9 need it).
3. FX totals ethics — V1 stance upheld here; any future change is a
   Product decision.
4. Standalone FX converter worth shipping at all.
