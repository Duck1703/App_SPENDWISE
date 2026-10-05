# M7 — Hardening (a11y, performance, error polish, release config)

Status: **READY FOR EXTERNAL AUDIT**
Starting SHA: `b9d8503` (M6 conditional closeout)

## Canonical scope — resolved from ROADMAP + ARCHITECTURE

ROADMAP M7: "Hardening / UX / Testing". ARCHITECTURE §34 row M7:
"Hardening — a11y pass, perf, error polish, release build config;
depends on all; acceptance gate = full quality gates green."
ARCHITECTURE §36: "Release build check at M7 (`assembleRelease` w/ R8)."
ARCHITECTURE §47 Q4: release signing is Human-owned (internal/direct
install first) — **defer signing config; document the deferral here**.

M7 is a **cross-cutting hardening milestone, not a feature slice** —
all PRODUCT screens S01–S25 shipped in M0–M6. M7 adds no new screens,
endpoints, or dependencies.

## Authorized work items

1. **Release build config**
   - kotlinx-serialization R8 keep rules — `serializer(Type)` is invoked
     reflectively by the Retrofit converter; generated `$$serializer`
     class names must survive minification. Latent release defect:
     `app-release-unsigned.apk` DEX contains zero `$$serializer` names.
   - Verify release DEX retains serializer names post-rules.
   - `versionName` is still `0.1.0-m0` — bump for the V1 surface;
     document signing + versioning ownership (Q4 deferral).
2. **Accessibility pass** — touch targets ≥48dp, merged row semantics,
   content-descriptions, progress semantics, dialog labeling, error
   announcements across all shipped screens.
3. **Performance pass** — LazyColumn keys/stability, no avoidable
   recomposition work in hot paths, no blocking calls on main.
4. **Error polish** — uniform error/empty/loading/retry surfaces,
   honest ambiguous outcomes, no raw exception text anywhere in UI.
5. **Backend contract re-verification** — every consumed endpoint
   re-verified against backend `8194daa` source (read-only); MockWebServer
   suite remains authoritative proof.
6. **Session/privacy re-verification** — generation guards, resume
   semantics, backup/cleartext config (already hardened; re-verify).
7. **Quality gates** — all M0–M6 gates re-run on the hardened tree;
   Sentinel + Argus reviews; reconcile every blocker/major.

## Forbidden scope

- No new user-facing features or screens.
- No backend or reference-repo modifications.
- No new dependencies (hardening uses the existing toolchain only).
- No release signing config (Q4 — Human-owned).
- No M8 work.

## Evidence so far

- Manifest: `usesCleartextTraffic=false`, `allowBackup=true` with
  `datastore/` excluded from both cloud-backup and device-transfer
  (token DataStore never leaves the device). INTERNET only.
- Timber: `DebugTree` planted under `BuildConfig.DEBUG` only; no
  `Log.`/`println` in app sources.
- `onVisible` resume wiring: session-expired pushes a real nav
  destination, decomposing covered screens (`LaunchedEffect` re-fires);
  planning/import destinations additionally observe `ON_RESUME`.
- R8 finding (fix in scope): DEX lacks `$$serializer` names → add
  canonical keeps, rebuild, re-inspect.

## Stop conditions

- Any hardening fix that would change user-visible behavior beyond
  copy/a11y/semantics → stop, report to THE HUMAN.
- Any backend contract contradiction discovered → report, don't patch.

## Delivered — hardening changes

| Change | Files | Evidence |
| --- | --- | --- |
| kotlinx-serialization R8 keeps (Companion/`serializer()`/`$$serializer`, scoped to app wire surface + `kotlinx.serialization.json`) | `app/proguard-rules.pro` | release DEX: `$$serializer` names 0 → 28 (all 20 remote DTOs + ProblemDetailDto + 7 typed routes) |
| `versionName` `0.1.0-m0` → `1.0.0`; signing deferral documented (Q4) | `app/build.gradle.kts` | assembleRelease PASS |
| Category picker announces selection | `CreateTransactionScreen.kt` | `selectable` + `Role.RadioButton` |
| Unbounded lists compose lazily | `TransactionsScreen`, `AccountDetailScreen`, `ImportScreen` | `items(key, contentType)`; per-row `SwCard` (Home card language); import rows keep "on demand" toggle |
| Font-scale clip guard | `MoreScreen.kt` | root `verticalScroll` |
| Keyboard occlusion on forms | 6 create/import screens | `.imePadding()` after `verticalScroll` (edge-to-edge doesn't auto-inset) |
| Regression guard for release keeps | `ReleaseHardeningTest.kt` | 3 JVM tests |

## External-audit gate

```
M7_SENTINEL=PASS (0 blockers/majors/minors — reviews/M7-SENTINEL-REVIEW.md)
M7_ARGUS=PASS (0 blockers/majors/minors, 4 notes — reviews/M7-ARGUS-REVIEW.md)
M7_JVM_TESTS=422/422 (419 + 3 release-keep guards)
M7_CONNECTED_INSTRUMENTATION=NOT_RUN_NO_DEVICE (adb devices = 0; androidTest compile-verified)
M7_GATES=assembleDebug+testDebugUnitTest+lintDebug+compileDebugAndroidTestKotlin+assembleRelease+detekt: PASS
M7_LINT=0 errors, 20 informational warnings (unchanged set)
M7_DETEKT=0 code smells
M7_CONTRACT_REVERIFY=PASS (all 12 consumed endpoint groups match backend 8194daa; fx/export/import-report correctly unconsumed)
M7_SESSION_PRIVACY=PASS (generation guards, resume wiring, backup exclusion, cleartext off — re-verified)
M6_COMPLETE=YES
M7_TECHNICAL_COMPLETE=YES
M7_READY_FOR_EXTERNAL_AUDIT=YES
SAFE_TO_AUDIT=YES
```

## External audit (post-delivery)

Independent audit of `b9d8503..54993ed` — see
[reviews/M7-EXTERNAL-AUDIT-SUMMARY.md](../reviews/M7-EXTERNAL-AUDIT-SUMMARY.md):

```
M7_EXTERNAL_AUDIT_FINAL=ACCEPTED
BLOCKER=0  MAJOR=0  MINOR=1  NOTE=4
Release falsification: pre-M7 rules -> 0 $$serializer names in DEX;
M7 rules -> 28/28 (expected set independently derived: 22 DTOs +
6 data-class routes; 16 object routes need no $$serializer).
MINOR M7-EXT-01 deferred: residual grouped-card eager lists
(Accounts/Budgets/Rules/Recurring) — domain-bounded, visual redesign
not hardening fix.
```

## Human closeout (conditional authorization exercised)

```
M7_HUMAN_APPROVED=YES (conditional authorization — all gates met)
M7_STATUS=COMPLETE
M8_STATUS=DOES NOT EXIST IN CANONICAL GOVERNANCE
```

Canonical milestone sequence per ARCHITECTURE §34 and ROADMAP is
M0–M7 — **M7 is the final planned milestone**; no M8 exists in
ROADMAP, ARCHITECTURE, or PRODUCT. Backend-gated future capabilities
(BE-001…BE-011, PRODUCT §41) have no scheduled milestone until a
backend contract exists and THE HUMAN authorizes one. Attempting to
resolve "canonical M8" therefore hits the explicit stop condition —
no M8 work was started.

## Scope exclusions honored

No new screens/endpoints/dependencies, no backend or reference edits,
no signing config (Q4 deferral documented), no feature work inside the
hardening milestone. The only intentional visual change: unbounded
transaction/history/import lists render as per-row `SwCard`s (Home
card language) instead of a grouped card — required for lazy
composition; recorded in M7-ARGUS-REVIEW NOTE-4.
