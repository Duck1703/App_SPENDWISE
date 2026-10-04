# M0 — Spendwise Android Foundation

STATUS: **READY_FOR_HUMAN_REVIEW**

## Objective

Create a compiling, tested Android foundation — toolchain, module, DI,
navigation shell, network plumbing, money/serialization primitives, secure
session storage, design-system skeleton, real tests, real quality gates —
with **zero** product features (build a strong foundation, not features).

## Approvals relied upon

- P1 — HUMAN_APPROVED (`0583af6`)
- P2 — HUMAN_APPROVED (`18b559c`)
- P3 — HUMAN_APPROVED (`e852562`)
- P4 — HUMAN_APPROVED (`89fac0b`), answers: `Q1_HOME_HERO_LABEL=THIS_MONTH`,
  `Q2_MIN_SDK_26=APPROVED`, `Q3_BASE_URL=PROPERTY_DRIVEN; ACTUAL DEV/PROD
  URL DEFERRED`, `Q4_RELEASE_SIGNING=HUMAN_OWNED; INTERNAL/DEBUG FIRST`

## P4 closeout (this milestone's first commit, `89fac0b`)

- AD-001…AD-022 promoted into `DECISIONS.md` with meaning preserved.
- P4 milestone marked `P4_HUMAN_APPROVED=YES` / `P4_STATUS=COMPLETE`.
- `ROADMAP.md`: `P4=COMPLETE`, `M0=CURRENT`,
  `ANDROID_APP_DEVELOPMENT_STARTED=YES`; later Human gates preserved.

## Scope performed / exclusions

Created the Android scaffold and all foundation code under `app/`.
**Not** implemented (explicitly forbidden in M0): login/registration
screens, accounts, transactions, dashboard, import, recurring, budgets,
rules, real backend calls, any vertical slice, any M1 work. No
feature-specific repository interfaces, Room, WorkManager, paging,
analytics, crash SDKs, or feature modules.

## Toolchain (verified on this machine)

- JDK `ms-17.0.20.1` via `JAVA_HOME` (Gradle daemon JVM)
- Gradle `9.1.0` (repo wrapper; `gradlew` committed)
- AGP `9.0.0` (built-in Kotlin — no `kotlin-android` plugin)
- Kotlin compiler plugins `2.3.0` (compose, serialization) — pinned to the
  AGP-embedded compiler generation, matching the proven local toolchain
- KSP `2.3.4` (KSP2), Hilt `2.60.1`, compile/target SDK `36`, minSdk `26`
- `local.properties` (gitignored) points at the installed SDK

## Architecture implemented

| Area | Implementation |
| --- | --- |
| App entry | `SpendwiseApplication` (@HiltAndroidApp, debug-only Timber, session restore) · `MainActivity` (edge-to-edge, session-driven root) |
| Navigation | `@Serializable` typed routes (`AppRoutes.kt`); auth graph (`AuthFlow`) + four-destination main shell (`SpendwiseMainShell`, bottom `NavigationBar`); root switches on session state (`SpendwiseApp`) |
| DI | Hilt: `CommonModule` (dispatchers), `SessionModule` (DataStore/TokenStore/Clock), `NetworkModule` (Json/OkHttp/Retrofit) |
| Network | `AuthInterceptor` (Bearer when signed-in), `ErrorInterceptor` (non-2xx → `ApiException` w/ tolerant problem-detail), `ProblemDetailDto`, debug-only redacted HEADERS logging; **no service interfaces** — nothing can call the backend |
| Money | `Money(BigDecimal, CurrencyCode)`; `CurrencyCode` open-value class; `BigDecimalSerializer` emits unquoted JSON numbers, exact-value decode |
| Session | `Session` sealed state (Restoring/SignedOut/SignedIn/Expired), `SessionManager`, `TokenStore` → `DataStoreTokenStore` (Preferences DataStore), `KeystoreCipher` AES/GCM/NoPadding |
| Design system | `SpendwiseTheme` (light-only M3 scheme from P3A tokens), `SwColors/SwSpacing/SwRadii` + a11y `TouchTargetMin`, typography skeleton (system font; Manrope asset lands in M2) |
| IDs | `AccountId/TransactionId/RecurringId/BudgetId/RuleId/ImportBatchId` value classes (AD-007 pattern) |
| Package layout | `app` · `core/common` · `core/designsystem` · `core/model` · `core/network` · `core/serialization` · `core/session` · `navigation` — feature/repo packages land with their milestones |

## Security evidence

- `usesCleartextTraffic="false"`; HTTPS-only base URL default
- Session DataStore (`files/datastore/`) excluded via
  `data_extraction_rules.xml` (cloud + device-transfer) and
  `full_backup_content.xml` (pre-S)
- Token stored only as AES/GCM ciphertext (keystore-backed key, 128-bit
  tag); passwords never persisted; only token + `expiresAt` + last username
- OkHttp logging debug-only, HEADERS level, `Authorization`/`Cookie`/
  `Set-Cookie` redacted; no body logging; no token logged anywhere

## Test results (JVM, `testDebugUnitTest`)

**36 tests · 0 failures · 0 errors · 0 skipped**

- `BigDecimalSerializerTest` (7): unquoted-number emission, exact value,
  scale/trailing-zero preservation, quoted-string tolerance, rejection
- `MoneyCurrencyTest` (4): known + unknown-valid codes, invalid-syntax
  rejection, amount/currency integrity
- `SessionManagerTest` (8): restore paths, expired-token discard +
  username retention, sign-in/out, authenticated-401 expiry
- `AuthInterceptorTest` (3, MockWebServer): header absent/present/
  not-overwritten
- `ErrorInterceptorTest` (5, MockWebServer): 2xx passthrough,
  problem-detail `fields`, body-less errors, IOException family
- `AppErrorMappingTest` (9): status/`fields` → `AppError`, tolerant DTO
- `SpendwiseAppTest` (androidTest, 4 Compose tests): session shell
  routing + four-destination navigation — **compiled but not executed**
  (no device/emulator: `adb devices` empty)

## Quality gates (all executed locally)

- `ASSEMBLE_DEBUG=PASS` (42 tasks)
- `UNIT_TESTS=PASS` (36/36)
- `LINT_DEBUG=PASS` — 0 errors, 18 warnings (all informational:
  version-availability notices + `mipmap-anydpi-v26` redundancy)
- `ANDROID_TEST_COMPILE=PASS` (`compileDebugAndroidTestKotlin`)
- `RELEASE_BUILD_OR_COMPILE=PASS` — `assembleRelease` unsigned, R8
  (minify+shrink) ran clean; release signing deferred to Human (Q4)
- `detekt` (warn-only, per §36): runs and reports; **0 findings** after
  project config (`config/detekt/detekt.yml`)
- `CONSOLE_OR_BUILD_BLOCKING_WARNINGS=0` (remaining console notes:
  Gradle-10 deprecation notice from plugins, configuration-cache reuse)

## Sentinel validation

`TEST_VERDICT=PASS` · `BLOCKERS=0` · `MAJORS=0` —
36/36 JVM tests executed and pass; instrumentation tests compile,
unexecuted (no device), recorded honestly.

## Argus review

`REVIEW_MODE=ISOLATED` — see
[../reviews/M0-ARGUS-REVIEW.md](../reviews/M0-ARGUS-REVIEW.md)

- `BLOCKER=0 · MAJOR=0 · MINOR=2 · NOTE=9`
- Findings: `Session.Expired` placeholder loses last-username on sign-out
  (documented M1 wiring gap, unreachable in M0); crypto round-trip
  (KeystoreCipher/DataStoreTokenStore) has no executable test yet
  (needs instrumentation/Robolectric). `ARGUS_VERDICT=PASS`

## Milestone report fields

```
ASSEMBLE_DEBUG=PASS
UNIT_TESTS=PASS
LINT_DEBUG=PASS
ANDROID_TEST_COMPILE=PASS
RELEASE_BUILD_OR_COMPILE=PASS
CONSOLE_OR_BUILD_BLOCKING_WARNINGS=0

BASE_URL_SOURCE=PROPERTY(-Pspendwise.api.base.url)→ENV(SPENDWISE_API_BASE_URL)→DEFAULT
DEFAULT_BASE_URL=https://example.invalid/
REAL_BACKEND_CALLED=NO
AUTH_INTERCEPTOR=PASS
PROBLEM_DETAIL_FOUNDATION=PASS
APP_ERROR_FOUNDATION=PASS

MONEY_TYPE=Money(BigDecimal,CurrencyCode)
BIGDECIMAL_JSON_SERIALIZER=PASS
FLOAT_DOUBLE_FOR_MONEY=NO
CURRENCY_TYPE=CurrencyCode value class (open ISO-4217, 3-letter syntax)
UNKNOWN_VALID_CURRENCY_SUPPORTED=YES

SESSION_MANAGER=PASS
SESSION_STATE_MODEL=Session{Restoring,SignedOut,SignedIn,Expired}
TOKEN_STORAGE=DataStore+Keystore AES/GCM ciphertext
ANDROID_KEYSTORE_AES_GCM=PASS
DATASTORE=PASS
PASSWORD_PERSISTED=NO
TOKEN_LOGGED=NO
BACKUP_PROTECTION=dataExtractionRules+fullBackupContent exclude files/datastore/

COMPOSE_APP_SHELL=PASS
DESIGN_SYSTEM_SKELETON=PASS
TOP_LEVEL_DESTINATIONS=4
HOME=FOUNDATION_ONLY
TRANSACTIONS=FOUNDATION_ONLY
ACCOUNTS=FOUNDATION_ONLY
MORE=FOUNDATION_ONLY
FINAL_FEATURE_UI_IMPLEMENTED=NO

BIGDECIMAL_TESTS=PASS
MONEY_CURRENCY_TESTS=PASS
SESSION_TESTS=PASS
AUTH_HEADER_TESTS=PASS
ERROR_MAPPING_TESTS=PASS
COMPOSE_UI_TEST_SOURCE=PASS
CONNECTED_INSTRUMENTATION_EXECUTED=NO (adb devices: none attached)

TEST_VERDICT=PASS
BLOCKERS=0
MAJORS=0

BACKEND_UNCHANGED=YES (8194daa, user-owned dirty docs unchanged)
REFERENCE_UNCHANGED=YES (5f9622c, clean)
PROTOTYPE_MODIFIED=NO
PRIVATE_DATA_ACCESSED=NO
```

## Boundary preservation

- Backend `D:\spendwise`: HEAD `8194daa` unchanged; same user-owned dirty
  docs as at M0 start — untouched.
- Reference `D:\app_spendwise\kotlin-accelerator-ai`: HEAD `5f9622c`,
  clean — read-only respected (only the standard Gradle wrapper binary was
  copied as the conventional bootstrap artifact).
- Prototype `D:\app_spendwise\spendwise-ui-prototype`: untouched
  (pre-existing uncommitted state is the Human's accepted baseline).
- Private fixtures `D:\java_spring_2_10_2026\private-bank-fixtures`:
  never accessed.
- Writes confined to `D:\app_spendwise\spendwise-android`.

## Deferred items (recorded, not silent)

- Manrope font asset bundling → M2 design-system milestone (skeleton
  typography uses the system family meanwhile).
- Crypto round-trip test for `KeystoreCipher`/`DataStoreTokenStore` →
  first instrumentation-capable milestone (needs device/Robolectric).
- Session-expired preserved-stack resume + draft restore → M1 (the sealed
  state and placeholder route exist; no auth can produce Expired in M0).
- Kotlin 2.4.x / OkHttp 5.5.x bumps → deliberate hold at AGP-9-embedded
  compiler generation (2.3.0 plugins) until a coordinated toolchain bump.

## Human gate

M0 is **READY_FOR_HUMAN_REVIEW**. Next milestone after approval:
`M1 — Authentication & Session`. Do not begin M1 without Human approval.

## Verdict

```
M0_READY=YES
M0_READY_FOR_HUMAN_REVIEW=YES
M0_HUMAN_APPROVED=NO
ANDROID_APP_DEVELOPMENT_STARTED=YES
SPENDWISE_ANDROID_STATUS=M0_READY_FOR_HUMAN_REVIEW
NEXT=HUMAN M0 REVIEW
```
