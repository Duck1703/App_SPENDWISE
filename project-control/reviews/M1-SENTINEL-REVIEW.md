# M1 SENTINEL Review — Authentication & Session

Reviewer role: **SENTINEL** (test engineer / verification), per
`agent-system/agents/sentinel.md`. Scope: correctness, test coverage,
security, scope discipline, quality gates for the M1 vertical slice.

## Verified contract (source-checked)

Backend HEAD `8194daa` (read-only):
`POST /api/auth/register` → 201 `{id, username}`; `POST /api/auth/login`
→ 200 `{accessToken, expiresAt}`; `POST /api/auth/logout` → 204.
400 + `fields{}` for bean validation; 400 w/o fields for duplicate
username; 401 ProblemDetail `Invalid credentials.` for login failures;
filter-chain 401s for unauthenticated calls elsewhere.

## Correctness checklist

| Check | Result |
| --- | --- |
| DTO field names match backend records exactly | PASS |
| Register → no auto-login (backend returns no token); navigates to login w/ prefill + banner | PASS |
| Public vs authenticated 401 classified via `@PublicEndpoint` on the Retrofit method (Invocation tag) — no URL matching | PASS |
| Login/register 401 → inline error, never session expiry | PASS (contract test `login 401 yields ApiException and does NOT expire the session`) |
| Authenticated 401 → `Session.Expired` + dead credential dropped from store (no resurrection on restart) | PASS |
| Locally-expired token never sent (`accessToken()` guard) | PASS |
| Same-identity resume → `resumedSession=true`; different user → no resume + cache invalidation | PASS (unit-tested incl. userId-over-username precedence) |
| Session-expired pushed over preserved stack; back blocked | PASS (BackHandler + app-level nav test) |
| Logout: remote best-effort, local always cleared | PASS (tests cover 401 + offline) |
| Repo boundary: `ApiException`/`IOException`/`CancellationException`/generic → `AppErrorException` only | PASS |
| No raw exception or backend `title`/`detail` text reaches the UI; `fields{}` messages are the designed display channel | PASS |
| No token/password logging; debug OkHttp logging is HEADERS-only with Authorization redacted | PASS |
| Token at rest stays Keystore-AES ciphertext (`DataStoreTokenStore`) | PASS |

## Test coverage (unit: 87 passing)

- **Contract/wire**: `AuthServiceContractTest` (6) — paths, bodies, DTOs,
  Authorization header presence/absence, public-vs-authenticated 401.
- **Session**: `SessionManagerTest` (17) — restore/live/expired, expiry,
  resume identity, different-user reset, restart persistence, invalidations.
- **Repository**: `AuthRepositoryTest` (10) — success/error mapping,
  logout always-clears, resume flags.
- **ViewModels**: `LoginViewModelTest` (12), `RegisterViewModelTest` (8) —
  validation, submit lock, error mapping, route args, events.
- **Interceptors/mapping**: existing suites updated + 401-expiry cases.
- **JWT claims**: `JwtClaimsTest` (3).
- **Compose** (androidTest, compile-verified; no device): `SpendwiseAppTest`
  (8 — session graph routing incl. expired push + resume restore + fresh
  landing), `LoginScreenTest` (8), `RegisterScreenTest` (5),
  `SessionExpiredScreenTest` (1), `MoreScreenTest` (2).

## Gates (this machine)

`assembleDebug` PASS · `testDebugUnitTest` PASS (87/87) · `lintDebug` PASS
(0 errors, 19 informational warnings — dependency-version notices, same
class as M0) · `compileDebugAndroidTestKotlin` PASS · `assembleRelease`
PASS · detekt 0 findings.

## Sentinel verdict

```
SENTINEL_VERDICT=PASS
UNIT_TESTS=87
UNIT_FAIL=0
CONTRACT_TESTS=PASS
SESSION_TESTS=PASS
VIEWMODEL_TESTS=PASS
COMPOSE_TESTS=PASS (compile-verified; device run deferred — no device)
SECURITY_REVIEW=PASS
SCOPE_CHECK=PASS (no feature work beyond auth/session)
```
