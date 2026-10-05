# Post-V1 backend gates — analysis only (M8 §28)

Backend `D:\spendwise` @ `8194daa` is authoritative and untouched. This
document is **analysis only** — no post-V1 feature is implemented or
authorized. M9 is not defined.

## Method

Diffed the Android `SpendwiseApi` surface (23 calls) against every
Spring `@*Mapping` in the backend (27 mappings, 0 PUT/PATCH anywhere).

## Backend-ready surfaces the app does not consume

| Backend endpoint | Controller | Candidate feature | Gate |
| --- | --- | --- | --- |
| `GET /api/imports/{id}` | `ImportReportController` | Import history / per-batch report detail | App has no import-list UI — product decision first |
| `GET /api/transactions/export` | `CsvExportController` | CSV export to user storage | Needs save-to-file UX + permission-free SAF flow; product decision |
| `GET /api/fx/convert` | `FxController` | Opt-in FX-converted dashboard totals | Deliberately refused in V1 (honest "mixed currencies" copy) — Human product call whether a conversion is ever honest enough |

## Backend gaps (features with NO endpoint — report, never patch)

| Feature | Backend state | Consequence |
| --- | --- | --- |
| Transaction edit / delete / correction | No PUT/PATCH/DELETE on `/api/transactions` | "Entries can't be edited" copy is honest on both ends; any edit UX is backend-gated |
| Account edit / delete | No PUT/DELETE on `/api/accounts` | Same — read+create only today |
| Budget edit / delete | No PUT/DELETE on `/api/budgets` | "Budgets can't be edited" is backend-accurate |
| Rule edit | No PUT/PATCH on `/api/categorization-rules` | Delete+recreate is the only path today |
| Category create/update/delete | `GET /api/categories` only (seeded set) | User-defined categories are backend-gated |
| Token refresh / change password / account deletion | `register/login/logout` only | Session expiry = full re-login (V1-consistent); refresh-token UX is backend-gated |

## Alignment check

Every Android copy claim that the UI makes about mutability
("can't be edited right now", "a limit applies until its month ends")
was verified against the backend surface — the app promises nothing the
backend cannot do. The three unconsumed GETs are additive reads, safe
to adopt later without contract churn.

## Recommendation for the next milestone (when Human-defined)

Highest-value, backend-ready work would be **import history**
(`GET /api/imports/{id}`) and **CSV export** — both are pure consumers
of existing endpoints. Any mutating feature (edit/delete anywhere)
requires backend work first and is reported, not patched, per
WORKSPACE-BOUNDARIES.
