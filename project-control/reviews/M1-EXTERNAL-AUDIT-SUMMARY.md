# M1 External Audit Summary

Independent adversarial audit of the M1 commit delta, performed in a fresh
Devin session after the internal M1 report. Audit was read-only; no source,
tests, docs, or history were modified during it.

## Audit identity

```
AUDITOR=DEVIN_NEW_INDEPENDENT_SESSION
AUDIT_MODE=READ_ONLY_ADVERSARIAL
AUDITED_HEAD=f6991af9355c6e1ce20a86860d96af1305ecb588
AUDITED_RANGE=9a5fb90..f6991af9
DELTA=54 files (+3521 / −175), 34 new files
WORKTREE_CLEAN_AT_START=YES
```

## Verdict

```
VERDICT=ACCEPTED
BLOCKER=0
MAJOR=0
MINOR=1
NOTE=4
M1_CORE_CORRECTNESS=PASS
M1_SECURITY=PASS
M1_CONTRACT_FIDELITY=PASS
M1_TEST_EVIDENCE=PASS
M1_READY_FOR_HUMAN_APPROVAL=YES
M1_HUMAN_APPROVED=YES
```

Human authorization recorded: execution of the M2 delivery task constitutes
explicit Human approval of M1. `M1_STATUS=COMPLETE`,
`M2_AUTHORIZED_TO_START=YES`.

## Verified independently by the audit

- Backend auth contract re-checked against backend source at `8194daa`
  (register 201 `{id,username}`; login 200 `{accessToken,expiresAt}`;
  logout 204 + `jti` blacklist; `fields{}` on 400; `Instant` → ISO-8601).
- Public-vs-authenticated 401 classification uses the Retrofit `Invocation`
  tag via `@PublicEndpoint` — no URL sniffing.
- Expired/invalid credentials cannot resurrect: `clearToken()` on expiry and
  on local-expiry restore.
- Cross-user isolation: `userId` (JWT `sub`) primary, `username` fallback;
  different-identity reauth clears the protected stack and emits cache
  invalidation.
- AES/GCM token encryption correct (random 12-byte IV per encrypt, 128-bit
  tag, Keystore key); ciphertext-only on disk; backup rules cover DataStore.
- No password/token/body logging; Authorization redacted.
- All quality gates rerun independently and passed; 88 unit tests observed
  (internal reports claimed 87 — understating, not overclaiming).
- Boundaries preserved: backend `8194daa`, reference `5f9622c`, prototype
  untouched, private fixtures never accessed.

## Findings

### EXT-MINOR-1 — Unguarded IOException on cold-start session restore

`SessionManager.restore()` → `tokenStore.load()` →
`DataStore.data.first()` could propagate a raw `IOException` (storage-level
failure distinct from protobuf `CorruptionException`, which was already
handled) into the `appScope.launch` in `SpendwiseApplication`, producing a
cold-start crash instead of failing closed to `SignedOut`. Low-probability,
fail-closed in nature — rated MINOR.

Required remediation (per audit): catch the load/restore failure at the
proper boundary and transition safely to `SignedOut`, consistent with
existing decrypt/corruption fail-closed behavior. Remediated in the M1
hardening commit that follows this closeout.

### EXT-NOTE-1 — Internal reports understate test count

Internal Sentinel/Argus docs claim 87 unit tests; the audit's independent
XML count observed 88. Understates coverage; no impact.

### EXT-NOTE-2 — Compile-only instrumentation evidence

24 androidTest cases drive real NavHost behavior but were compile-verified
only — no connected device existed. First device run may surface assertion
fixes. Accepted M1 evidence-floor limitation; non-blocking.

### EXT-NOTE-3 — Defensive-path test gaps

No tests for concurrent authenticated-401 idempotency, malformed
`expiresAt`, or blank `accessToken` in a 200 response. All paths verified
fail-safe by source inspection. Non-blocking.

### EXT-NOTE-4 — Register secondary "Sign in" link pops to Welcome

`RegisterDestination`'s "Already have an account? Sign in" uses
`popBackStack()`; on the direct Welcome→Register path it returns to
Welcome, adding one tap. Marginal UX; non-blocking.

## Argus minors re-evaluation (performed by the audit)

All four Argus minors (MIN-1 expired-screen flicker, MIN-2 in-memory
`Expired`, MIN-3 spurious cold-start invalidation, MIN-4 logout-401
transient state) were independently confirmed and accepted as deferred —
no elevation of severity.

## Next action

HUMAN M1 APPROVAL — granted via the M2 delivery authorization. M1 closed;
M2 opened.
