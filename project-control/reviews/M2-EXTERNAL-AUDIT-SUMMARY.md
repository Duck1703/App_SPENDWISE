# M2 External Audit Summary — Accounts + Design-System Core

Independent adversarial read-only audit of canonical M2
(`f6991af..a43ba2a` and `954d2b8..a43ba2a`), performed against
`AGENTS.md`/`WORKFLOW-CONTRACT.md`/canonical docs, backend source at
`8194daa`, and the actual diffs — not the internal Sentinel/Argus
reports. This file records the verdict the Human reviewed when issuing
the conditional overnight authorization.

## Verdict

```
VERDICT=FINDINGS
BLOCKER=0
MAJOR=1

M2_CORE_CORRECTNESS=PASS
M2_FINANCIAL_CORRECTNESS=PASS
M2_SECURITY_PRIVACY=FAIL
M2_CONTRACT_FIDELITY=PASS
M2_TEST_EVIDENCE=PASS
M2_READY_FOR_HUMAN_APPROVAL=NO
```

## Mandatory finding

### EXT-MAJOR-1 — Session-scoped cache isolation is timing-dependent, not generation-stamped

- `RemoteAccountRepository.list()` writes `cache = accounts`
  unconditionally after fetch; no session-identity/generation check.
- The `cacheInvalidations` collector runs on `@ApplicationScope`
  (`Dispatchers.Default`); `emitInvalidation` is a buffered `tryEmit` —
  fire-and-forget with no happens-before against subsequent cache reads
  on the main thread.
- Reachable poison path: user A's `list()` in-flight (30s read timeout)
  → expiry/sign-out → different-identity sign-in emits the clear → A's
  stale response lands after the clear but before A's ViewModel is
  disposed → `cache = A's accounts` persists into B's session. A second
  path exists if the Default-dispatched clear loses a scheduling race
  against B's fresh `list()` cache-read.
- Expected: "one user's accounts can never reach another session"
  (§13) holds unconditionally. Actual: held probabilistically.
- Impact: narrow-race display of user A's account names/types/balances
  to user B on a shared device (in-memory residue only; server-side
  isolation unaffected).

## External minors

- **EXT-MIN-1** — Manrope applied to only 7 of 15 M3 typography slots;
  `displaySmall` (hero amount), `bodySmall`, `titleSmall`, `labelSmall`
  fall back to the platform typeface. P3A fidelity gap.
- **EXT-MIN-2** — `MoneyFormat` localizes grouping but hardcodes `.`
  as the decimal separator → ambiguous output in `,`-decimal locales
  (vi-VN, de-DE). Display-only.
- **EXT-MIN-3** — `SwSkeletonRows` has no loading semantics; TalkBack
  is silent during loads.
- **EXT-MIN-4** — `SessionManager.onSignedIn` propagates
  `tokenStore.save` `IOException` unhandled (fails closed, but surfaces
  as an indistinguishable generic failure).
- **EXT-MIN-5** — `TouchTargetMin = 44.dp` meets the P3A 44–56 floor
  but is below Android's 48dp recommended minimum.

## Carried internal findings — reassessed

- **MIN-1** (refresh failure replaces Content with full-screen Error) —
  still valid, deferrable; fixed in remediation.
- **MIN-2** (non-atomic cache read-modify-write) — still valid;
  resolved alongside EXT-MAJOR-1 in the same cache abstraction.
- **MIN-3** — **RESOLVED_AS_NON_ISSUE**: Argus's premise was wrong in
  both directions — the server validates currency via the same
  `java.util.Currency.getInstance`, so every JDK-available code is
  server-acceptable, and `CurrencyCode` keeps unknown syntactically
  valid codes representable on the read path. No contract violation.
- **MIN-4** (no create idempotency) — confirmed backend limitation:
  ambiguous-failure retry can duplicate an account, and no delete
  endpoint exists to undo it. Deferred to backend support; client-side
  honest-copy mitigation applied in remediation.

## Evidence notes

- All three endpoints verified against backend source: `POST/GET
  /api/accounts`, `GET /{id}`; `fields{}` map; `findByIdAndUserId`
  ownership; `balanceAmount = openingBalance.amount()` server-side;
  `NUMERIC(19,2)`.
- 152 unit tests counted from XML reports (0 failures/skips); 44
  androidTest cases compile-verified only — no device.
- Gates re-run: compileDebugKotlin, testDebugUnitTest,
  compileDebugAndroidTestKotlin, lintDebug, detekt, assembleDebug,
  assembleRelease — all PASS.
- Boundaries held: backend/reference/prototype read-only; private
  fixtures untouched; no source edited by the auditor.

## Post-remediation verification — `db09826`

Remediation landed in `db09826` ("M2 external-audit remediation").
Post-fix falsification pass and gates:

- `SessionCache` (`core/session/SessionCache.kt`) stamps every stored
  value with the session generation captured at write time, under one
  mutex. `read`/`write`/`update`/`invalidate` all re-validate
  `sessionManager.sessionGeneration` inside the mutex — a write or
  merge captured in generation N can never publish or be read in
  generation N+1 regardless of when the `cacheInvalidations`
  collector is scheduled. Stale writes throw `SessionEndedException`
  (a `CancellationException`) instead of publishing.
- `SessionManager` advances an `AtomicLong` synchronously inside
  `emitInvalidation`, before the `tryEmit` — every invalidating
  transition (sign-out, expiry, different-identity sign-in, restore-
  to-signed-out) is a generation boundary. Concurrent emits cannot
  collapse.
- `tokenStore.save`/`clear` IO failures fail closed: session is not
  established; `AuthRepository` maps `IOException` to
  `AppError.Unknown` (EXT-MIN-4).
- Accounts list keeps rendered content on refresh failure and
  surfaces an inline error banner + pull-to-refresh (MIN-1);
  initial-load failure still maps to the full-screen error state.
- Create form states the ambiguity honestly on failure — a retry may
  duplicate the account (MIN-4; idempotency remains a backend gap).
- Manrope now covers all 15 M3 typography slots (EXT-MIN-1);
  `MoneyFormat` uses `DecimalFormat` with locale decimal+grouping on
  an exact `BigDecimal` path (EXT-MIN-2); skeleton rows carry merged
  loading semantics (EXT-MIN-3); `TouchTargetMin` raised to 48.dp and
  applied to back buttons + segmented control (EXT-MIN-5).
- Deterministic concurrency suite
  (`data/AccountRepositoryConcurrencyTest.kt`): stale cross-user
  response, delayed invalidation collector (parked scheduler), RMW
  races, in-flight logout, repeated transitions — all proven unable
  to publish stale data.
- Unit tests: **171/171, 0 failures, 0 skipped** (+19 vs audited
  baseline). Gates: assembleDebug, testDebugUnitTest, lintDebug,
  detekt (0 smells), compileDebugAndroidTestKotlin, assembleRelease —
  all PASS. `adb devices`: none attached — androidTest remains
  compile-verified only.
- `M2_READY_FOR_HUMAN_APPROVAL`: findings resolved; per contract the
  Human holds the approval gate. `M2_HUMAN_APPROVED` remains `NO`
  until the Human records it.
