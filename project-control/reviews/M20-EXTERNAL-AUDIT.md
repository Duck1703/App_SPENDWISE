# M20 INDEPENDENT EXTERNAL AUDIT — Offsetting Entry

```
ROLE: EXTERNAL AUDITOR (independent — findings derived from source,
      real Postgres, and the physical device; delivery claims treated
      as inputs to falsify, not evidence)
MILESTONE: M20 — PRODUCT §21 offsetting entry (S10 → S11, journey J14)
ANDROID_RANGE: e1f149d..b0a359b (delivery 06a10bf + audit remediation)
BACKEND_RANGE: 9970c3e..4ced814 (test-only; production tree identical
               to the M19-accepted baseline)
DEVICE: physical Pixel 4 XL (Android 13) — serial redacted
```

## 1. Predecessor chain — verified, not assumed

```
M19_EXTERNAL_AUDIT_FINAL=ACCEPTED   (reviews/M19-EXTERNAL-AUDIT.md)
M19_HUMAN_APPROVED=YES              (milestones/M19-ACCOUNT-LIFECYCLE.md)
M19_STATUS=COMPLETE
ACCOUNT_EDIT_ARCHIVE=APPROVED
ANDROID_M19_ACCEPTED_HEAD=e1f149d99ef48192d63e83a76207395acc978e7e
  (merge-base vs HEAD = e1f149d — proven ancestor; the earlier
  `c50ebd4` label was the remediation commit beneath the docs closeout)
BACKEND_M19_ACCEPTED_HEAD=9970c3e944a11e295115bb5787b83e1aca5c3f75
  (merge-base vs HEAD = 9970c3e — proven ancestor)
ANDROID_M20_DELIVERY_HEAD=06a10bfafef24d9036468442ace878578236c0f4
BACKEND_M20_DELIVERY_HEAD=1768a72c65a006baf3c9c5a61852e8b084f1cf09
ANDROID_CLEAN_BEFORE=YES
BACKEND_PREEXISTING_DIRTY=README.md (M) · HUONG-DAN-SU-DUNG.md (??) ·
  docs/SPENDWISE-REQ.md (??) — human-owned, untouched throughout
REFERENCE_HEAD=5f9622c59ce602ce33c98ee633ba799f903b67e1 (clean)
```

## 2. Canonical contract re-derived

PRODUCT.md §21 (lines 465–488) + S10 (§305/§438) + J14 (§682) + §33
terminology (line 704):

- `INCOME`/`EXPENSE` detail → "Create offsetting entry" → create form
  prefilled **inverse type**, same account, same amount, same currency,
  same category (if set). Real `POST /api/transactions`. Original stays
  in history. Verbatim honest copy required. `TRANSFER_*` hidden. Never
  labeled undo/delete/edit. Append-only — BE-005 stays product-gated.
- §20: date is user-selectable "Today"-defaulted (M11 picker) — an
  offset landing on today is canonical, not a defect.

**M20_PRODUCT_ALIGNMENT=PASS** (post-remediation — see §13).

## 3. Backend production scope — verified at git level

```
git diff --name-status 9970c3e..4ced814 → one file:
  A src/test/java/com/spendwise/account/TransactionOffsetContractTest.java
BACKEND_PRODUCTION_CHANGED=NO
BACKEND_TEST_ONLY_CHANGE=YES
SCHEMA_CHANGED=NO · NEW_OFFSET_ENDPOINT=NO · NEW_OFFSET_LINKAGE=NO
```

## 4. Audited Android diff (e1f149d..b0a359b)

Production surface — 9 files: `AppRoutes` (`offsetTransactionId` arg),
`TransactionDetailViewModel` (account resolve + `offsetEligible`),
`TransactionDetailScreen` (OutlinedButton + verbatim caption),
`CreateTransactionViewModel` (source re-fetch + guarded prefill),
`CreateTransactionScreen` (info banner), `TransactionDestinations`,
`MainFlow` (detail→create wiring), `TransactionsUiState` (two state
fields), `strings.xml`. No logging, no wire DTO change, no new
dependency, no new permission. `M20_SCOPE_ONLY=YES`.

## 5. Financial correctness — real Postgres evidence

`TransactionOffsetContractTest` (MockMvc + real DB, 6 tests):

- EXPENSE 25.00 then INCOME 25.00 on a 100.00 account → `75 → 100`.
  Original row re-read byte-identical (type/amount/category/occurredOn/
  sourceRef/description). Offset row is `MANUAL:`-sourced, distinct id.
- **INCOME 40.00 then EXPENSE 40.00 → `140 → 100`** (added by audit —
  the delivery exercised only one direction).
- Both rows in unpaged list, `/page` (`totalElements=2`), `?q=` note
  search (added by audit — the delivery's test name claimed search but
  never sent `q`), and CSV export (header + 2 data rows).
- Foreign-account offset body → bare 404; foreign balance untouched.
- Archived account remains raw-API writable (M19 invariant — pinned).
- Partial inverse = ordinary smaller row (85.00 after INCOME 10 vs
  EXPENSE 25) — honest, no false "fully reversed" semantics.

`LEDGER_MATH=PASS` `ORIGINAL_ROW_UNCHANGED=YES` `APPEND_ONLY=PASS`
`NEW_ROW_DISTINCT=YES` — BigDecimal comparisons throughout, no floats.

## 6. Eligibility — verified at both layers

| Source | Detail action | Create-gate |
|---|---|---|
| INCOME / EXPENSE, live account | shown | prefills |
| TRANSFER_IN / TRANSFER_OUT | hidden | `optionsError` refusal |
| UNKNOWN type | hidden | refusal |
| Archived-account row | hidden | refused — authoritative fresh check (see EXT-M20-2) |
| Unresolved/foreign/missing id | hidden / `NotFound` | `optionsError` refusal |

`FORGED_ROUTE=REJECTED` — `offsetTransactionId` is a lookup key only;
`findById` resolves owner-scoped server data (cold cache → one
unfiltered owner fetch → miss = `NotFound`). No field is trusted from
nav args. `AUTHORITATIVE_FETCH=PASS` `NAV_ARGS_TRUSTED_FOR_FIELDS=NO`.

## 7. Prefill discipline — challenged

- **Prefill-once**: guarded by `offsetSourceId != null`; re-runs cannot
  reapply (pinned). Form is not interactive while `optionsLoading`
  (skeleton) — no "late prefill clobbers live input" window exists.
- **Ordering**: options + source resolve inside one `loadOptions`
  coroutine — either order lands in the same serialized path.
- **User edits**: `onAmountChange`/`onTypeChange`/`onAccountChange`/
  `onCategoryChange` write state directly; a re-run preserves every
  field (pinned incl. user-picked account surviving a re-run).
- **Session boundary**: `SessionEndedException`/`SessionExpired` on the
  source lookup arm `optionsExpired` — nothing publishes; same-user
  resume re-runs via `onVisible` and prefills (pinned dead-gen test).
- **A→B isolation**: different-identity login clears the protected
  stack (VM destroyed); repository writes are generation-guarded —
  dead-session results throw `SessionEndedException`, never publish.

## 8. Submission contract

- Canonical `POST /api/transactions` via `TransactionRepository.create`
  — request DTO unchanged; wire pin asserts no `offset`/`link`/
  `originalId` field in the serialized body.
- `isSubmitting` lock: double-tap → one repository call (pinned in
  offset mode); `TRANSMITTED_401_POST_COUNT=1` / `AUTO_REPLAY=0` —
  `SessionRefreshContractTest` pins the identical `create transaction`
  call at wire level; the offset rides it verbatim.
- Ambiguous commit → `Ambiguous` copy ("couldn't confirm… check your
  transactions") — no auto-retry anywhere (pinned).
- Post-success: pops to the source detail; `mutations` + cache
  invalidation repaint the list; original row unchanged.

## 9. Initial external findings (frozen before internal-review read)

`INITIAL_VERDICT=FINDINGS` — 0 Blocker, 1 Major, 2 Minor, 4 Note.

- **EXT-M20-1 (MAJOR, ANDROID) — REMEDIATED `b0a359b`.** A `loadOptions`
  re-run after the offset prefill (account-404 refresh, retry, resume)
  silently substituted a vanished source account with
  `accounts.firstOrNull()` — the "offsetting entry" banner persisted
  while the financial target quietly became a *different* account. The
  delivery's own test asserted the substitution and the code comment
  claimed "never a silent substitute." Reproduction: offset-prefill →
  archive source account elsewhere → re-run → `accountId == "acc-9"`.
  Fix: in offset mode a stale `accountId` drops out (`Required` on
  submit — deliberate re-pick), never substitutes. Normal create's
  first-account default is unchanged. Regression pins: no-substitution
  + zero create calls + user-picked-live-account preserved.
- **EXT-M20-2 (MINOR, ANDROID) — REMEDIATED `b0a359b`.** The offset
  eligibility check trusted the cache-first picker list; a cross-session
  archive could pass the gate on a stale flag (ARGUS NOTE-1's claim that
  a reload always re-fetches was wrong — `findById`/`list()` are
  cache-first). The prefill path now re-resolves the source account with
  `refresh=true` and refuses if archived/unresolvable. Regression pin:
  stale list + fresh-archived → `optionsError`.
- **EXT-M20-3 (MINOR, TEST/BACKEND) — REMEDIATED `4ced814`.** The
  contract suite exercised only EXPENSE→INCOME (the §17-required
  INCOME→EXPENSE direction was untested) and
  `bothRowsAppearInListPageSearchAndExport` never sent `q` — an
  overclaimed search pin. Added the inverse direction + a real
  `?q=offset-proof` assertion returning both rows.
- **EXT-M20-4 (NOTE, TEST)** — device-tier offset coverage is
  screen-level (stateless Compose); no nav-level journey harness exists
  in this codebase (consistent with M12–M19's tier split: screen +
  JVM-VM + real-Postgres contract). The E2E path is covered by
  screen pins + VM pins + backend contract.
- **EXT-M20-5 (NOTE, PRODUCT)** — the banner text stays verbatim if the
  user flips the type back (the copy describes intent; it never claims
  balance restoration). Accepted §21 edit-freedom.
- **EXT-M20-6 (NOTE, ANDROID)** — `inverseType` returns INCOME for
  non-INCOME types; unreachable behind `isOffsettableOn` (private
  extension, gated call site). Fragile ordering only.
- **EXT-M20-7 (NOTE, ANDROID)** — double-tap on the detail action can
  stack two create destinations (standard nav behavior; each form has
  its own submit lock). Bounded, user-visible.

## 10. Internal-review comparison

| Internal claim | Independent verdict |
|---|---|
| SENTINEL PASS (0B/0M/1M-remediated/3N) | PASS upheld; MIN-1 fix verified real. NOTE-2 ("vanished account falls back — identical to prefill") **disputed** → EXT-M20-1 MAJOR: under the offset banner a silent retarget breaks §21's same-account promise; normal-mode parity reasoning doesn't transfer. |
| ARGUS PASS (0B/0M/0M/3N) | PASS upheld. NOTE-1's premise ("archive in another session hides the action on next refresh") was **factually wrong** — cache-first lookups keep it stale; EXT-M20-2 closed the consequential gate at prefill time. |
| Handoff gate numbers (457/742/218×2) | Independently reproduced on delivery source before remediation (457/457, 742/742, 218/218); re-derived post-remediation (458/458, 745/745, 218/218). |
| Handoff finding #2 (fallback acceptable) | Superseded by EXT-M20-1 remediation — recorded here as delivered-then-fixed. |

`INTERNAL_FINDINGS_CONFIRMED=1` (MIN-1) · `EXTERNAL_ONLY_FINDINGS=3`
(EXT-M20-1, -2, -3) · `FALSE_POSITIVES=0`.

## 11. Final gates — all on remediated source

| Gate | Result |
|---|---|
| Backend full (`mvnw clean test`, real Postgres) | **458/458** — 0 F/0 E/0 S (107 classes; offset suite 6/6) |
| Backend build | **PASS** (`clean` lifecycle, Flyway validate 13 migrations) |
| Android JVM (`testDebugUnitTest --rerun-tasks`) | **745/745** — 0 F/0 E/0 S (53 classes) |
| `lintDebug` | **PASS** — 0 errors, 22 warnings (pre-existing) |
| `detekt` | **PASS** — 0 smells / 231 files |
| `assembleDebug` / `assembleRelease` | **PASS** — `minifyReleaseWithR8` green |
| `lintVitalRelease` | **PASS** |
| `compileDebugAndroidTestKotlin` | **PASS** |
| R8/serializers | `TransactionCreateRoute$$serializer` + `offsetTransactionId` retained in `mapping.txt` |
| Manifest/privacy | INTERNET only; `usesCleartextTraffic=false`; `datastore/` excluded from backup + device transfer; zero logging in the M20 diff |
| Release APK | `app-release-unsigned.apk` — 2,478,662 B — SHA-256 `22313a217f9e22c35dacd14366740fe27f541c5ef179550dca30228599d9251b` |
| Physical Pixel 4 XL | `connectedDebugAndroidTest` **218/218** run 1 · **218/218** run 2 |
| Install + launch | debug APK installed; `MainActivity` resumed |

## 12. M0–M19 regression evidence

`M0_M19_REGRESSION=PASS` — backend 458/458 and Android 745/745 cover:
M3 create path (canonical post reused verbatim); M12 transfer pairing
(`TRANSFER_*` excluded at both gates, transfer service untouched);
M13 no-replay (wire-pinned for this exact endpoint); M15 pagination
(`totalElements=2` for the pair); M16 optimistic locking (account PATCH
suite green); M17 descriptions (offset note starts empty, 255-cap
inherited, surrogate-safe); M17 CSV security (both rows export, no
formula-injection surface added); M18 search (`?q=` hits both rows);
M18 load-more (untouched); M19 archive (raw-API writability pinned;
picker exclusion now enforced authoritatively in offset mode).

## 13. Boundaries

```
HUMAN_BACKEND_DOCS_UNCHANGED=YES   (README.md / HUONG-DAN / SPENDWISE-REQ
                                    retain their pre-existing dirty state)
REFERENCE_UNCHANGED=YES            (kotlin-accelerator-ai @ 5f9622c, clean)
PROTOTYPE_MODIFIED=NO
PRIVATE_DATA_ACCESSED=NO           (synthetic fixtures only)
COURSE_UPDATE_EXECUTED=NO          (COURSE_SYNC_DEFERRED=YES)
M20_BACKEND_COURSE_SYNC_REQUIRED=NO (production delta = NONE)
PUSH_PERFORMED=NO · TAG_CREATED=NO
M21_DEFINED=NO · M21_STARTED=NO
```

## 14. Final git state

```
ANDROID_HEAD_AFTER=<see closeout commit>
BACKEND_HEAD_AFTER=4ced814c3e45068b1c7dfc115c3ddcf59ecb7a34
  (M19 base 9970c3e + 1768a72 test file + 4ced814 test strengthening;
   production tree identical to M19-accepted baseline)
ANDROID_REMEDIATION_COMMIT=b0a359b12ac576dc914e16fd373cdc225da5b46f
BACKEND_REMEDIATION_COMMIT=4ced814 (test-only)
UNRESOLVED_BLOCKERS=0
UNRESOLVED_MAJORS=0
```

## 15. Verdict

```
M20_EXTERNAL_AUDIT_FINAL=ACCEPTED
BLOCKER=0 · MAJOR=0 (1 remediated: EXT-M20-1)
MINOR=0 (2 remediated: EXT-M20-2, EXT-M20-3) · NOTE=4
M20_HUMAN_APPROVED=YES   (per the audit-commission conditional
                          closeout: all mandatory gates green)
M20_STATUS=COMPLETE
OFFSETTING_ENTRY=APPROVED
SPENDWISE_STATUS=M20_COMPLETE
NEXT_ACTION=HUMAN_M21_DECISION
```

The offsetting-entry flow is a faithful PRODUCT §21/S10/J14
implementation: an eligible INCOME/EXPENSE row offers a clearly
explained compensating-entry action; the create form prefills the
inverse type with the source's own account/amount/category fetched from
authoritative data; the post is an ordinary canonical create; the
original is immutable and fully visible; no wire field, endpoint, or
ledger semantics were invented. The one Major found by this audit —
silent account retargeting under the offset banner — and both test-integrity
Minors were remediated with failing-capable regression pins and re-proven
through every final gate on the final source.

M21 remains undefined and untouched.
