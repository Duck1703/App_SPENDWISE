# M18 — Transaction Search (server-side `q`)

```
STATUS: TECHNICAL_COMPLETE — delivered, self-reviewed, ready for
        external audit
M17_COMPLETE=YES
M18_DEFINED=YES
M18_STARTED=YES
M18_TECHNICAL_COMPLETE=YES
M18_READY_FOR_EXTERNAL_AUDIT=YES
M18_EXTERNAL_AUDIT=NOT_RUN
M18_HUMAN_APPROVED=NO
M18_HUMAN_AUTHORIZATION=EXPLICIT (M18 delivery block)
CANONICAL_REQUIREMENT=REQ-BE-007 residual scope — the `search` half of
  "pagination + date-range/search" M15 explicitly deferred while BE-010
  (persisted description) was undelivered; M17 delivered BE-010.
TRANSACTION_SEARCH=TECHNICAL_COMPLETE
M19_DEFINED=NO
M19_STARTED=NO
SPENDWISE_STATUS=M18_READY_FOR_EXTERNAL_AUDIT
NEXT_ACTION=HUMAN_GATE — authorize M18 external audit or accept
BASELINE: Android 71a4148 (M17-accepted) · Backend 752eac3 (M17-accepted)
GATES: backend 407/407 · Android JVM 672/672 · lint 0e/22w · detekt 0
  · debug+release+R8 green · physical Pixel 4 XL 200/200 ×2 ·
  install+launch verified · SENTINEL PASS · ARGUS PASS
  (0 Blocker/0 Major/0 Minor — see reviews/M18-*-REVIEW.md)
DELIVERY: Android 83a5679 (slice) + 1deeb16 (reviews/status)
  · Backend 752eac3..0719836 (bounded `q` prerequisite + contract test)
COURSE_SYNC_DEFERRED=YES
```

## Selection rationale (decision record)

Candidates re-evaluated against CURRENT canonical sources
(`PRODUCT.md` §153/§771, `POST-V1-BACKEND-REQUIREMENTS.md`,
`POST-V1-CAPABILITY-MATRIX.md`, `ROADMAP.md`, M15/M17 milestone
records — M9–M17 removed from candidacy) and the actual backend
surface (`752eac3`).

| Candidate | Verdict |
|---|---|
| **Transaction search (server-side `q`)** | **SELECTED**: the residual canonical scope of REQ-BE-007. M15's milestone rejected `q` with exactly one reason — "`description` is NOT persisted (BE-010 undelivered) — a search param would match only category/type, which the existing filters already cover; near-fake search violates the honesty invariant." M17 delivered BE-010: `description` is now a real persisted, owner-scoped, user-authored text column over every transaction kind that carries text (manual notes + imported statement text). M17's own selection table recorded the dependency: "BE-010 is the strict prerequisite; landing notes first makes a future search milestone honest." The M15 `searchPage` Specification is the exact composition seam — `q` becomes one more owner-scoped predicate server-side before pagination. Score: priority 5 + value 5 + backend 5 + android 4 + testability 3 + leverage 3 + physical 2 − ambiguity 1 − security 2 − schema 0 − history 0 − scope 2 = **22**. |
| REQ-BE-005 transaction correction | Rejected: register requires an explicit product decision (PATCH vs reversal/void); financial-history ambiguity unresolved. |
| REQ-BE-006 account edit/archive | Rejected: "define archived-account semantics" unresolved — transactions/transfers/budgets/recurring all reference accounts. |
| Client-side search over fetched rows | Rejected: capability-matrix verdict stands — under pagination a local pass hides matches on not-yet-loaded pages; server-side is the honest form and is now possible. |
| REQ-BE-009 hardening cluster | Rejected: low user-visible value; mixed grab-bag, not one capability. |
| FX converter tool | Rejected: product-gated (V1 never-mix-currencies), external dependency. |
| Profile management | Rejected: PRODUCT excludes new auth surfaces near-term. |
| Category management | Rejected: categories are a fixed server enum; no user-lifecycle requirement exists in the register. |
| Remaining recurring lifecycle (pause/skip) | Rejected: not in the canonical register; undefined semantics. |

## User outcome

On the Transactions tab the user types a query in a search field; the
list is re-fetched from the server and shows only transactions whose
**persisted note** contains the query text (case-insensitive). Search
composes with every existing M15 filter (account, type, category,
uncategorized, provenance, from/to) and with pagination — a search is
an intersection over the whole owner-scoped ledger, never a filter of
already-loaded rows. Clearing the field restores the full filtered
list. A query that matches nothing shows an honest "no matches" state.

## Backend contract (bounded M18 prerequisite)

No new endpoint. One additive optional parameter on the accepted M15
contract:

`GET /api/transactions/page?…&q=<text>`

Semantics (all derived from existing conventions):

- Parameter: `q` — optional query string (the name M15's milestone
  itself used for this param).
- Match: case-insensitive substring (`LOWER(description) LIKE
  %q%`) over `transactions.description` only — the persisted M17
  column. `sourceRef` is technical identity text (M15 already judged
  it near-fake); category/type/account remain structured filters,
  not text search.
- NULL descriptions never match — a `q`-filtered page returns only
  rows that have notes (manual notes + imported statement text).
- Blank handling: `q` absent, empty, or whitespace-only → ignored
  (same as not sending it); never an error, never a wildcard.
- Bound: `q` longer than 255 → `400` (the description column's own
  bound; a longer needle can never match a ≤255 field).
- Injection safety: LIKE metacharacters `%`, `_`, `\` in the query are
  escaped; the predicate is a bound criteria parameter — no string
  concatenation into SQL, ever.
- Composition: `q` ANDs with `accountId`, `type`, `category`,
  `uncategorized`, `provenance`, `from`, `to` in the single
  `searchPage` Specification — all server-side before pagination.
- Ordering/pagination: unchanged — `occurredOn DESC, id DESC`,
  0-based `page`, `size` 1–100, `totalElements`/`totalPages`/`hasNext`
  reflect the searched+filtered set.
- Owner scope: unconditional `userId` predicate — a foreign user can
  never see another's note text, match counts, or page metadata.
- Errors: unchanged shape — `400` on overlength `q`, `404` on
  foreign/unknown `accountId`, `401` unauthenticated.
- Schema: NONE — `description` is already `VARCHAR(255)` (V12); no
  index added (bounded LIKE on owner-scoped rows; per-user ledgers are
  small — an unindexed scan is honest at this scale).

## Android scope

- `TransactionsService.page`: new optional `@Query("q")`.
- `TransactionRepository.listPage`: `q` passthrough.
- `TransactionFilters.query` (applied term): participates in
  `isEmpty`, data-class equality (all M15 stale guards cover it
  automatically), and the wire mapping (`blank → null`).
- `TransactionsViewModel`: `searchInput` StateFlow for the field;
  `onSearchQueryChange` debounces (~350 ms) before applying to
  `filters.query` (keystrokes never storm the wire); `clearSearch`
  applies immediately. Query change restarts pagination at page 0 —
  automatic via the existing nonce/loadSerial machinery.
- `TransactionsScreen`: search field above the filter row — leading
  search icon, trailing 48dp clear affordance (TalkBack-labelled),
  `ImeAction.Search`, result/empty/error/retry states unchanged.
- Create form is untouched; M17 note entry stays as-is.

## Invariants protected

- M13: `GET /page` is a read — single-flight refresh + one bounded
  retry is the accepted GET contract; no mutation involved.
- M15: same endpoint, same ordering, same page metadata; `q` is an
  additive predicate — all stale-response/load-more/session-generation
  guards apply unchanged (filter equality includes `query`).
- M16: recurring `@Version` + scheduler untouched.
- M17: search reads `description`, never writes it; normalization,
  surrogate-safe cap, CSV neutralization, rule-engine raw input, and
  import dedup all unchanged.

## Non-goals

- No fuzzy/prefix/ranked search, no full-text engine, no index work.
- No searching `sourceRef`, category names, or account names.
- No search on the unpaged `GET /api/transactions` (list contract
  stays frozen at M15 semantics).
- No search UI on other surfaces (dashboard, budgets, imports).
- No REQ-BE-005 edit-of-description, no M19, no course sync.

## Test strategy

Backend (real Postgres, `TransactionSearchContractTest`): description
match/no-match, case-insensitivity, blank/whitespace q, >255 → 400,
`%`/`_`/`\` literalization, apostrophe, Vietnamese, emoji, q+date,
q+category, q+type, q+account, q+provenance, q+pagination metadata,
ordering stability, owner isolation (foreign note/counts never
returned), unauthenticated 401, unpaged-list contract unchanged.

Android: wire test (`q` param + encoding), repository passthrough,
VM (debounce apply, clear-immediate, query restarts page 0, stale Q1
success/error never mutates Q2 state, session A→B), Compose (input,
clear, results, empty, IME), physical connected suite ×2.
