# M13 ARGUS REVIEW — Independent QA / Adversarial Pass

```
ROLE: ARGUS (Independent QA Reviewer)
SCOPE: backend f1ac79a diff + android 3e8551a..3686788 diff,
       reviewed line-by-line against the M13 brief's security invariants.
METHOD: defect hunting, not restatement. Each item independently
        reasoned against source, then either reproduced or disproven.
VERDICT: PASS — 0 Blocker, 0 Major, 2 Minor, 3 Note.
         One latent defect (credential toString leak) found during review
         was FIXED IN-PLACE before this report (see Finding A-1).
```

## A. Findings

### A-1 (FIXED) — `Session.SignedIn`/`StoredSession` `toString()` printed credentials
The Kotlin data-class `toString()` emitted `token=...` since M1 and, after
M13, `refreshToken=...` — a 30-day credential. No current call site logs
the object (verified by grep — only OkHttp HEADERS logging exists, which
redacts Authorization), but a single future `Timber.d(session)` or crash
breadcrumb would leak it. **Remediated in this milestone**: both classes
override `toString()` with `***` redaction; regression test
`session and stored-session toString never leak credentials` pins it.

### A-2 — MINUTE — orphaned refresh credential when a revoked-but-live bearer hits logout
`POST /api/auth/logout` is `permitAll`, but the resource-server filter
still rejects a *present-but-invalid* JWT before the controller sees the
body. Scenario: token locally valid, server-blacklisted (e.g. a second
device logged out the same credential) → 401 → the refresh token in the
body is never processed → it orphans server-side until the 30-day family
cap. Bounded: hashed storage, single-user app, absolute TTL, and the
client still signs out locally. **Accepted, documented.** (The common
cases are covered: expired-token clients send no bearer — the body alone
authorizes revocation — and valid-token clients revoke jti + refresh
atomically.)

### A-3 — MINUTE — `RefreshTokenService` map growth
Refresh records are purged only on `rotate()` and on expiry-at-read; a
login burst without refresh leaves ACTIVE entries until restart. Same
documented posture as `TokenBlacklist` (process-local, single instance);
bounded by real usage, restart clears. Noted for the distributed future.

### A-4 — NOTE — pre-send refresh blocks the OkHttp dispatcher thread
`AuthInterceptor` calls `refreshBlocking` under a `ReentrantLock`; the
HTTP call itself can hold the lock ~15–45s worst case, stalling queued
intercepted calls. Correct by design (single-flight requires
serialization); verified acceptable at single-user request rates.

### A-5 — NOTE — same-identity re-login does not bump generation
A refresh in flight across a *same-user* re-login can still apply
(generation only bumps on identity change/sign-out). Harmless: identity
is identical and the freshest credentials win; different-user re-login
bumps via `emitInvalidation` — verified in tests.

### A-6 — NOTE — `Renewed` response trusted only to the captured generation
`applyRenewedSession` cannot verify the refreshed *user* (the response
carries no identity claim) — but the generation check plus server-side
family binding makes a cross-user apply impossible: the credential is
scoped to the user who minted it, and any intervening login/logout moves
the generation.

## B. Contract fidelity (backend ↔ client, verified against source)

| Wire fact | Backend | Android | ✓ |
|---|---|---|---|
| `POST /api/auth/refresh` body | `RefreshRequest{refreshToken}` `@NotBlank` | `RefreshRequestDto{refreshToken}` | ✓ |
| refresh response | `AuthResponse{accessToken, refreshToken, expiresAt}` | `LoginResponseDto` (refreshToken nullable for legacy decode) | ✓ |
| refresh rejection | `AuthenticationException` → 401 `Invalid credentials.` | `Outcome.Rejected` on 401/400 → session Expired | ✓ |
| logout | `permitAll`, optional `LogoutRequest`, always 204 | `@PublicEndpoint`, `LogoutRequestDto{refreshToken?}`, 401 ignored | ✓ |
| rotation | old token retired, successor minted, same family expiry | store save + in-memory swap are one atomic `applyRenewedSession` | ✓ |
| reuse detection | rotated-token replay → family revoked | client holds only the latest; a 401 expiry surfaces re-auth | ✓ |

## C. Invariant audit — the "never duplicate a financial write" spine

Traced every 401 path:

1. **Pre-send** (locally-expired access): refresh BEFORE transmit → the
   mutation goes out once with a live token. Proven: `mutation transmitted
   once` wire test (`requestCount == 2` including refresh).
2. **Post-401 mutation**: `TokenAuthenticator` renews credentials but
   returns `null` — OkHttp surfaces the original response; the mutation is
   never re-issued. Proven: `a transmitted mutation that 401s is NEVER
   replayed`.
3. **Post-401 read**: GET/HEAD retried once, `X-Spendwise-Refresh-Retry`
   marker prevents loops.
4. **Retry storm**: `retryOnConnectionFailure(false)` unchanged; the bare
   refresh client matches.
5. **Ambiguous transport failure**: `IOException` → `Transient` → session
   intact, NO silent replay anywhere (there is no replay path at all for
   mutations).

## D. Boundary/scope audit

- Backend diff limited to `auth/` + properties: `AuthController`,
  `AuthService`, `AuthResponse`, `SecurityConfig`, `IssuedSession`,
  `RefreshRequest`, `LogoutRequest`, `RefreshTokenService`, properties ×2,
  tests ×3. **No schema migration, no financial-endpoint change** —
  authorization table gained two permitAll rows (`/refresh`, `/logout`)
  exactly as specified.
- Human-owned backend files untouched: `README.md`,
  `HUONG-DAN-SU-DUNG.md`, `docs/SPENDWISE-REQ.md` remain uncommitted
  modifications.
- Reference project untouched.
- No new Android dependencies, no new permissions, no cleartext.

## E. Residual risks accepted

- Process-local refresh store = forced re-login on every backend restart
  (documented design choice, fail-closed, no migration).
- 15-minute access JWT window between refreshes unchanged.
- `TokenBlacklist`/`RefreshTokenService` unbounded growth — pre-existing
  documented pattern, single-instance scope.
