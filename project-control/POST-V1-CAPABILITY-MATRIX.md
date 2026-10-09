# Post-V1 Capability Matrix — source-of-truth (analysis only)

**STATUS: PROPOSED_ONLY — NOT_CANONICAL — HUMAN_APPROVAL_REQUIRED**

Reconstructed from `PRODUCT.md` §41 BE register + full Spring mapping
audit at backend `8194daa` + read-only Android readiness review.
Every row cites source evidence; no scoring without rationale.

## Backend surface (verified @8194daa)

26 method mappings across 12 controllers. Mutations: **2 DELETEs**
(`recurring/{id}`, `categorization-rules/{id}` — both consumed by the
app). **Zero PUT/PATCH** anywhere. No pagination (3 query filters on
`GET /api/transactions`: `accountId`, `category`, `type` — verified;
`Pageable` absent from `TransactionRepository`).

## Backend-ready (endpoint consumed-able today, zero backend change)

| ID | Name | Backend status | Android status | Risk | Disposition |
| --- | --- | --- | --- | --- | --- |
| BR-EXPORT | `GET /api/transactions/export` → CSV ledger export | **COMPLETE**: auth'd (filter), owner-scoped, `text/csv` + `Content-Disposition: attachment`, optional `accountId` (404 foreign), columns `occurredOn,type,amount,currency,category,sourceRef`, `toPlainString` precision | Absent; needs raw-body Retrofit call + `CreateDocument` SAF (mirror of existing `OpenDocument` infra) | LOW (read-only, server-canonical) | **BACKEND_READY** |
| BR-IMPREPORT | `GET /api/imports/{id}` → per-batch report detail | **COMPLETE for detail**: auth'd, owner-scoped 404, rich `ImportBatchSummaryResponse` (counts + per-row status/reason/transactionId). **NO list endpoint** — detail-by-known-id only | `ImportBatchSummaryDto` already exists (POST reuse); batchId in `ImportBatch` model, never persisted | LOW endpoint / MEDIUM feature (discoverability gap) | **PARTIALLY_READY** — see BE-004 |
| BR-FX | `GET /api/fx/convert` → ad-hoc conversion | **COMPLETE for a converter tool**: auth'd, `amount`/`from`/`to`, same-currency → rate 1, else live external rate → `setScale(2, HALF_UP)`, `success:false` on provider failure (never fabricated). Display-only by design; **no rate timestamp** | Deliberately refused in V1 for totals (honest mixed-currency copy) | MEDIUM (external dep, honesty framing) | **BACKEND_READY_BUT_PRODUCT_GATED** for totals; small standalone-converter option |

## BE register (PRODUCT.md §41) — audited

| BE | Name | Backend truth @8194daa | Gap shape | Disposition |
| --- | --- | --- | --- | --- |
| BE-001 | `occurredOn` settable + returned | **Persisted** on `Transaction.occurredOn` (defaults `LocalDate.now()` server-side); internal `TransactionCreationCommand` carries it (recurring/import set real dates). **`CreateTransactionRequest` lacks the field; `TransactionResponse` omits it** — but CSV export leaks it | **Contract-level, not data-model**: add field to request+response DTOs + validation | BACKEND_GATED (small backend change, highest user value) |
| BE-002 | Refresh token / longer session | `AuthController` = register/login/logout only; `SecurityConfig` stateless JWT + `TokenBlacklist`; no refresh endpoint/entity | New token type, rotation/revocation storage, reuse detection, logout semantics | BACKEND_GATED (security-sensitive, medium-large) |
| BE-003 | Public transfer endpoint | **`POST /api/transfers` shipped (backend `8803396`)** — atomic own-account pair, owner-scoped 404s, pre-write same-currency guard; `transfer_ref` links both legs | Delivered (M12): controller + DTOs + Android create flow | DELIVERED (M12) |
| BE-004 | Import batch list | `ImportBatchRepository.findAllByUserId` **exists but is never called**; no list controller method | **Thin**: service method + `GET /api/imports` returning summary list | BACKEND_GATED (small — one endpoint away) |
| BE-005 | Transaction edit/delete (or void) | Nothing on `/api/transactions` | Mutation endpoint + reversal/audit semantics | BACKEND_GATED (financial-correctness, medium) |
| BE-006 | Account edit/archive | **`PATCH /api/accounts/{id}` shipped (backend `342e85d`)** — sparse rename+`archived`, owner 404, `@Version` 409, V13 migration | Delivered (M19): edit screen, badges, picker exclusion | DELIVERED (M19) |
| BE-007 | Pagination + date-range/search | `findAllByUserIdWithReferences` returns whole ledger; no Pageable; 3 filters only | Pageable plumbing + `occurredOn` filter params (depends on BE-001 contract exposure for client date semantics) | BACKEND_GATED (medium, deferred until ledgers grow) |
| BE-008 | Multipart size config | Spring ~1 MB default; 500 on oversize | Property/config change | BACKEND_GATED (trivial) |
| BE-009 | Recurring currency defect + mixed-currency stats + typed errors + batch status | Recurring currency validation absent (D14, unreachable via app's currency lock); stats services exist; error payloads generic | Backend hardening cluster | BACKEND_GATED (mixed sizes; low user-visible value now — app already locks currency) |
| BE-010 | Persisted `description` on transactions | **`description` persisted + returned + exported (backend `9c123f7`)** — V12 nullable `VARCHAR(255)`; blank → null; import text capped | Delivered (M17): entity column + response field + Android note entry/display | DELIVERED (M17) |
| BE-011 | Budget edit/delete | Nothing mutating on `/api/budgets`; unique category+month blocks recreation of a wrong current-month budget | PATCH/DELETE + month-boundary semantics | BACKEND_GATED (small-medium, real user pain) |

## Non-BE product candidates (Post-V1, no backend gate)

| Candidate | Status | Notes |
| --- | --- | --- |
| Standalone FX converter (More → tool) | PRODUCT_GATED (decision) | Endpoint exists; display-only semantics fit a tool, **not** portfolio totals. Low user value; external-rate honesty burden |
| FX-normalized portfolio totals | PRODUCT_GATED + honesty risk | Would contradict V1 "never mix currencies" decision; `setScale(2)` unsuitable for e.g. VND precision; no rate timestamp → no rate-of-record. **NOT_RECOMMENDED_YET** |
| Transaction search (client-side over fetched list) | ANDROID_READY-ish | Whole ledger already fetched unfiltered; client search over it is honest (server-authoritative data, transient compute). Small feature, real value — but pagination (BE-007) is the *right* long-term answer |

## Corrections vs prior M8 matrix (`POST-V1-BACKEND-GATES.md`)

1. "27 mappings" → **26**; "0 PUT/PATCH" correct but omits that **2 DELETEs exist** (consumed).
2. "Import history" misnamed → **import report detail-by-known-id**; no list (BE-004, thin gap — unused `findAllByUserId` exists).
3. `occurredOn` described as "unsettable + unreturned" → true at API, but **persisted** (BE-001 is a DTO contract gap, cheaper than implied).
4. FX framed as "opt-in FX totals" → endpoint semantics suit a converter tool only; totals remain product-gated.
5. `transferRef`/description persistence not previously noted; statistics package exists service-only (no controller) — consumed only by `DashboardReadService` via `StatisticsService` (basic stats). `AdvancedStatisticsService` is **not wired to any endpoint**.
