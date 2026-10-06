# M13 SENTINEL REVIEW — Session Refresh / Token Renewal

```
ROLE: SENTINEL (Test Engineer)
MILESTONE: M13 — REQ-BE-002 Session Refresh / Token Renewal
ANDROID_RANGE: 3e8551a..3686788 (+ governance 3131536)
BACKEND_COMMIT: f1ac79a (bounded prerequisite, human-authorized)
REVIEW_MODE: adversarial self-audit per workflow contract; all evidence
             reproduced from live gates, not assumed.
VERDICT: PASS — 0 Blocker, 0 Major, 3 Minor/Note
```

## 1. Test evidence (re-executed for this review)

| Gate | Result |
|---|---|
| Backend full regression (`mvn test`) | 327 tests, 1 failure = documented pre-existing `RecurringSchedulingJobTest` timing flake (expected 1, got 2 — identical to M12 baseline, unrelated to auth) |
| Backend focused M13 | RefreshTokenServiceTest 8/8, AuthControllerRefreshTest 8/8, SecurityConfigTest 4/4, AuthServiceTest login method-selector 1/1 (outer-class surefire/nested reporting quirk documented) |
| Android JVM full regression | **565/565** (M12 baseline 533 → +32) |
| Android lint / detekt | clean / 0 smells |
| Pixel 4 XL connected (×2) | **178/178 both runs** (174 baseline + 4 new store tests) |
| Release (minified R8) | builds; `app-release-unsigned.apk` SHA-256 `f583307a1b90b3bbc7e595987fff7fd00899137fe2c51417cdfbe26e0cf24b51` |

## 2. Invariant coverage — "refresh must NEVER create duplicate writes"

Wire-proven in `SessionRefreshContractTest`:

- `a transmitted mutation that 401s is NEVER replayed` — server sees
  exactly ONE `/api/probe` + one `/api/auth/refresh`; the caller gets
  `ApiException(401, sessionRetryable)` → `AppError.SessionRetryable`.
- `locally-expired token … mutation transmitted once` — pre-send renewal:
  refresh → then the write with the fresh bearer; the stale token never
  hits the wire. `server.requestCount == 2`.
- `server-side 401 on a read is renewed and retried exactly once` —
  GET retried with the renewed bearer, marker header prevents loops.
- `refresh rejection … expires the session definitively` — 401+401 →
  `Session.Expired`, store wiped.
- Legacy no-refresh 401 → `Session.Expired`, exactly 1 request (no
  refresh attempt) — M12 semantics preserved byte-for-byte.

Single-flight proven in `SessionRefresherTest.concurrent callers collapse
into exactly one refresh call` (2 threads racing a gated response → 1 wire
call) plus `a peer renewal makes a late caller AlreadyAlive`.

Generation safety proven by `a session boundary mid-flight discards the
renewed credentials` (signOut during in-flight refresh → credentials
discarded, session stays SignedOut) and `applyRenewedSession on a stale
generation is discarded`.

Persistence honesty: `persistence failure on apply is transient — never
a fake session` (disk-full → Transient, old session kept, no volatile
credential adoption).

## 3. Backend security coverage

`RefreshTokenServiceTest` (8): issue→rotate chain, rotated-token replay →
family revocation, expired family rejection, revoked rejection, unknown/
blank rejection, absolute TTL (rotation does not extend), per-user family
isolation, mint uniqueness. `AuthControllerRefreshTest` (8): wire shape,
sanitized 401s, logout revokes-both + always-204, permitAll reachability
with dead/absent bearer.

## 4. Findings

### Minor-1 — server-dead bearer on logout can orphan the refresh credential
When the access token is locally valid but already blacklisted server-side,
Spring's bearer filter 401s the `permitAll` logout before the controller
reads the body → the presented refresh token is never revoked. Local state
still clears; the orphan is hashed, family-bound, and dies at the 30-day
absolute cap. **Accepted — bounded blast radius, documented limitation.**
Possible M-future hardening: client could strip the bearer when calling
logout with a locally-dead token (already the behavior) — the residual is
only the narrower revoked-but-unexpired case.

### Minor-2 — `isPublicCall()` duplicated in three files
`AuthInterceptor`, `ErrorInterceptor`, `TokenAuthenticator` each carry a
3-line private copy. Acceptable seam-local duplication; noted, not worth a
shared abstraction inside the auth-critical path.

### Note — `AuthInterceptor` nullable refresher default
`SessionRefresher? = null` lets pre-existing contract tests run without a
refresher (legacy semantics — precisely what they verify). Production Hilt
always injects the singleton; the dedicated `SessionRefreshContractTest`
wires the real instance. Deliberate test seam, documented in code.

## 5. Regression surface

- All 533 M12-era JVM tests still pass unmodified-in-behavior (DTO/session
  changes are defaulted — source-compatible).
- `SessionExpiredScreen` resume path untouched; `SessionRetryable` falls
  into existing generic `else` error branches (verified: no exhaustive
  `when` broke).
- `retryOnConnectionFailure(false)` re-asserted in `NetworkModuleTest`.
- Manifest audit: INTERNET only, `usesCleartextTraffic=false`, datastore/
  excluded from cloud-backup AND device-transfer; serializers kept in R8
  output (`LoginResponseDto` + `refreshToken` present in mapping.txt).

## 6. Scope audit

M13-only diff: session/refresh files + auth DTO/service/repository +
network seam + tests + contract doc. No endpoint, schema, dependency,
financial-logic, or unrelated-file changes. M14 untouched.
