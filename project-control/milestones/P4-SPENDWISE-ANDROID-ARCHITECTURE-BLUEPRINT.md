# P4 — Spendwise Android Architecture Blueprint

STATUS: **READY_FOR_HUMAN_REVIEW**

## Objective

Remove architectural ambiguity so M0 can begin safely after Human
approval: decide module structure, UI/state model, navigation, DI,
networking, serialization, money handling, auth/session, storage,
errors, import, testing, security, and design-system translation —
without producing any Android implementation.

## Approvals relied upon

- P1 — HUMAN_APPROVED (`0583af6`)
- P2 — HUMAN_APPROVED (`18b559c`)
- P3 — HUMAN_APPROVED (`e852562`)
- P3A — HUMAN_ACCEPTED_FOR_P4=YES (prototype is the accepted design
  baseline; captured in `P3A-DESIGN-HANDOFF.md`)

## Starting SHAs

| Repository | HEAD |
| --- | --- |
| Product | `e852562874068dbad1f6ad1ef2cc705670768d71` (main, clean) |
| Backend (read-only) | `8194daa9959f2d568dc13ab0f211787195efc79d` |
| Reference (read-only) | `5f9622c59ce602ce33c98ee633ba799f903b67e1` |
| Prototype (read-only) | working tree as accepted (uncommitted R1–R4 ok) |

## Inputs

- `PRODUCT.md`, `BACKEND-CONTRACT.md`, `ANDROID-BASELINE.md`,
  `ROADMAP.md`, `DECISIONS.md`, `WORKSPACE-BOUNDARIES.md`, governance
- P3A prototype docs: `P3A-UIUX-BLUEPRINT.md`, `P3A-DESIGN-SYSTEM.md`,
  `P3A-HUMAN-REVIEW.md`, `P3A-R1/R2/R3/R4` reports,
  `P3A-SOURCE-TRACEABILITY.md`, `P3A-R3-ASSET-MANIFEST.md`,
  `THIRD_PARTY_ASSETS.md`, prototype README (route table)
- Current-source checks (§40 of ARCHITECTURE.md): security-crypto
  deprecation, Navigation typed routes, DataStore guidance, AGP/JDK.

## Scope performed

Architecture planning only. **Zero** Android/Gradle/Kotlin/Compose/
resource/manifest files created. Pseudocode shapes in the document are
"shape only" contracts.

## Architecture decisions (22, all PROPOSED)

AD-001 single `:app` module · AD-002 MVVM Route→VM→Screen, no mandatory
reducer · AD-003 per-screen UiState + effects · AD-004 Navigation
Compose typed routes, auth+main graphs, expiry overlay · AD-005
Retrofit+OkHttp · AD-006 kotlinx.serialization + BigDecimal serializer ·
AD-007 Money/CurrencyCode/typed IDs · AD-008 defensive enums · AD-009
java.time · AD-010 Keystore-AES + DataStore token · AD-011 session-expiry
resume + draft preservation · AD-012 sealed AppError at repo boundary ·
AD-013 no Room / NETWORK_REQUIRED · AD-014 minSdk 26 + toolchain policy ·
AD-015 Hilt · AD-016 nine repositories + orchestration-only use cases ·
AD-017 OpenDocument + streaming multipart · AD-018 SpendwiseTheme/Sw*
design system + licensed assets · AD-019 scroll-derived Home FAB +
Transactions header add · AD-020 logging policy · AD-021 JVM-first test
pyramid · AD-022 no WorkManager/analytics/crash SDK.

Invariants INV-001…INV-015 codify financial truthfulness (backend
authoritative, no Float/Double, no silent currency mixing, no statement
parsing, no transfer code, immutable history, no fake refresh, no
sensitive logging, no dead affordances).

## Rejected approaches

Multi-module/dynamic features · Ktor client · Moshi/Jackson ·
java.util.UUID wrapping · EncryptedSharedPreferences (deprecated) ·
Koin & manual DI · Room/Paging-3 · WorkManager · Result-vs-throw mixed
convention · DRE-KT/reducer frameworks · analytics & crash SDKs (V1) ·
client-side statement parsing · fake refresh/pagination/search ·
Float/Double money paths.

## Human override applied

P3's PD-004 offsetting-entry correction was **dropped by THE HUMAN**
during P3A — V1 history is strictly read-only (INV-007). Stale
references remain in PRODUCT.md (§21, PD-004, J14, S10, §13, §33) and
are recorded here as **superseded-by-override** — the approved input
document is not edited; the override is canonical via
P3A-DESIGN-HANDOFF.md §3. ROADMAP M3 corrected.

## P3A handoff usage

`P3A-DESIGN-HANDOFF.md` canonizes: 4 destinations, FAB rule (task §5
supersedes R4's inverse trigger), header add, one-row filters,
read-only history, "Recorded as today", synchronous import, session
flow, mixed-currency rule, design tokens, licensed assets, a11y floor.

## Testing strategy

JVM-first pyramid: pure mapping/money/ProblemDetail tests, MockWebServer,
fake repositories, ViewModel+Turbine state tests, thin Compose UI tests
on critical flows; detekt warn-only; `test`/`lint`/`assembleDebug` are
the real failing gates (coverage verification consciously deferred).

## Security strategy

HTTPS-only (debug cleartext limited to dev host), Keystore-encrypted
token in DataStore (backup-excluded), no secrets in BuildConfig, no
sensitive logging, backend authorization authoritative, 404-normalcy.

## M0 readiness

M0 is defined exactly (ARCHITECTURE §35): scaffold/toolchain/Hilt/theme
skeleton/nav shell/network plumbing/session store/repo interfaces/
test scaffold/quality gates — **no feature UI**. Post-P4 sequence
M0–M7 is dependency-ordered (auth → accounts → transactions →
home/dashboard → import → planning → hardening).

## Human questions

4 open questions (ARCHITECTURE §39): Home hero label, minSdk 26,
dev/prod base URLs (M0-blocking input), release signing ownership.

## Argus review

`ARGUS_REVIEW_MODE=ISOLATED` —
[../reviews/P4-ARGUS-REVIEW.md](../reviews/P4-ARGUS-REVIEW.md)

- BLOCKER = 0 · MAJOR = 2 (login-401 scoping; session-cache
  invalidation) · MINOR = 10 · NOTE = 11
- All findings reconciled in ARCHITECTURE.md. ARGUS_VERDICT = **PASS**

## Outputs / files changed

- `project-control/ARCHITECTURE.md` — replaced placeholder with blueprint
- `project-control/P3A-DESIGN-HANDOFF.md` — new canonical design input
- `project-control/milestones/P4-SPENDWISE-ANDROID-ARCHITECTURE-BLUEPRINT.md` — this file
- `project-control/reviews/P4-ARGUS-REVIEW.md` — new
- `project-control/ROADMAP.md` — status + refined sequence

Unchanged (verified): `PRODUCT.md`, `BACKEND-CONTRACT.md`,
`ANDROID-BASELINE.md`, `DECISIONS.md`, `WORKSPACE-BOUNDARIES.md`,
`agent-system/**`, `AGENTS.md`. No Android source exists.

## Repository preservation

- Backend `D:\spendwise`: HEAD unchanged; dirty docs unchanged.
- Reference `D:\app_spendwise\kotlin-accelerator-ai`: HEAD unchanged, clean.
- Prototype `D:\app_spendwise\spendwise-ui-prototype`: untouched.
- Private fixtures `D:\java_spring_2_10_2026\private-bank-fixtures`: never accessed.

## Human gate

P4 is **READY_FOR_HUMAN_REVIEW** — not approved. On approval, ADs may be
promoted to `DECISIONS.md` at closeout and M0 may begin.

## Verdict

`SAFE_TO_BEGIN_M0_AFTER_HUMAN_APPROVAL=YES` (pending Human answers to
Q3/Q4 — base URLs and signing are M0 inputs, not blockers to review).

ANDROID_APP_DEVELOPMENT_STARTED=NO
