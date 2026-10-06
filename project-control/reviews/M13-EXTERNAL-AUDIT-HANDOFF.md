# M13 External Audit Handoff — Session Refresh / Token Renewal (REQ-BE-002)

```
MILESTONE: M13 — Session Refresh / Token Renewal
SELECTION: REQ-BE-002 — highest canonical priority remaining
  (BE-001→M11, BE-003→M12; PRODUCT Q2 flags BE-002 "high priority";
   the spec's "sequence after value items" caveat is satisfied —
   every value item shipped). Exactly one capability; M14 untouched.
ANDROID_RANGE: 3e8551a..ef04a42 (start 3e8551a → end ef04a42)
  - 3131536 governance: M12 canonical closeout + M13 brief/roadmap
  - 3686788 feature: session model/store, refresher, interceptors,
    repository, DTOs, tests, contract doc
  - ef04a42 remediation: credential toString redaction + review docs
BACKEND: D:\spendwise fd2ca1b → f1ac79a (bounded, human-authorized
  prerequisite; human-owned README/HUONG-DAN/SPENDWISE-REQ docs
  remain uncommitted and untouched)
WORKTREES: android clean at ef04a42; backend clean except the three
  pre-existing human doc files
```

## Contract delivered

| Endpoint | Change |
|---|---|
| `POST /api/auth/login` | `AuthResponse` gains `refreshToken` (opaque 256-bit, URL-safe) |
| `POST /api/auth/refresh` | NEW, permitAll. `{refreshToken}` → `200 AuthResponse` (rotated pair, new access JTI). 401 sanitized `Invalid credentials.` on unknown/expired/revoked/blank; rotated-token replay → whole-family revocation |
| `POST /api/auth/logout` | Now permitAll + optional `{refreshToken}` body; always 204; revokes bearer `jti` + presented refresh token |

Backend storage: process-local SHA-256-hashed allowlist
(`RefreshTokenService`), family-bound absolute TTL 30d
(`app.auth.refresh-session-days`), rotation never extends. No schema
migration; restart fails closed. `TokenBlacklist` behavior unchanged.

## Android slice delivered

- `Session.SignedIn`/`StoredSession`/`TokenStore`/`DataStoreTokenStore`:
  nullable `refreshToken`, Keystore-encrypted at rest, absent on pre-M13
  blobs → legacy behavior; `toString()` redacts all credentials.
- `SessionRefresher` (new): `ReentrantLock` single-flight on a bare
  OkHttp client (no interceptors/authenticator/logging, no retry);
  outcome model Renewed/AlreadyAlive/NoCredential/Rejected/Transient/
  Discarded; generation-checked apply; persistence failure → Transient.
- `AuthInterceptor`: pre-send renewal when access is locally expired and
  a credential survives — mutations transmit exactly once under fresh
  credentials; public endpoints are refresh-exempt.
- `TokenAuthenticator` (new): server-side 401 backstop — renews once,
  retries GET/HEAD once (marker header), **never re-issues a
  non-idempotent request**.
- `ErrorInterceptor` + `SessionManager.onRequestRejected`: session dies
  only when the LIVE credential was rejected AND no refresh credential
  survives; otherwise `ApiException.sessionRetryable` →
  `AppError.SessionRetryable` (generic retry UI, not sign-out).
- Cold start: expired access + live refresh → `Restoring` parks behind
  `pendingRenewal`; `SpendwiseApplication` resolves via
  `renewPendingSession` → SignedIn / Expired (transient keeps the stored
  credential; rejection wipes).
- `AuthRepository`: login persists `refreshToken` (blank/absent →
  legacy); logout sends it in the body — works even with a dead access
  token.

## Gate evidence

| Gate | Result |
|---|---|
| Backend full regression (`mvn test` @ f1ac79a) | **327 tests, 1 failure** = `RecurringSchedulingJobTest…ExactlyOneTransaction` (expected 1 got 2) — the documented pre-existing M12 baseline flake, unrelated to auth |
| Backend M13 focused | RefreshTokenServiceTest 8/8 · AuthControllerRefreshTest 8/8 · SecurityConfigTest 4/4 · AuthServiceTest login method-selector 1/1 (surefire outer-class reporting quirk — method runs and passes) |
| Android JVM full regression | **566/566** (533 baseline + 33 M13) |
| Android lint | clean (standalone re-run; one earlier composite-run flake was an AGP generated-source race, not code) |
| detekt | 0 smells |
| Pixel 4 XL (API 33, 99041FFBA006F0) connected ×2 | **178/178 + 178/178** (174 baseline + 4 new store tests) |
| Minified release | `app-release-unsigned.apk` SHA-256 `0523324a3473b708787fbb2ec74e0b551d916aafe1f7a1999cf24a70b9cdde46` |
| Manifest/serializer/backup audit | INTERNET only · `usesCleartextTraffic=false` · `datastore/` excluded from cloud-backup + device-transfer · `LoginResponseDto`+`refreshToken` kept in R8 mapping |

Key wire-proven invariants (`SessionRefreshContractTest` + `SessionRefresherTest`):
mutation 401 → exactly 1 wire hit, never replayed; locally-expired send →
refresh-then-write, stale token never transmitted; concurrent renewals →
1 HTTP call; mid-flight sign-out → response discarded; refresh rejection
→ Expired + wiped store; transient failure → session intact; cold-start
park → resolve; legacy blobs → M12 semantics byte-identical.

## Reviews

- `M13-SENTINEL-REVIEW.md` — PASS, 0B/0M/2 Minor + 1 Note
- `M13-ARGUS-REVIEW.md` — PASS, 0B/0M/2 Minor + 3 Note; in-review defect
  A-1 (credential toString leak) remediated in `ef04a42`

Accepted residuals: server-dead-bearer logout can orphan the refresh
credential until the 30d cap (bounded, hashed, documented); in-memory
refresh store = forced re-login per backend restart (documented design);
process-local map growth mirrors `TokenBlacklist` posture.

## Status fields

```
M12_COMPLETE=YES
M13_TECHNICAL_COMPLETE=YES
M13_READY_FOR_EXTERNAL_AUDIT=YES
M13_HUMAN_APPROVED=NO
M14_DEFINED=NO
M14_STARTED=NO
SCOPE_AUDIT: M13-only; no unrelated features; no schema change; no
  dependency change; no financial-logic change; M14 not started.
SPENDWISE_STATUS=M13_READY_FOR_EXTERNAL_AUDIT
NEXT_ACTION=ONE_LARGE_INDEPENDENT_EXTERNAL_M13_AUDIT
```
