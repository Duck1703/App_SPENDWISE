# M18 — External Audit (Transaction Search, server-side `q`)

Independent adversarial audit of the M18 delivery claims. The delivery
report was treated as a hypothesis; every claim was re-derived from source
and falsified against real Postgres, a real servlet container, and the
physical Pixel 4 XL.

```
AUDIT=M18-EXTERNAL-AUDIT
ROLE=independent external auditor (adversarial; not a re-run of ARGUS)
BASELINE: Android db414df (delivery head) · Backend 0719836 (delivery head)
FINAL:    Android dfda980 · Backend d8554f0
```

## 1. Method

- Re-derived the canonical contract from REQ-BE-007 + the M18 brief —
  not from the delivery report.
- Audited the complete backend diff (`752eac3..0719836`) and the complete
  Android diff (`71a4148..db414df`) line by line.
- Added an independent backend test class
  (`TransactionSearchExternalAuditTest`, 10 tests) and a real-HTTP class
  (`TransactionSearchHttpEncodingTest`, 1 test, random-port Tomcat) —
  none reuse the delivery fixtures.
- Added Android coverage for the discovered defect plus refresh-under-
  search (2 tests).
- Captured generated Hibernate SQL to prove parameter binding, owner
  scoping, DB-side filtering/pagination, and ordering.
- Re-ran every gate on the final (post-remediation) source.

## 2. Independent findings

### EXT-M18-1 — MINOR (Android) — FOUND, REPRODUCED, FIXED `dfda980`

`TransactionsViewModel.markPending()` cleared `refreshError`/`moreError`
but not `loadingMore`. Sequence: load-more in flight → new query/filter
starts (markPending) → fresh page-0 fetch fails → retained Content keeps
`loadingMore=true`. The parked append can never publish (stale
`loadSerial`), so scroll-loading dead-ended until manual refresh.

Reproduced by new test `a failed refetch releases a superseded append
flag` (failed before fix, passes after). Fix: a page-0 load clears the
obsolete append flag; stale-append suppression unchanged. Both SENTINEL
and ARGUS missed this — their probes covered q-specific races but not
the markPending/append-flag interaction on failure.

### Notes (no action taken; pinned by tests or documented)

| # | Severity | Area | Note |
|---|----------|------|------|
| N1 | Note | Backend | Whitespace-only overlength `q` (e.g. 300 spaces) → 400: length validation precedes blank normalization. Defensible either way; pinned by `overlengthWhitespaceOnlyQueryIsRejectedNotIgnored`. |
| N2 | Note | Backend | Byte-level LIKE: a decomposed (NFD) needle does not match composed (NFC) storage; NBSP needle is literal, not blank. No normalization promised; pinned. |
| N3 | Note | Backend | `+` in a raw query decodes to space on the wire (Tomcat form semantics); Retrofit percent-encodes literal `+` as `%2B`. Proven on real HTTP. |
| N4 | Note | Android | `q` text reaches debug logcat URLs (OkHttp HEADERS interceptor, DEBUG only, auth headers redacted) — consistent with all existing URL logging; release unaffected. |
| N5 | Note | Android | `ImeAction.Search` has no `KeyboardActions` — the IME key is a cosmetic no-op while debounce already applies. |
| N6 | Note | Android | Search input not client-capped at 255 — a >255-char needle yields an honest server 400 → generic error UI. Contract-sanctioned client-defect path. |
| N7 | Note | Env | `hs_err` OOM killed the Gradle daemon once during parallel backend+Android gate load; rerun clean. Environment memory pressure, not product. |

## 3. Falsification results (all delivery claims held)

| Claim | Probe | Result |
|---|---|---|
| Filter-before-pagination over full history | matches only on page ≥2 of the unfiltered set | PASS — real Postgres |
| Owner-scoped counts | same term present for users A and B | PASS — A's `totalElements` excludes B's rows; B's text never in payload |
| LIKE metachar literalization | lone `%`, `_`, `\` needles | PASS — each matches only its literal self |
| Parameter binding, no concatenation | captured Hibernate SQL | `lower(t1_0.description) like ? escape '\'` — all values bound |
| Owner predicate unconditional | captured SQL | `where t1_0.user_id=?` always present |
| DB-side pagination + ordering | captured SQL | `order by occurred_on desc,id desc offset ? fetch first ?` |
| Real servlet percent decoding | random-port Tomcat, raw URI | PASS — `%25`→`%` literal needle; `%2525` matched only literal `%25` (single-decode); UTF-8 needle decoded correctly; MockMvc artifact definitively ruled out |
| Validation order | overlength + whitespace-only needles | PASS — pinned |
| Frozen unpaged endpoint | `GET /api/transactions` ignores `q` | PASS |
| Ordering under same-date ties | equal `occurred_on`, distinct ids | PASS — `id DESC` tiebreak |
| Filter composition | q×type×account×category×dates | PASS — intersection counts distinguishable (fixtures designed so a wrong operator fails) |
| A→B privacy on session switch | nav backstack analysis | PASS — `SignedIn(B)` after `Expired(A)`/`SignedIn(A)` destroys the preserved backstack (`popUpTo(0){inclusive}`); VMs + pending debounce jobs die with it; generation guards cover in-flight responses |
| Debounce / immediate clear / page-0 restart | VM tests incl. new refresh-under-search | PASS — refresh re-sends active `q` from page 0 |
| Stale success/error suppression | serial + filter-equality guards | PASS (40/40 VM tests on final source) |
| No request/SQL logging of `q` | application properties + interceptors | PASS — none configured; OkHttp HEADERS is DEBUG-only and redacts auth |

## 4. Comparison with internal reviews

| | SENTINEL | ARGUS | This audit |
|---|---|---|---|
| Verdict | PASS | PASS | PASS (conditional on human approval) |
| Blocker/Major/Minor | 0/0/0 | 0/0/0 | 0/0/**1** (EXT-M18-1, fixed) |
| Notes | 2 | 3 | 7 |

Agreement: their MockMvc-artifact Note (my real-HTTP test proves the
product was never wrong), emulator-instability Note (re-observed —
single hierarchy-loss flake on a cold-booted AVD, zero FATAL EXCEPTION,
identical test green on Pixel), JVM/DB `lower()` divergence Note (my
Unicode-boundary tests pin it as an honest limitation). No
contradictions. External-only additions: EXT-M18-1 + N1–N7.

## 5. Final gate results (post-remediation source)

| Gate | Result |
|---|---|
| Backend full suite | **419/419** (407 + 11 audit + 1 http; 0 failures) |
| Android JVM | **674/674** (672 + 2 audit; 0 failures) |
| detekt | 0 smells |
| lintDebug | 0 errors / 22 warnings (= M17 accepted baseline) |
| assembleDebug / assembleRelease + R8 + lintVitalRelease | green; `mapping.txt` 46 MB; `TransactionDtos` survive obfuscation |
| Physical Pixel 4 XL connected suite — run 1 | **200/200** |
| Physical Pixel 4 XL connected suite — run 2 | **200/200** |
| Emulator (advisory, cold-booted mid-run) | 199/200 — 1 compose-hierarchy flake, environment per §3/NOTE-2, zero FATAL EXCEPTION |
| Install + launch final APK on Pixel | verified — `MainActivity` resumed |

Regression invariants re-verified inside the green suites:
M13 (single-flight refresh, session generation), M15 (page metadata,
ordering, stale/load-more guards), M16 (`RecurringOptimisticLockTest` +
scheduler jobs), M17 (`CsvTransactionStore`, TPBank import dedup,
description persistence/normalization, surrogate-safe cap, CSV formula
neutralization).

## 6. Mandatory acceptance conditions

```
UNRESOLVED_BLOCKERS=0
UNRESOLVED_MAJORS=0
UNRESOLVED_MINORS=0   (EXT-M18-1 fixed at dfda980, regression-tested)
M17_COMPLETE=YES
M18_PRODUCT_ALIGNMENT=PASS
M18_BACKEND_CONTRACT=PASS
M18_OWNER_ISOLATION=PASS
M18_SEARCH_COMPLETENESS=PASS
M18_FILTER_COMPOSITION=PASS
M18_PAGINATION=PASS
M18_COUNT_QUERY=PASS
M18_SPECIAL_CHARACTER_SAFETY=PASS
M18_HTTP_ENCODING=PASS
M18_SESSION_GENERATION=PASS
M18_QUERY_RACE_SAFETY=PASS
M18_STALE_ERROR_SAFETY=PASS
M18_LOAD_MORE_SAFETY=PASS
M18_ACCESSIBILITY=PASS
M18_TEST_INTEGRITY=PASS
M18_PHYSICAL_RUNTIME=PASS
M18_RELEASE=PASS
M0_M17_REGRESSION=PASS
```

## 7. Verdict

```
M18_EXTERNAL_AUDIT_FINAL=ACCEPTED
M18_HUMAN_APPROVED=YES   (authorized by the audit-commission instruction)
M18_STATUS=COMPLETE
SERVER_SIDE_TRANSACTION_SEARCH=APPROVED
```

Remediation commits: Android `dfda980` (EXT-M18-1 fix + 2 tests) ·
Backend `d8554f0` (11 independent audit tests — additive only, zero
production-code change required; backend claims held as delivered).

## 8. Boundary confirmations

```
COURSE_UPDATE_EXECUTED=NO
COURSE_SYNC_DEFERRED=YES
PUSH_PERFORMED=NO
TAG_CREATED=NO
M19_DEFINED=NO
M19_STARTED=NO
REFERENCE_REPO_TOUCHED=NO
PROTOTYPE_TOUCHED=NO
PRIVATE_FIXTURES_ACCESSED=NO
BACKEND_HUMAN_DOCS_UNTOUCHED=YES   (README.md, HUONG-DAN-SU-DUNG.md,
                                    docs/SPENDWISE-REQ.md left as found)
```

Final git state: Android `main` = `dfda980`, clean. Backend `main` =
`d8554f0`, only pre-existing human-owned dirty files remain.
