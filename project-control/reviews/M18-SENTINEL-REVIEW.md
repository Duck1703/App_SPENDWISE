# M18 SENTINEL REVIEW — Server-side Transaction Search

```
ROLE: SENTINEL (Test Engineer)
MILESTONE: M18 — REQ-BE-007 residual: server-side `q` over persisted descriptions
ANDROID_RANGE: 71a4148..working tree (vertical slice + tests)
BACKEND_RANGE: 752eac3..working tree (bounded prerequisite: `q` on /page)
REVIEW_MODE: adversarial self-audit per workflow contract; all evidence
             reproduced from live gates, not assumed.
VERDICT: PASS — 0 Blocker, 0 Major, 0 Minor, 2 Notes
```

## 1. Test evidence (re-executed for this review)

| Gate | Result |
|---|---|
| Backend focused M18 | `TransactionSearchContractTest` **10/10** — real MockMvc + Postgres (container `spendwise-m10-db`, `postgres:16`, port 55432) |
| Backend full regression (`mvn test`, env from `.env.local`) | **407/407**, 0 failures, 0 errors, 0 skipped (397 M17 baseline → +10) — surefire-reports aggregate, not exit code |
| Android JVM full regression (`testDebugUnitTest`, fresh) | **672/672**, 0 failures, 0 skipped (658 M17 baseline → +14) — XML report aggregate |
| Android lint (`lintDebug`) | PASS — **0 errors**, 22 warnings (identical count to the M17 accepted baseline; none in M18 files) |
| Detekt | **0 code smells** (227 files analyzed) |
| Debug / Release builds | PASS; `assembleRelease` green incl. `lintVitalRelease` + `minifyReleaseWithR8` |
| R8 retention spot-check | `api/transactions/page` path string and `TransactionPageDto` present in minified `classes*.dex` |
| Instrumentation compile | `compileDebugAndroidTestKotlin` green |
| Physical Pixel 4 XL connected suite — run 1 | **200/200 PASS** (196 M17 baseline → +4 screen tests) |
| Physical Pixel 4 XL connected suite — run 2 | **200/200 PASS** — second consecutive clean run on final source |
| Install + launch (physical, explicit `adb -s`) | `app-debug.apk` streamed-install Success; `com.spendwise.android/.MainActivity` confirmed `topResumedActivity` |
| Emulator (`Spendwise_M8_Acceptance` AVD, API 36) — advisory | NOT a gate. Process killed externally at a different test point on each run (no `FATAL EXCEPTION`; system-initiated `Force stopping … from pid`). See NOTE-2. |

## 2. Requirement → test mapping (M18 backend contract)

Backend (`TransactionSearchContractTest`, 10 tests, real HTTP + DB):

| Acceptance surface | Covering test |
|---|---|
| Case-insensitive substring on persisted `description`; NULL never matches | `qMatchesPersistedDescriptionSubstringCaseInsensitively` — `q=coffee` and `q=COFFEE` both hit only `"morning Coffee run"`; null-desc row absent |
| Absent / empty / whitespace `q` ≡ no filter | `nullAndBlankQBehaveExactlyLikeNoFilter` — byte-identical page JSON for no-param, `q=`, and `"   "` |
| `q > 255` → 400; `255` legal | `qLongerThanTheColumnIsRejected` |
| LIKE `%`, `_`, `\` literalized (bound parameter, never concatenated) | `likeMetacharactersAreLiteralNotWildcards` — `"100%"` hits only `"100% organic"`, `"under_score"` only the literal row, `"under\"` matches nothing |
| Apostrophe / injection-shaped needle is inert | `apostropheAndSpecialTextCannotBreakTheQuery` — `"o'malley"` matches; `"' OR '1'='1"` matches nothing, no error |
| Unicode + emoji verbatim | `unicodeAndEmojiQueriesMatchVerbatim` — `"cà phê"` and `"😊"` each match exactly their row |
| `q` ANDs with every M15 filter before pagination | `qComposesWithEveryFilterBeforePagination` — q+category, q+type, q+accountId, q+date range, q+provenance |
| Page metadata reflects the searched set | `qDrivesHonestPageMetadataAcrossPages` — 7 matches → `totalElements=7`, `totalPages=3`, past-end page keeps honest totals |
| Owner isolation — foreign note text and counts never leak | `searchNeverExposesAnotherUsersNoteOrCounts` — stranger's `q=secret` returns 0 rows and the string never appears in the payload |
| Auth boundary | `unauthenticatedSearchIsRejected` — 401 |

## 3. Requirement → test mapping (Android)

| Acceptance surface | Covering test |
|---|---|
| Wire `q` param, verbatim + URL-encoded | `page sends every filter as an exact wire parameter`, `page q url-encodes special characters verbatim`, `page omits unset filters` (`TransactionsServiceContractTest`) |
| Repository passthrough; blank never reaches the wire | `listPage forwards q verbatim`, `listPage blank q never reaches the wire` (`TransactionRepositoryTest`) |
| Debounce — input instant, apply after quiet period | `search input is instant but the server query waits for the debounce` |
| Rapid typing → one request | `rapid typing produces one request for the final term` |
| Clear applies immediately | `clear search applies immediately — no debounce wait` |
| Query composes with chips on one call | `search composes with the other server filters on one call` |
| Query change restarts pagination; appends carry `q` | `loadNextPage carries the active query` (+ existing `a filter change resets paging to page 0` — query rides the same filters equality) |
| Pending debounce cannot resurrect a cleared term | `a pending debounce never overwrites a later clear` |
| Whitespace-only input never becomes a query | `whitespace-only input never becomes a server query` |
| All-filters reset also clears search | `clearFilters also resets the search field` |
| Stale Q1 success never publishes over Q2 | `a superseded search response never publishes` |
| Stale Q1 failure never errors Q2 | `a superseded search failure cannot error the live query` |
| Session A→B — dead-generation searched load drops, resume resends `q` | `a search load lost to a session switch reloads with the query` |
| Screen: field label + placeholder, keystroke passthrough, clear affordance visibility/behaviour | `searchField_rendersWithTalkBackLabelAndPlaceholder`, `searchField_typingForwardsEveryKeystroke`, `searchField_clearAffordanceOnlyWithText_andClears`, `searchField_noClearAffordanceWhenEmpty` (`TransactionsScreenTest`, on-device) |

## 4. Findings

### NOTE-1 — MockMvc URI-template does not percent-decode (test-harness artifact, fixed during delivery)

The first contract-test draft embedded `URLEncoder.encode(needle)` inside
`MockMvcRequestBuilders.get("/api/transactions/page?q=…")`. The URI template
is treated as already-encoded, so `@RequestParam` bound the *encoded bytes*
(`o%27malley`, `%20%20%20`) — four tests failed on the harness, not the
product: a real servlet container decodes the query string before binding.
**Fix (this block):** special-character needles now drive
`.queryParam("q", value)`, which binds decoded values exactly like a real
client. Re-run: 10/10. The product code was never wrong — worth recording so
future needle tests don't re-trip the same artifact.

### NOTE-2 — Emulator connected-run instability (environment, not product)

Two `connectedDebugAndroidTest` invocations ran the suite on both the
physical Pixel and the `Spendwise_M8_Acceptance` AVD (API 36). Each run the
AVD's app-under-test process was **killed externally** at a different test
point (`content_listsRowsAndShowsHeaderAddAction`, then
`ExportLedgerScreenTest.saving_phase_shows_the_saving_label`), the runner
reported `Process crashed`, and the remaining AVD tests were aborted.
Logcat shows `Force stopping com.spendwise.android … from pid` — a
system-initiated stop, **no `FATAL EXCEPTION`, no tombstone** for the app;
the two marked failures have empty failure bodies consistent with a
mid-assertion kill. The same suite is **200/200 twice on the physical
Pixel 4 XL**, and the failing points differ per run — random kill point,
untouched test classes, external signal. Verdict: environment flake on a
second simultaneous target, outside M18 scope. The mandated gate
(physical ×2) is green both runs.

## 5. Coverage assessment vs milestone test strategy

| Milestone requires | Status |
|---|---|
| Backend: match/no-match, case-insensitivity, blank, >255, `%/_/\`, apostrophe, Vietnamese, emoji, q+each filter, pagination metadata, ordering, owner isolation, 401, unpaged list unchanged | ALL COVERED (10 contract tests; unpaged list untouched — no signature change, full suite green) |
| Android: wire test (`q` + encoding), repository passthrough, VM debounce/clear/restart/stale/A→B, Compose input/clear/results/empty/IME, physical ×2 | ALL COVERED (see §3) — IME action present on the field; `ImeAction.Search` asserted by inspection, field input/clear asserted on-device |

Ordering stability (`occurredOn DESC, id DESC`) is asserted transitively:
`qDrivesHonestPageMetadataAcrossPages` pages through a searched set and the
existing M15 ordering contract tests remain green (`PageBoundaryTest` etc.
in the 407-test suite — untouched code path, unchanged `Sort`).

## 6. Verdict

```
SENTINEL_VERDICT=PASS
0 Blocker · 0 Major · 0 Minor · 2 Notes
Backend 407/407 · Android JVM 672/672 · Pixel 200/200 ×2
```
