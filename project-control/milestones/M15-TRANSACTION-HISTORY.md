# M15 — Scalable Transaction History (pagination + date-range)

```
STATUS: COMPLETE — external audit ACCEPTED, Human-approved.
Independent external audit: 1 Major + 3 Minor found and remediated
in-task (stale-append error-path state mutation → page-skip gap risk;
48dp touch-target shortfall; dishonest append-error copy; backend
boundary-test gaps); all final gates re-green on remediated source.
Physical Pixel 4 XL (Android 13, SERIAL=<REDACTED>): 186/186 ×2
consecutive on final source (+186/186 ×2 preliminary on delivery
source), install+launch clean.
M14_COMPLETE=YES
M15_DEFINED=YES
M15_STARTED=YES
M15_TECHNICAL_COMPLETE=YES
M15_READY_FOR_EXTERNAL_AUDIT=YES
M15_EXTERNAL_AUDIT_FINAL=ACCEPTED
M15_EXTERNAL_AUDIT_COMPLETE=YES
M15_HUMAN_APPROVED=YES
M15_PHYSICAL_RUNTIME=PASS
M15_STATUS=COMPLETE
M15_HUMAN_AUTHORIZATION=EXPLICIT (M15 delivery block + conditional
  external-audit closeout authorization)
CANONICAL_REQUIREMENT=REQ-BE-007
SCALABLE_TRANSACTION_HISTORY=APPROVED
M16_DEFINED=NO
M16_STARTED=NO
SPENDWISE_STATUS=M15_COMPLETE
NEXT_ACTION=HUMAN_M16_DECISION
BASELINE: Android 256c2ce (M14-accepted) · Backend 4ed4f81
DELIVERED:  Android 3081349 + 6cfcaf2 + 685eb14 + 7488d47
  + audit remediation ebd966a (+ hygiene 48ec4ab) · Backend d3e73c8
  + audit test-strengthening ef08539
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

## Delivery evidence (all reproduced live)

| Gate | Result |
|---|---|
| Backend focused | `TransactionPageContractTest` 12/12 — real MockMvc + Postgres |
| Backend regression | 362 tests, 1 failure = documented `RecurringSchedulingJobTest` date-dependent baseline flake (expected 1 got 2; identical since M10) |
| Backend package | `spendwise-0.1.0-SNAPSHOT.jar` built |
| Android JVM | 615/615, 0 skipped |
| lint / detekt | clean / 0 smells |
| assembleDebug / assembleRelease | green; `app-release-unsigned.apk` 2,451,754 B sha256 `25908dd83246200a8b09…` |
| Physical Pixel 4 XL (Android 13, `ANDROID_SERIAL` forced) | **186/186 ×2 consecutive** + 19/19 re-run after a11y remediation; external audit re-ran **186/186 ×2 preliminary + 186/186 ×2 final-source** |
| Install + launch | `MainActivity` top-resumed, no crash (re-verified post-remediation) |
| Sentinel / Argus / External | PASS / PASS / **ACCEPTED** — reviews in `project-control/reviews/` |
| External-audit remediation | `ebd966a` (VM stale-append guard + 48dp clear target + copy fix + 2 regression tests) · backend `ef08539` (3 boundary/composition contract tests) |
| Post-remediation gates | backend targeted 15/15 · backend full 365 (1 documented baseline flake) · JVM 617/617 fresh · lint/detekt/debug/release green · release sha256 `5075f0e1e3c46b86…` |

Device-only defect found and fixed in-milestone: the From/To chips
start clipped inside the horizontally-scrolling filter row on a
411dp-wide physical phone — screen tests now `performScrollTo()`
before asserting (a real device-only catch the emulator missed), and
the date-chip clear affordance was enlarged from an 18dp icon to a
40dp-wide full-chip-height tappable target (a11y remediation).

## Course-sync record (deferred)

```
COURSE_UPDATE_EXECUTED=NO
COURSE_SYNC_DEFERRED=YES
M14_BACKEND_RANGE_FOR_FUTURE_SYNC=da147b8fe5b1d836fe8e26f0a9fb961bbde80a59..4ed4f813b4aaed3721622dede6dfe3a527db3b89
M15_BACKEND_RANGE_FOR_FUTURE_SYNC=4ed4f813b4aaed3721622dede6dfe3a527db3b89..ef08539
BACKEND_SYNC_SUMMARY=one read-only endpoint GET /api/transactions/page
  (TransactionController#page + TransactionService#searchPage spec +
  TransactionRepository JpaSpecificationExecutor + TransactionPageResponse
  + TransactionProvenance + TransactionPageContractTest, incl. external-audit
  single-day/leap-day/filter-before-slicing contract tests); unpaged list
  endpoint unchanged; no schema migration; no auth-surface change.
```
