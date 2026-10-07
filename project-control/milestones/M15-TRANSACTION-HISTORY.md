# M15 — Scalable Transaction History (pagination + date-range)

```
STATUS: CURRENT — defined, implementation in progress.
M14_COMPLETE=YES (ACCEPTED + Human-approved; physical gate passed
  Pixel 4 XL 180/180 ×2 — closeout 256c2ce)
M15_DEFINED=YES
M15_STARTED=YES
M15_TECHNICAL_COMPLETE=NO
M15_READY_FOR_EXTERNAL_AUDIT=NO
M15_HUMAN_APPROVED=NO
M15_HUMAN_AUTHORIZATION=EXPLICIT (THE HUMAN's M15 delivery block:
  "select exactly one substantial capability ... implement the
  complete backend + Android vertical slice")
CANONICAL_REQUIREMENT=REQ-BE-007
M16_DEFINED=NO
M16_STARTED=NO
SPENDWISE_STATUS=M15_IN_PROGRESS
BASELINE: Android 256c2ce (M14-accepted) · Backend 4ed4f81
```

## Selection rationale (decision record)

Candidates re-evaluated against CURRENT canonical sources
(`PRODUCT.md` §19/§39/§41, `POST-V1-BACKEND-REQUIREMENTS.md`,
`POST-V1-CAPABILITY-MATRIX.md`) and the actual backend source
(`D:\spendwise` @ `4ed4f81` — full controller/repository inventory
taken, not assumed). M9–M14 removed from candidacy (delivered).

| Candidate | Verdict |
|---|---|
| REQ-BE-005 transaction correction | **Rejected**: PATCH-vs-void is an unresolved Human product decision; §23 delivery guard bars destructive financial-history mutation without explicit Product semantics. |
| REQ-BE-006 account edit/archive | Rejected: "define archived-account semantics" (picker hiding vs balances) is a product decision; medium risk, lower pain. |
| **REQ-BE-007 pagination + date-range** | **SELECTED**. PRODUCT §19: date filter "deferred pending BE-001/BE-007" — BE-001 (occurredOn) is delivered, unblocking it. §39: "slow loads → BE-007 priority" — ledger now grows via manual+import+recurring+transfer writes. Dead `Pageable` methods already exist on `TransactionRepository`; bounded contract, no schema, no product ambiguity. Highest user value among safe candidates: real browsing of a growing ledger. |
| REQ-BE-008 multipart size | Rejected: trivial config; canonical doc says "can ride any backend release" — not a milestone. |
| REQ-BE-009 hardening cluster | Rejected: low user-visible value; not a coherent user capability. |
| REQ-BE-010 persisted description | Rejected: `description` currently means categorization-hint only (§23 guard — silently persisting it invents product semantics); needs schema + Human decision. |
| Server-side text search (`q`) | Rejected: `description` is NOT persisted (BE-010 undelivered) — a search param would match only category/type, which the existing filters already cover; near-fake search violates the honesty invariant. |
| Standalone FX converter | Rejected: product-gated display-only endpoint; low user value; honesty burden. |
| Recurring/rule PATCH lifecycle | Rejected: not in the BE register; narrower user value than REQ-BE-007. |
| Profile / category management | Rejected: zero backend surface — pure invention. |

Scoring: priority 4 + user value 4 + backend readiness 3 + android
readiness 3 + testability 3 + leverage 3 − ambiguity 1 − security 1 −
schema 0 − scope 3 = **16** — highest defensible.

## User outcome

The Transactions tab renders the ledger in bounded server pages
(newest first) instead of one unbounded fetch; scrolling loads the
next page; a From/To date-range filter narrows by `occurredOn`; the
existing account/type/category/uncategorized/source filters remain
and now execute **server-side** (required for honest paging — a
client pass over page N would hide matches on later pages).

## Backend contract (bounded prerequisite — human-authorized)

Backend `D:\spendwise` gains exactly one read-only endpoint; the
existing `GET /api/transactions` contract is unchanged (still serves
the whole filtered list to its current consumers — detail lookup,
import surfaces).

- `GET /api/transactions/page` — owner-scoped paginated+filtered
  ledger read → `200 TransactionPageResponse`
  `{content:[TransactionResponse], page, size, totalElements,
  totalPages, hasNext}`.
  - Params (all optional unless noted):
    `accountId` UUID — unknown/foreign → 404 (same as list);
    `type` enum — bad value → 400 (Spring binding);
    `category` code — unknown code → empty page (list parity);
    `uncategorized` boolean — `category`+`uncategorized` together → 400;
    `provenance` enum MANUAL|IMPORTED|RECURRING|TRANSFER|UNKNOWN —
    maps to `sourceRef LIKE '<P>:%'` (the exact client derivation);
    UNKNOWN → `sourceRef IS NULL` (impossible today → empty, honest);
    `from`/`to` ISO `yyyy-MM-dd` on `occurredOn` — `from>to` → 400;
    `page` ≥0 (default 0 — negative → 400);
    `size` 1..100 (default 50 — outside → 400).
  - Ordering: `occurredOn DESC, id DESC` (deterministic; `occurredOn`
    alone is non-unique and `id` is a UUID — still a stable
    tiebreaker for pagination correctness).
  - Read-only; no mutation, no ledger touch.

No schema change, no new error shapes, no auth-surface changes.

## Android scope

- `TransactionDtos`: `TransactionPageDto` (serializable; envelope).
- `TransactionsService.page(...)` — `GET api/transactions/page`.
- `TransactionRepository.listPage(filters,page,size)` → domain
  `TransactionPage`; generation-guarded; page content appended into
  the SessionCache so `findById` keeps working from loaded rows.
- `TransactionsUiState`: `Content` gains `totalElements` (server
  count — honest), `hasMore`, `loadingMore`; filters gain
  `from`/`to` `LocalDate?`.
- `TransactionsViewModel`: `loadNextPage()` (guarded), refresh
  re-fetches page 0 and REPLACES items; all filters now wire params.
- `TransactionsScreen`: LazyColumn end-of-list trigger + footer
  loading row + From/To chips opening Material3 date pickers
  (pattern reused from M11 create-date picker); count label shows
  server `totalElements`.
- M13 integration: GET — authenticator retry applies; no mutation in
  this milestone → the no-replay invariant is trivially preserved
  and still wire-proven for regression.

## Ownership / privacy

Owner-scoped `userId = :callerId` predicate — identical to the list
contract. Foreign `accountId` → 404 indistinguishable from missing.
Adversarial User A/User B tests mandatory (B's rows never leak into
A's pages, totals, or date-range results).

## Error semantics

- 400 → validation (from>to, bad page/size, category+uncategorized
  conflict, malformed enum/date)
- 404 → `AppError.NotFound` (foreign/unknown accountId)
- 401 → `Session.Expired` (M13 machinery, unchanged)

## Non-goals

- No change to `GET /api/transactions` (unpaged) — other consumers
  keep working.
- No text search (`q`) — BE-010 undelivered; would be near-fake.
- No sort controls — server fixes `occurredOn DESC, id DESC`.
- No `GET /api/transactions/{id}`; detail keeps resolving from the
  fetched cache.
- No transaction/account mutation; no FX; no description
  persistence; M16 remains undefined.

## Tests

Backend: happy page 0/middle/last, ordering across equal occurredOn,
from/to inclusive bounds, from>to 400, page/size bounds 400,
category+uncategorized conflict 400, provenance→sourceRef mapping,
accountId 404 foreign/missing, cross-user isolation (content AND
totalElements), empty page `200 {content:[]}` not 404, filter
combination coverage.

Android: MockWebServer wire tests (exact path+params, omission of
absent params, 400/404/5xx, request count), repository (page
mapping, generation race, append-to-cache findById, error mapping),
ViewModel (loadNextPage append/dedupe, hasMore end, refresh resets,
filter-change resets pages, expired mid-page, date filter state),
Compose (scroll triggers load-more, footer spinner, date pickers
open/set/clear, a11y labels, vi-VN-safe).

## Gates

Full backend regression (baseline exception: documented
`RecurringSchedulingJobTest` only) · backend package · Android JVM ·
lint · detekt · assembleDebug · compileDebugAndroidTestKotlin ·
assembleRelease + serializer/manifest/backup audit · **physical
Pixel full connected suite** (device attached this milestone) ·
final install+launch sanity · Sentinel · Argus → external-audit
handoff. `M15_READY_FOR_EXTERNAL_AUDIT=YES` only when every gate is
green and review blockers/majors are remediated.

## Course-sync record (deferred)

```
COURSE_UPDATE_EXECUTED=NO
COURSE_SYNC_DEFERRED=YES
BACKEND_RANGE_FOR_FUTURE_SYNC=<recorded on delivery commit>
BACKEND_SYNC_SUMMARY=<recorded on delivery commit>
```
