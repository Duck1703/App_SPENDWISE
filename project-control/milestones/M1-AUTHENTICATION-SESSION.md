# M1 — Authentication & Session

Status: **TECHNICALLY COMPLETE — READY FOR EXTERNAL AUDIT**
(`M1_HUMAN_APPROVED=NO`; do not start M2)

## Authorization

Human approved M0 (`M0_HUMAN_APPROVED=YES` recorded in `9a5fb90`), which
opened M1 per `ROADMAP.md`.

## Backend contract verified (source of truth, read-only)

Backend `D:\spendwise` HEAD `8194daa`:

| Endpoint | Success | Failure surface |
| --- | --- | --- |
| `POST /api/auth/register` `{username, password}` | 201 `{id, username}` | 400 + `fields{}` (validation); 400 w/o fields (duplicate — indistinguishable, hedged copy) |
| `POST /api/auth/login` `{username, password}` | 200 `{accessToken, expiresAt}` | 401 `Invalid credentials.`; 400 + `fields{}` |
| `POST /api/auth/logout` (Bearer) | 204 | 401 — ignored by caller |

JWT: HS256, `sub`=user UUID, `jti` blacklisted on logout, default 15-min
expiry. No refresh endpoint exists — no fake refresh implemented (INV-009).

## Implemented

- **Data**: `AuthService` (+`@PublicEndpoint` marking), wire DTOs,
  `RemoteAuthRepository` (register/login/logout; session establishment;
  `AppErrorException`-only failure surface).
- **Session**: `Session{Restoring|SignedOut|SignedIn|Expired}`,
  `resumedSession` identity-continuity flag (userId-primary via JWT `sub`,
  username fallback), `cacheInvalidations` for session-boundary hygiene,
  dead-credential drop on expiry, local `expiresAt` guard, encrypted
  `DataStoreTokenStore` extended with `userId`.
- **Network**: `ErrorInterceptor` — authenticated-401 → expiry signal
  (idempotent); public-call 401s pass through as `ApiException`.
- **UI**: Welcome / Login / Register / SessionExpired screens
  (`feature/auth`, stateless + Hilt-wired destinations), password
  visibility toggles, inline field + form errors, submit locking, IME
  handling, a11y semantics; More hub with confirmed sign-out
  (`feature/more`).
- **Navigation**: `AuthFlow` (Welcome→Login/Register) and `MainFlow`
  (shell + pushed `SessionExpired`/`Login(resume)`); single idempotent
  session effect: expired-push, same-identity resume pop (login + expired
  entries), different-identity fresh shell, back-blocked expired screen.
  No duplicate session-expired destination exists.

## Tests

`testDebugUnitTest`: **87/87 PASS** — wire contract (MockWebServer +
real interceptors), repository mapping, session transitions incl.
restart/resume/reset, ViewModel validation + submit-lock, JWT claims.
androidTest: 24 Compose cases across app-shell navigation (expired push,
resume restore, fresh landing, sign-out) and the stateless screens —
compile-verified; no device available for instrumentation.

## Quality gates

`assembleDebug` PASS · `lintDebug` PASS (0 errors; 19 informational
dependency-version warnings, same class as M0 baseline) ·
`compileDebugAndroidTestKotlin` PASS · `assembleRelease` PASS ·
detekt **0 findings**.

## Reviews

- Sentinel: [reviews/M1-SENTINEL-REVIEW.md](../reviews/M1-SENTINEL-REVIEW.md) — PASS.
- Argus: [reviews/M1-ARGUS-REVIEW.md](../reviews/M1-ARGUS-REVIEW.md) —
  0 blockers, 0 majors, 4 minors (accepted), 5 notes. Reconciled.

## Scope exclusions honored

No accounts, transactions, dashboard, import, budgets, recurring, or
rules work. No feature repositories, Room, WorkManager, or extra modules.
One additive dependency: `material-icons-extended` (password-visibility
icons — required by the auth screens; no upgrades/replacements).

## Boundary preservation

- Backend: HEAD `8194daa` unchanged; pre-existing user-owned dirty docs
  only — untouched by M1.
- Reference `D:\app_spendwise\kotlin-accelerator-ai`: HEAD `5f9622c` —
  read-only respected.
- Prototype `D:\app_spendwise\spendwise-ui-prototype`: not modified.
- Private fixtures: never accessed.
- All writes confined to `D:\app_spendwise\spendwise-android`.

## Deferred / known limitations

- Real backend E2E not run: no permitted dev environment (base URL is
  property-driven; `example.invalid` placeholder). Wire-level contract is
  covered by MockWebServer + the real interceptor stack.
- Keystore cipher round-trip still needs a device/emulator
  (deferred from M0 — first instrumentation-capable milestone).
- `Session.Expired` is in-memory; process death while expired restores to
  SignedOut (documented degradation).
- Register has no password-confirmation field (matches P3A baseline).

## Human gate

`M1_TECHNICAL_COMPLETE=YES`, `M1_READY_FOR_EXTERNAL_AUDIT=YES`,
`M1_HUMAN_APPROVED=NO`, `M2_STARTED=NO`. Next action: **EXTERNAL M1 AUDIT**.
