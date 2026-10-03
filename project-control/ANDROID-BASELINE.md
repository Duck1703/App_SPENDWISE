# Android Senior Reference Baseline

Evidence-backed audit of the senior Android reference project, produced by phase P1.
Every claim is anchored to repository evidence and tagged VERIFIED (seen directly in
source/config), INFERRED (reasonable but not directly proven), or NOT_EVIDENCED.

## 1. Audit identity

- Reference path: `D:\app_spendwise\kotlin-accelerator-ai`
- Branch: `master`
- HEAD: `5f9622c59ce602ce33c98ee633ba799f903b67e1`
- Audit date: 2026-10-04
- Audit mode: static, READ ONLY — no Gradle execution, no test execution, no app
  runtime, no modification of the reference
- Source-grounding rule: repository evidence only; remembered "best practices" are
  not evidence. Evidence precedence: source code > build config > tests >
  manifests/config > docs > comments. Conflicts recorded as REFERENCE_DRIFT
  (section 30), never silently reconciled.

## 2. Executive summary

- The reference is a gamified quiz/trivia app (`com.creative.kta`), not a finance
  app: questions, lifelines, money ladder, leaderboard, achievements, paywall.
- Modules: `:app`, `:core`, `:data`, `:domain`, `:secrets`, `:theme`,
  `:ui-components`, plus three `com.android.dynamic-feature` modules
  (`:feature:game`, `:feature:menu`, `:feature:paywall`) that depend **on** `:app`
  (inverted dependency; `settings.gradle.kts`).
- Layering is Clean-inspired: **interfaces live in `:domain`**
  (6 repositories + ~9 services + 3 managers), **implementations live in `:data`**.
  Domain is not pure Kotlin — it references `android.content.Context`, RevenueCat
  types, and Compose `@Stable` (drift, section 30).
- State management is a custom unidirectional pattern on top of a third-party
  micro-library **DRE-KT** (`dev.drekt.*`, `io.github.dantech0xff:dre-android:0.1.3`):
  pure `Reducer`s produce `ReduceResult(state, sideEffects, asyncOp)`; 5 of 6
  feature ViewModels extend `DreStoreViewModel`. Two ViewModels (Paywall, Splash)
  use plain `ViewModel` + `MutableStateFlow`.
- DI is **Koin 4.1.1**: per-layer modules + per-feature modules discovered at
  runtime by `Class.forName` over hardcoded class names (`FeatureRegistry`,
  `FeatureNavigationRegistry`) — because feature modules depend on `:app`, `:app`
  cannot reference their classes directly.
- Navigation: single `NavHost`, **type-safe `@Serializable data object` routes**,
  4 flat destinations, zero route arguments, zero deep links, no SavedStateHandle,
  no result passing. A fixed `FeatureNavigationCallbacks` interface carries all
  feature→app navigation — and **silently drops its declared arguments**
  (`AppNavHost.kt:30-32` ignores `category`/`isPractice`).
- Networking is **Supabase (postgrest + auth)** plus a Ktor client used only for a
  Gemini hint API. There is no generic REST layer; Spendwise's Spring Boot REST
  backend has no direct analogue here.
- Persistence is **DataStore preferences ×4 only**; no Room, no relational local
  store. Production `QuestionRepository` is bound to a bundled **mock** — the app
  ships local content.
- Errors: `kotlin.Result<T>` + `runCatching` everywhere remote; **no typed error
  hierarchy**; a string-sniffing `mapAuthError` maps exception text to hardcoded
  English UI strings; other surfaces pass raw `e.message` to UI.
- Testing is JVM-only and genuinely strong for pure logic: 52 test-source files,
  **0 instrumentation/Compose UI tests**. JUnit4 + MockK + Turbine +
  kotlinx-coroutines-test; reducers tested as pure functions.
- Quality gates exist but are deliberately toothless: detekt runs with
  `ignoreFailures = true`; JaCoCo verification thresholds exist per module but the
  aggregate report task never invokes verification (hollow gate).
- Documentation drift is significant: `docs/system-architecture.md` describes an
  abandoned ServiceLoader/fun-interface design; `CLAUDE.md` version claims and
  several facts are stale; trust code, not docs (section 30).
- For Spendwise the most valuable inheritances are **patterns**, not code:
  injected dispatchers, interface-in-domain repositories, type-safe routes,
  Route-creates-VM wiring, SharedFlow one-off events, pure-function domain logic
  with strong unit tests, design-token discipline.

## 3. Repository topology

Modules (`settings.gradle.kts`, `include` list — VERIFIED):

| Module | Type | Role | Deps on internal modules |
| --- | --- | --- | --- |
| `:app` | `com.android.application` | Entry, NavHost, splash, WorkManager workers, app-level Koin modules | `core`, `data`, `domain`, `secrets`, `theme`(api), `ui-components`(api) |
| `:core` | Android library (`kta.android.library`) | `UseCase`/`FlowUseCase` bases, `QzDispatchers`, `ScopeQualifier`, `FeatureRegistry`, navigation contracts + routes, `LevelConfig`, gate configs, `BadgeType`, utils | none (exports `dre-android`, `navigation-compose`, `kotlinx-serialization` as `api`) |
| `:domain` | Android library | Domain models, repository/service/manager **interfaces**, 16 use-case classes, `AuthErrorMapper`, `QuizGameConfig` | `core`, `secrets` |
| `:data` | Android library | Repository/service/manager impls, Supabase client factory, Ktor client, DataStore stores, Firebase Analytics/Remote Config, DTOs+mappers | `core`, `domain`, `secrets` |
| `:secrets` | Android library + CMake NDK | XOR-obfuscated native secrets (`libkta-secrets.so`) | none |
| `:theme` | Android library | `QzdsTheme`, 2-layer design tokens, 15 CompositionLocals, Material3 bridge | none |
| `:ui-components` | Android library | `Qzds*`/feature component catalog (~128 `@Composable`s) | `theme` (api), `core` |
| `:feature:game` | `com.android.dynamic-feature` | Gameplay FSM, screens, dialogs, VM | `app`, `core`, `domain`, `theme`, `ui-components` |
| `:feature:menu` | `com.android.dynamic-feature` | Menu + settings + leaderboard + onboarding (4 sub-packages) | `app`, `core`, `domain`, `theme`, `ui-components` |
| `:feature:paywall` | `com.android.dynamic-feature` | Premium purchase UI (6 files) | `app`, `core`, `domain`, `theme`, `ui-components` |
| `build-logic` | included build | 5 convention plugins | — |

- Feature modules are **install-time** dynamic features (`<dist:install-time/>`),
  not on-demand — the machinery exists only to invert the compile-time dependency
  (`feature/* → :app`), verified via manifests + `build.gradle.kts`.
- Non-module directories: `ai/` (AI-skill source bundle + codegen templates, not
  app code — VERIFIED), `config/detekt`, `docs/`, `graphics/` (screenshots),
  `scripts/`, `.claude/`, `.codex/` (vendored skills/agents).
- Source-set naming inconsistent: `:app` uses `src/main/java`, most modules use
  `src/main/kotlin` — trivial drift.

## 4. Platform/build baseline

Declared versions (`gradle/libs.versions.toml`, `app/build.gradle.kts`,
`core/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties` — DECLARED,
not latest):

| Item | Declared |
| --- | --- |
| AGP | `9.0.0` |
| Kotlin | `2.3.0` (Kotlin Android + `org.jetbrains.kotlin.plugin.compose`; built-in Kotlin via `android.builtInKotlin=true` in `gradle.properties`) |
| Gradle wrapper | `gradle-wrapper.properties` present (distributionUrl not re-quoted) |
| compileSdk / targetSdk / minSdk | `36 / 36 / 26` |
| Java/JVM | `VERSION_17` / `JvmTarget.JVM_17` |
| Compose BOM | `2026.01.00` |
| Compose compiler | Kotlin compose plugin (no KSP/KAPT anywhere — VERIFIED) |
| Navigation | `androidx.navigation:navigation-compose:2.9.6` (type-safe routes) |
| DI | `io.insert-koin` BOM `4.1.1` (core, android, androidx-compose, workmanager, test) |
| Backend SDK | `io.github.jan-tennert.supabase` BOM `3.3.0` (postgrest-kt, auth-kt) |
| HTTP | `io.ktor:ktor-client-*:3.4.0` (Android engine, content-negotiation, logging, mock) |
| Serialization | `kotlinx-serialization-json:1.10.0` + plugin |
| Persistence | `datastore-preferences/core:1.2.0`; `multiplatform-settings-no-arg:1.3.0` (declared, no direct code references — INFERRED for supabase session store) |
| Coroutines | `1.10.2` (+ `kotlinx-coroutines-test`) |
| State library | `io.github.dantech0xff:dre-android:0.1.3` — **hardcoded in `core/build.gradle.kts:31`, bypasses the version catalog** (drift) |
| Imagery/effects | `coil3:3.3.0`, `dev.chrisbanes.haze:1.7.1`, `kotlinx-collections-immutable:0.3.8` (hardcoded version) |
| Billing | `com.revenuecat.purchases:purchases:9.28.1`, `slide-to-unlock:1.0.2` |
| Firebase | BOM `34.8.0` (analytics, config; **no crashlytics**) |
| Auth UX | `androidx.credentials:1.5.0` + `credentials-play-services-auth`, `googleid:1.2.0` |
| Background | `androidx.work:work-runtime-ktx:2.11.0` |
| Other | `core-splashscreen:1.2.0`, `timber:5.0.1`, `activity-compose:1.12.2`, `lifecycle-runtime-ktx:2.10.0`, `compose.material:1.10.1` + `material3:1.4.0` (both M2 and M3 present — drift), `ui-text-google-fonts:1.10.1` |
| Testing | `junit:4.13.2`, `androidx.test.ext:junit:1.3.0`, `espresso-core:3.7.0`, `mockk:1.14.7`, `turbine:1.2.1`, `compose-ui-test-junit4` (declared, unused) |
| Static analysis | `detekt:1.23.7` + `detekt-compose-rules:0.4.22` + `detekt-formatting` (all formatting rules disabled except `NoUnusedImports`) |
| Coverage | JaCoCo `0.8.12` via `kta.jacoco` convention + `gradle/jacoco-aggregation.gradle.kts` |

Build organization: `pluginManagement { includeBuild("build-logic") }`,
`repositoriesMode = FAIL_ON_PROJECT_REPOS`, google repo content-filtered.
Convention plugins (`build-logic/convention/...` — VERIFIED):
`kta.android.library` (compileSdk 36, minSdk 26, Java 17, detekt, consumerProguard),
`kta.android.feature` (dynamic-feature + `enableUnitTestCoverage`),
`kta.android.compose`, `kta.jacoco` (per-module `jacocoTestReport` +
`jacocoVerification`, default threshold `0.80`, `extra["jacocoMinCoverage"]`
override — `:app` sets `0.65`), `kta.detekt` (`ignoreFailures = true`,
`autoCorrect = true`, parallel).

Build types: `debug` / `release` only — no flavors. Release: minify + shrink +
`proguard-rules.pro`; **signing silently falls back to debug keys** when
`KEYSTORE_PASSWORD` env is unset (`app/build.gradle.kts:72-76` — drift/risk).
Feature flags at build time via `local.properties` → `BuildConfig` fields:
`PREMIUM_GATE_ENABLED`, `DAILY_CHALLENGE_ENABLED` (default `false`),
`REVENUECAT_API_KEY`.

## 5. Application bootstrap

Verified chain (all in `app/src/main/java/com/creative/kta/`):

```
AndroidManifest (.App, .LauncherActivity, Theme...Splash)
→ App.onCreate                                   App.kt:25-73
    ├─ Timber.plant(DebugTree)                   debug only, :29-31
    ├─ RevenueCat configure                      gated by BuildConfig.PREMIUM_GATE_ENABLED, :37-50
    ├─ startKoin { workManagerFactory();         :52-66
    │    modules(coreModule, dataModule, domainModule,
    │             viewModelModule, notificationModule, appConfigModule)
    │    modules(FeatureRegistry.discoverFeatureModules())   ← reflection
    │  }
    ├─ NotificationChannelManager.createChannels :70
    └─ ScheduleChargingSyncUseCase(WorkManager).invoke()     :72 (manual ctor, not injected)
→ LauncherActivity.onCreate                      LauncherActivity.kt:25-36
    ├─ installSplashScreen() (androidx core splash)
    ├─ enableEdgeToEdge()
    └─ setContent { AppTheme { AppContent() } }
        → AppNavHost(startDestination = SplashRouteData)     AppNavHost.kt:48-50
            → SplashRoute → SplashViewModel (simulated progress ~3-4s) → MenuRouteData
```

- `LauncherActivity` injects `NotificationScheduler`, `LocalDataManager`,
  `AudioManager` via `by inject()` — UI components may depend on domain services
  directly (VERIFIED).
- Manifest removes `WorkManagerInitializer` (`tools:node="remove"`) so Koin
  `workManagerFactory()` supplies workers — VERIFIED.
- Firebase/Supabase have **no explicit init** — lazy Koin singles; Firebase via
  `FirebaseInitProvider`/`google-services.json` — VERIFIED (google-services.json
  is gitignored, absent from checkout).

## 6. Architecture model

Evidence supports **MVVM + Clean-inspired layering + an in-house
reducer/effect/async-op pattern (DRE-KT)**, with deliberate exceptions:

- `UI (Routes/Screens/Dialogs) → I*ViewModel → UseCase | Manager | Repository →
  Service/Store`. ViewModels dispatch actions into pure reducers; reducers return
  `(state, sideEffects, asyncOp)`; fire-and-forget `SideEffectHandler`s bridge to
  timers/audio/analytics/navigation/dialog-queue; a VM-owned `executeAsyncOp`
  performs I/O and dispatches result actions (`GameViewModel.kt:131-170`).
- Not every screen uses the full machinery: `PaywallViewModel` and `SplashViewModel`
  are plain `ViewModel`s with `MutableStateFlow` + `SharedFlow` — VERIFIED.
- Layer rules enforced socially, not mechanically: no ArchUnit/Konsist tests
  (NOT_EVIDENCED). Breaches exist (section 30): RevenueCat types in domain and UI
  state, `Context` in domain service API and a ViewModel, wire DTOs doubling as
  domain models.
- `:domain` is an **Android library**, not a pure Kotlin module — it depends on
  Android types and on `:secrets` (`domain/build.gradle.kts`).

## 7. Representative flow traces

### FLOW A — READ: Leaderboard (VERIFIED end-to-end)

```
MenuTopCompose → viewModel::navigateToLeaderboard
→ MenuViewModel dispatch(MenuAction.NavigateToLeaderboard)
→ MenuReducer → EnqueueDialog(MenuDialogState.Leaderboard)
→ MenuDialogSideEffectHandler → MenuDialogQueueController.enqueue
→ dialogState StateFlow → MenuDialogLayer → LeaderboardPopup
→ koinViewModel<LeaderboardViewModel>() typed as ILeaderboardViewModel
→ init { loadLeaderboard() } → dispatch(LoadLeaderboard)
→ LeaderboardReducer → asyncOp = FetchLeaderboard
→ executeAsyncOp → repository.cachedList (volatile cache) first
→ LeaderboardRepositoryImpl.fetchLeaderboard(limit=100)
   data/repo/leaderboard/LeaderboardRepositoryImpl.kt:21
   withContext(dispatchers.io) { runCatching {
     supabaseClient.from("leaderboard").select()
       .decodeList<LeaderboardEntry>().take(limit) } }
   // limit applied client-side after decoding — drift
→ dispatch(FetchSuccess / FetchEmpty / FetchFailed(hasCachedData))
→ LeaderboardState → sealed LeaderboardUiState {Empty|Success|Error}
→ PullToRefreshBox + LeaderBoardList (LazyColumn)
```

### FLOW B — MUTATION: Sign in with email (VERIFIED end-to-end)

```
QzdsEmailAuthDialog submit → viewModel::signInWithEmail
→ MenuViewModel.executeAsyncOp(SignInWithEmail)
→ SignInWithEmailUseCase(EmailCredentials)              domain/usecase/auth/
    → authService.signInWithEmail(email, password)      domain interface
        AuthServiceSupabaseImpl.kt:134 — withContext(io){ runCatching {
          supabaseClient.auth.signInWith(Email){...} }.fold(...) }
    → on success: authService.loadAuthState() → Authenticated
        → syncManager.syncUserProfile()                 SyncManagerImpl.kt:30-65
          fetchAppUser → mergeProfiles (last-write-wins) →
          localDataManager.saveUserProfile + userService.upsertUser
→ .fold → dispatch(EmailSignInSuccess) | dispatch(EmailSignInFailure(mapAuthError(e)))
→ reducer → MenuDialogState.EmailAuth(errorMessage) → dialog status text
```

### FLOW C — COMPLEX STATE: Menu → Game → Answer → Overlay (VERIFIED)

```
MenuViewModel.startNewGame() → dispatch(MenuAction.StartNewGame)
→ MenuReducer → effects [PlayButtonClick, DismissDialog, NavigateToGame]
→ MenuNavigationSideEffectHandler → _menuUiEvents.emit(NavigateToGame)
→ MenuRoute LaunchedEffect collector → onNavigateToGame → navController.navigate(GameRouteData)
→ GameRoute LaunchedEffect → gameViewModel.startNewGame() → dispatch(StartGame)
→ GameStateMachine → asyncOp=LoadGame
→ executeAsyncOp → GameAsyncOpExecutor.loadGame() on dispatchers.io
   (questionRepository.newGameQuestions() → CurrentGameSession)
→ dispatch(GameLoaded) → phase=Playing + [PlayFeedback, StartTimer]
→ TimerSideEffectHandler → GameTimer (FACTORY scope) → _timerData → timerUiState
→ Answer tap → dispatch(SubmitAnswer) → guard phase is Playing
   → phase=Answered(PENDING) + [PauseTimer, feedback, analytics] + asyncOp=ProcessAnswer
→ AnswerProcessor + delay(1500ms) → dispatch(AnswerRevealed)
   → phase=Answered(REVEALED) + asyncOp=ShowExplanation → delay(1000ms)
   → dispatch(ShowExplanation) → EnqueueOverlay(Explanation)
→ OverlaySideEffectHandler → GameOverlayQueueManager.currentOverlay (StateFlow)
→ dialogState = combine(state, overlay) → buildGameDialog(...) (suspend — fetches
   localized content from TextContentRepository)
→ GameDialog.AIExplanation → GameDialogHost (DialogScaffold + AnimatedVisibility)
→ Dismiss → dispatch(DismissOverlay) → correct → LoadNextQuestion → phase=Playing
   | wrong → phase=GameOver + [ClearOverlays, StopTimer, EnqueueOverlay(GameEnded)]
→ onBackToMenu → [StopTimer, RecordGameStats, NavigateToMenu]
   → NavigationSideEffectHandler → gameEvents.emit → onNavigateBack → navigateUp()
```

## 8. Compose UI architecture

VERIFIED patterns:

- **Route composables own the VM**: `val vm: IMenuViewModel = koinViewModel<MenuViewModel>()`
  inside `MenuRoute.kt:40`; same for `GameRoute`, `SplashRoute`,
  `SettingsPopup`, `LeaderboardPopup`, `OnboardingOverlay`. Nav callbacks arrive
  as lambda params wired to `FeatureNavigationCallbacks`. **Deviation**:
  `PaywallRoute.kt:19` takes `viewModel: PaywallViewModel = koinViewModel()` as a
  parameter default — drift.
- **State collection**: `collectAsStateWithLifecycle()` exclusively (25 sites);
  zero bare `collectAsState` in source.
- **One-off events**: `MutableSharedFlow` (unbuffered) → `SharedFlow`, collected
  in `LaunchedEffect(Unit)` at the Route; no `Channel.receiveAsFlow` anywhere.
- **Screens**: mostly stateless content + `*UiState` + callbacks — but
  `MenuScreenContent`/`MenuDialogLayer` receive the **whole `IMenuViewModel`**
  (inconsistent input style, drift).
- **Stability**: `@Immutable`/`@Stable` on UiState/dialog classes and some
  `I*ViewModel` interfaces; `kotlinx-collections-immutable` in state. Drift:
  `IGameViewModel`/`ISettingViewModel` unannotated.
- **Previews**: ~93 `private` previews (56 in ui-components, 37 in features),
  sample-data-driven; `PreviewMenuViewModel` fake implements `IMenuViewModel` —
  the one concrete payoff of VM interfaces (VERIFIED).
- **Effects**: `LaunchedEffect`/`DisposableEffect` for event collection,
  `FLAG_KEEP_SCREEN_ON`, permission launchers; `rememberSaveable` in form fields;
  `derivedStateOf` in animated counters; `mutableStateOf` confined to leaf UI.
- **Animation is mandated** (docs) and pervasive (~100+ animation-API call sites;
  counts are grep-approximate):
  `AnimatedContent`/`AnimatedVisibility`, `animate*AsState`, `spring/keyframes`,
  `QzdsMotionPresets`. Nav transitions are actually **fade-only** despite comments
  claiming slide — drift.
- **Visual effects**: `dev.chrisbanes.haze` blur (~200+ token references across
  sources) with SDK≥33 gate + dim fallback (`HazeUtils.kt`); `coil3.AsyncImage`
  everywhere — **including local drawables** (non-idiomatic, drift).
- Lists: `LazyColumn` ×3; `LeaderBoardList` uses positional `key = { it }` —
  weak-key drift vs stable `key(...)` elsewhere.

## 9. State management

Two coexisting patterns (VERIFIED):

1. **DRE-KT** (5/6 feature VMs): `DreStoreViewModel<State, Action, Effect, AsyncOp>`
   + pure `Reducer` (`GameStateMachine`, `MenuReducer`, `SettingReducer`,
   `LeaderboardReducer`, `OnboardingReducer` — standalone reducer files living
   beside `*Contract.kt` files that declare the State/Action/Effect/AsyncOp
   vocabulary).
   `ReduceResult(state, sideEffects: List, asyncOp? = null)` — **at most one
   async op per reduce**. Reducers are pure and crash via `error()` on unhandled
   actions. State machines model phases explicitly (`GamePhase`: 5 states;
   `GameUiOverlay`: 9 states — docs claim 8, drift).
2. **Plain MVVM** (`PaywallViewModel`, `SplashViewModel`): `MutableStateFlow` +
   `update { copy() }` inside `viewModelScope.launch`, `MutableSharedFlow` events.

Cross-cutting: derived `StateFlow`s via `map/combine + distinctUntilChanged +
stateIn(viewModelScope, WhileSubscribed(5_000), initial)` — consistent.
ViewModel-owned imperative state exists *outside* DRE state: `_timerData`,
`_gameEvents`, `GameOverlayQueueManager` (ReentrantLock + LinkedList),
`MenuDialogQueueController` — the reducer pattern does NOT cover all state.
No `SavedStateHandle` anywhere — process-death restoration is NOT_EVIDENCED.

## 10. ViewModel conventions

- Interface-backed: `IGameViewModel`, `IMenuViewModel`, `ISettingViewModel`,
  `ILeaderboardViewModel`, `IMenuOnboardingViewModel`, `ISplashViewModel` —
  **not** `PaywallViewModel` (drift). Interfaces enable previews/fakes.
- Constructor injection incl. the reducer itself; deps are domain interfaces +
  `QzDispatchers` (Game uses `dispatchers.mainImmediate` as `dispatchContext`;
  Menu relies on library default — minor drift).
- `init` blocks: collect external flows → `dispatch(...DataChanged)` actions
  (Menu, Settings, Onboarding) or eager load (Leaderboard, Splash). Game has no
  init — start driven by Route `LaunchedEffect`.
- Public API = thin `dispatch(action)` methods + `StateFlow`s + `SharedFlow`
  events. No Compose `State` in VMs.
- Drift: `SettingViewModel` injects `context: Context`
  (`@SuppressLint("StaticFieldLeak")`) and resolves strings/drawables in the VM —
  localization inside the VM, unique to it.
- Drift: `OnboardingSideEffectHandler` is DI-constructed while all other handlers
  are built inside VM constructors.

## 11. Navigation

- `androidx.navigation:navigation-compose:2.9.6` with **type-safe routes**:
  `@Serializable data object`s `SplashRouteData`, `MenuRouteData`,
  `GameRouteData`, `PaywallRouteData` (`core/navigation/Routes.kt`,
  `app/ui/navigation/SplashNavigation.kt`) — VERIFIED.
- Single flat `NavHost` in `:app` (`AppNavHost.kt`), 4 destinations; paywall
  registers a `dialog<PaywallRouteData>` destination.
- Feature destinations self-register: each feature implements
  `FeatureNavigationProvider → FeatureNavigationRegistrar.registerComposable(callbacks)`;
  discovered by **reflection over hardcoded FQCNs**
  (`FeatureNavigationRegistry.KNOWN_NAVIGATION_PROVIDERS`), invoked inside
  `remember {}` in `AppNavHost`.
- Cross-feature navigation flows through the fixed `FeatureNavigationCallbacks`
  interface (5 methods). **Declared args are dropped**: `navigateToGame(category,
  isPractice)` ignores both params — `GameRouteData` is a `data object` that
  cannot carry them (`AppNavHost.kt:30-32`) — dead plumbing, drift.
- No nested graphs, deep links, bottom nav, route args, `SavedStateHandle`, or
  `previousBackStackEntry` result passing — VERIFIED absent.
- Back behavior: `navigateUp()`; `MenuRoute` swallows back (`BackHandler{}`);
  `GameRoute` maps back to `EXIT_GAME` dialog.
- Transitions: `NavTransitions` fade `tween(300)` via `QzdsPrimitiveMotion.LONG`;
  comments say "slide" — drift.

## 12. Dependency injection

- **Pattern**: constructor DI, Koin modules per layer (`coreModule`, `dataModule`,
  `domainModule`, `viewModelModule`, `notificationModule`, `appConfigModule`) +
  per-feature `internal` modules (`gameFeatureModule`, `menuFeatureModule`,
  `paywallFeatureModule`) — VERIFIED.
- Registration: `startKoin` in `App.onCreate`; feature modules contributed via
  `FeatureModuleInitializer.koinModules` discovered by `Class.forName`.
- Bindings: domain interfaces → data impls as `single`; use cases as `factory`;
  ViewModels via `viewModel {}`/`viewModelOf`; workers via `worker<>`.
- Qualifiers: `enum class ScopeQualifier : Qualifier { SINGLETON, FACTORY }` for
  injected `CoroutineScope`s (`coreModule`); `AIHintProviderType {GEMINI, LOCAL}`
  for strategy providers — VERIFIED.
- ViewModel retrieval: `koinViewModel<Concrete>()` inside Routes, typed to `I*`
  interfaces; `by inject()` in Activity.
- Reducers/`GameAsyncOpExecutor`/`AnswerProcessor` are constructed inline, not
  DI-bound — deliberate for pure classes (INFERRED intent).
- `koin-test` deps declared but **no `checkModules`/module-verification tests
  exist** — DI graph unverified (NOT_EVIDENCED).
- DI PATTERN vs LIBRARY: the *pattern* (constructor injection + layered modules +
  qualifiers + testable seams) is the transferable asset; Koin itself is a
  candidate, not a locked choice (P4 decides).

## 13. Networking

- **Supabase-kt 3.3.0** is the primary "network" layer:
  `SupabaseClientFactory.createSupabaseClient(url, key) { install(Auth);
  install(Postgrest) }` — only two plugins; no Realtime/Storage; default engine
  (`data/supabase/SupabaseClientFactory.kt`) — VERIFIED.
- Query style = postgrest DSL: `from("users").select { filter { eq(...) } }
  .decodeSingleOrNull<SupabaseUser>()`, `upsert { onConflict }`,
  `insert`, `order(...).limit(n)` — VERIFIED in `UserServiceSupabaseImpl`,
  `LeaderboardRepositoryImpl`, `LocalDailyChallengeRepository`.
- URL/key sourced from **NDK-obfuscated secrets** (`SecretManager` →
  `NativeSecretBridge` JNI → `libkta-secrets.so`), generated by
  `scripts/encode-secrets.py` XOR-encoding `local.properties` values at build
  time — mechanism VERIFIED (no values quoted).
- **Ktor `HttpClient(Android)`** exists for exactly one consumer — the Gemini
  hint API (`GeminiHintProvider.callGeminiApi`, POST + `x-goog-api-key` header +
  `GeminiRequest` DTO); ContentNegotiation(JSON), `HttpTimeout` 10s/5s, Timber
  `LogLevel.BODY` logging **debug only** (`DataModule.kt:77-97`). Manual retry:
  `repeat(maxRetries + 1)` catching per-attempt `Exception`.
- The Gemini API key comes from **Firebase Remote Config** at runtime, not the
  secrets pipeline — inconsistent secret sourcing (drift).
- Pagination: `fetchLeaderboard(limit)` decodes the whole table then `.take(limit)`
  client-side (drift/perf note); no generic pagination abstraction.
- File upload/download: NOT_EVIDENCED.
- Base-URL/environment handling: single Supabase project via secrets; no
  multi-environment config.
- For Spendwise: **there is no Retrofit-style REST layer to reuse** — the backend
  is a BaaS SDK. The transferable pattern is *service interface in domain + impl
  in data + injected client factory + typed DTOs*; HTTP stack selection is a P4
  decision informed by the P2 contract audit.

## 14. Repository/data sources

- Interfaces in `domain/repository/*` + `domain/service/*` + `domain/manage/*`
  (+ `domain/local/LocalDataManager`): `QuestionRepository`,
  `LeaderboardRepository`, `GameHistoryRepository`, `DailyChallengeRepository`,
  `AchievementRepository`, `TextContentRepository`, `AuthService`, `AIHintService`,
  `AnalyticsManager`, `AudioManager`, `HapticManager`, `SubscriptionService`,
  `SyncManager`, `NotificationScheduler`, `NotificationPermissionChecker`,
  `UserProfileManager`, `AchievementManager`, `SubscriptionManager`,
  `LocalDataManager` — VERIFIED.
- Impls in `data/repo/*`, `data/service/*`, `data/local`, `data/manage`:
  `LeaderboardRepositoryImpl`, `LocalGameHistoryRepository`,
  `LocalDailyChallengeRepository`, `LocalAchievementRepository`,
  `LocalTextContentRepositoryImpl`, **`MockQuestionRepository`**,
  `AuthServiceSupabaseImpl`, `UserServiceSupabaseImpl`,
  `RevenueCatSubscriptionService`, `SyncManagerImpl`, `FirebaseAnalyticsManager`,
  `FirebaseRemoteConfigManager`, `AIHintServiceImpl` (+`Gemini/Local` providers),
  `UserProfileManagerImpl`, `AchievementManagerImpl`, `SoundPoolAudioManager`,
  `VibratorHapticManager`, `LocalDataStoreManagerImpl` — VERIFIED.
- `LocalDataStoreManagerImpl`, `LeaderboardRepositoryImpl` trace — see flows §7.
- Return conventions are **mixed** (drift): `Result<T>` for remote ops, `Flow<T>`
  for observable local state, plain `suspend` for local writes, `T?`
  null-on-error for `fetchAppUser`/`loadAuthState`.
- Cache: ad-hoc — `@Volatile cachedEntries` inside `LeaderboardRepositoryImpl`;
  `repository.cachedList` consulted by the ViewModel before fetching (cache
  policy lives partly in the VM — questionable).
- Offline-first / conflict resolution: only `SyncManagerImpl.mergeProfiles`
  (last-write-wins on one field) — narrow, INFERRED adequate for its use case.
- Interface-location drift: `UserService`, `RemoteConfigManager`,
  `AIHintProvider` interfaces live in `:data` while siblings live in `:domain`.

## 15. DTO/domain/UI mapping

- **Conflation exists**: `LeaderboardEntry`, `DailyChallengeScore`,
  `GameHistoryEntry` are `@Serializable` snake_case wire models declared **in
  `:domain`** — the Supabase row DTO and domain model are the same class (drift).
- **Proper separation also exists**: `SupabaseUser` (data DTO) →
  `toAppUser()` → `AppUser` (data) → `UserProfileLocal` (domain) →
  `toUserProfile()` → `UserProfile` (domain); `UpsertUserRequest` ←
  `UserProfileLocal` — explicit mappers in data (`data/service/user/*`) —
  VERIFIED.
- UI mapping: feature ViewModels map domain → dedicated `*UiState`
  (`LeaderboardEntry.toUserProfileUiState()` inside the leaderboard VM) — the UI
  rarely sees raw wire models. **Exceptions (drift)**: `PaywallUiState` carries
  RevenueCat `Offerings`/`Package`; `QuizData` (`@Serializable` domain) flows
  into game session state unmapped.
- Spendwise implication (recorded as constraint §33): backend DTOs must not
  become UI models; the reference itself shows both the cost of conflation and a
  working mapper style.

## 16. Persistence/cache

- **DataStore preferences only — 4 stores** (all `private val Context.x by
  preferencesDataStore(...)`): `quiz_local_preferences` (settings+profile+onboarding
  via `LocalDataStoreManagerImpl`), `game_history` (JSON `List<GameHistoryEntry>`
  in one string key, cap 50), `daily_challenge` (last date/score/count),
  `achievements` (JSON id set + timestamp map) — VERIFIED.
- No Room/SQLDelight/files/EncryptedSharedPreferences — VERIFIED absent.
- JSON-blob-in-preferences is the de facto structured persistence technique —
  workable for small lists, no querying.
- Writes wrapped in `withContext(dispatchers.io)` in most impls (drift: 3 impls
  skip dispatcher injection).

## 17. Coroutines/Flow

- **`QzDispatchers { io, main, mainImmediate }`** injected into services,
  repositories, providers, managers — VERIFIED; **no raw `Dispatchers.IO`**
  references in data/domain main sources.
- `withContext(dispatchers.io)` wraps I/O uniformly.
- Injected scopes: `ScopeQualifier.SINGLETON` app scope (remote-config init,
  subscription refresh) and `FACTORY` (GameTimer) — VERIFIED via `CoreModule`.
- Drift: `VibratorHapticManager` self-creates `CoroutineScope(SupervisorJob())`
  — bypasses the qualifier convention, never cancelled.
- Flow style: `.map{}.distinctUntilChanged().stateIn(viewModelScope,
  WhileSubscribed(5_000), initial)` pervasive; `combine` for multi-source state;
  `MutableSharedFlow` one-off events; `callbackFlow` absent; `shareIn` absent in
  data/domain.
- Cancellation relies on structured concurrency (viewModelScope/injected scopes);
  no manual `Job` juggling.

## 18. Error handling

- Dominant: `runCatching {}.fold({ Result.success }, { Result.failure })` around
  remote calls, logged via Timber — VERIFIED.
- **No custom exception types, no sealed error hierarchy** — errors degrade to
  `Throwable`/`String` quickly — VERIFIED.
- `mapAuthError(e)` (`domain/usecase/auth/AuthErrorMapper.kt`): string-matching
  on `e.message.lowercase()` → hardcoded English strings; applied **in the
  ViewModel**, not inside use cases; only exists for auth — leaderboard/sync/
  paywall surfaces receive raw `e.message` — inconsistent (drift).
- RevenueCat path throws; `PaywallViewModel` catches and stores `errorMessage`.
- `LocalGameHistoryRepository` catches JSON parse errors, logs `Timber.w`, and
  degrades to `emptyList()` (`LocalGameHistoryRepository.kt:105-112`) — silent
  to callers/users though logged.
- `SyncState.Error(message)` is the only structured error carrier.
- Spendwise needs a typed error model driven by the backend contract (P2); the
  reference demonstrates what *not* to standardize on (message-sniffing).

## 19. Loading/empty/success modeling

Mixed conventions per screen (drift — no single UiState template, VERIFIED):

- `LeaderboardUiState` — the only true sealed tri-state: `Empty | Success(data,
  isRefreshing) | Error(message)`.
- `MenuContentUiState` — flat class + `isLoading` flag; `empty` sentinel.
- `GameScreenUiState` — content-only; loading implicit in
  `session.isInitialized()`.
- `PaywallUiState` — `isLoading`, `isPurchasing`, nullable `offerings`,
  `errorMessage` flags.
- `SettingScreenUiState` — no loading/error fields at all.
- Dialog error/success strings are carried inside dialog state
  (`MenuDialogState.EmailAuth(mode, errorMessage, successMessage)`), rendered by
  `QzdsEmailAuthStatusMessages` — no inline per-field errors.
- Refresh semantics: `isRefreshing` inside `Success` (leaderboard) — reasonable
  pattern.
- For Spendwise: an explicit loading/content/error convention is needed; the
  reference is a catalog of options, not a standard (ADAPT).

## 20. Design system

- `QzdsTheme` (`:theme`): **2-layer tokens** — primitives
  (`QzdsPrimitiveColors/Spacing/Shape/Motion/Opacity/IconSize/Elevation/Border`)
  + semantic/component tokens exposed through **15 CompositionLocals**, bridged
  into `MaterialTheme` (`toMaterial3ColorScheme()` + Be Vietnam Pro typeface);
  dark + light palettes both implemented; compact-screen font scaling via custom
  `LocalDensity` (has a unit test) — VERIFIED.
- Custom type scale (`h1..h5`, `caption1/2+Bold`, `buttonGiant..Tiny`) — non-M3
  naming.
- `:ui-components`: ~128 `@Composable`s; **~24 public `Qzds*`-prefixed
  components** (docs claim "55+" — overstated, drift) plus feature components
  (`QuestionPanel`, `AnswerOption`, `LeaderBoardList`...). Stateless API style:
  `modifier` first, dedicated `*UiState` data classes, trailing callbacks, slot
  params where needed; 56 previews; `Modifier.qzds{Shimmer,Pulse,...}` effect
  modifiers.
- Both `compose.material` (M2) and `material3` on the classpath — mixed (drift).
- Accessibility: `contentDescription`s present in spots; no systematic a11y
  layer — NOT_EVIDENCED beyond incidentals.
- For Spendwise: the *discipline* (primitive→semantic→component tokens +
  previews + motion constants) transfers; the quiz-specific visual language does
  not.

## 21. Forms/input handling

- Centralized **in the reducer**: `MenuReducer` validates email regex +
  `MIN_PASSWORD_LENGTH = 6` + confirm-match for sign-in/up/reset/OTP flows;
  violations return dialog-state updates (`MenuDialogState.EmailAuth(mode,
  errorMessage)`) — VERIFIED.
- Field widgets: `OutlinedTextField` + `KeyboardType` + `ImeAction` +
  FocusManager traversal + `PasswordVisualTransformation` + `enabled =
  !isLoading`; `rememberSaveable` for field values — VERIFIED.
- Single top-of-form error/status message (`QzdsEmailAuthStatusMessages`), no
  per-field `isError` — limited but consistent.
- Relevance: Spendwise transaction forms (amount, note, account, date) benefit
  from reducer-side validation + submit-flow modeling; per-field errors are a
  gap to design better (P3/P4).

## 22. Authentication/session/security

- Supabase auth-kt: email/password sign-in/up, password reset via email OTP
  (`resetPasswordForEmail` → `verifyEmailOtp(RECOVERY)` → `updateUser`), Google
  sign-in via **Credential Manager + `GetGoogleIdOption` + SHA-256 nonce** →
  `auth.signInWith(IDToken)` (`AuthServiceSupabaseImpl.kt:77-118`) — VERIFIED.
- Session model: `sealed class AuthState { Guest | Authenticated(AuthUser) }`;
  `authState: Flow<AuthState>` maps `auth.sessionStatus`; `loadAuthState()`
  returns `AuthState?` — VERIFIED.
- Token storage/refresh: delegated to supabase-kt internals (declared
  `multiplatform-settings-no-arg` presumably backs session persistence —
  INFERRED); **no encrypted storage** (no Keystore/EncryptedSharedPreferences) —
  VERIFIED absent. Profile/settings in plain DataStore.
- Secrets: NDK XOR obfuscation pipeline (`scripts/encode-secrets.py` → generated
  `secrets.cpp`, gitignored) — obfuscation not encryption; `crypto.py` encrypts
  the local.properties *sample* with AES-GCM/PBKDF2.
- **Potential secret risk**: `local.properties.sample.enc` is a committed
  encrypted blob (POTENTIAL_SECRET_RISK=repo-root/local.properties.sample.enc —
  encrypted, no plaintext exposed). Gemini API key delivered via Remote Config —
  design smell.
- Debug logging leaks PII: `AuthUser`, full profiles, remote-config values logged
  at debug level (`SyncManagerImpl.kt:42-51`) — flag.
- Spendwise: auth/session decisions are **not** transferable — Supabase auth !=
  the Spring Boot backend's contract. DEFER to P2/P3.

## 23. Testing strategy

- **52 files under `src/test` across all modules; 0 under `androidTest` —
  VERIFIED**. JVM-only testing; Compose UI tests declared as deps but absent.
- Per module: app 2 (workers), core 5 (incl. `TestDispatchers` util), data 16
  (repos/services/managers), domain 8 (models + 6 auth use-cases), game 13
  (reducer + VM + handlers + utils), menu 5 (reducer + VMs), **paywall 0**,
  secrets 1, theme 1, ui-components 1 (incl. `TestDispatchers` helper in core;
  rows sum to 52).
- Style: JUnit4 + MockK + Turbine + `runTest`/`StandardTestDispatcher` +
  `Dispatchers.setMain`; backtick test names; `// region` organization; no
  Given/When/Then DSL; **no fakes — mocks only**.
- Documented MockK+`Result` pitfall: use cases with `Result<T>` returns are
  tested as **real instances backed by mocked service interfaces** — VERIFIED in
  `MenuViewModelTest`/`SignInWithEmailUseCaseTest`.
- Reducer tests are the strongest asset: `sut.reduce(state, action)` asserted on
  `ReduceResult.state/sideEffects/asyncOp` (~24 cases in `GameStateMachineTest`,
  all validation branches in `MenuReducerTest`) — pure-function testing pays off.
- Weak spots: `AuthServiceSupabaseImplTest` is a single `assert(true)`
  "documentation test"; `LeaderboardRepositoryImplTest` admits needing
  integration tests; `FeatureRegistryTest`/`FeatureNavigationRegistryTest` only
  verify graceful `ClassNotFoundException` on JVM (features absent) — they test
  nothing real. No ArchUnit/Konsist enforcement.
- Coverage plumbing: per-module `jacocoVerification` thresholds exist
  (`jacocoMinCoverage`, default 0.80, app 0.65) **but the aggregate
  `jacocoReportAll` task never invokes verification** — the CI "coverage gate"
  produces reports only (hollow gate — drift).
- TEST_PATTERN_PRESENT vs TEST_COVERAGE_PROVEN: patterns are present and good;
  runtime pass rates/coverage are **not proven** by static audit.

## 24. Modularization

- Hybrid: **layer modules** (`:core`, `:domain`, `:data`) + **dynamic feature
  modules** + **shared UI modules** (`:theme`, `:ui-components`) + **secrets**.
- Why dynamic features: **not delivery** — all three are install-time; the real
  reason is inverted compile-time dependency (feature → `:app`), enabling a
  plugin-style registry. Cost: reflection discovery with hardcoded FQCNs +
  R8 keep rules + features can't be type-referenced by `:app`.
- `:feature:menu` actually bundles **four** features (`feature.menu`,
  `feature.settings`, `feature.leaderboard`, `feature.onboard` packages) —
  module≠feature (drift-ish).
- Boundary quality: decent interface separation domain↔data, but enforcement is
  convention-only (no arch tests); leaks documented (section 30).
- Assessment for Spendwise: the module count and dynamic-feature machinery are
  **over-engineered** for a first finance client. The transferable idea is
  *boundaries with a purpose* (contracts in a domain-ish layer, impls in data,
  UI per feature) at much lower ceremony — likely 1–3 Gradle modules at M0.
  "More modules ≠ more senior" — REJECT the topology, ADAPT the principle.

## 25. Build/code-quality conventions

- Convention plugins centralize module boilerplate (`kta.*`) — good pattern,
  heavyweight execution.
- Version catalog for nearly everything — **except** `dre-android` and
  `kotlinx-collections-immutable` (hardcoded) — drift.
- detekt: custom yml thresholds (complexity 20, params 10/20, line 120), compose
  ruleset — **but `ignoreFailures = true`, `autoCorrect = true`** — findings are
  warnings that auto-rewrite; the gate cannot fail (drift).
- JaCoCo: thresholds exist, aggregation miswired (§23).
- Naming: `Qzds*` design-system prefix; `Qz*` infra prefix; `I*ViewModel`
  interfaces; `*Route`/`*Screen`/`*RouteData`; `*Contract`/`Reducer`/`State`/
  `Action`/`Effect`/`AsyncOp`/`SideEffectHandler`; `*UseCase`; `*Manager`/`*Impl`;
  `*Repository`/`Local*`/`Mock*`/`SupabaseImpl`; `*FeatureModule`/`Initializer`/
  `NavigationProvider`; `// region` blocks; backtick test names — VERIFIED.
- Casing inconsistencies: `QzDs*`/`Qz*` variants, `java` vs `kotlin` source sets,
  `feature/leaderboard` package hosted in `:feature:menu` module — drift.

## 26. Configuration/environment

- `local.properties` → `BuildConfig` fields: `REVENUECAT_API_KEY`,
  `PREMIUM_GATE_ENABLED`, `DAILY_CHALLENGE_ENABLED` (build-time kill-switches
  bound to `PremiumGateConfig`/`DailyChallengeGateConfig` interfaces in `:core`).
- Secrets: `local.properties` → `encode-secrets.py` (XOR) → generated
  `secrets.cpp` → JNI `NativeSecretBridge` → `SecretManager` (enum-keyed) —
  VERIFIED mechanism.
- `google-services.json` gitignored/absent; CI injects via secret store
  (`pr-check.yml`).
- Runtime flags: `FirebaseRemoteConfigManager` (`feature_*`, `game_config`,
  `minimum_app_version`, `force_update_message` keys; XML defaults; 0s/3600s
  fetch intervals debug/release).
- Environment story: single Supabase backend; no dev/staging/prod variants; no
  flavors.
- Drift: release signing falls back to debug keys when env signing vars absent.

## 27. Background work

- WorkManager only (no Service/Alarm/BroadcastReceiver — VERIFIED):
  `DailyQuizWorker` (daily notification, retry), `ChargingSyncWorker`
  (`syncUserProfile`, retry; 24h periodic, charging+connected constraints,
  `KEEP`; debug enqueues an immediate one-shot instead).
- `DailyNotificationScheduler` — computes initial delay to target hour/minute;
  scheduled from `LauncherActivity.onPause` after permission+settings check.
- Workers bound via Koin `worker<>`; manifest removes default WM initializer.
- Relevance to Spendwise: periodic refresh/notifications are product-dependent —
  DEFER.

## 28. Performance patterns

- Pervasive animation + haze blur + Coil — an "experience-first" performance
  budget, not a minimal one.
- Recomposition hygiene: `@Immutable`/`@Stable` models, immutable collections,
  `derivedStateOf` in counters, `key()` in lists (with one weak-key LazyColumn).
- No Paging library, no baseline profiles, no macrobenchmarks — NOT_EVIDENCED.
- Image loading via AsyncImage (incl. drawables — waste, drift).
- `LeaderboardRepositoryImpl` decodes full table then `.take(limit)` — noted
  inefficiency.

## 29. Observability

- Timber: ~154 call sites; `DebugTree` planted **only in debug** — release has
  zero logging/crash pipeline — VERIFIED.
- Firebase Analytics behind `AnalyticsManager` interface; events emitted only
  from `*SideEffectHandler`s (analytics as effects — clean separation);
  `setUserId` unused — anonymous analytics.
- Firebase Remote Config fetch at init on injected app scope.
- Ktor `LogLevel.BODY` debug-only; PII in debug logs (§22).
- **No crash reporting** (no Crashlytics/Sentry dep) despite docs claiming it —
  drift.
- Spendwise: logging+analytics behind interfaces = KEEP pattern; crash reporting
  decision is P3/P4.

## 30. Reference drift

Consolidated register (evidence in sections above; doc claims checked against code):

| # | Drift | Evidence |
| --- | --- | --- |
| DR-1 | `docs/system-architecture.md` describes an abandoned design (ServiceLoader discovery, `fun interface UseCase`, manifest meta-data, fictional `GameState` fields/action names) | `FeatureRegistry.kt` (Class.forName), `UseCase.kt` (abstract class) |
| DR-2 | CLAUDE.md claims Compose BOM 2025.12.01, Supabase 3.2.6, "19 action handlers", "7 side-effect handlers", `GameUiOverlay` 8 states, `loadNewGame()` | catalog (2026.01.00, 3.3.0), `GameStateMachine` (21 handlers), `GameViewModel` (6 handlers), `GameUiOverlay` (9), `startNewGame` |
| DR-3 | `codebase-summary.md` wrong dep arrows and counts; docs claim "QuestionRepository — Supabase + local cache" | prod binding is `MockQuestionRepository` (`DataModule.kt:116`) |
| DR-4 | `FeatureNavigationCallbacks.navigateToGame(category, isPractice)` args dropped at NavHost | `AppNavHost.kt:30-32` |
| DR-5 | RevenueCat types leak into domain (`SubscriptionService`, `SubscriptionManager.offerings`, `PurchasePackageUseCase`) and UI (`PaywallUiState`) | domain/service/subscription/*, paywall/viewmodel/* |
| DR-6 | `Context` in domain API (`AuthService.signInWithGoogleCredential`) and in `SettingViewModel` (StaticFieldLeak) | AuthService.kt, SettingViewModel.kt:32-40 |
| DR-7 | Wire DTOs conflated with domain models (`LeaderboardEntry`, `DailyChallengeScore` `@Serializable` snake_case in :domain) | domain/repository/* |
| DR-8 | Compose `@Stable` on domain models (`UserProfile`, `AuthState`, `Rank`) | domain files |
| DR-9 | Mixed error conventions (Result vs null-on-error vs throw vs raw `e.message`); `mapAuthError` exists only for auth | AuthErrorMapper.kt, LeaderboardViewModel.kt:108 |
| DR-10 | Dead/unwired code: `FlowUseCase` never subclassed; `GameHistoryRepository.addEntry` never called; all 3 daily use-cases + 2 achievement use-cases registered but unreachable; `analytics.setUserId` unused; `multiplatform-settings` unreferenced; `GEMINI_API_TIMEOUT_MS` key unread | greps across repo |
| DR-11 | Zero androidTest sources despite espresso/compose-ui-test deps; `feature:paywall` has zero tests | directory scan |
| DR-12 | Coverage gate hollow (`jacocoReportAll` never runs `jacocoVerification`); aggregate `coverageModules` omits `:secrets` despite it hosting a test; detekt `ignoreFailures` | gradle/jacoco-aggregation.gradle.kts:19-22, DetektConventionPlugin.kt |
| DR-13 | Paywall deviates: no `I*` interface, no reducer, VM as parameter | PaywallRoute.kt:19 |
| DR-14 | `VibratorHapticManager` self-made CoroutineScope; `ScheduleChargingSyncUseCase` manually constructed | VibratorHapticManager.kt:24, App.kt:72 |
| DR-15 | `domain` declares work-runtime + koin-workmanager deps but hosts no workers (only interfaces) | domain/build.gradle.kts |
| DR-16 | Inconsistent wiring/casing: `OnboardingSideEffectHandler` DI-built vs others inline; `MenuViewModel` no dispatchContext vs Game's; `QzDs`/`Qz`/`Qzds` casing; `java` vs `kotlin` dirs; `feature.leaderboard` pkg inside `:feature:menu` | multiple files |
| DR-17 | Both M2 `compose.material` and M3 present; `AsyncImage` used for local drawables; `Toast` once vs snackbar convention | catalog, ui files |
| DR-18 | `NavTransitions` comments say slide, code is fade; `EaseStandard = LinearEasing` | NavTransitions.kt, QzdsPrimitiveMotion.kt:16 |
| DR-19 | Screens receive whole VM (`MenuScreenContent(viewModel=...)`) vs state+callbacks elsewhere | MenuRoute.kt:49-53 |
| DR-20 | `AIHintResponse.confidence = (69..100).random()` fabricated; `SplashViewModel` simulates a progress bar | AIHintResponse.kt, SplashViewModel.kt:45-58 |
| DR-21 | Release signing falls back to debug keys; Gemini key via Remote Config while others are NDK-obfuscated | app/build.gradle.kts:72-76, FirebaseRemoteConfigManager |
| DR-22 | Debug builds log PII (full AuthUser, profiles); Ktor BODY logging debug-only | SyncManagerImpl.kt, DataModule.kt:86-94 |
| DR-23 | Unencrypted session/profile storage (plain DataStore + settings) | LocalDataStoreManagerImpl.kt:22 |
| DR-24 | Reducers `error()`-crash on unhandled actions | GameStateMachine.kt:43, MenuReducer |
| DR-25 | `QuestionRepository` bound to bundled mock in production; several remote features (achievements, daily submit, game history write) effectively dormant | DataModule.kt:116 + no call sites |

## 31. KEEP / ADAPT / REJECT / DEFER matrix

Classifications judge **patterns** for Spendwise — not library lock-ins and not
code copying. Final decisions land in P4 (with P2/P3 inputs).

| Area | Senior pattern | Evidence | Classification | Why | Spendwise implication | Final decision stage |
| --- | --- | --- | --- | --- | --- | --- |
| Module organization | Layer modules + dynamic features + reflection registry | §3, §24 | ADAPT (boundaries) / REJECT (dynamic features+reflection) | Boundaries aid separation; dynamic-feature machinery is complexity without benefit for a finance client | Few modules (≈1–3) at M0; direct, static wiring | P4 |
| UI architecture | Route creates VM via `koinViewModel<Concrete>()` typed as `I*`; screens get state+callbacks | §8 | KEEP (Route-VM wiring) / ADAPT (I* interface for every VM) | Clean seam for previews/tests; interface-per-VM adds indirection with rare payoff | Routes own VMs; decide interface policy per need, not blanket | P4 |
| ViewModel | Constructor-injected, dispatch/launch public API, StateFlow+SharedFlow exposure | §10 | KEEP | Testable, uniform, UI-facing contract is minimal | Same shape | P4 (naming/shape) |
| State management | DRE-KT reducers + side-effect handlers + async-ops; also plain MVVM | §9 | ADAPT | Pure reducers are excellent for complex flows (game FSM, form validation); mandatory machinery is overkill for CRUD screens | Uni-directional state everywhere; formal reducer only where complexity warrants (e.g., import flow, transaction form); library choice open | P4 |
| Navigation | Type-safe serializable routes, single NavHost | §11 | KEEP (typed routes) / REJECT (callback interface + reflection registrars) | Typed routes are the modern baseline; flat `FeatureNavigationCallbacks` already drops args and doesn't scale | `composable<T>` typed routes **with real arguments** (account/transaction ids); direct graph registration | P4 |
| Dependency injection | Koin, per-layer modules, qualifier enums, injected scopes | §12 | KEEP pattern / DEFER library | Constructor injection + seams is right; Koin vs alternatives is a decision | Module-per-layer convention; qualifiers for scopes/dispatchers | P4 |
| Networking | Domain service interface + data impl + client factory + DTOs (Supabase/Ktor) | §13 | KEEP pattern / DEFER stack | Spendwise uses plain REST, not a BaaS SDK | Service interface per backend resource; DTOs at data layer | P2+P4 |
| Repository | Interfaces in domain, impls in data, `Result<T>` remote, `Flow<T>` observable | §14 | KEEP / ADAPT (mixed return conventions) | Clean seam + testability; standardize return types | Same topology; one error/return convention | P4 |
| Use cases | `UseCase<I,O>`/`FlowUseCase` bases; thin wrappers with occasional orchestration | §6, §14 | ADAPT | Useful for orchestration (sign-in → sync); pass-through wrappers add noise | Use cases for multi-step logic; VMs may call repositories directly for simple reads | P4 |
| Model mapping | Mixed: DTO=domain conflation AND explicit mapper chains | §15 | ADAPT | Reference is internally inconsistent; Spendwise needs strict separation | DTO (data) → domain → UI models; no backend DTO in UI state | P2 (shapes) + P4 (convention) |
| Persistence | DataStore preferences ×4; JSON blobs; no Room | §16 | KEEP (DataStore for prefs) / DEFER (entity persistence) | Settings/session prefs fit DataStore; transaction caching strategy undecided | DataStore for settings; offline DB decision awaits product/backend facts | P2+P3+P4 |
| Coroutines/Flow | Injected `QzDispatchers`, `withContext(io)`, `stateIn(WhileSubscribed)`, injected scopes | §17 | KEEP | Testable, consistent, near-exemplary | Same: app-named dispatchers interface + TestDispatchers early | P4 (naming) |
| Error handling | `Result<T>` + `runCatching`; message-sniffing `mapAuthError`; raw `e.message` elsewhere | §18 | KEEP (Result style) / REJECT (string-sniffing + passthrough) | Result works; untyped errors already hurt the reference | Typed error model mapped from backend error contract | P2 (model) + P4 (mapper) |
| Loading state | Per-screen mix (sealed tri-state vs flags vs implicit) | §19 | ADAPT | Reference demonstrates options but no standard | One explicit loading/content/error convention | P4 |
| Design system | 2-layer tokens + CompositionLocals + M3 bridge + previews | §20 | ADAPT | Token discipline is strong; 15 locals + quiz visual language don't transfer | Small Spendwise token set + previews; M3-first | P4 |
| Forms | Reducer-centralized validation → dialog/field state | §21 | ADAPT | Good fit for transaction forms; lacks per-field errors | Validation in state layer; design per-field errors | P3+P4 |
| Testing | JVM-first: reducer tests, VM tests (Turbine), repo tests (MockK), real-UC-mocked-service, TestDispatchers | §23 | KEEP | Strong, cheap, fast — directly reusable conventions | SENTINEL playbook; add enforcement where reference lacks it | P4 (targets) |
| Build conventions | Version catalog + convention plugins + JaCoCo + detekt | §4, §25 | KEEP (catalog) / ADAPT (convention plugins; make gates actually fail) | Centralized deps good; plugin machinery premature at M0; gates must bite | Catalog from day one; detekt/ktlint that fails; wire coverage verification or drop it | P4 |
| Configuration | local.properties→BuildConfig flags; NDK XOR secrets; remote config | §26 | KEEP (flag/config pattern) / REJECT (NDK secrets) / DEFER (remote config) | Build flags are simple and useful; XOR-in-.so is obfuscation theater for a base URL; remote config is product-dependent | `local.properties`/env-driven backend base URL; no compiled-in secrets beyond necessity | P4 |
| Auth/session | Supabase auth-kt + sealed `AuthState` + Credential Manager | §22 | DEFER (KEEP only the sealed-session-state concept) | Backend contract determines everything | `AuthState`-like session model after P2 | P2+P3+P4 |
| Background work | WorkManager periodic sync + daily notification via Koin workers | §27 | DEFER | Product-dependent | Notification/sync needs decided at P3 | P3+P4 |
| Observability | Timber + Firebase Analytics behind interface + Remote Config | §29 | KEEP (behind-interface logging/analytics) / DEFER (specifics, crash reporting) | Separation is right; providers undecided | Thin `Logger`/`Analytics` seams; provider choice later | P3+P4 |
| Feature discovery | `FeatureRegistry`/`FeatureNavigationRegistry` via `Class.forName` FQCN lists | §11-12 | REJECT | Fragile reflection + R8 keeps for zero runtime benefit at install-time delivery | Explicit registration | — |
| Whole-VM to screens | `MenuScreenContent(viewModel)` | §8 drift | REJECT | Couples screen to VM API; previews get heavier | Screens take state + lambdas | — |
| Message-sniff errors | `mapAuthError` on `e.message` → hardcoded English | §18 | REJECT | Brittle, unlocalizable, invisible to tests of real error types | Typed errors | P2+P4 |
| Fake/mock prod binding | `MockQuestionRepository` as production binding | §14 | REJECT (as habit) | Mocks are for tests/dev builds, not the shipped data path | Real backend binding; mocks confined to tests/previews | — |
| NDK secret obfuscation | XOR-in-.so secrets pipeline | §26 | REJECT | Complexity ≫ value for a REST base URL/anon key | Standard config channels | — |
| I* interface per VM | `IGameViewModel` etc. | §10 | ADAPT→likely skip | Payoff seen only for preview fakes; Koin+VMs test fine without | Introduce only where a seam is needed | P4 |
| Blanket animation mandate | animation "non-negotiable" + haze everywhere | §8, §20 | REJECT (mandate) / ADAPT (judgment) | Finance UI wants restraint; haze adds GPU cost | Meaningful transitions only | P3 |
| `error()` on unhandled action | Reducer crash on unknown action | §9 | REJECT | A crash-path for programmer error in prod | Return no-op or logged error | — |

## 32. Do Not Copy Blindly

Concrete structures from the reference that must NOT be carried into Spendwise:

1. **Dynamic feature modules + reflection discovery** (`Class.forName` FQCN
   lists, R8 keep rules, inverted `feature→:app` deps). High ceremony, real
   fragility, zero benefit for a finance client.
2. **DRE-KT as a mandatory framework** for every screen. Keep the *idea* of pure
   state transitions for genuinely complex flows; do not import a niche
   third-party FSM library as the default state solution.
3. **`I*ViewModel` interfaces everywhere** — indirection justified only by
   preview fakes in practice.
4. **The `Qzds`/`Qz*` naming and the quiz design catalog** (money ladder,
   lifelines, glitch/breathing effects, haze-everything). Build a minimal
   Spendwise design system instead.
5. **Quiz domain model and its domain-purity violations** — RevenueCat types in
   domain/UI, `Context` in service APIs, `@Serializable` wire models declared in
   `:domain`. These are anti-patterns the reference itself suffers from.
6. **The `FeatureNavigationCallbacks` flat interface** — it already swallows
   declared arguments; typed routes with real args are the better tool.
7. **NDK XOR secrets pipeline** — obfuscation, not security; inappropriate for
   Spendwise's config surface.
8. **RevenueCat/paywall machinery** — no monetization requirement is established
   for Spendwise.
9. **Message-sniffing error mapping and raw `e.message` UI passthrough** —
   replaced by a typed error model (P2).
10. **A mock repository as the production binding** (`MockQuestionRepository`)
    and dormant registered-but-unreachable features — Spendwise binds the real
    backend or doesn't ship the feature.
11. **Supabase-as-backend assumption** — Spendwise consumes the Spring Boot
    REST API; there is no BaaS SDK to port.
12. **DataStore-JSON-blob structured persistence for financial data** — adequate
    for a 50-item history, not a transactions store (decision deferred to P3/P4).
13. **Hollow quality gates** (detekt `ignoreFailures`, unwired JaCoCo
    verification, zero androidTest coverage while declaring UI test deps) —
    adopt the gates, wire them to fail.
14. **Debug-build PII logging and `LogLevel.BODY`** — a finance app must not
    normalize logging full user/account payloads.
15. **Firebase analytics/remote-config presence by default** — add only if the
    product blueprint calls for it.

## 33. Preliminary P1 constraints for Spendwise

All statements below are `PRELIMINARY_P1_CONSTRAINT` — inputs to P4, not final
decisions. Each cites its evidence basis.

- PRELIMINARY_P1_CONSTRAINT — **Few modules first.** Start ≈1–3 Gradle modules;
  grow only when a boundary pays rent. Basis: dynamic-feature+registry cost vs.
  benefit (§24); Spendwise scope.
- PRELIMINARY_P1_CONSTRAINT — **UI never consumes backend DTOs.** DTO → domain →
  UI model separation; the reference's conflation (§15, DR-5/DR-7) is the
  cautionary evidence.
- PRELIMINARY_P1_CONSTRAINT — **Unidirectional state everywhere; formal
  reducer/FSM only where complexity warrants** (multi-step import, transaction
  form). Basis: DRE machinery pays off in the game FSM but is ceremony for CRUD
  (§9).
- PRELIMINARY_P1_CONSTRAINT — **Constructor DI with per-layer modules and
  injected dispatchers/scopes** (`*Dispatchers` + `TestDispatchers` equivalent).
  Basis: §12/§17 — strongest transferable pattern.
- PRELIMINARY_P1_CONSTRAINT — **Repository/service interfaces on the domain
  side, implementations in data**; one return-convention (`Result<T>` or typed
  error — pending P2 error model). Basis: §14/§18.
- PRELIMINARY_P1_CONSTRAINT — **Type-safe navigation routes with real
  arguments** (accountId, transactionId); no callback-interface indirection.
  Basis: §11 + DR-4.
- PRELIMINARY_P1_CONSTRAINT — **Explicit loading/content/error UI-state
  convention** — the reference is a menu of options, not a standard (§19).
- PRELIMINARY_P1_CONSTRAINT — **One-off UI events via `SharedFlow` collected at
  the Route**; state via `collectAsStateWithLifecycle`. Basis: §8/§9 — consistent
  and correct.
- PRELIMINARY_P1_CONSTRAINT — **JVM-first test strategy**: pure-logic
  reducer/mapper tests, VM tests with Turbine+coroutines-test+MockK, repository
  tests with mocked services; `TestDispatchers` from day one. Add the gates the
  reference lacks (working coverage check, meaningful lint). Basis: §23/§25.
- PRELIMINARY_P1_CONSTRAINT — **Minimal design-token system + previews**;
  Material3-first; no ported visual language. Basis: §20.
- PRELIMINARY_P1_CONSTRAINT — **Config via `local.properties`/env → BuildConfig
  for the backend base URL**; no compiled-in secrets machinery. Basis: §26.
- PRELIMINARY_P1_CONSTRAINT — **Logging/analytics behind thin interfaces;
  no PII payloads**; crash-reporting decision deferred. Basis: §29/DR-22.
- PRELIMINARY_P1_CONSTRAINT — **Auth/session shape is UNDECIDED** until the
  backend contract is audited; only the sealed-session-state concept is a
  candidate. Basis: §22 + P2 dependency.

## 34. P2 backend questions

Questions the backend contract audit (`D:\spendwise`, READ ONLY) must answer —
NOT answered here, not to be assumed:

1. Authentication: endpoints, token format (JWT? opaque?), refresh mechanism,
   expiry, signup/login/logout semantics, guest/anonymous mode?
2. API base URL(s) and environment story (dev/prod), versioning path scheme.
3. Error response schema: status codes, body shape, machine-readable codes,
   per-field vs global errors, localization.
4. Money representation: minor units vs decimal, currency code per account or
   per transaction, multi-currency support, rounding.
5. Date/time representation: ISO-8601? timezone semantics, date-only vs
   timestamp for transactions.
6. Pagination: cursor/offset/page params + response envelope for transactions
   and accounts.
7. Account model: fields, types, constraints, ordering, archival semantics.
8. Transaction model: income/expense/transfer as one resource or several;
   transfer semantics (atomic two-leg? linked records?); category model.
9. Import contract: CSV/PDF upload endpoint (multipart shape, size limits),
   async processing/status polling, dedup behavior, error/partial-result model.
10. Mutation semantics: idempotency support, optimistic-update tolerance,
    `updated_at`/version fields for sync/conflict handling.
11. Filtering/search endpoints (date range, category, account, text) needed by
    transaction lists and dashboard queries.
12. Rate limits / retry guidance for a mobile client.
13. Any aggregate/dashboard endpoints (totals by period/category) vs client-side
    computation.

## 35. P3 product questions

Questions for the product blueprint — NOT answered here:

1. Primary navigation structure: bottom-nav destinations vs single-flow app;
   which surfaces are top-level (dashboard, transactions, accounts, import)?
2. Account model UX: multiple accounts, switching, per-account views, net-worth
   display.
3. Dashboard composition and priorities (balance? recent? trends? budgets?).
4. Transaction list UX: grouping (by date?), filtering, search, infinite scroll
   vs paged.
5. Transaction creation UX: amount-first? type tabs (income/expense/transfer)?
   category picker design; required vs optional fields.
6. Import workflow UX: file picker → upload → progress → result review →
   confirm? handling duplicates/partial failures.
7. Authentication UX: required at all? email? Google? skip/guest mode? How does
   the user relate to backend accounts?
8. Empty states and first-run experience (no accounts → prompt create? sample
   data?).
9. Localization: languages required (read-only observation: `D:\spendwise`
   contains Vietnamese docs, e.g. `HUONG-DAN-SU-DUNG.md` — app language
   requirements?).
10. Offline expectations: read-only cache of last data vs full offline queue —
    or online-first.
11. Monetization/premium: does any exist? (Reference paywall concepts do not
    transfer by default.)
12. Dark mode, currency display preferences, notification requirements.
13. Minimum Android version and device targets.

## 36. Evidence index

Primary anchors inspected directly by this audit (all paths relative to
`D:\app_spendwise\kotlin-accelerator-ai`):

- `settings.gradle.kts` — module list, `includeBuild("build-logic")`, repo modes
- `build.gradle.kts` (root) — plugin aliases `apply false`, jacoco aggregation
- `gradle/libs.versions.toml` — all declared versions
- `app/build.gradle.kts` — dynamicFeatures, BuildConfig flags, SDK levels,
  signing fallback, coverage threshold
- `core/build.gradle.kts:31` — hardcoded `dre-android:0.1.3` + `api` exports
- `domain/build.gradle.kts`, `data/build.gradle.kts` — module deps, RC/work deps
- `app/src/main/AndroidManifest.xml` — `.App`, `.LauncherActivity`, WM
  initializer removal
- `app/.../App.kt` — startup chain
- `app/.../LauncherActivity.kt` — splash/theme/content + injected services
- `app/.../ui/navigation/AppNavHost.kt` — NavHost, callbacks dropping args
- `app/.../ui/navigation/SplashNavigation.kt`, `NavTransitions.kt`,
  `ui/launcher/splash/*`
- `app/.../di/AppModule.kt` — viewModelModule/notificationModule/appConfigModule
- `app/.../sync/*`, `app/.../notification/*` — workers + scheduler
- `core/.../di/{CoreModule,FeatureModuleInitializer,FeatureRegistry,
  ScopeQualifier}.kt`
- `core/.../navigation/{FeatureNavigationRegistry,Routes}.kt`
- `core/.../usecase/{UseCase,FlowUseCase}.kt`
- `core/.../dispatchers/{QzDispatchers,QzDispatchersImpl}.kt` +
  `core/src/test/.../TestDispatchers.kt`
- `core/.../config/{LevelConfig,PremiumGateConfig,DailyChallengeGateConfig}.kt`
- `domain/.../di/DomainModule.kt` — 16 use-case factories
- `domain/.../repository/{leaderboard/LeaderboardRepository,...}` —
  interfaces + wire-shaped models
- `domain/.../service/auth/{AuthService,AuthState}.kt`
- `domain/.../usecase/auth/*` — 7-8 use cases + `AuthErrorMapper.kt`
- `domain/.../manage/{user/UserProfileManager,subscription/SubscriptionManager,
  achievement/AchievementManager}` + `AchievementDefinitions.kt`
- `domain/.../local/LocalDataManager.kt` (+Local models)
- `data/.../di/DataModule.kt` — all bindings incl. `MockQuestionRepository`
- `data/.../repo/leaderboard/LeaderboardRepositoryImpl.kt` (+test)
- `data/.../service/auth/AuthServiceSupabaseImpl.kt` (+test)
- `data/.../service/user/{SupabaseUser,AppUser,UpsertUserRequest,
  UserServiceSupabaseImpl}.kt`
- `data/.../service/sync/SyncManagerImpl.kt`
- `data/.../service/ai/*` — providers + Gemini DTOs
- `data/.../service/analytics/FirebaseAnalyticsManager.kt`,
  `service/config/FirebaseRemoteConfigManager.kt`
- `data/.../local/LocalDataStoreManagerImpl.kt`,
  `repo/{history,daily,achievement}/*`
- `data/.../supabase/SupabaseClientFactory.kt`
- `feature/game/.../viewmodel/{GameViewModel,IGameViewModel}.kt`
- `feature/game/.../domain/manage/*` — `GameStateMachine`, `GamePhase`,
  `GameUiOverlay`, `GameAction`, `GameSideEffect`, `GameAsyncOp(Executor)`,
  `GameOverlayQueueManager`, `CurrentGameSession`, `handler/*` ×6
- `feature/game/.../screen/{GameRoute,GameScreenUiState,GameScreenMapper}…`,
  `dialog/*`, `navigation/GameNavigation.kt`, `di/GameFeatureModule.kt`
- `feature/menu/...` — `MenuRoute`, `MenuViewModel`, `IMenuViewModel`,
  `MenuReducer`, `MenuContract`, `MenuDialogQueueController`, `handler/*`,
  `settings/*`, `leaderboard/*`, `onboard/*`, `di/MenuFeatureModule.kt`,
  `navigation/MenuNavigation.kt`
- `feature/paywall/...` — `PaywallRoute`, `PaywallViewModel`, `PaywallUiState`,
  `PaywallNavigation.kt`, `PaywallFeatureModule.kt`
- `theme/...` — `QzdsTheme`, `QzdsPrimitive*`, `Qzds*Tokens`, motion presets
- `ui-components/...` — `Qzds*` component catalog, `DialogScaffold`,
  `HazeUtils`, `QzdsEmailAuth*` form components
- `secrets/` — `build.gradle.kts` (CMake/exec pipeline), `NativeSecretBridge`,
  `SecretManagerImpl` (+test)
- `build-logic/convention/src/main/kotlin/*.kt` — 5 convention plugins
- `gradle/jacoco-aggregation.gradle.kts`, `config/detekt/detekt.yml`
- `scripts/encode-secrets.py`, `crypto.py` (repo root), `.github/workflows/pr-check.yml`
- `docs/*`, `CLAUDE.md`, `README.md` — drift-checked
- 52 `src/test` files across modules (listed §23)

## 37. Audit limitations

- **Static analysis only.** P1 did NOT run Gradle, unit tests, instrumentation
  tests, or the app; runtime behavior, test pass rates, actual coverage, and
  build reproducibility are unverified.
- The DRE-KT library (`dre-android:0.1.3`) is an external artifact; its internal
  semantics (e.g., whether `executeAsyncOp` exceptions are caught by the base
  VM) are INFERRED from usage, not from library source.
- Generated artifacts not inspectable: `secrets.cpp` (gitignored, build-time),
  `google-services.json` (absent), R8/ABI build outputs.
- Line numbers cite the audited HEAD (`5f9622c`); they may drift on later
  commits.
- Sub-features not fully traced: every screen was inventoried, but only the
  representative flows in §7 were traced end-to-end; exhaustive per-screen
  behavior (e.g., all 21 GameAction handlers) is out of scope.
- No backend contract was inspected (P2 scope); no Spendwise architecture was
  finalized (P4 scope).
- Claims in `docs/`/`CLAUDE.md` were used only as leads; where they conflict
  with code they are recorded as drift, not treated as fact.
