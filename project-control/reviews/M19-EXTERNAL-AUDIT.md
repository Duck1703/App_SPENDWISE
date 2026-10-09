# M19 — External Audit (Account Edit / Archive, REQ-BE-006)

Independent adversarial audit of the M19 delivery claims. The delivery
report and internal reviews were treated as hypotheses; every claim was
re-derived from source and falsified against real Postgres, the real
HTTP layer, and the physical Pixel 4 XL.

```
AUDIT=M19-EXTERNAL-AUDIT
ROLE=independent external auditor (adversarial; not a re-run of ARGUS/SENTINEL)
BASELINE: Android 642433f (delivery head) · Backend 342e85d (delivery head)
FINAL:    Android c50ebd4 · Backend 9970c3e
```

## 1. Method

- Re-derived the canonical contract from REQ-BE-006 + the M19 brief —
  not from the delivery report. Pinned: `PATCH /api/accounts/{id}`,
  sparse `name?`/`archived?` (>=1 field else 400), owner-scoped 404,
  JPA optimistic-lock 409, V13 `BOOLEAN NOT NULL DEFAULT FALSE`,
  archive = entry-picker semantics only (ledger/history/search/export
  retained, raw-API writes still allowed).
- Audited the complete backend diff (`d8554f0..342e85d`, 9 production
  files) and the complete Android diff (`e58dbbd..642433f`, all data /
  repository / ViewModel / screen / picker / navigation files) line by
  line.
- Added an independent backend audit suite — no delivery fixtures
  reused:
  - `AccountUpdateExternalAuditTest` — 15 tests (sparse/absent-vs-null
    semantics, validation parity, coercions, owner isolation,
    raw-API writability on archived accounts, atomicity, idempotency).
  - `AccountPatchConflictAuditTest` — 1 test (HTTP-level 409 mapping
    via `MockMvc` + mocked service throwing
    `OptimisticLockingFailureException`).
  - `V13MigrationAuditTest` — 3 tests (column shape, Flyway
    `validate()`, upgrade-replay preserving existing rows as
    `archived=false`).
- Added Android regression pins: `archived=false` unarchive wire
  transmission, update-failure-emits-no-mutation, dead-generation
  mid-flight discard, session-expired save lock release.
- Verified M2 test-restoration integrity: `AccountsServiceContractTest`
  diff vs `e58dbbd` is purely additive — all 7 original M2 tests
  survive verbatim, 6 M19 tests appended (now 14 total after audit's
  +1).
- Re-ran every gate on the final (post-remediation) source.

## 2. Initial independent findings (frozen before remediation)

### EXT-M19-1 — MINOR (backend) — FOUND, REPRODUCED, FIXED `9970c3e`

`UpdateAccountRequest.name` used `@Pattern(regexp = ".*\\S.*")` while
account create uses `@NotBlank`. In Java regex `.` excludes line
terminators, so a name containing `\n` — creatable via
`POST /api/accounts` — was un-settable via PATCH (400). The milestone
claims an "identical rule". Fails closed; no data hazard; MINOR.

Reproduced: `{"name":"a\nb"}` (JSON-escaped newline) → 400 before fix,
200 + stored verbatim after `(?s)` DOTALL fix, with the same body
accepted on create (parity pinned in one test).

An audit-test artifact is also documented: the first probe sent a raw
control character inside the JSON string (illegal JSON, 400 at parse
before the DTO exists) — the divergence claim still held from source
analysis (the old pattern provably cannot match `\n`), and the
corrected probe now exercises the real validation path.

### EXT-M19-2 — NOTE (backend, observed contract pinned)

Jackson default coercion accepts `{"archived":1}` → `archived=true`
and `{"name":123}` → `name="123"` — the same mapper governs create and
every other DTO in the API (parity; not M19-specific). Lenient typing,
harmless domain-wise (still owner-scoped, still an explicit write of a
boolean/string domain). Hardening coercion globally would be a
cross-cutting API change outside M19 scope. Pinned as observed
behavior by `numericArchivedLiteralCoercesToTrue` /
`numericNameCoercesToStringLikeCreate`; no remediation taken.

### EXT-M19-3 — NOTE (test strength) — PINS ADDED `c50ebd4`

Three adversarial edges were uncovered by delivery tests: unarchive
wire transmission (`archived:false` could have been silently dropped
by the sparse encoder), update-failure mutation emission, and
repository update on a dead generation mid-flight. All three are now
pinned by new tests. No defect found — the pins confirm the
implementation was already correct.

### EXT-M19-4 — NOTE (Android, accepted design)

Entry pickers serve `list()` cache-first: an archive made in another
session/device only reflects after the next cache invalidation or
fresh `list(refresh=true)`. In-session writes update the cached row,
so the app itself is consistent; this is the AD-013 accepted
cache-first design, not a defect.

### EXT-M19-5 — NOTE (docs, low)

`EditAccountViewModel`'s docstring says the form "reloads server
truth" on 409; the actual UX is a persistent conflict banner + copy
telling the user to reopen the screen (auto-reload would silently
destroy the user's typed input — arguably the worse behavior).
`SwBanner` has no action slot, so no reload affordance is wired.
Honest UX, slightly overstated docstring. Also fixed a misleading
`explicitNulls=false` comment on `UpdateAccountRequestDto` — sparse
omission actually comes from kotlinx `encodeDefaults=false` + null
defaults (wire-proven by contract tests).

## 3. Falsification results (delivery claims re-derived)

| Claim | Probe | Result |
|---|---|---|
| V13 additive, preserves rows + balances | `V13MigrationAuditTest` on real Postgres; Flyway `validate()`; column shape | PASS — `BOOLEAN NOT NULL DEFAULT FALSE`, existing rows replay to `archived=false`, `validate()` clean |
| Sparse PATCH never clears unspecified fields | `{archived:true}` then `{name:...}` sequences; stored entity asserted | PASS — untouched fields retained verbatim |
| Explicit-null vs absent | `{"name":null}` single-field patch | PASS — treated as empty patch → 400 (null ≡ absent, no NPE, no clear) |
| `archived=false` transmitted + applied | wire-level request-body test + backend unarchive probe | PASS — false is emitted on the wire and stored |
| Cross-user PATCH | PATCH B's id as user A | PASS — 404, foreign row untouched |
| Real JPA optimistic locking | two-EntityManager `@DataJpaTest` (delivery) + HTTP 409 slice test | PASS — second writer throws `OptimisticLockingFailureException`; handler maps 409 truthfully |
| Archived response consistency | `archived` on list/get/dashboard paths | PASS — one `AccountResponse` mapper; dashboard embeds same DTO |
| All four entry pickers exclude archived | source audit + VM tests exercising real `list()` path | PASS — CreateTransaction, CreateTransfer both ends, CreateRecurring, Import target |
| Transfer peer counts active only | `hasTransferPeer` on active set; all-archived path | PASS — all-archived → honest "Required" field error, no crash (`checkNotNull` guarded) |
| Archived retained for lookup surfaces | source audit of filters | PASS — history filter options, export scope, name resolution, list/detail/dashboard all unfiltered |
| Raw-API writes on archived accounts | POST transactions/import on archived id | PASS — succeeds (pinned contract semantics, not a freeze) |
| Server-truth prefill | `findById(accountId, refresh=true)` | PASS — edit form never seeds from cache |
| Sparse request diff | `name`/`archived` sent only when changed vs server baseline | PASS — `hasChanges` uses `name.trim()`; padded-but-equal input disables Save (empty PATCH impossible) |
| M13 no-replay | source audit of `TokenAuthenticator`/`AuthInterceptor` + `retryOnConnectionFailure(false)` | PASS — transmitted PATCH never re-issued; at most one pre-send refresh |
| 409 honest | VM test + copy audit | PASS — persistent banner "Changed elsewhere" + reopen guidance, no fake success, lock released |
| Ambiguous outcome honest | VM test + copy audit | PASS — "couldn't confirm" copy, lock released, no success claim |
| A→B stale responses/errors | generation-stamp + `SessionEndedException`-before-emit order | PASS — dead-generation write discarded, no cross-user mutation event; pinned by new test |
| Double submit | `isSubmitting` early-return test | PASS |
| Balance/ledger immutability | full diff audit of `Account`/`AccountService`/`AccountMapper` | PASS — type/currency/opening/balance have no patch surface; archive writes no ledger row |
| Mutation repaint (SENTINEL MINOR-1) | `AccountsViewModel`/`AccountDetailViewModel` collect `mutations` → `refresh()` = real server fetch | PASS — verified fix, regression tests present |
| Recurring scheduler on archived | `RecurringTransactionScheduler` uses `template.account()` unfiltered | PASS — generation continues (archive ≠ stop, canonical) |
| M0–M18 regression | full suites re-run | PASS — backend 452/452, Android JVM 722/722, Pixel 213/213 ×2 |
| Release/R8 serializer retention | `proguard-rules.pro` keep rules + `assembleRelease` R8 pass | PASS — `@Serializable` companions + `$$serializer` retained |
| Manifest/security | `usesCleartextTraffic=false`, `datastore/` excluded from backup + device-transfer, injectable base URL | PASS |

## 4. Comparison with internal reviews + handoff

| | SENTINEL | ARGUS | This audit |
|---|---|---|---|
| Verdict | PASS | PASS | PASS (conditional on human approval) |
| Blocker/Major/Minor | 0/0/1 (fixed in-delivery) | 0/0/0 | 0/0/**1** (EXT-M19-1, fixed) |
| Notes | 2 | 3 | 5 (EXT-M19-2..5 groups) |

Agreement: SENTINEL's MINOR-1 repaint defect verified remediated;
ARGUS's picker sweep matches the independent map exactly; handoff's
"raw-API writability on archived" pinned-semantics note confirmed by
direct probe. No contradictions.

External-only additions: EXT-M19-1 (name-validation divergence —
missed by both internal reviews), the Jackson coercion pin, and the
four new Android edge pins. The M2-test-restoration incident recorded
in delivery evidence re-verified independently: originals intact.

## 5. Final gate results (post-remediation source)

| Gate | Result |
|---|---|
| Backend full suite (`mvn test`, real Postgres) | **452/452**, 0 fail, 0 err, 0 skip (433 delivery + 19 audit) |
| Android JVM (`testDebugUnitTest`) | **722/722**, 0 fail, 0 err, 0 skip (718 delivery + 4 audit) |
| `lint` / `lintDebug` | 0 errors (22-w baseline parity) |
| `detekt` | 0 code smells (231 files) |
| `assembleDebug` / `assembleRelease` + `minifyReleaseWithR8` + `lintVitalRelease` | all green on final source |
| Physical Pixel 4 XL `99041FFBA006F0` connected suite — run 1 | **213/213**, 0 fail, 0 skip |
| Physical Pixel 4 XL — run 2 (consecutive, same final source) | **213/213**, 0 fail, 0 skip |
| Install + launch final debug APK on Pixel | `Performing Streamed Install — Success`; `MainActivity` `topResumedActivity` |

## 6. Mandatory acceptance conditions

```
UNRESOLVED_BLOCKERS=0
UNRESOLVED_MAJORS=0
UNRESOLVED_MINORS=0   (EXT-M19-1 fixed at backend 9970c3e, regression-pinned)
M18_COMPLETE=YES
M19_PRODUCT_ALIGNMENT=PASS
M19_BACKEND_CONTRACT=PASS
M19_OWNER_ISOLATION=PASS
M19_SPARSE_PATCH=PASS
M19_ARCHIVE_UNARCHIVE=PASS
M19_OPTIMISTIC_LOCK=PASS
M19_PICKER_EXCLUSION=PASS
M19_LOOKUP_RETENTION=PASS
M19_LEDGER_IMMUTABILITY=PASS
M19_SERVER_TRUTH_PREFILL=PASS
M19_M13_NO_REPLAY=PASS
M19_CONFLICT_HONESTY=PASS
M19_AMBIGUOUS_HONESTY=PASS
M19_SESSION_GENERATION=PASS
M19_MUTATION_REPAINT=PASS
M19_TEST_INTEGRITY=PASS   (incl. M2 test restoration)
M19_V13_MIGRATION=PASS
M19_PHYSICAL_RUNTIME=PASS
M19_RELEASE=PASS
M0_M18_REGRESSION=PASS
```

## 7. Verdict

```
M19_EXTERNAL_AUDIT_FINAL=ACCEPTED
M19_HUMAN_APPROVED=YES   (per the audit-commission instruction:
                          conditional closeout authorized on all gates green)
M19_STATUS=COMPLETE
ACCOUNT_EDIT_ARCHIVE=APPROVED
```

Remediation commits: Backend `9970c3e` (EXT-M19-1 fix + 19 independent
audit tests) · Android `c50ebd4` (EXT-M19-3 pins + comment fix). The
only production-code change required was one regex flag; every other
delivery claim held under falsification.

## 8. Boundary confirmations

```
COURSE_UPDATE_EXECUTED=NO
COURSE_SYNC_DEFERRED=YES
PUSH_PERFORMED=NO
TAG_CREATED=NO
M20_DEFINED=NO
M20_STARTED=NO
REFERENCE_REPO_TOUCHED=NO
PRIVATE_FIXTURES_ACCESSED=NO
BACKEND_HUMAN_DOCS_UNTOUCHED=YES   (README.md, HUONG-DAN-SU-DUNG.md,
                                    docs/SPENDWISE-REQ.md left as found)
```

Final git state: Android `main` = `c50ebd4`, clean. Backend `main` =
`9970c3e`, only pre-existing human-owned dirty files remain.
