# M1 ARGUS Review — Authentication & Session

Reviewer role: **ARGUS** (independent QA reviewer), per
`agent-system/agents/argus.md`. Independent pass over the M1 diff —
navigation/session semantics, error taxonomy, security boundaries, test
meaningfulness, scope.

## Review focus

1. Session state machine and 401 semantics vs ARCHITECTURE §13.
2. Resume/different-user navigation correctness on the real NavHost.
3. Credential handling (storage, logging, resurrection paths).
4. Whether tests actually exercise the claimed behaviors.
5. M1 scope containment (no feature creep).

## Findings

### Blockers — none

### Majors — none

### Minors

- **MIN-1** `SessionExpiredScreen` reads `session` at composition time;
  during the resume-pop transition it can briefly render without the
  "Signed in as" line. Cosmetic only — the entry is leaving the stack.
  Accepted.
- **MIN-2** `Session.Expired` is intentionally in-memory only. Process
  death while expired restores to `SignedOut` (the dead credential was
  already dropped) rather than re-showing the expired screen. Acceptable,
  documented degradation — drafts are lost on process death anyway (§13).
- **MIN-3** `cacheInvalidations` emits once on cold-start `SignedOut`
  (fresh install). One spurious signal to future collectors; harmless by
  contract (collectors must be idempotent).
- **MIN-4** A logout-401 transiently flips `SignedIn → Expired → SignedOut`
  in one boundary crossing; `StateFlow` conflation means collectors on main
  only ever see the final state. Benign; documented.

### Notes

- **NOTE-1** Persisted `username`/`userId` hints survive `clearToken()`
  (by design, for resume UX) but are currently only consumed by the
  in-memory `Expired` state — the stored hints are reserved for future
  "welcome back" prefill. Not a leak: they are the user's own identity on
  their own device.
- **NOTE-2** Register has no password-confirmation field — matches the
  P3A minimal-form baseline. Confirm with THE HUMAN if confirmation is
  desired later.
- **NOTE-3** Backend `fields{}` validation messages surface verbatim in
  supporting text (the designed display channel, §15); they are English
  server copy. Client-side copy covers everything else.
- **NOTE-4** `MoreScreenTest.confirmSignOut_firesCallback` locates the
  dialog confirm button by node order — functional but brittle if the
  dialog gains buttons.
- **NOTE-5** `runBlocking` inside `ErrorInterceptor` bridges the sync
  OkHttp chain to the suspending `onAuthenticationExpired()` — bounded
  (a DataStore write on an OkHttp thread), deliberate and documented.

## Verification performed

- Re-verified backend contract against `AuthController.java`,
  `ApiExceptionHandler.java`, `SecurityConfig.java` at `8194daa`.
- Re-ran all gates: `assembleDebug`, `testDebugUnitTest` (87/87),
  `lintDebug` (0 errors), `compileDebugAndroidTestKotlin`,
  `assembleRelease`, detekt (0 findings).
- Traced the full resume cycle: `Expired` push over `MainShellRoute` →
  `LoginRoute(resume)` → same-identity `SignedIn(resumedSession=true)` →
  `popBackStack<SessionExpiredRoute>(inclusive)` → preserved inner tab
  stack. Different-identity path clears to a fresh shell.
- Confirmed dead-credential drop on expiry (`tokenStore.clearToken()`)
  prevents token resurrection on restart.
- Confirmed no secrets in logs; Authorization redaction; HEADERS-only
  debug logging; token ciphertext at rest.
- Confirmed boundary preservation: backend/reference/prototype untouched;
  no private-fixture access.

## Verdict

```
ARGUS_VERDICT=FINDINGS
BLOCKER=0
MAJOR=0
MINOR=4
NOTE=5
UNRESOLVED_BLOCKERS=0
UNRESOLVED_MAJORS=0
RECONCILIATION=All minors accepted with rationale; no blockers or majors
require code changes. M1 is clear for external audit.
```
