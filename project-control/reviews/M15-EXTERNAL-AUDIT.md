# M15 EXTERNAL AUDIT — Scalable Transaction History (REQ-BE-007)

```
AUDITOR:  independent external audit (fresh session; all delivery
          claims treated as hypotheses and re-derived)
ANDROID_AUDITED:  256c2ce..bb33ffc delivery chain + audit remediation
                  ebd966a + hygiene 48ec4ab (audited HEAD = 48ec4ab)
BACKEND_AUDITED:  4ed4f81..d3e73c8 delivery + audit test-strengthening
                  ef08539 (audited HEAD = ef08539)
DEVICE:   Pixel 4 XL, Android 13 (serial redacted; emulator excluded
          via ANDROID_SERIAL)
VERDICT:  ACCEPTED — after remediation of 1 Major + 3 Minor in-task;
          all final gates re-green on post-remediation source.
```

## Findings and dispositions

| # | Sev | Finding | Disposition |
|---|---|---|---|
| EXT-M15-MAJOR-1 | Major→FIXED | `loadNextPage` catch paths mutated `_state.value` without the `tick`/`filters` staleness guard the success path has: a superseded append's `SessionEndedException` cleared `loadingMore` on the *new* query's Content — the scroll trigger could then double-fire the same page index, and both publishes would increment `nextPage` past a never-fetched page → **ledger gap**. A non-expired `AppError` likewise leaked `moreError` onto a different filter set. | `ebd966a` — catch paths now guarded by `isCurrentQuery(tick, filters)`; two new regression tests (`stale append failure cannot poison the new query's state`, `stale append session-end clears nothing on the new query`) prove the gap is closed. |
| EXT-M15-MINOR-1 | Minor→FIXED | Date-chip clear affordance was 40dp×48dp — below the project's canonical 48×48 (`SwSpacing.TouchTargetMin`); internal reviews had accepted 40dp. | `ebd966a` — `.size(SwSpacing.TouchTargetMin)` (48×48). |
| EXT-M15-MINOR-2 | Minor→FIXED | `transactions_more_error_body` claimed "The list you see is complete" after a failed append — false when `hasMore` remains true; dishonest ledger wording. | `ebd966a` — "Loaded entries are still here — tap or pull down to retry." |
| EXT-M15-MINOR-3 | Minor→FIXED | `markPending` cleared `refreshError` but left a stale `moreError` banner coexisting with the refresh spinner (Sentinel S-1 overlap). | `ebd966a` — `moreError = null` on pending mark. |
| EXT-M15-MINOR-4 | Minor→FIXED | Backend contract tests lacked the strongest §85 probes: `from==to` single-day, leap-day, and an explicit filter-before-slicing proof. | `ef08539` — three new real-Postgres cases, all green (15/15). |
| EXT-M15-NOTE-1 | Note | Canonical REQ-BE-007 says "Pageable on `GET /api/transactions`"; delivered as sibling `GET /api/transactions/page`, keeping the unpaged endpoint byte-identical for detail/import consumers. Capability-equivalent, additive, human-governed milestone decision — intent satisfied. | Documented. |
| EXT-M15-NOTE-2 | Note | Offset pagination drifts if rows are inserted between page requests (new row at top can shift a boundary row onto two pages — client dedup absorbs it — or push one forward past a fetched boundary until refresh). Page-number semantics are the accepted canonical model. | Documented. |
| EXT-M15-NOTE-3 | Note | No composite `(user_id, occurred_on, id)` index; `uq_txn_source_ref UNIQUE(user_id, source_ref)` still bounds scans to the caller's rows — adequate at per-user ledger scale; a schema migration was correctly NOT invented. | Documented; revisit if ledgers grow. |
| EXT-M15-NOTE-4 | Note | `provenance=UNKNOWN` → `sourceRef IS NULL` → empty on the NOT NULL column — honest by construction, divergent from client derivation only for rows the server cannot write. | Confirmed internal finding; accept. |
| EXT-M15-NOTE-5 | Note | Invalid `from>to` yields server 400 → generic refresh banner (recoverable, honest) rather than a range-specific message. | Documented; possible UX polish later. |

## Verified claims (re-derived, not trusted)

- Owner scope: unconditional `userId = :caller` predicate; `totalElements`
  owner-scoped; foreign/missing `accountId` → 404 (test-proven, two real
  users).
- DB-backed paging: `findAll(spec, pageable)` — real LIMIT/OFFSET +
  count; fetch joins guarded out of the count query by result-type
  check; no in-memory slicing anywhere.
- Ordering `occurredOn DESC, id DESC` — deterministic ties proven on
  Postgres (4 same-date rows, disjoint size-2 pages).
- Inclusive `from`/`to` on `occurredOn`; single-day `from==to`;
  leap-day 2024-02-29; year boundary 2023-12-31→2024-01-01 — all live
  against Postgres.
- Validation: `page<0`, `size∉1..100`, `from>to`, category+uncategorized
  conflict, bad enum/date → 400; out-of-range page → 200 empty;
  unauthenticated → 401.
- Wire: MockWebServer asserts exact path, every param, null-omission
  set-equality, bearer propagation, 401→Session.Expired.
- Paging state machine: single-flight (`loadingMore`), supersede-guard
  on success AND error paths, `distinctBy(id)` merge, end-of-ledger
  stop, refresh = page-0 replace, `Showing N of M` = server
  `totalElements`.
- Session safety: generation-stamped SessionCache; A→B races cannot
  publish (stale `cache.write` → SessionEndedException → drop);
  M13 GET retry-once machinery untouched and still contract-tested.
- a11y: chip cds announce selected date; clear affordance is its own
  48×48 node with parameterized "Clear <From|To> date filter" cd;
  horizontal row reachable at 411dp (proven on-device).
- Release: fresh `app-release-unsigned.apk` 2,451,754 B sha256
  `5075f0e1e3c46b86…` (differs from delivered `25908dd8` — remediated
  source); R8 mapping keeps `TransactionPageDto$Companion.serializer`.
- Manifest/backup: INTERNET only, cleartext off, datastore excluded
  from cloud-backup + device-transfer + fullBackupContent; no new
  permissions/exported components; no sensitive logging in diff.

## Gate evidence on final source

| Gate | Result |
|---|---|
| Backend targeted | `TransactionPageContractTest` 15/15 |
| Backend full | 365 tests, 1 failure = documented date-dependent `RecurringSchedulingJobTest` baseline flake (unchanged signature) |
| Backend package | `spendwise-0.1.0-SNAPSHOT.jar` built |
| Android JVM (fresh `--rerun-tasks`) | 617/617, 0 fail/skip |
| lint / detekt / assembleDebug / androidTest-compile | all PASS / 0 smells |
| Physical Pixel 4 XL — preliminary (pre-remediation) | 186/186 ×2 |
| Physical Pixel 4 XL — final source | see Final section below |
| Internal reviews | Sentinel PASS, Argus PASS — read post-freeze; all internal findings confirmed; external added the MAJOR stale-append fix |

## Final section

Final-source gates (post-remediation HEAD `48ec4ab` / backend `ef08539`):

- Physical Pixel 4 XL: **186/186 ×2 consecutive** on remediated source
  (plus 186/186 ×2 preliminary on delivery source — four consecutive
  full-suite greens in this audit).
- Install + launch: `MainActivity` top-resumed, process alive, no crash.
- M0–M14 regression: no behavioral change outside `feature/transactions`
  + one additive backend endpoint; unpaged list byte-identical; the full
  connected suite (which covers M1 auth, M2 accounts, M3 transactions,
  M4 home, M5 import, M9 export, M11 dates, M12 transfers, M13 session,
  M14 budgets) passed 186/186 twice on final source.
- Scope audit: no search, no transaction/account mutation, no FX, no
  profile/category CRUD, no M16, no course edits, no schema change.

VERDICT: **M15_EXTERNAL_AUDIT_FINAL=ACCEPTED**. All §109 conditions
satisfied; conditional Human closeout authorization exercised:
M15_HUMAN_APPROVED=YES, M15_STATUS=COMPLETE.
