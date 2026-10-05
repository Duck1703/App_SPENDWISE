# M3 External Audit Summary — Transactions

Independent adversarial read-only audit of canonical M3
(`c701715..41e8929`), performed against `AGENTS.md`/
`WORKFLOW-CONTRACT.md`/canonical docs, backend source at `8194daa`,
and the actual diffs — not the internal Sentinel/Argus reports.

## Verdict

```
VERDICT=FINDINGS
BLOCKER=0
MAJOR=1
MINOR=1
NOTE=5

M3_CORE_CORRECTNESS=PASS
M3_FINANCIAL_CORRECTNESS=PASS
M3_SESSION_PRIVACY=PASS (no cross-identity leak found — see EXT-MAJOR-1)
M3_RESUME_CORRECTNESS=FAIL (EXT-MAJOR-1)
M3_CONTRACT_FIDELITY=PASS
M3_TEST_EVIDENCE=PASS
M3_READY_FOR_HUMAN_APPROVAL=NO (until remediation + verification)
```

## Mandatory finding

### EXT-MAJOR-1 — Dead-generation (`SessionEndedException`) paths leave stuck pending state and skip resume-retry — one root cause, nine sites

The M2 remediation (`SessionCache`) made every session-scoped read
throw `SessionEndedException` when a response lands after the
generation boundary — correctly. But the ViewModel layer only treats
`AppErrorException(SessionExpired)` (the 401 path) as a
resume-retry signal. The dead-generation path — reached when the
session ends *between* a request's dispatch and its response (a second
401 fired expiry first, sign-out raced the call, or a different-user
sign-in bumped the generation) — either propagates uncaught or is
swallowed without setting any `expired*`/`optionsExpired`/
`historyExpired` flag and without clearing the pending state the load
armed.

On a **same-user resume** — the designed recovery path — `onVisible()`
sees all flags false and does nothing. The surface then sits on a
skeleton, a stuck `isRefreshing` spinner, an empty filter bar, or a
permanently-locked submit button forever. Cross-identity sessions are
safe (the stack is cleared and the VM destroyed), so this is a
resume-correctness defect, not a privacy leak — but it is exactly the
flow M3 was mandated to harden, and it deterministically breaks
re-authentication continuity on protected surfaces.

Sites (all reachable; all reproduce on same-user resume):

| # | File / site | Failure |
| --- | --- | --- |
| 1 | `TransactionsViewModel` list collector | `catch (SessionEndedException) → null` drops the result; `markPending()`'s `Loading`/`isRefreshing` persists; `expiredMidLoad` never set → skeleton/spinner forever |
| 2 | `TransactionsViewModel.loadOptions` | catches only `AppErrorException`; dead-gen propagates → `optionsExpired` unset → filter chips stay empty after resume |
| 3 | `TransactionDetailViewModel.load` | swallows dead-gen quietly; `expiredMidLoad` unset → `Loading`/spinner stuck |
| 4 | `AccountDetailViewModel.load` | same swallow → same stuck state |
| 5 | `AccountDetailViewModel.fetchHistory`/`retryHistory` | catches only `AppErrorException`; dead-gen propagates → `history` stuck `Loading`, `historyExpired` unset |
| 6 | `CreateTransactionViewModel.loadOptions` | catches only `AppErrorException` → `optionsLoading` stuck true → skeleton forever |
| 7 | `CreateTransactionViewModel.submit` | dead-gen propagates → **`isSubmitting` stays `true` — the preserved draft is permanently locked on same-user resume**. The POST almost certainly landed (the repository throws after the response and after invalidations), so silent handling would also hide a completed write |
| 8 | `AccountsViewModel.refresh` (M2 file) | no dead-gen catch → `Loading`/`isRefreshing` stuck; `expiredMidLoad = !loadSettled` also still misclassifies refresh-expiry — the exact class RECONCILED-MAJ-1 fixed in the M3 VMs but left here |
| 9 | `CreateAccountViewModel.submit` (M2 file) | dead-gen propagates → **`isSubmitting` stuck — draft permanently locked**; the account write may have landed |

Root cause: the session-layer invariant "a session boundary during an
in-flight operation must release pending state and arm resume-retry"
is enforced for `AppErrorException(SessionExpired)` but not for
`SessionEndedException`. The two signals mean different things (401 =
rejected request; dead-gen = response arrived too late) but require
the *same* surface treatment — plus, for submit paths, honest
ambiguity marking since the write may have committed server-side.

## External minor

- **EXT-MIN-1** — `OkHttpClient` never disables
  `retryOnConnectionFailure` (OkHttp default `true`). OkHttp's
  transparent transport retry can re-transmit a repeatable
  `RequestBody` POST when the connection fails recoverably — including
  after the body was sent and the server may have committed. This sits
  *below* every application-level no-retry guarantee: the create-form
  comment already claims "no automatic retry at any layer", which is
  false at the transport layer until this is disabled. Pre-existing
  plumbing exposure (M0→M2's account create had it too); M3 made it
  load-bearing for money.

## Notes

- **NOTE-1** `TransactionDetailViewModel`'s `runCatching` on the
  account-name lookup also swallows `SessionEndedException` — harmless:
  the label degrades and the overlay owns the session transition.
  Consistent with internal NOTE-1.
- **NOTE-2** A session boundary between `list()` returning and the
  trailing account-name lookup in `TransactionsViewModel.fetch` is
  covered once site-1 lands — the dead-gen propagates out of `fetch`
  into the collector's catch.
- **NOTE-3** The submit-lock fixes mark `formError` ambiguous (txn:
  `Ambiguous`; accounts: `Network`, which already renders
  "couldn't confirm — check your accounts") because a dead-generation
  create means the response succeeded — the write almost certainly
  committed. This matches the M2 ambiguous-copy convention exactly.
- **NOTE-4** Internal reviews were sound on contract, race-suppression,
  and financial invariants; this audit's delta is confined to the
  dead-generation/resume seam and transport-retry depth.
- **NOTE-5** Instrumentation remains compile-verified; `adb devices`
  empty on this machine.

## Evidence notes

- Backend re-verified at `8194daa` (source, not reports): only
  `GET /api/transactions` (`accountId`/`type`/`category`), `POST
  /api/transactions` (`{accountId,type,amount,currency,category?,
  description?}`), `GET /api/categories`; no `GET /{id}`, no
  edit/delete, no ORDER BY, no date, no pagination; `amount
  @DecimalMin(exclusive)/@Digits(17,2)`; `description @Size(255)` —
  every claim in the milestone file holds against source.
- 246 unit tests counted from XML reports — 0 failures, 0 skips.
  androidTest compile-verified only (no device).
- Diff inventory: M3-scoped, no M4 surface, no backend/reference/
  prototype writes; backend HEAD `8194daa` unchanged.
- Static sweep: no Float/Double in financial paths, no println/Log,
  debug-only Timber + HEADERS-level redacted OkHttp logging, no
  secrets.
- The internal `RECONCILED-MAJ-1` fix (401-path resume-retry) is
  verified correct — this audit extends the same invariant to the
  parallel dead-generation path it missed.

## Stop condition status

Remediation is in scope (the overnight authorization directs
"BLOCKER/MAJOR reconciliation"). All nine sites share one fix shape:
catch the dead-generation signal where the pending state lives, arm
the matching resume-retry flag, release pending UI — plus ambiguous
marking on the two submit paths and one OkHttp builder line for
EXT-MIN-1.

## Post-remediation verification

Remediation landed in this working tree (single commit, code + tests):

- **Uniform invariant**: every `SessionEndedException` path now mirrors
  the 401 path — arm the matching resume-retry flag
  (`expiredMidLoad`/`optionsExpired`/`historyExpired`) and release the
  pending state (`isRefreshing`/`optionsLoading`/skeleton).
  `onVisible()` on a same-user resume re-runs exactly the aborted
  piece; a different-user boundary destroys the VM anyway, so the
  flags are inert there.
- **Submit locks**: `CreateTransactionViewModel.submit` and
  `CreateAccountViewModel.submit` release `isSubmitting` on a dead
  generation and mark the outcome ambiguous
  (`CreateTxnFormError.Ambiguous` / `CreateFormError.Network` — the
  latter already renders "couldn't confirm — check your accounts").
  The draft is preserved; resubmission is user-initiated only — never
  an automatic replay of a non-idempotent POST.
- **`AccountsViewModel` invariant repair**: `expiredMidLoad =
  !loadSettled` removed — a session boundary during ANY in-flight load
  (initial or refresh) now arms resume-retry unconditionally;
  `loadSettled` deleted. This completes the class RECONCILED-MAJ-1
  started inside the pipeline.
- **EXT-MIN-1**: `OkHttpClient.retryOnConnectionFailure(false)` —
  no transparent transport re-send of a repeatable POST body can
  duplicate a financial write below the app layer. Documented in the
  module's retry policy comment.
- **New regression tests (+12)**: dead-generation mid-load /
  mid-refresh / options / history / submit paths across
  `TransactionsViewModelTest` (+3), `TransactionDetailViewModelTest`
  (+1), `AccountDetailViewModelTest` (+2), `AccountsViewModelTest`
  (+2, and one existing test updated to the stronger uniform
  invariant — refresh-expiry now reloads on resume rather than
  trusting stale content), `CreateTransactionViewModelTest` (+2),
  `CreateAccountViewModelTest` (+1), `NetworkModuleTest` (+1 —
  asserts `retryOnConnectionFailure` is disabled).
- **Gates re-run post-remediation**: `assembleDebug` PASS,
  `testDebugUnitTest` **258/258** (0 failures, 0 skips),
  `lintDebug` PASS (0 errors; 20 informational warnings — same class
  as M2), `compileDebugAndroidTestKotlin` PASS, `assembleRelease`
  PASS, detekt **0 findings**. `adb devices`: none attached —
  androidTest remains compile-verified only.
- **Falsification pass**: each new test fails against the pre-fix
  code (stuck `Loading`/`isRefreshing`/`optionsLoading`/
  `isSubmitting`, no resume reload) and passes after; the updated
  accounts test locks the uniform invariant.

```
POST_REMEDIATION_VERDICT=FINDINGS_RESOLVED
EXT_MAJOR_1=RESOLVED (9/9 sites + uniform invariant)
EXT_MIN_1=RESOLVED
M3_JVM_TESTS=258/258
M3_READY_FOR_HUMAN_APPROVAL=YES (pending human closeout)
```
