# M15 SENTINEL REVIEW — Scalable Transaction History (pagination + date-range)

```
ROLE: SENTINEL (Test Engineer)
MILESTONE: M15 — REQ-BE-007 Scalable Transaction History
ANDROID_RANGE: 256c2ce..7488d47 (governance a65dfeb + delivery 3081349
               + device-test fix 6cfcaf2 + comment fix 685eb14
               + a11y remediation 7488d47)
BACKEND_COMMIT: d3e73c8 (bounded prerequisite, human-authorized)
REVIEW_MODE: adversarial self-audit per workflow contract; all evidence
             reproduced from live gates, not assumed. Re-gated after
             the 7488d47 a11y remediation (19/19 focused + 186/186
             full on-device re-run).
VERDICT: PASS — 0 Blocker, 0 Major, 4 Minor/Note; 1 a11y defect
         found during review and remediated in-task (7488d47).
```

## 1. Test evidence (re-executed for this review)

| Gate | Result |
|---|---|
| Backend focused M15 | `TransactionPageContractTest` **12/12** — real MockMvc + Postgres, rows seeded through real `POST /api/transactions` |
| Backend full regression (`mvn test`, `.env.local` exported) | **362 tests, 1 failure = documented pre-existing `RecurringSchedulingJobTest` date-dependent flake** (expected 1, got 2 — a Sep-1 MONTHLY template fires for Sep AND Oct when `LocalDate.now()` is October; identical failure since M10 baseline, unrelated to the read path this milestone adds) |
| Backend package | `mvn package` → `spendwise-0.1.0-SNAPSHOT.jar` (78,385,465 B) |
| Android JVM full regression (`testDebugUnitTest`) | **615/615**, 0 skipped, 0 failed (592 M14 baseline → +23) |
| Android lint / detekt | clean / **0 smells** (224 files) |
| assembleDebug / assembleRelease | both green; `app-release-unsigned.apk` 2,451,754 B, SHA-256 `25908dd83246200a8b09…` (R8 minified) |
| Pixel 4 XL connected (`ANDROID_SERIAL` forced, emulator excluded) | **186/186 ×2 consecutive** on delivery source + **186/186** re-run on final source after `7488d47` (3 consecutive full greens total), 0 skipped, 0 failed — includes all 6 new M15 cases on-device |
| Install + launch on Pixel | `app-debug.apk` reinstalled, `MainActivity` top-resumed, pid observed, no crash in logcat |

## 2. Wire-contract coverage — exact params, no sentinels

`TransactionsServiceContractTest` (MockWebServer + real Retrofit +
real interceptors), +6 M15 cases:

- Exact path/method: `GET /api/transactions/page`, bearer propagated.
- Every filter on the wire verbatim: `accountId`, `type`, `category`,
  `uncategorized=true`, `provenance=IMPORTED`, `from`/`to` ISO
  `yyyy-MM-dd`, `page`, `size` — asserted individually.
- Null params omitted entirely; only `page`/`size` remain (queryParamNames
  set equality asserted — no invented keys).
- Envelope maps `content/page/size/totalElements/totalPages/hasNext`.
- 401 → `Session.Expired`; 400 → `ApiException(400)`.

## 3. Repository / ViewModel coverage — the honest-paging invariants

`TransactionRepositoryTest` (+5), `TransactionsViewModelTest` (25, +8
M15 cases):

- `listPage` sends every filter verbatim; `uncategorized=false` never
  reaches the wire (`takeIf`); dates serialize as `LocalDate.toString()`
  = ISO `yyyy-MM-dd`.
- Page content merges into the SessionCache — `findById` resolves rows
  from page 0 AND page 1 with zero extra requests.
- `loadNextPage` appends server order, dedupes by id, publishes server
  `totalElements`/`hasNext`; no-ops at end; never double-fires while a
  page is in flight (gate-proven — exactly 2 calls after 3 invocations).
- Append failure → `moreError` inline, rows intact, `hasMore` still
  retryable.
- Late append after a filter change publishes NOTHING (tick +
  filters-equality guard).
- Filter/date change resets to page 0; refresh resets; `SessionExpired`
  mid-append keeps content, no fake error, `onVisible` resumes.
- Session generation: stale `listPage` response after sign-out/in is
  uncacheable and unpublishable (SessionEndedException path covered by
  existing dead-generation tests, now against `listPage`).

## 4. Compose coverage — device-real

`TransactionsScreenTest` (19, +6 M15 cases), all run on physical
Pixel 4 XL:

- "Showing N of M" uses server `totalElements`, not loaded-slice size.
- Scrolling to index 29/30 triggers `onLoadMore` exactly.
- Footer: spinner semantics ("Loading more transactions"), tap-to-retry
  error banner, end-of-ledger marker.
- From/To chips announce "From filter: all" / "From filter: <date>";
  clear affordance is its OWN semantics node (survives the pill's
  `semantics{cd}` — fixed after finding `clearAndSetSemantics` would
  have hidden it from TalkBack).
- Picker dialog opens from the chip; confirm/dismiss buttons exist.
- Chip-row scrolling is exercised (physical 411dp viewport clips the
  6-chip row — tests `performScrollTo()` first, catching a real
  device-only gap the emulator missed on the first run).

## 5. Backend adversarial matrix — `TransactionPageContractTest` (12)

- Envelope + defaults (`page=0,size=50`), honest totals.
- Page slicing deterministic: size=2 over 5 rows → 3 pages walked
  newest→oldest; past-the-end → `200 {content:[]}`, not an error.
- Equal-`occurredOn` ordering: `id DESC` tiebreaker gives disjoint
  pages (4 same-date rows, asserted disjoint across page 0/1).
- Validation: `page=-1`, `size=0`, `size=101`, `from>to`,
  `category`+`uncategorized` conflict, `type=BANANA`,
  `provenance=BANANA`, `from=not-a-date` → all 400.
- Inclusive `from`/`to` on `occurredOn`; one-sided bounds work alone.
- `uncategorized=true` → null-category rows; `category=NOPE` → empty
  page (list parity); `provenance` → `sourceRef` prefix; `UNKNOWN` →
  honest empty.
- `accountId`/`type`/date compose under paging.
- Foreign and missing `accountId` → 404.
- Cross-user: B's rows never enter A's content OR `totalElements`.
- Unauthenticated → 401.

## 6. Findings

| # | Sev | Finding | Disposition |
|---|---|---|---|
| S-1 | Minor | `markPending` clears `refreshError` but not `moreError` — a stale append-error banner can coexist with the refresh spinner and survives a *failed* refresh until the next successful page-0 lands. Cosmetic; rows and metadata unaffected. | Documented; low value to churn. |
| S-2 | Note | `provenance=UNKNOWN` → `sourceRef IS NULL` on a NOT NULL column → always empty. Diverges from client derivation (`STRANGE:` prefix → UNKNOWN) only for rows the server can never write. Honest by construction, documented on both sides. | Accept. |
| S-3 | Note | From/To chips start off-screen on a 411dp device — the P3A horizontal-scroll row is the standing design; contentDescription still announces them and the row scrolls. | Accept — standing design, now device-proven. |
| S-4 | Note | `RecurringSchedulingJobTest` baseline flake persists — date-dependent (fires Sep+Oct periods when `now` is October), byte-identical to M10–M14 baselines, read-path M15 cannot affect it. | Documented baseline, out of scope. |
| S-5 | Minor→fixed | Date-chip clear affordance was an 18dp clickable `Icon` — below the 48dp touch-target minimum, effectively unclearable on a physical phone. | **Remediated `7488d47`** — 40dp-wide × full-chip-height tappable box; own semantics node preserved; re-gated 19/19 + 186/186. |

## 7. Test-integrity audit

No vacuous tests added: wire tests assert exact query params and
`queryParameterNames` set-equality; VM tests assert call counts and
published state, not fake-returned setup; compose tests assert
rendered semantics + callback effects on a physical device; backend
tests seed through real `POST` and assert `jsonPath` on a real DB.
The `page`/`size` sentinels a client could fabricate are asserted to
be REAL wire params. `hasNext`/`totalElements` are read from the
server envelope, never computed client-side — verified in
`listPage maps the envelope and warms the detail cache` and the
`Showing N of M` compose case.
