# QA REPORT — M18 Server-side Transaction Search

From: ARGUS
To: ATLAS (reconciliation), THE HUMAN (via ATLAS at gate)
Date: 2026-10-08
Evidence reviewed: `project-control/milestones/M18-TRANSACTION-SEARCH.md`,
Android working-tree diff vs `71a4148`, backend working-tree diff vs
`752eac3` (`TransactionService`, `TransactionController`,
`TransactionSearchContractTest`), `M18-SENTINEL-REVIEW.md`, live gate
output (backend 407/407, JVM 672/672, Pixel 200/200 ×2).

## Scope fidelity

Authorized scope: one additive optional `q` parameter on the accepted
M15 `GET /api/transactions/page` contract + the Android search field that
drives it. Nothing more.

Delivered = exactly that. Verified against the non-goals list:

- **No new endpoint** — `TransactionController.page` signature extended,
  no new mapping. `GET /api/transactions` (unpaged) byte-identical.
- **No fuzzy/prefix/ranked search, no index** — one `LOWER … LIKE %q%`
  predicate; V12 column reused, **zero migrations** in the diff.
- **No sourceRef/category/account-name search** — the predicate reads
  `root.get("description")` only.
- **No search UI on other surfaces** — one `SearchField` on
  `TransactionsScreen`; dashboard/budgets/imports untouched.
- **No REQ-BE-005 edit path, no M19** — create form untouched.

The only judgment calls — needle bound at 255 (the column's own bound →
400 not empty page), unindexed LIKE (documented: owner-scoped rows are
small) — are recorded in the milestone §"Backend contract" and defended
by contract tests. Correct calls.

## Architecture fidelity

- Backend: `q` lands exactly at the documented seam — one predicate in
  the existing `searchPage` `Specification`, ANDed with owner/account/
  type/category/uncategorized/provenance/date before pagination. The
  count-query guard (`getResultType() != Long`) still prevents fetch
  joins leaking into `COUNT` — `totalElements` stays honest (proven by
  `qDrivesHonestPageMetadataAcrossPages`).
- Blank handling is defense-in-depth: server `normalizeQuery`
  (`null`/blank → null, predicate drops out) AND client suppression
  (`blank → null`, never on the wire). Verified both directions.
- LIKE escaping order is correct — `\\` first, then `%`, `_`; a literal
  `\%` needle escapes to literal-backslash + literal-percent. Bound
  criteria parameter — no string concatenation into SQL, ever.
- Android: `query` lives on `TransactionFilters` — a data-class field —
  so **every** M15 stale-response/load-more/session-generation guard
  (equality compare, `loadSerial`, `flatMapLatest`, page-0 restart)
  covers it without a single new guard. That is the honest way to add a
  filter axis: no parallel stale machinery to diverge.
- `searchInput` (instant field) vs `filters.query` (debounced applied
  term) separation is correct: the field never lags, the wire never
  storms. Clear cancels the pending job first — a queued apply cannot
  resurrect a cleared term (tested).

## Reference alignment

N/A — kotlin-accelerator-ai is a different domain; nothing copied.
Read-only honored for both reference repos.

## Backend contract alignment

- Additive optional param — absent `q` produces byte-identical M15
  responses (asserted literally by `nullAndBlankQBehaveExactlyLikeNoFilter`,
  which compares page JSON for no-param vs `q=` vs `"   "`).
- Wire name `q` matches the name M15's own milestone pre-committed.
- Error contract unchanged: 400 overlength `q` (same `DomainException`
  validation band as `page`/`size`), 404 foreign `accountId` (checked
  before the page query — a foreign account + overlength `q` still 400s
  first, consistent with existing validation order), 401 unauthenticated.
- Sort unchanged (`occurredOn DESC, id DESC`); envelope fields unchanged
  (`content/page/size/totalElements/totalPages/hasNext`).

## Adversarial checks run

| Probe | Result |
|---|---|
| `q.length() > 255` counts UTF-16 units — could a legal needle be rejected? | No defect: M17's entity cap is 255 UTF-16 units, so every stored `description` has `.length() ≤ 255` — a longer needle can never match; 400 is correct and consistent. |
| JVM `toLowerCase(ROOT)` vs Postgres `lower()` collation edge | Vietnamese + emoji needles verified verbatim on-device-level real Postgres. Extreme edge (e.g. dotted-İ where JVM/DB lowercase diverge) is theoretically possible but cosmetic — worst case is a missed match, never a leak or crash. Note only. |
| Foreign-user needle against foreign text | `searchNeverExposesAnotherUsersNoteOrCounts` — counts AND text both sealed by the unconditional `userId` predicate. |
| `q` + count query fetch-join leak | Guard preserved; totals correct across pages. |
| Debounce race: apply fires after user cleared | `a pending debounce never overwrites a later clear` — job cancelled first. |
| Query rides stale-guard machinery (success + error + session A→B) | Three dedicated VM tests — superseded response dropped, superseded failure can't error live state, dead-generation searched load drops and resume resends `q`. |
| Client-side filtering creeping in | `git grep` — no `.filter` on loaded items; all narrowing is wire-side. |
| MockMvc encoded-needle artifact | Found and fixed in-block (see SENTINEL NOTE-1): four initial failures were the harness binding encoded bytes literally; product code never wrong. Documented to prevent recurrence. |

## Findings

| ID | Severity | Area | Description | Recommended owner |
|---|---|---|---|---|
| — | Blocker | — | none | — |
| — | Major | — | none | — |
| — | Minor | — | none | — |
| NOTE-1 | Note | Backend test | MockMvc URI-template percent-decode artifact fixed in-block; `pageQ` helper now the pattern for special-char needles | closed |
| NOTE-2 | Note | Environment | AVD (API 36) connected-run instability: app-under-test force-stopped externally at random test points across two runs; no FATAL EXCEPTION, no tombstone; physical Pixel 200/200 ×2 green. Advisory only — not the milestone gate | environment, no code action |
| NOTE-3 | Note | Backend | JVM/DB `lower()` collation divergence edge is cosmetic at worst (missed match on a pathological needle) — documented here, no action at this scale | accepted |

## Verdict

```
ARGUS_VERDICT=PASS
0 Blocker · 0 Major · 0 Minor · 3 Notes
Scope: exact. Contract: additive-only. Guards: inherited, not duplicated.
```
