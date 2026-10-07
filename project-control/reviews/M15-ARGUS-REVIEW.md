# M15 ARGUS REVIEW — Scalable Transaction History (REQ-BE-007)

```
ROLE: ARGUS (Independent QA Reviewer)
MILESTONE: M15 — REQ-BE-007 Scalable Transaction History
ANDROID_RANGE: 256c2ce..7488d47 (gov a65dfeb · delivery 3081349 ·
               device-test fix 6cfcaf2 · comment fix 685eb14 ·
               a11y remediation 7488d47)
BACKEND_RANGE: 4ed4f81..d3e73c8 (bounded-write, human-authorized)
REVIEW_MODE: independent adversarial re-derivation — contract re-read
             from canonical sources, diff walked file-by-file, gates
             re-verified, findings reproduced not assumed.
VERDICT: PASS — 0 Blocker, 0 Major, 3 Note; 1 device-only defect and
         1 a11y defect found and remediated in-task (re-gated green).
```

## 1. Independence checks

- Contract re-derived from `POST-V1-BACKEND-REQUIREMENTS.md` REQ-BE-007
  and the milestone doc, then compared field-by-field against
  `TransactionController.page`, `TransactionService.searchPage`,
  `TransactionPageResponse`, `TransactionProvenance`, the Android
  `TransactionsService.page` signature and `TransactionPageDto`.
  **Contract match: EXACT** — params, defaults (page=0, size=50,
  cap 100), error matrix (400 page/size/date/conflict/enum, 404 foreign
  account, 401 unauthenticated), envelope field names, ordering
  `occurredOn DESC, id DESC`.
- Backend diff is exactly 6 files (3 modified, 3 new) — no collateral.
  Human-owned dirty docs (`README.md`, `HUONG-DAN-SU-DUNG.md`,
  `docs/SPENDWISE-REQ.md`) verified untouched.
- The unpaged `GET /api/transactions` is byte-identical — Home,
  AccountDetail history, import surfaces and `findById` fallback keep
  their contract.
- No schema migration — confirmed by empty `V*` delta; `occurred_on`
  (V5) and `source_ref` (V3) already exist.

## 2. Honesty invariants — verified in code, not doc claims

- `totalElements` displayed in the header is the server envelope value
  (`Showing N of M`); no count is derived from loaded items. The
  `isEmpty → Empty` transition uses `totalElements == 0`, not
  `items.isEmpty()` — a filtered-to-zero result cannot masquerade as
  an empty ledger.
- Uncategorized and provenance moved from client-side passes to real
  server parameters — **required** under paging: a local pass over
  page N would hide matches on unloaded pages. Verified the VM sends
  `uncategorized`/`provenance` on the wire and asserts it in tests.
- No local aggregation, no persisted page cache as truth; the
  SessionCache merge backs `findById` only and is generation-stamped.
- Ordering has no invented field — `id DESC` tiebreak over UUIDs is
  deterministic without fabricating `createdAt`.
- `provenance=UNKNOWN` → `sourceRef IS NULL` is honest-by-construction
  on a NOT NULL column; the divergence from client derivation covers
  only rows the server cannot write. Acceptable, documented.

## 3. Session / concurrency safety

- `listPage` takes the generation stamp pre-fetch and `cache.write`
  re-validates it — a response produced under session N throws
  `SessionEndedException` under N+1 and never publishes (covered by
  the rewritten dead-generation VM tests against `listPage`).
- `loadNextPage` guards: `hasMore && !loadingMore && !isRefreshing`,
  tick + filters-equality before publish — a late append under a
  superseded query drops cleanly. Gate-proven by the parked-
  CompletableDeferred test (exactly 2 calls for 3 invocations).
- M13 no-replay: the endpoint is GET — authenticator retry applies,
  no mutation exists to replay; `SessionRefreshContractTest` unchanged
  and still green.
- `refresh()` re-fetches page 0 and REPLACES items — no stale-row
  accumulation across refresh.

## 4. Findings

| # | Sev | Finding | Disposition |
|---|---|---|---|
| A-1 | Major→fixed | **Date-chip clear affordance had an 18dp touch target** — the `ic_lucide_x` Icon was clickable at 18×18dp, below the 48dp minimum; on a physical phone the only way to clear a date bound was effectively impossible. | **Remediated in `7488d47`** — 40dp-wide × full-chip-height tappable box, icon stays 18dp visually; `dateChip_showsSelectedDateAndClears` re-verified on the Pixel (19/19 class re-run). |
| A-2 | Minor→fixed | **Device-only test defect**: From/To chips start clipped in the horizontally-scrolling filter row on a 411dp viewport — the first physical run failed 3 tests that passed on the emulator's wider default window. | **Fixed in `6cfcaf2`** — `performScrollTo()` before assert/click; this was the intended value of the physical gate. |
| A-3 | Minor | `markPending` clears `refreshError` but not `moreError` — a stale append banner coexists with the refresh spinner until the next page-0 lands. Cosmetic. | Documented (SENTINEL S-1); defer. |
| A-4 | Note | `provenance=UNKNOWN` semantics asymmetry (see §2). | Accepted — honest by construction. |
| A-5 | Note | `RecurringSchedulingJobTest` baseline flake — date-dependent (Sep-1 MONTHLY template fires Sep+Oct once `now` is October); identical since M10; read-path M15 cannot affect it. | Documented baseline; **recommend a future backend task pins `now` in that test** — recurring debt, flagged again. |

## 5. Gate evidence (all reproduced this review)

- Backend: `TransactionPageContractTest` **12/12** (real DB, seeded
  via real POSTs); full regression 362 tests / 1 documented baseline
  flake; `mvn package` jar built.
- Android: JVM **615/615**; lint clean; detekt **0 smells**;
  `assembleDebug` + `assembleRelease` green; unsigned release APK
  2,451,754 B (R8 minified — 22MB debug → 2.45MB release confirms
  shrinking actually ran); serializer keeps cover the new DTO via the
  app-wide `@Serializable` rules.
- Manifest/permissions: `INTERNET` only; `usesCleartextTraffic=false`;
  `datastore/` excluded from cloud backup AND device transfer; no
  `Log`/`println` in main sources; no new permissions for M15.
- Physical Pixel 4 XL (Android 13, `ANDROID_SERIAL` forced, emulator
  excluded): **186/186 ×2 consecutive** on delivery source, then
  **186/186** again on the final source after a11y remediation —
  three consecutive full greens total; all 6 new M15 connected cases
  ran on-device (verified in result XMLs).
- Install + launch sanity on the final build: `MainActivity`
  top-resumed, process alive, no crash entries.

## 6. Verdict

M15 ships an honest, server-authoritative paged ledger with inclusive
`occurredOn` bounds — every filter is a real wire parameter, totals
are server-computed, ordering is deterministic without inventing a
timestamp, session generation is preserved on every page, and the
physical gate passed consecutively on the shipped source. The two
defects found during this review cycle (device-only clipped chips in
tests, 18dp clear target) were remediated and re-gated in the same
task. **PASS — ready for the independent external audit.**
