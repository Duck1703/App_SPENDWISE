# M15 EXTERNAL-AUDIT HANDOFF — Scalable Transaction History (REQ-BE-007)

```
M15_READY_FOR_EXTERNAL_AUDIT=YES
M15_HUMAN_APPROVED=NO          (audit gate precedes human approval)
SPENDWISE_STATUS=M15_READY_FOR_EXTERNAL_AUDIT
NEXT_ACTION=ONE_LARGE_INDEPENDENT_EXTERNAL_M15_AUDIT
```

## Scope delivered

One substantial capability: **REQ-BE-007 — scalable transaction
history**. Server-backed paged transaction endpoint consumed by a
lazy-paging Android Transactions tab, plus inclusive `occurredOn`
date-range filtering (the half of the requirement that was deferred
pending BE-001 dates, now unblocked).

Deliberately NOT in scope: text search (no persisted searchable text —
`description` is not a stored field, so a server `q` would be fake
search), transaction correction, account archive, persisted
descriptions, FX totals, recurring PATCH — all product-gated or
M16/post-M15.

## Backend contract (bounded-write, human-authorized)

Commit: `d3e73c8` on `D:\spendwise` (range `4ed4f81..d3e73c8`,
6 files, no collateral; human-owned docs untouched).

`GET /api/transactions/page` — read-only, authenticated, owner-scoped:

```
Params: accountId? type? category? uncategorized? provenance?
        from? to? page=0 size=50 (1..100)
        from/to = ISO yyyy-MM-dd, inclusive bounds on occurredOn
Validation: page>=0 · size 1..100 · category+uncategorized conflict → 400
            from>to → 400 · foreign/missing accountId → 404
Response: { content[], page, size, totalElements, totalPages, hasNext }
Ordering: occurredOn DESC, id DESC (no createdAt exists on Transaction)
Provenance: persisted sourceRef prefixes (MANUAL: IMPORT: RECURRING:
            TRANSFER:); UNKNOWN → sourceRef IS NULL (honest on a
            NOT NULL column → empty, never fabricated)
Implementation: dynamic JPA Specification — predicates added only for
            supplied params; avoids Postgres untyped-null binding and
            nullable-LIKE failures; count query has no fetch join.
Unpaged GET /api/transactions: unchanged — still backs detail,
            account history, import surfaces.
```

Files: `TransactionController`, `TransactionService`,
`TransactionRepository` (now `JpaSpecificationExecutor`), new
`TransactionPageResponse`, `TransactionProvenance`,
`TransactionPageContractTest`.

## Android implementation

Commits: `3081349` (delivery), `6cfcaf2` (physical-test fix),
`685eb14` (comment), `7488d47` (a11y touch target).

- `TransactionsService.page` — exact wire signature above, Retrofit.
- `TransactionRepository.listPage` — fetches a `TransactionPage`
  domain object; page content merges into the SessionCache
  (generation-stamped) so `findById` detail lookup still resolves.
- `TransactionsViewModel` — `fetch` reloads page 0; `loadNextPage`
  appends under `hasMore && !loadingMore && !isRefreshing` with a
  monotonic load serial + filter-identity check so a late append under
  a superseded filter set is dropped; `distinctBy { it.id }` on merge;
  append failure preserves loaded items and requires explicit retry.
- `TransactionsScreen` — six server-side filters (Account, Type,
  Category, Source, From, To) in a horizontally-scrolling chip row;
  DatePickerDialog for From/To with UTC-safe conversion (no timezone
  day-shift); `Showing N of M` header uses the server envelope total;
  footer shows spinner / tappable error banner / end-of-ledger marker;
  date-chip clear affordance is a 40dp-wide tappable target.
- Session refresh (M13): GETs retry via authenticator; the endpoint
  performs no mutation so no replay risk exists.

## Gate evidence

| Gate | Result |
|---|---|
| Backend focused | `TransactionPageContractTest` 12/12 — real MockMvc + Postgres |
| Backend full regression | 362 tests, 1 failure = documented date-dependent `RecurringSchedulingJobTest` baseline flake (identical since M10, unrelated to read-path M15) |
| Android JVM | 615/615 (includes 22 wire-contract + repo/VM paging tests) |
| lint / detekt | clean / 0 smells |
| Debug + Release | `assembleDebug` green; `app-release-unsigned.apk` 2,451,754 B, sha256 `25908dd83246200a8b09…` (R8 minified; app-wide `@Serializable` keeps cover new DTO) |
| Physical Pixel 4 XL (Android 13) | **186/186 ×2 consecutive** on delivery source + **186/186** on final source after a11y remediation; all M15 connected tests ran on-device |
| Install + launch | `MainActivity` top-resumed, no crash |
| Security | INTERNET only, cleartext off, datastore excluded from backup + device-transfer, no logging of tokens/secrets |
| Sentinel | PASS — `project-control/reviews/M15-SENTINEL-REVIEW.md` |
| Argus | PASS — `project-control/reviews/M15-ARGUS-REVIEW.md` |

## Known non-blocking limitations

- `provenance=UNKNOWN` maps to `sourceRef IS NULL`; the column is
  NOT NULL so UNKNOWN returns empty rather than fabricated rows.
- `markPending` clears `refreshError` but not `moreError` — a stale
  append banner can coexist briefly with the refresh spinner
  (cosmetic; Sentinel S-1).
- The backend date-dependent `RecurringSchedulingJobTest` flake is
  pre-existing baseline debt (recurring recommendation: pin `now`).

## Commit references

```
Backend:  d3e73c8  (4ed4f81..d3e73c8 — bounded-write authorized)
Android:  a65dfeb governance · 3081349 delivery · 6cfcaf2 test fix
          685eb14 comment · 7488d47 a11y touch target
Baseline: Android 256c2ce (M14-accepted) · Backend 4ed4f81
```

No pull-request lifecycle state is claimed; these are local commits on
the working branches.
