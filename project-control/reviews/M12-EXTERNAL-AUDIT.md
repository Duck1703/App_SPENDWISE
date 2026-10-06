# M12 External Audit — Transfers Between Own Accounts

```
AUDITED_RANGE (Android): 7f7be92..231e10e
  (feature 00fd436 + second-cycle remediation 231e10e)
BACKEND: D:\spendwise bc6e0bb..fd2ca1b
  (8803396 public endpoint + fd2ca1b date-snapshot/atomicity-test fix)
INITIAL_VERDICT: FINDINGS (0 blocker / 1 major / 3 minor / 3 notes)
FINAL_VERDICT: ACCEPTED — all findings remediated in-milestone; every
  gate independently re-run afterward.
METHOD: independent source review, falsification attempts, fresh gate
  runs on the physical Pixel 4 XL, full backend regression — Sentinel/
  Argus read only AFTER the initial verdict froze.
```

## Physical evidence (Pixel 4 XL, API 33, vi-VN, serial redacted)

Device awake, keyguard non-blocking, authorized debug/test install only.
Two consecutive full-suite runs against the live environment:

| Run | Executed | Pass | Fail | Skip |
| --- | --- | --- | --- | --- |
| 1 | 174 | 174 | 0 | 0 |
| 2 | 174 | 174 | 0 | 0 |

Reported `174/174` independently reproduced twice (result XML: 174/0/0/0
each run). Includes `CreateTransferScreenTest` (form, pickers, submit
lock, need-two-accounts state, ambiguous banner, cross-currency text),
`TransactionsScreenTest` affordance gating, and `AccountDetailScreenTest`
Transfer-action visibility + prefill.

Second-cycle remediation changed no `src/main` and no `androidTest`
source (one JVM test file + backend) — the connected evidence remains
valid against byte-identical shipped code.

## Canonical contract re-derivation

REQ-BE-003 (`POST-V1-BACKEND-REQUIREMENTS.md`) + `BACKEND-CONTRACT.md`
specify: `POST /api/transfers` with `sourceAccountId`,
`destinationAccountId`, `amount`, optional `currency`; 201 →
`{transferRef, outTransaction, inTransaction}`; owner-scoped 404s;
400 on same-account/non-positive/malformed/cross-currency; atomic pair;
append-only ledger; no other transfer endpoints. PRODUCT Q6 prioritizes
transfers as the post-V1 capability. Implementation matches — verified
against source, not the report.

## Falsification results

**Ownership.** Both accounts resolved via `findByIdAndUserId`; foreign
source AND foreign destination each return 404 (not 403 — no existence
leak), proven in `TransferControllerTest` with real cross-user fixtures
in both directions, plus no-write assertions on every rejection path.

**Atomicity.** `@Transactional` service + `Propagation.REQUIRED` join —
single boundary, verified architecturally AND empirically: the rewritten
service test forces `existsById` to throw on leg 2's call (via
`@MockitoSpyBean`) after leg 1's write attempt inside a non-transactional
test → real rollback boundary → zero transaction rows + both balances
unchanged. This replaced the vacuous cross-currency rollback test that
the new pre-write guard had silently invalidated (EXT-M12-MAJOR-1).

**Conservation / authority.** No `Money` arithmetic in the Android
transfer path; the only client-side math is `BigDecimal` parse for
input validation. Balances move only inside the server's transaction;
the client decodes the server's own leg rows and invalidates both
caches so the next read is a server read.

**Date consistency.** `occurredOn` is now one `LocalDate.now()`
snapshot passed to both legs — a midnight-adjacent transfer cannot
split the pair's dates (EXT-M12-MINOR-1, remediated in `fd2ca1b`).

**Ambiguous writes.** `NetworkUnavailable` and
`SessionEndedException`-after-response both map to `FormError.Ambiguous`
— "couldn't confirm whether this transfer was recorded — check your
transactions before trying again". No retry on the non-idempotent POST;
submit lock prevents double-taps; draft preserved. Verified at VM level
and on-device.

**Scope.** Backend diff adds exactly one endpoint + two DTOs + the
pre-write guard — no GET/PUT/PATCH/DELETE on transfers, no idempotency
key, no FX call, no Flyway change. Android adds one Retrofit method,
one route, one feature package, two affordances. No local paired rows,
no offline queue, no `transferRef` reuse as a list filter — grep-verified.

## Boundary validation matrix (backend, all green)

| Input | Result |
| --- | --- |
| same source/destination | 400 |
| amount `0.00` | 400 |
| amount `-5.00` | 400 (added second cycle) |
| amount `10.005` (>2dp) | 400 |
| cross-currency pair | 400, pre-write |
| request currency ≠ account currency | 400 |
| omitted currency | defaults to source's |
| foreign/unknown account | 404 |
| unauthenticated | 401 |

## Test-integrity audit

Every M12 test is behavioral — no tautologies: contract test asserts the
literal path + byte-exact body + bearer propagation through real
interceptors; repository tests assert dual invalidation and
never-reaches-wire on the same-account guard; the forced-rollback test
fails *after* leg 1's write attempt (call #2 on the spy) so rollback —
not rejection — is what's proven; VM tests bind assertions to recorded
fake call args. Fakes record inputs (`FakeTransfersService` call log,
`FakeTransactionRepository.transfer` tracking).

## Findings — all frozen, then remediated

```
EXT-M12-MAJOR-1: rollsBackDebitWhenCreditLegFails vacuous after the
  pre-write currency guard (failed before leg 1, proved nothing about
  rollback). RESOLUTION=FIXED — spy-forced leg-2 failure, real tx
  boundary, zero-rows + unchanged-balances asserts.
EXT-M12-MINOR-1: legs evaluated LocalDate.now() twice — midnight
  split possible. RESOLUTION=FIXED — single occurredOn snapshot.
EXT-M12-MINOR-2: negative amount untested at the boundary.
  RESOLUTION=FIXED — -5.00 → 400 slice case.
EXT-M12-MINOR-3: hasTransferPeer unproven at VM level.
  RESOLUTION=FIXED — 4 VM tests (true/false/failure-hidden/
  expiry-resume).
EXT-M12-NOTE-1: different-user reauth preserves inert draft text —
  inherited M8 convention; foreign IDs reconcile out on reload.
  RESOLUTION=ACCEPTED.
EXT-M12-NOTE-2: repo require(legs-differ) crashes on programmer error.
  RESOLUTION=ACCEPTED — unreachable via UI; server validates too.
EXT-M12-NOTE-3: pickers not disabled during submit — convention parity
  with txn-create; submit lock covers the window. RESOLUTION=ACCEPTED.
```

Internal-review comparison: Sentinel's 3 remediated minors (stale
destination, cross-currency error clarity, affordance gating) and
Argus's matching pass were independently re-derived and verified
present in code. The external pass's 1 major + 3 minor were
external-only — Argus had cited the vacuous test as proof, which this
audit falsified. No internal false positives; no unresolved external
findings.

## Gate evidence (all independently re-run post-remediation)

- Backend: `mvnw test` **311 tests / 1 failure** = the documented
  `RecurringSchedulingJobTest` timing flake (expected 1, was 2 —
  identical signature, pre-existing test-design defect reproduced at
  the M11 baseline in a detached worktree; zero transfer/scheduling
  code touched by M12).
- Android JVM: `testDebugUnitTest --rerun-tasks` **533/533**, 0
  failures — fresh execution, not UP-TO-DATE cache.
- Connected: 174/174 ×2 on the physical Pixel 4 XL.
- Static/release (main sources unchanged since these gates):
  detekt 0 smells; lintDebug clean; assembleDebug green;
  assembleRelease (minified) green — unsigned APK SHA-256
  `334d776514943614413f595b15494b4a80a6a21308b2865e4a79e1654c0e2d5a`;
  33 serializers retained incl. `CreateTransferRequestDto$$serializer`,
  `TransferResponseDto$$serializer`, `TransfersService`; merged
  manifest INTERNET-only, `usesCleartextTraffic=false`.

## Final verdict

```
M12_EXTERNAL_AUDIT_FINAL=ACCEPTED
BLOCKER=0 MAJOR=0 MINOR=0 NOTE=3 (all majors/minors remediated in-milestone)
M12_READY_FOR_EXTERNAL_AUDIT=YES
```

The chain holds end to end: user picks source/destination/amount →
client validates exactly (positive, ≤2dp, ≤17 int digits, same-currency
named rejection) → one POST with bearer → server owner-checks both
accounts, rejects invalid pairs pre-write, writes an atomic
shared-`transferRef` pair on one `occurredOn` → returns both legs →
client decodes the server's rows, invalidates both caches, emits the
mutation → every surface re-reads server truth. No fabrication, no
local ledger, no retry of ambiguous writes, no scope leak, no
regression beyond the documented pre-existing flake.
