# P1 Argus Review

Review mode: ISOLATED (independent read-only subagent context — separate execution
from the audit author). The reviewer had no write access; this file was persisted
verbatim by ATLAS from the reviewer's returned report, with the reconciliation
section appended.

## Scope

Reviewed `project-control/ANDROID-BASELINE.md` (§§1–37) against the repository it
audits: `D:\app_spendwise\kotlin-accelerator-ai`, branch `master`, HEAD
`5f9622c59ce602ce33c98ee633ba799f903b67e1` — verified unchanged. ~45 claims
spot-checked across topology, build, bootstrap, nav, DI, networking, repos,
mapping, persistence, coroutines, errors, state/VM, design system, forms, auth,
testing, modularization, conventions, config, background, observability, drift,
and the classification matrix. Read-only throughout; no builds/tests executed;
no secret values read or reproduced.

## Evidence sampled

Representative re-verified sources (HEAD 5f9622c): `.git/HEAD` + ref;
`settings.gradle.kts`; `gradle/libs.versions.toml`; `gradle.properties`;
`app/build.gradle.kts` (:72-76 signing fallback, `jacocoMinCoverage="0.65"`);
`app/proguard-rules.pro` (6 reflection FQCN keeps); `AndroidManifest.xml`
(WM initializer removal); `App.kt`; `LauncherActivity.kt`;
`ui/navigation/AppNavHost.kt` (:30-32 dropped args), `SplashNavigation.kt`,
`NavTransitions.kt`; `sync/ScheduleChargingSyncUseCase.kt`;
`core/build.gradle.kts:31` (hardcoded dre dep); `core/.../di/*`;
`core/.../navigation/*`; `core/.../dispatchers/*`; `domain/build.gradle.kts`;
`domain/.../di/DomainModule.kt` (16 factories); `domain/.../service/auth/*`;
`domain/.../repository/leaderboard/*`; `domain/.../usecase/auth/AuthErrorMapper.kt`;
`data/.../di/DataModule.kt` (:116 mock binding, :77-97 Ktor config);
`data/.../supabase/SupabaseClientFactory.kt`;
`data/.../repo/leaderboard/LeaderboardRepositoryImpl.kt`; all 4 DataStore impls;
`data/.../service/{auth,sync,ai,user,config,analytics}/*`;
`feature/{game,menu,paywall}/build.gradle.kts` + manifests (`install-time`);
`feature/game/.../manage/*` + `viewmodel/*` + `di/*`; `feature/menu/...` Route,
ViewModels, reducers, settings/leaderboard/onboard packages;
`feature/paywall/*`; `theme/*` (15 CompositionLocals);
`ui-components/build.gradle.kts` + `HazeUtils.kt`; `secrets/build.gradle.kts`;
`scripts/encode-secrets.py`; `crypto.py` (repo root);
`local.properties.sample.enc`; `build-logic/convention/*` (5 plugins);
`gradle/jacoco-aggregation.gradle.kts`; `config/detekt/detekt.yml`;
`.github/workflows/pr-check.yml`; `docs/system-architecture.md`,
`docs/codebase-summary.md`, `CLAUDE.md`; all 52 `src/test` files; zero
`src/androidTest` files.

## Findings

### ARG-P1-01 — MINOR
- Claim under review: §3 topology table lists `:ui-components` deps as
  "`theme`, `core`, `domain`(for models)".
- Evidence: `ui-components/build.gradle.kts:13-14` declares only
  `api(project(":theme"))` + `implementation(project(":core"))`; zero
  `com.creative.kta.domain` references under `ui-components/`.
- Problem: fabricated dependency — overstates coupling (UI components
  structurally cannot see domain models; cleaner than documented).
- Required correction: change the row to `theme` (api) + `core`.
- **Disposition: FIXED** — row corrected.

### ARG-P1-02 — MINOR
- Claim under review: §9 implies the five reducers are "bundled in
  `*Contract.kt` files".
- Evidence: reducers are standalone files (`GameStateMachine.kt`,
  `MenuReducer.kt`, `SettingReducer.kt`, `LeaderboardReducer.kt`,
  `OnboardingReducer.kt`); `*Contract.kt` files declare State/Action/Effect/
  AsyncOp vocabulary only.
- Problem: misstates file organization.
- Required correction: reword.
- **Disposition: FIXED** — phrasing corrected.

### ARG-P1-03 — MINOR
- Claim under review: §23 test inventory says "game 12" while total says 52;
  own rows summed to 51.
- Evidence: `feature/game/src/test/` contains 13 files.
- Problem: off-by-one per-module count; internal inconsistency.
- Required correction: game row → 13.
- **Disposition: FIXED** — corrected to 13 with reconciliation note.

### ARG-P1-04 — MINOR
- Claim under review: DR-24 anchors `error()` at `GameStateMachine.kt:44`.
- Evidence: the `error()` call is on line 43; line 44 is
  `return handler(state, action)`.
- Problem: off-by-one evidence anchor.
- Required correction: cite :43.
- **Disposition: FIXED** — anchor corrected to :43.

### ARG-P1-05 — MINOR
- Claim under review: §36 evidence index implies `crypto.py` is under `scripts/`.
- Evidence: `crypto.py` is at repo root; `encode-secrets.py` is under `scripts/`.
- Problem: path imprecision in evidence index.
- Required correction: list `crypto.py` (repo root) separately.
- **Disposition: FIXED** — annotated "(repo root)".

### ARG-P1-06 — NOTE
- Claim under review: §18 "`LocalGameHistoryRepository` silently swallows JSON
  parse errors → empty list".
- Evidence: `LocalGameHistoryRepository.kt:105-112` logs `Timber.w` then returns
  `emptyList()`.
- Problem: "silently" overstates — logged but not propagated.
- Required correction: clarify logged degradation.
- **Disposition: FIXED** — corrected to "logs `Timber.w`, degrades to
  `emptyList()` — silent to callers".

### ARG-P1-07 — NOTE
- Claim under review: exact counts "haze (208 refs)", "107 API matches".
- Evidence: plain token counts yield ~371 / ~101 respectively depending on the
  pattern; `fun Qzds*` ≈ 20-24. Counting rule was unspecified.
- Problem: counts not reproducible as stated.
- Required correction: mark approximate or state the pattern.
- **Disposition: FIXED** — counts now marked "~100+"/"~200+" with an
  approximate-count note.

### ARG-P1-08 — NOTE
- Claim under review: coverage narrative.
- Evidence: `gradle/jacoco-aggregation.gradle.kts:19-22` `coverageModules` omits
  `:secrets` (which hosts `SecretManagerImplTest`).
- Problem: minor missed drift — aggregate coverage silently excludes a module.
- Required correction: add to DR-12.
- **Disposition: FIXED** — DR-12 extended.

### ARG-P1-09 — NOTE
- Claim under review: §35 P3 Q9 parenthetical "the backend docs are Vietnamese".
- Evidence: true (e.g., `D:\spendwise\HUONG-DAN-SU-DUNG.md`) but unanchored.
- Problem: unanchored factual aside.
- Required correction: cite the filename / mark as read-only observation.
- **Disposition: FIXED** — cited `HUONG-DAN-SU-DUNG.md` as a read-only
  observation.

### ARG-P1-10 — NOTE
- Claim under review: `ROADMAP.md` links a P1 milestone file that did not yet
  exist.
- Problem: P1 evidence trail lacked its milestone record at review time.
- Required correction: ATLAS to file the P1 milestone record.
- **Disposition: FIXED** — `project-control/milestones/P1-SENIOR-ANDROID-REFERENCE-AUDIT.md`
  created in this same milestone commit.

## Classification challenge

- KEEP calls are pattern-KEEPs, not library locks (DI "KEEP pattern / DEFER
  library"; networking "KEEP pattern / DEFER stack"; every row carries a
  "Final decision stage"). Two KEEP rows embed library vocabulary (typed routes
  via navigation-compose `@Serializable`; MockK/Turbine testing conventions) —
  defensible because §12/§23/§31 defer library selection to P4, but the
  pattern/library split could be crisper. Accepted as-is (NOTE-level).
- ADAPT calls carry evidence-anchored reasoning (DRE payoff vs ceremony; mapper
  inconsistency; loading-state catalog; design discipline vs quiz visuals).
- REJECT calls are the strongest section — each cites verified evidence.
- DEFER calls correctly deferred (auth/session, background work, remote config,
  crash reporting, entity persistence).
- No premature library selection; no KEEP locking Spendwise architecture before
  P4; §33 constraints uniformly tagged PRELIMINARY_P1_CONSTRAINT.
- No backend-contract assumptions; §34 leaves P2 questions open; no P2/P3/P4
  scope leakage detected.

## Boundary review

- REFERENCE_MODIFIED=NO — HEAD `5f9622c59ce602ce33c98ee633ba799f903b67e1`
  verified unchanged at review time (pointer + content checks).
- BACKEND_MODIFIED=NO — read-only access only.
- ANDROID_IMPLEMENTATION_CREATED=NO — `spendwise-android` contains only
  governance markdown + `.gitignore`; zero Kotlin/Gradle/manifest files, no
  `app/`/`src/`/`build/`/`gradle/` directories.

## Verdict

**PASS** — with minor corrections (all applied; see dispositions).

~45 spot-checked evidence anchors held against the source, many line-precise.
The drift register, hollow-gate findings, inverted dependency direction,
reflection discovery, and classification matrix all survive contact with the
repo. All 37 mandated sections present; 3 verified end-to-end flow traces.
Corrections were accuracy fixes; none changed any KEEP/ADAPT/REJECT/DEFER
conclusion.

## Reconciliation (ATLAS)

| Finding | Severity | Disposition |
| --- | --- | --- |
| ARG-P1-01 | MINOR | FIXED — `:ui-components` deps corrected to `theme`(api)+`core` |
| ARG-P1-02 | MINOR | FIXED — reducer/contract phrasing corrected |
| ARG-P1-03 | MINOR | FIXED — game test count corrected to 13; rows sum to 52 |
| ARG-P1-04 | MINOR | FIXED — anchor corrected to `GameStateMachine.kt:43` |
| ARG-P1-05 | MINOR | FIXED — `crypto.py` marked repo-root |
| ARG-P1-06 | NOTE | FIXED — logged degradation clarified |
| ARG-P1-07 | NOTE | FIXED — counts marked approximate |
| ARG-P1-08 | NOTE | FIXED — DR-12 extended with `:secrets` coverage omission |
| ARG-P1-09 | NOTE | FIXED — Vietnamese-docs observation anchored |
| ARG-P1-10 | NOTE | FIXED — P1 milestone record created |

Open findings after reconciliation: **none**.
