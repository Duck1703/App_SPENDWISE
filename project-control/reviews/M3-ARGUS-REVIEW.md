# M3 ARGUS Review — Transactions

Reviewer role: **ARGUS** (independent QA reviewer), per
`agent-system/agents/argus.md`. Independent pass over the M3 diff —
contract fidelity, financial-correctness invariants, session/cache
boundaries, navigation semantics, test meaningfulness, scope.

## Review focus

1. Backend transaction/category contract vs implementation at `8194daa`
   (source, not reports): endpoint set, DTO shapes, validation limits,
   error surface.
2. INV-012 / financial invariants: positive amounts, type-carried
   direction, no client ledger, BigDecimal-only, no retry of the
   non-idempotent POST.
3. Session integration: generation-stamped caches, expiry overlay over
   pushed transaction screens, same-user resume, no cross-user leak.
4. Whether tests exercise the claimed behaviors (not coverage theater).
5. M3 scope containment — read-only ledger per the Human P3A override;
   no offset-entry UI, no edit/delete, no transfer UI, no M4 dashboard.

## Findings

### Blockers — none

### Majors — none remaining

- **RECONCILED-MAJ-1** `TransactionsViewModel` set its "load settled"
  marker after `list()` but before the trailing account-name lookup, so
  a session expiry landing on the name leg (or on any refresh after a
  first success) classified itself "not mid-load" — a same-user resume
  then never re-ran it and the surface could sit on skeleton/stale rows
  forever. The same `expiredMidLoad = !loadSettled` shape existed in
  `TransactionDetailViewModel` and `AccountDetailViewModel`. Reconciled:
  `SessionExpired` caught inside a load now always marks the load for
  resume-retry (a load was provably in flight); the vestigial
  `loadSettled` flags were removed. New regression test
  `SessionExpired on the account-name leg still reloads on resume`
  proves the stuck-state path is gone. Verified green.

### Minors — none remaining

- **RECONCILED-MIN-1** Filter chips read the applied-filters snapshot on
  `Content`, so a pending filter change briefly displayed the stale
  selection (and reset to "All" while loading out of Empty). Chips now
  bind to a live `filters` flow reflecting the pending selection.
- **RECONCILED-MIN-2** The create form's hint field accepted 256 chars
  while the backend caps `@Size(255)` — a keystroke too many was a
  guaranteed 400. Now clamped at 255.
- **RECONCILED-MIN-3** "%1$d transactions" pluralized (1 transaction /
  N transactions), matching the M2 hero convention.

### Notes

- **NOTE-1** `TransactionDetailViewModel` deliberately `runCatching`s
  the account-name lookup — a SessionExpired there is swallowed and the
  detail still publishes with a degraded label. Correct: the overlay
  still pushes (SessionManager already expired), the transaction row
  itself was fetched under the live generation, and the screen is being
  torn down anyway. Verified by trace.
- **NOTE-2** Type segmented control and account dropdown aren't
  disabled mid-submit; the POST captures its snapshot at submit, so a
  late flip can't alter the in-flight request — and the ambiguous-failure
  path covers the response-died-in-transit case. Accepted.
- **NOTE-3** Provenance filtering evaluates `sourceRef` prefixes over
  the already-fetched list — no server param exists, and the UI labels
  it "Source" honestly rather than implying a server filter.
- **NOTE-4** The transaction cache is last-list-write: a filtered list
  replaces the previous cache entry, and detail lookup falls back to
  one unfiltered fetch on a miss. Bounded, correct, and honest by
  design — detail can never be cheaper than one list call.
- **NOTE-5** Duplicate-create ambiguity after a lost response is an
  inherited backend limitation (no idempotency key) — the UI surfaces
  "couldn't confirm — check your transactions" and never auto-retries.
  Report-only backend gap; matches the M2 MIN-4 treatment.
- **NOTE-6** Compose instrumentation is compile-verified only —
  `adb devices` is empty on this machine; no device run exists to
  claim. Same disclosure as M1/M2.

## Verification performed

- Re-read `TransactionController`, `CategoryController`,
  `TransactionResponse`, `CreateTransactionRequest`, `CategoryResponse`,
  `TransactionService`, and `sourceRef` provenance at backend `8194daa`
  — endpoint set, param names, DTO fields, validation annotations.
- Confirmed **no** `GET /{id}`, edit, delete, or transfer-create
  endpoints exist on the backend and none are declared client-side; the
  UI offers only INCOME/EXPENSE.
- Grepped financial paths for Float/Double — none; `BigDecimal` on the
  wire and in the model; client caps mirror `@Digits(17,2)` and
  `@Size(255)` exactly; comma/dot input normalized to `.`.
- Confirmed "All" never reaches the wire — repository tests capture
  call args and assert nulls; Retrofit omits null `@Query` params.
- Traced session boundaries: `SessionCache` stamps every write with the
  producing generation; `list`/`findById`/`create` re-validate before
  publishing; a dead-generation publish throws `SessionEndedException`
  (a `CancellationException`) which the list pipeline deliberately
  swallows without killing the collector. `mutations` never emits for a
  dead generation.
- Traced nav: create/detail push on the outer graph over the preserved
  shell; the expiry overlay fires over them; same-user resume pops
  overlay+login and re-enters the destination (`onVisible` re-runs the
  aborted load; the create draft survives). Post-create landing sets a
  single-shot savedStateHandle flag to select the Transactions tab only
  when the shell is beneath.
- Traced account-detail history: `?accountId=` scoped list, non-fatal
  section with inline retry, `mutations`-driven reload — account header
  never blocked.
- Re-ran all gates after reconciliation: `assembleDebug`,
  `testDebugUnitTest` (246/246), `lintDebug` (0 errors),
  `compileDebugAndroidTestKotlin`, `assembleRelease`, detekt
  (0 findings). `adb devices` empty — instrumentation compile-only.
- Confirmed boundaries: backend `8194daa` untouched; reference and
  prototype repos untouched; no M4 surface (no dashboard aggregates,
  no date pickers, no budget/recurring/rules UI, no edit/delete UI).

## Test evidence assessment

SENTINEL's 246-test suite is credible: contract tests capture actual
request params and decode real response shapes; concurrency tests assert
the generation stamp under racing sessions; VM tests drive fake
repositories through expiry/resume and stale-response paths. The
stuck-skeleton regression was caught *inside* the pipeline and is now
locked by a dedicated test. Compose coverage is stateless-composable
level — honest but not instrumentation-verified (no device).

## Regression risk

M1/M2 surfaces untouched except `AccountDetailViewModel`/`Screen`/
`Destinations` (history section + navigation args) and the shared shell
(Transactions tab now real). The full 246-test run re-verifies M1/M2 in
the same pass; account-detail tests were updated for the new section.
Low residual risk.

## Verdict

```
ARGUS_VERDICT=PASS
BLOCKER=0
MAJOR=0 (1 found and reconciled pre-review — RECONCILED-MAJ-1)
MINOR=0 (3 found and reconciled pre-review)
NOTE=6
M3_READY_FOR_EXTERNAL_AUDIT=YES
```

## Recommended next action

ATLAS to record the milestone state as `READY_FOR_EXTERNAL_AUDIT`,
checkpoint the work cleanly, and stop — the external audit and the
human gate decide completion. M4 stays closed until then.
