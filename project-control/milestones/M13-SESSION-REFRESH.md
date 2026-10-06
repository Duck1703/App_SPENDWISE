# M13 — Session Refresh / Token Renewal

```
STATUS: IN_DELIVERY
M13_DEFINED=YES
M13_STARTED=YES
M13_TECHNICAL_COMPLETE=NO
M13_READY_FOR_EXTERNAL_AUDIT=NO
M13_HUMAN_APPROVED=NO
M14_DEFINED=NO
M14_STARTED=NO
M13_HUMAN_AUTHORIZATION: EXPLICIT — Human authorized "select exactly
  one substantial, coherent M13 capability", its bounded backend
  prerequisite, and the full delivery block ending at
  M13_READY_FOR_EXTERNAL_AUDIT=YES.
BASELINE: Android 3e8551a (post-M12-accepted) · Backend fd2ca1b
CANONICAL_REQUIREMENT: REQ-BE-002 (POST-V1-BACKEND-REQUIREMENTS.md)
```

## Selection rationale

BE-002 is the **last undelivered member of the canonical priority
subset** (PRODUCT.md: "priority subset — BE-001 dates, BE-002 sessions,
BE-003 transfers"). BE-001 shipped in M11, BE-003 in M12. PRODUCT Q2:
"Accept [15-min sessions] + BE-002 high priority → escalate BE-002
pre-implementation"; the risk register ties session churn to user
frustration. The spec's own sequencing caveat — "sequence after value
items" — is now satisfied: every value item (dates, transfers, import
history, export) has shipped. M12's non-idempotent transfer write
makes safe session renewal materially more important.

Scored against alternatives:

| Candidate | Prio | Value | BE ready | A ready | Ambig | Schema | Verdict |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **BE-002 session refresh** | 5 | 4 | 2 | 3 | 1 | 0 | **SELECTED** |
| BE-011 budget edit/delete | 3 | 3 | 3 | 4 | 2 | 0 | next-tier |
| BE-005 txn edit/void | 3 | 4 | 2 | 3 | 3 (edit-vs-void is an open product fork) | 1 | deferred — product decision |
| BE-006 account archive | 3 | 2 | 2 | 4 | 3 (archive semantics undefined) | 1 | deferred |
| BE-010 persisted notes | 2 | 3 | 2 | 4 | 1 | 2 (schema) | deferred |
| BE-007 pagination | 2 | 2 | 2 | 2 | 1 | 0 | trigger unmet (deferred till load pain) |
| BE-008/BE-009/FX/search | — | — | — | — | — | — | too small / product-gated |

## User outcome

A signed-in user whose access token expires continues working without
re-entering credentials: the client renews silently via a rotating
refresh token. Re-authentication is required only when the session is
genuinely dead — refresh expiry (session lifetime cap), revocation,
logout, or token-reuse detection. Session-expired overlay, same-user
resume, and different-user reset semantics are unchanged.

## Backend contract (to land under bounded authorization)

- `POST /api/auth/login` — response gains `refreshToken` (opaque,
  256-bit, single-use). `AuthResponse{accessToken, refreshToken,
  expiresAt}`.
- `POST /api/auth/refresh` — **permitAll**. Body `{refreshToken}` →
  `200 AuthResponse` with a NEW refresh token (rotation); the presented
  token is retired. `401` for unknown/expired/revoked/malformed
  credentials — uniform `Invalid credentials.` problem. Replaying a
  rotated token revokes the whole family (reuse detection).
- `POST /api/auth/logout` — now **permitAll** with optional body
  `{refreshToken}`. Revokes the access-token `jti` (when a valid JWT
  is presented) AND the presented refresh token. Always `204` —
  non-disclosing. Lets a client whose access token already expired
  still kill its refresh credential (the refresh token in the body is
  itself sufficient authority to revoke itself).
- Storage: **in-memory** SHA-256-hashed allowlist (`RefreshTokenService`)
  — process-local like `TokenBlacklist`; restart invalidates every
  refresh token (fail-closed: users re-authenticate once). No schema
  migration. Family lifetime = fixed 30 days from login
  (`app.auth.refresh-session-days`); rotation does not extend it.
- Access-token behavior unchanged: 15-minute JWT, old access tokens
  remain valid until their own expiry after a refresh (standard
  bearer semantics); logout revocation unchanged.

## Security invariants (the milestone's spine)

1. **Refreshing authentication must NEVER create duplicate financial
   writes.** A non-idempotent request that was transmitted and answered
   with 401 is never replayed. Renewal happens *before* send when the
   local clock already knows the token is dead, or *after* a 401 for
   the credential only — the mutation itself returns to its caller.
2. Single-flight: N concurrent renewals collapse into one HTTP call.
3. Generation ownership: a refresh response arriving after logout /
   different-user sign-in is discarded (stale-generation check before
   apply).
4. A refresh result is only applied to the session it was minted for.
5. Definitive server rejection (401) of a refresh credential expires
   the session; transport failure does not (offline ≠ dead session).
6. Refresh tokens are credential material: Keystore-encrypted at rest,
   never logged (bare OkHttp client carries no logging interceptor;
   HEADERS logging never logs bodies anyway).
7. Legacy stored sessions (no refresh token — pre-M13 blobs) behave
   exactly as today: access expiry → session-expired → re-auth.

## Android architecture

- `StoredSession`/`Session.SignedIn`/`TokenStore` gain nullable
  `refreshToken` (absent = legacy = today's behavior exactly).
- `SessionRefresher` (new): owns the bare `OkHttpClient` (no
  authenticator/interceptors, `retryOnConnectionFailure(false)`),
  `synchronized` single-flight with stale-token short-circuit,
  generation-checked `applyRenewedSession`, outcome model
  {Renewed/Alive/NoCredential/Rejected/Transient/Discarded}.
- `AuthInterceptor`: when `accessToken()` is null but a renewal
  credential exists → blocking single-flight refresh → attach fresh
  token → request transmits once (pre-send path).
- `TokenAuthenticator` (new OkHttp `Authenticator`, backstop for
  server-side rejections the local clock missed): refresh once
  (single-flight), retry idempotent GET/HEAD once with a marker
  header; non-idempotent methods are never re-issued.
- `ErrorInterceptor`: 401 on an authenticated call →
  `SessionManager.onRequestRejected(usedToken)` → session dies only
  when the *current* token was rejected AND no renewal credential
  survives; renewed/alive cases carry `ApiException.sessionRetryable`
  → `AppError.SessionRetryable` (VM `else` branches already surface a
  generic retryable error — honest).
- `SessionManager`: `onSignedIn(+refreshToken)`;
  `accessToken()` stops flipping to Expired while a renewal credential
  survives; `pendingRenewal` cold-start path; `onRenewalRejected`;
  `signOut` clears both credentials.
- `AuthRepository`: login persists `refreshToken`; logout sends
  `{refreshToken}` best-effort (permitAll endpoint → works even with a
  dead access token).
- Cold start: access expired + refresh present → `Session.Restoring`
  holds → refresher renews → `SignedIn` (no stale protected data is
  shown while identity is unconfirmed) or `Expired`/`SignedOut`.

## Non-goals

- No change to access-token TTL, JWT shape, or signing.
- No persisted server-side refresh store, no device list, no
  "revoke all sessions" surface (multi-device = independent families).
- No edit/delete of any financial resource; no new UI screens — this
  is session infrastructure; the existing expired-session overlay is
  the UI.
- No automatic replay of any non-idempotent request, ever.
- No M14 work of any kind.

## Tests

Backend: `RefreshTokenServiceTest` (issue/rotate/reuse-family-revoke/
expiry/logout/restart-equivalence), `AuthControllerRefreshTest` (login
returns refresh; refresh happy path; rotated replay → 401 + successor
dead; access-as-refresh → 401; malformed → 400; logout-with-body
revokes refresh; logout bare → 204; refreshed token reaches a
protected endpoint), slice test for request binding.
Android JVM: MockWebServer flow tests (pre-send refresh → mutation
transmits exactly once; concurrent-401 single-flight; GET retry-once;
POST never replayed; refresh-401 → Expired; transient → no expiry;
generation discard; legacy session unchanged; loop guard),
SessionManager unit tests for every transition, repo/logout wiring,
store roundtrip. Connected: `SessionRuntimeM8Test` gains
refreshToken persistence roundtrip + legacy-blob reads on the real
Keystore path.

## Stop conditions

Beyond the contract's global stops: any requirement for persisted
refresh storage beyond the additive in-memory design, any demand for
per-request-mutation replay, or a canonical doc contradicting the
rotation/reuse semantics → STOP to THE HUMAN.

## Expected final report

Implementation + test + QA evidence, gates (backend suite, JVM,
connected ×2, lint/detekt/release/serializer/manifest), Sentinel +
Argus reviews, remediation record, external-audit handoff →
`M13_READY_FOR_EXTERNAL_AUDIT=YES`.
