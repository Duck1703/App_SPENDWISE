# Post-M20 Product Completion Gap Audit

**STATUS: PLANNING ANALYSIS — NOT_CANONICAL — creates no milestone, modifies
no production code, changes no roadmap.**

Commissioned by THE HUMAN to answer: *what remains before Spendwise is a
finished, usable personal-finance application?* Read-only inspection of the
Android and backend repositories, canonical docs, and audit records through
M20. No code, schema, or governance changes were made.

## 1. Canonical M20 accepted checkpoints — verified

| Check | Result |
|---|---|
| Android accepted HEAD | `20fbe6b2cfe37ac8a0881ffddff98f5bcaa5f213` — verified on `main`, worktree clean |
| Backend accepted HEAD | `4ced814c3e45068b1c7dfc115c3ddcf59ecb7a34` — verified on `main` |
| Backend dirty files | `README.md` (M), `HUONG-DAN-SU-DUNG.md` (??), `docs/SPENDWISE-REQ.md` (??) — Human-owned, untouched |
| `M20_EXTERNAL_AUDIT_FINAL` | ACCEPTED ([M20-EXTERNAL-AUDIT.md](M20-EXTERNAL-AUDIT.md)) |
| `M20_HUMAN_APPROVED` | YES (conditional closeout, all gates green on final source) |
| `M20_STATUS` | COMPLETE |
| `M21_DEFINED` / `M21_STARTED` | NO / NO — still true after this audit |
| Milestone chain | M0–M20 all COMPLETE with external audits accepted; M5/M6 rows in ROADMAP still carry stale "pending human sign-off" labels — bookkeeping note only: M7/M8 human approvals plus M8 V1 release acceptance subsumed that sign-off |

## 2. Full capability inventory

Classification: `ACCEPTED_COMPLETE` (external-audit accepted) ·
`IMPLEMENTED_NOT_ACCEPTED` · `PARTIAL` · `NOT_STARTED` ·
`DEFERRED_OR_OPTIONAL`.

### 2.1 Accepted complete — 26 capabilities

| Capability | Evidence | REQ |
|---|---|---|
| Register / Login / Logout / session-expiry-resume | M1 ext-audit ACCEPTED | — |
| Session refresh (rotating 30-day, single-flight, no-replay) | M13 ext-audit ACCEPTED | BE-002 |
| Home dashboard + month summary + mixed-currency honesty | M4 ext-audit ACCEPTED | — |
| Account list / create / detail | M2 ext-audit ACCEPTED | — |
| Account rename / archive / unarchive | M19 ext-audit ACCEPTED | BE-006 |
| Transaction create (INCOME/EXPENSE) | M3 ext-audit ACCEPTED | — |
| Transaction list / detail / filters (account/type/category) | M3 ext-audit ACCEPTED | — |
| Transaction dates (display + entry) | M11 ext-audit ACCEPTED | BE-001 |
| Transaction notes (`description` persist/display/export) | M17 ext-audit ACCEPTED | BE-010 |
| Transaction pagination + date-range + uncategorized + provenance | M15 ext-audit ACCEPTED | BE-007 |
| Transaction search (`q` over description) | M18 ext-audit ACCEPTED | BE-007 residual |
| Transfers (atomic paired `POST /api/transfers`) | M12 ext-audit ACCEPTED | BE-003 |
| CSV + TPBank-PDF import (dedup, row report) | M5 ext-audit ACCEPTED | — |
| Import history (server-backed list + per-batch report) | M10 ext-audit ACCEPTED | BE-004 |
| CSV ledger export (SAF share) | M9 ext-audit ACCEPTED | — |
| Categories (fixed-5 picker, assignment, rules-driven auto-cat) | M3/M6 ACCEPTED | — |
| Categorization rules list/create/disable | M6 ext-audit ACCEPTED | — |
| Budgets list/create/detail/calculation | M6 ext-audit ACCEPTED | — |
| Budget edit/delete | M14 ext-audit ACCEPTED | BE-011 |
| Recurring list/create/deactivate | M6 ext-audit ACCEPTED | — |
| Recurring template edit | M16 ext-audit ACCEPTED | BE-012 |
| Offsetting-entry correction (§21/S10/J14) | M20 ext-audit ACCEPTED | §21 |
| Encrypted session storage (Keystore AES/GCM, backup-excluded) | M7/M8 audited, M20 re-verified | AD-010 |
| Owner isolation / cross-user privacy | verified M13–M20 audits | — |
| Mutation no-replay on transmitted-401/ambiguous | M13 wire-pinned, inherited by all writes | — |
| Hardening (error surfaces, a11y labels, retry states) | M7 ext-audit ACCEPTED | — |

### 2.2 Not started / gated — feature backlog

| Capability | Status | Backend truth | Android | Priority |
|---|---|---|---|---|
| True transaction edit/void | NOT_STARTED | No PUT/PATCH/DELETE on `/api/transactions` (verified `TransactionController.java`: POST + GET + GET/page only) | absent | **P2** — needs PRODUCT DECISION (PATCH vs linked-void); M20 offset is the accepted remedy for personal V1 | BE-005 |
| Multipart size config | NOT_STARTED | Spring ~1 MB default → 500 on oversize | n/a | **P2** — trivial property change; TPBank PDFs typically under cap; real-user risk small | BE-008 |
| Backend hardening cluster | PARTIAL | recurring `@Version` done (M16/V11); server-side recurring-currency validation still absent (client-locked, D14 unreachable); typed domain error codes absent; mixed-currency stats policy absent | n/a | **P2** — correctness hygiene, no user-visible hole while the app locks currency | BE-009 |
| Categorization-rule edit | NOT_STARTED | No PATCH on `/api/categorization-rules` (delete+recreate is the shipped path) | absent | **P3** | — |
| `GET /api/transactions/{id}` | NOT_STARTED | absent (M20 `findById` works around via owner-scoped unfiltered fetch — safe at V1 ledger sizes) | workaround | **P3** | — |
| FX converter tool | NOT_STARTED | `GET /api/fx/convert` exists, display-only semantics (scale-2, no rate timestamp, `success:false` never fabricated) | absent | **P3** — product-gated; low value | — |
| Profile / identity mgmt (`/me`, change-password, account deletion) | NOT_STARTED | no `/me`; `AuthResponse` = tokens+expiry only | absent | **P3** — note: no password-recovery path exists at all; single-user mitigation is direct DB admin | — |
| User-defined categories | NOT_STARTED | `GET /api/categories` only; seeded fixed 5 | n/a | **P3** — backend-gated | — |
| Account hard-delete | NOT_STARTED | none; archive (M19) is the remedy | n/a | **P3** | — |
| Vietnamese locale | NOT_STARTED | n/a | `res/values` only — English-only | **P3** — Human scope decision (docs are VN; UI strings EN) | — |
| Settings/preferences surface | NOT_STARTED | n/a | no SettingsRoute exists | **P3** — nothing canonical requires it | — |
| Offline mode | EXPLICITLY_EXCLUDED (PRODUCT §30 NETWORK_REQUIRED) | — | — | — |
| Push / analytics / OCR / investments / shared accounts | EXPLICITLY_EXCLUDED (PRODUCT §38) | — | — | — |

**Endpoint consumption:** every backend mapping is consumed by the app except
`GET /api/fx/convert` (product-gated). Android advertises nothing the backend
cannot do (verified: 32 Retrofit calls vs 13 controllers).

## 3. Integration / cross-feature completeness

Journeys J1–J14 all delivered and audit-accepted (PRODUCT §32). Combined
journeys verified by milestone audits: create-account→transaction→dashboard
(M3/M4), import→history→search→export (M5/M10/M18/M9), recurring→generation
(M6/M16), budget→dashboard (M6/M14), archive→historical visibility (M19),
offset→authoritative balance (M20), A→B session isolation (M13+).

**Evidence-supported integration gap (the central finding):**

- **GAP-INT-1 — the app has never been verified end-to-end against the real
  backend, and currently cannot connect to one.** `usesCleartextTraffic="false"`
  is global in `AndroidManifest.xml` with **no** `networkSecurityConfig` and
  **no** debug-manifest override; the backend serves plain HTTP on `:8080`
  (no TLS anywhere in `application.properties`/Dockerfile/compose). Separately,
  `gradle.properties` ships `spendwise.api.base.url=https://example.invalid/` —
  every build without an explicit `-P` flag produces an app pointed nowhere.
  All 218 connected tests run against fakes/MockWebServer, not a live server.
  Consequence: a Human following today's instructions produces an app that
  cannot reach a real backend at all. **This is the gap between "fully tested"
  and "usable product".**

## 4. Financial integrity — verified invariants

BigDecimal `Money` everywhere; currency-matched transactions; backend-authoritative
balances (zero client-side arithmetic — AD-013); owner-scoped 404s; atomic
transfer pairing; import dedup (`sourceRef`); recurring generation idempotent;
optimistic locking on accounts/budgets/recurring (409); append-only ledger with
audited offset remedy; CSV formula-injection neutralized (M17); surrogate-safe
Unicode (M17); archive semantics (M19); search/pagination server-side (M15/M18).
**No unsupported financial assumption remains open.** Residual risks are
deployment-shape, not correctness: process-local refresh sessions (restart =
global re-login, documented fail-closed), server-local-day date stamping near
midnight (documented).

## 5. Security/session readiness

Verified: JWT auth, Keystore-encrypted tokens, rotation+reuse-revocation,
logout revokes both credentials, no sensitive logging (AD-020), cleartext
globally refused, datastore excluded from all backup channels, `/api/**`
authenticated, owner isolation, mutation no-replay.
Partially verified: `/actuator/metrics` requires auth (not in permitAll list);
`/actuator/health`, `/v3/api-docs`, `/swagger-ui/**` are permitAll (documented;
info-disclosure note only if exposed beyond LAN).
Not verified against a real deployment: TLS posture (none exists), secret
handling in a real environment, live session behavior.

## 6. Deployment readiness (backend)

| Question | Evidence | Verdict |
|---|---|---|
| Starts with prod-like config? | env-var config, fail-fast on missing secrets, `ddl-auto=validate` | YES (documented) |
| Postgres deployment documented? | docker-compose (postgres:16 + app, both healthchecked) + README | YES |
| Env vars safe? | `SPENDWISE_*` only, no defaults for secrets, `.env.local` gitignored | YES |
| Flyway reliable? | V1–V13, flyway-core + postgresql deps, validate-mode | YES |
| DB backups? | **nothing — no scripts, no docs, no schedule** | **NO — gap** |
| Restore tested? | no procedure exists | **NO — gap** |
| TLS/HTTPS? | HTTP only; needs reverse proxy or app config | **NO — gap (or scoped cleartext on Android)** |
| Health monitoring? | actuator health UP/DOWN, metrics authed | YES (sufficient for personal) |
| Reachable off-LAN? | not addressed anywhere | UNVERIFIED |
| Upgrade path? | Flyway auto-migrate on boot; no rollback runbook | PARTIAL |
| Error/log policy? | RFC-7807, traceId MDC, no body logging | YES |

## 7. Android release readiness

`applicationId=com.spendwise.android`, `versionCode=1`/`versionName=1.0.0`,
minSdk 26 / target 36, R8+shrink on release (verified per-milestone), manifest
INTERNET-only, backup rules correct. **Missing for a real install:** signing
(unsigned release; Human-owned Q4 — no keystore exists in-repo), concrete API
base URL, connectivity mechanism per §3.

## 8. P0/P1/P2/P3 backlog

- **P0-1 Connectivity**: app cannot reach the HTTP-only backend
  (`usesCleartextTraffic=false`, no networkSecurityConfig) **and** default base
  URL is `https://example.invalid/`. Resolution = Human decision: TLS
  termination on the backend host (recommended) or a domain-scoped cleartext
  exception + build with `-Pspendwise.api.base.url=<endpoint>`.
  Acceptance: the app on the physical Pixel completes a live login against the
  real deployed backend.
- **P1-1 Release signing**: unsigned APK only; Human-owned keystore +
  `signingConfig` wiring needed for a proper installable release (debug install
  is the interim path).
- **P1-2 Database backup/restore**: no plan, no script, no verified restore.
  Acceptance: documented `pg_dump` routine + one verified restore.
- **P1-3 Live end-to-end verification**: full user journey never exercised
  against the real backend on device. Acceptance: scripted live smoke
  (register→login→account→transaction→offset→import→export→dashboard→
  recurring→budget→rule→logout→re-login, no leakage) on the Pixel.
- **P1-4 Deployment runbook**: compose-up + env + build-flag + install +
  backup steps in one doc. (Bundled into the same milestone.)
- **P2**: BE-005 true correction (product decision); BE-008 multipart size;
  BE-009 hardening cluster; Swagger/api-docs exposure policy if backend ever
  leaves LAN; password-change/recovery surface.
- **P3**: rule edit, txn `GET/{id}`, FX converter, `/me`/profile, user
  categories, account hard-delete, VN locale, settings surface.

## 9. Minimum completion roadmap (PROPOSED ONLY)

**One milestone.** Feature development for personal V1 is done; what remains
is release/deployment readiness — a single coherent deliverable.

- **M21 (proposed) — "Personal Release & Deployment Hardening"**
  - Backend scope (bounded, only if authorized): none strictly required; BE-008
    multipart-size bump is a safe ride-along.
  - Android scope: connectivity wiring per Human decision (TLS base URL
    documented, or `networkSecurityConfig` scoped cleartext to the deployment
    host — never blanket cleartext); `signingConfig` wiring against a
    Human-supplied keystore (keystore itself never committed);
    `versionCode/versionName` policy.
  - Docs: deployment runbook (compose, env vars, build flag, install/update),
    `pg_dump` backup routine + restore-verification step, upgrade notes.
  - Verification: live end-to-end smoke on physical Pixel against the real
    deployed backend; full regression; R8 signed-build install.
  - Finishes: `SPENDWISE_PERSONAL_V1_COMPLETE=YES`.

**Extended (post-V1, all optional, each would need its own authorization):**
BE-005 true correction (product decision first) · BE-009 hardening ·
rule-edit · FX tool · profile/change-password · user categories · VN locale ·
pagination scale work. None required for personal V1.

## 10. Estimated remaining work

- **MINIMUM** — 1 milestone → 1 delivery + 1 external audit ≈ **2 Devin
  rounds** → final milestone M21. Confidence HIGH.
- **RECOMMENDED** — same 1 milestone (slightly broader runbook/backup/
  live-smoke scope) ≈ **2 Devin rounds** → M21. Confidence HIGH.
- **EXTENDED** — +1–4 optional feature milestones (BE-005, profile, FX,
  i18n…) ≈ **+2–8 rounds** → M22–M25. Confidence MEDIUM (Human appetite).

## 11. Recommended next milestone

**M21_PROPOSED_CAPABILITY = Personal Release & Deployment Hardening**
(not a feature milestone — every feature lane is either delivered or
product-gated; pushing another feature first would ship value into an app
that cannot yet connect to its backend).

Requirements: closes P0-1 connectivity, P1-1 signing, P1-2 backup, P1-3 live
smoke, P1-4 runbook. Backend scope minimal (optional BE-008). Risks:
Human-keystore handling, LAN-vs-TLS decision, secrets hygiene. Size: M.

## 12. Personal-V1 finish line

`SPENDWISE_PERSONAL_V1_COMPLETE=YES` iff all hold:

1. Real backend deployed per runbook, reachable by the app on the Pixel.
2. Connectivity resolved (TLS endpoint or scoped cleartext) — proven live.
3. Installable signed release (or Human-accepted signed-debug path).
4. Live smoke: full J1–J14 pass on device against the real backend.
5. DB backup routine documented; one restore verified.
6. Full regression green on final source (backend + JVM + connected).
7. Zero unresolved financial-integrity/security findings.
8. Human accepts deferred scope (BE-005, FX, profile, i18n).

## 13. Unknowns / Human decisions needed

1. Where does the backend run (LAN machine / NAS / VPS)? → drives TLS vs
   cleartext and the base URL.
2. Signing: Human generates the keystore (never committed) or accepts
   debug-signed installs?
3. Is BE-005 (true edit/void) wanted post-V1, or is the offset remedy the
   final correction UX?
4. Backup cadence/storage the Human wants.
5. Whether the Vietnamese locale matters for personal use.

## 14. Safety confirmation

`ANDROID_PRODUCTION_MODIFIED=NO` · `BACKEND_PRODUCTION_MODIFIED=NO` ·
backend Human-owned dirty files untouched · reference/prototype repos not
opened for writing · no private fixtures accessed · no push/tag/amend ·
no course update · `M21_STARTED=NO`.
