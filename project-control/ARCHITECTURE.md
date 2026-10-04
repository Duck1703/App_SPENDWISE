# ARCHITECTURE.md — Spendwise Android Architecture Blueprint

Produced by **P4 — Spendwise Android Architecture Blueprint**.

STATUS: **READY_FOR_HUMAN_REVIEW** — every decision below is `PROPOSED`
and requires `HUMAN_APPROVAL_REQUIRED=YES` at the P4 Human Gate before
M0. This is an architecture/implementation-planning artifact only: **no
Android/Gradle/Kotlin/Compose production code exists or may be created
from this file's presence**.

Authoritative inputs: `PRODUCT.md` (P3, Human-approved),
`BACKEND-CONTRACT.md` (P2, approved @ `8194daa`), `ANDROID-BASELINE.md`
(P1, approved), `P3A-DESIGN-HANDOFF.md` (Human-accepted UX,
`P3A_HUMAN_ACCEPTED_FOR_P4=YES`), `DECISIONS.md`, `WORKSPACE-BOUNDARIES.md`.
Current-source verifications are cited inline (§40).

---

## A. Architecture summary

**Style**: single-module, package-layered Android app — Jetpack Compose
UI, MVVM (Route → ViewModel → stateless Screen), repositories over a
Retrofit/OkHttp + kotlinx.serialization network layer, Hilt DI, coroutines/
Flow, in-memory session-scoped state, no local financial database.

**One-line**: *"A thin, honest client: Compose presents, ViewModels hold
state, repositories translate, the backend decides."*

| Axis | Decision | AD |
|---|---|---|
| Modules | Single `:app` module, enforced package boundaries | AD-001 |
| UI pattern | MVVM, Route→VM→Screen, immutable `UiState` + effects | AD-002, AD-003 |
| Navigation | Navigation Compose, `@Serializable` typed routes, two graphs | AD-004 |
| HTTP | Retrofit + OkHttp | AD-005 |
| JSON | kotlinx.serialization + custom `BigDecimal` serializer | AD-006 |
| Money | `Money(BigDecimal, CurrencyCode)`; never Float/Double | AD-007 |
| Currency | `value class CurrencyCode` (open ISO-4217, non-crashing) | AD-007 |
| IDs | typed `value class` wrappers over server strings | AD-007 |
| Enums | defensive mapping w/ `Unknown(raw)`; never mislabel | AD-008 |
| Time | `java.time` (`LocalDate`/`Instant`/`YearMonth`), minSdk 26 | AD-009 |
| Session | `SessionManager` + Keystore-AES + Preferences DataStore | AD-010 |
| Errors | sealed `AppError`; status+`fields` mapping; no sniffing | AD-012 |
| Persistence | NO Room; DataStore for token/username only | AD-013 |
| DI | Hilt | AD-015 |
| Upload | `OpenDocument` picker + streaming `RequestBody` multipart | AD-017 |
| Design | `SpendwiseTheme` tokens + `Sw*` components (light only) | AD-018 |
| Home FAB | scroll-derived extended↔compact | AD-019 |
| Logging | Timber (debug) + redacted headers; no bodies | AD-020 |

## B. Architectural invariants

| ID | Invariant |
|---|---|
| INV-001 | Backend is the authoritative financial ledger. |
| INV-002 | Android never computes a canonical account balance (no summing transactions into balances). |
| INV-003 | A financial amount never passes through `Float`/`Double` — JSON ↔ `BigDecimal` only. |
| INV-004 | Unlike currencies are never silently aggregated anywhere (totals only when the whole portfolio provably shares one currency). |
| INV-005 | Android never parses CSV/PDF statements into domain transactions — the file goes to the backend as bytes. |
| INV-006 | Transfers have no UI and no code path until a public backend contract exists. |
| INV-007 | Transaction history is immutable in the V1 UI — no edit/delete/correct/offset affordance (Human-overridden). |
| INV-008 | Unknown-but-valid server values (enums, currencies) never crash decoding; they map to `Unknown`/`raw` representations, never to a wrong known value. |
| INV-009 | 401 never triggers a fake refresh — it triggers the Session-expired flow. |
| INV-010 | Passwords, tokens, statement file bytes, and full financial payloads never enter application logs; `Authorization` is redacted even in debug. |
| INV-011 | Every amount on screen carries its ISO currency code; direction is sign + word + icon, never color alone. |
| INV-012 | Dead affordances are forbidden — no UI control exists for a capability the backend lacks. |
| INV-013 | The app never invents dates, descriptions, search, or pagination UI the wire cannot honor. |
| INV-014 | Server order is displayed as returned; no client-side chronology claim is made. |
| INV-015 | Network failures show an honest state + retry — never stale-data-as-truth. |

---

## 1. Module strategy (DECIDED)

**AD-001 — Single `:app` module with disciplined package boundaries.**

Chosen option **A**. Alternatives:

- **B. small multi-module** (`:app`, `:core`, `:feature:*`) — REJECTED:
  Gradle/build-sync overhead, premature for one developer and 25
  screens; boundaries enforced by packages are sufficient today.
- **C. senior-style dynamic features** — REJECTED outright (P1: dynamic
  features + reflection registries were explicitly REJECT-classified).

Why A: V1 scope is fixed and small; a single module gives the fastest
build/iterate cycle and the least machinery, while package discipline
keeps extraction easy. **Extraction triggers** (when any holds, split
`:core` / `:feature`): build times hurt, a second developer joins, a
feature needs independent versioning, or module boundaries are being
violated in code review repeatedly. Recorded as a deferred decision —
not "undecided."

## 2. Package / feature organization (DECIDED)

```
com.spendwise
├── app/                  # Application, MainActivity, DI bootstrap, build wiring
├── core/
│   ├── designsystem/     # theme (tokens), Sw* components, icons mapping, illustration refs
│   ├── navigation/       # route objects, NavHost, graphs, session guard wiring
│   ├── network/          # Retrofit services, DTOs, mappers, interceptors, ApiError plumbing
│   ├── session/          # SessionManager, TokenStore, auth interceptor glue
│   ├── model/            # Money, CurrencyCode, typed IDs, domain enums, shared models
│   ├── data/             # repository interfaces + impls (one file-set per repo)
│   └── ui/               # UiState helpers, AppError→UI, formatters (money/percent), preview data
└── feature/
    ├── auth/             # welcome, login, register, session-expired (route+vm+screen+form)
    ├── home/
    ├── accounts/         # list, create, detail
    ├── transactions/     # list, filters, detail, create
    ├── imports/          # choose, processing, result
    ├── recurring/        # list, create, detail
    ├── budgets/          # list, create, detail
    ├── rules/            # list, create, detail
    └── more/             # hub
```

Feature package internal layout (uniform): `XxxRoute.kt`,
`XxxViewModel.kt`, `XxxScreen.kt`, `XxxUiState.kt`,
`XxxFormState.kt` (where forms exist). No use-case layer by default
(§14 rule). No "domain module" — `core/model` holds the shared domain
types.

## 3. UI architecture (DECIDED)

**AD-002 — MVVM: Route composable → ViewModel → stateless Screen.**

- `Route` composable: obtains the ViewModel via `hiltViewModel()`,
  collects `uiState: StateFlow`, forwards state + lambdas to `Screen`;
  owns navigation-callback wiring (never passes NavController into VM).
- `Screen` composable: stateless — renders from `UiState`, emits user
  intents as lambdas (`onSubmit`, `onRetry`, `onFilterChange`).
  Previewable via fake `UiState` fixtures (`core/ui/preview`).
- ViewModel: single `MutableStateFlow<UiState>`, explicit action
  functions per user intent; one-off events (navigate, toast) via a
  `Channel`/SharedFlow `effects` consumed once by the Route.
- **No mandatory reducer framework** (P1 REJECT-adjacent: DRE-KT is not
  adopted). Pure `reduce(state, event)` functions are permitted **only**
  inside a ViewModel where a genuinely complex finite flow exists —
  the Import processing surface is the sole V1 candidate; even there a
  discriminated `UiState.status` is usually enough.

**AD-003 — Screen state model.**

Per-screen immutable `data class XxxUiState` with a discriminated
`status` (sealed or enum, per-screen — NOT one universal giant sealed
class):

```kotlin
// shape only — not code
data class TransactionsUiState(
  val status: Status,           // Loading | Content | Empty | NetworkError | ServerError
  val items: List<TxnRow>,      // empty unless Content
  val filters: Filters,         // account/type/category + local provenance
  val isRefreshing: Boolean,    // content stays + indicator — never flickers to skeleton
  val isSubmitting: Boolean,
  val submittingError: AppError?,
)
```

Rules:

- `Content` and `Empty` are derived (items empty → Empty) — never two
  booleans that can contradict.
- Transient action state (`isSubmitting`, `submittingError`) is
  orthogonal to body status — a failed save re-renders Content +
  inline error, never flashes the whole screen to error.
- `SessionExpired` is NOT a screen status — it's a global navigation
  event (§4/§13).
- Special states kept where product truth demands: `MixedCurrency`
  (Home), `ImportProcessing`/`ImportResult(partial|allDup|allFail)`
  (Import) — discriminated subtypes on those screens only.
- Validation errors live inside form state (`fields` map), not body
  status.
- Derived UI values (e.g., grouped/fitered rows) computed in VM or via
  `derivedStateOf` — never recomputed per recomposition.

Form state: `data class XxxFormState(val fields…, errors: Map<Field,String>, isSubmitting, serverError)` — field-level `AppError.Validation.fields` mapped onto named fields.

## 4. Navigation architecture (DECIDED)

**AD-004 — Navigation Compose with `@Serializable` typed routes; two
object graphs + session-expired overlay.**

```
auth graph                        main graph (single-activity NavHost)
├── Welcome                       ├── Home        ─┐ 4 top-level
├── Login                         ├── Transactions │ destinations
├── Register → Login(prefill)     ├── Accounts     │ (bottom bar)
└── (session-expired is pushed    └── More         │
     on top of main graph)            ├── Account detail ─→ Transactions?accountId
                                      ├── Txn create / Txn detail
                                      ├── Import choose → processing → result
                                      ├── Recurring list/create/detail
                                      ├── Budgets list/create/detail
                                      └── Rules list/create/detail
```

Decisions:

- **Typed routes** via `@Serializable` route objects (Navigation 2.8+
  official API) — args: `accountId` (string), `transactionId`, `budgetId`,
  `ruleId`, `recurringId`. No deep links in V1 (no requirement).
- **Bottom bar** visible on the 4 top-level destinations only; nested
  routes hide it (route→bar mapping via `currentBackStackEntry`
  destination check). Import flow hides it too.
- **Root selection**: `session` state decides start content —
  unauthenticated → auth graph; authenticated → main graph. Cold start
  shows a launch/splash composable while `SessionManager` restores.
- **Back stack**: logging in pushes main graph over auth (auth cleared
  via `popUpTo(0)` semantics); signing out pops everything to auth.
- **Session expiry**: any 401 **on an authenticated call** →
  `SessionManager` emits `Expired` → root `NavHost` effect navigates to
  `SessionExpired` (auth-graph screen pushed on top, main back stack
  PRESERVED). 401s on the login/register calls themselves are ordinary
  form errors — they never emit `Expired`; a logout 401 is ignored.
  After successful login, pop back to the prior destination —
  ViewModels of still-alive entries retain state (this is what restores
  the transaction draft, §13/§24).
- **Account detail → "see all"** navigates to `Transactions(accountId=…)`
  with the account filter pre-applied; back returns to Account detail.
- **Import processing**: `BackHandler` disabled; no cancel affordance.
- No nested nav graphs beyond auth/main — kept flat where possible.

## 5. Design-system architecture (DECIDED)

**AD-018 — `SpendwiseTheme` custom token layer over Material 3, light
only; `Sw*` component family; Lucide→VectorDrawable; Manrope; bundled
WebP illustrations.**

Translation of `P3A-DESIGN-HANDOFF` §8–§10:

- **Theme**: `SpendwiseTheme` wraps `MaterialTheme` supplying custom
  `SwColors`/`SwTypography`/`SwSpacing`/`SwRadii`/`SwElevation`
  composition-locals (M3 colorScheme populated from brand tokens for
  ripple/ripple-color & default component tinting; semantic roles —
  income/expense/warning/info/category tiles — live in `SwColors`, not
  forced into M3 slots).
- **Type**: Manrope as bundled font resource (subset available in
  prototype assets; OFL allows bundling) — downloadable-fonts as
  fallback option; money uses `fontFeatureSettings="tnum"`.
- **Components** (`core/designsystem/`): `SwButton` (filled/outlined/
  text/danger-outline), `SwPrimaryCta` (gradient), `SwTextField`
  (persistent label + error line/icon), `SwAmountField` (locked currency
  chip), `SwSegmentedControl`, `SwTypeSwitch` (Expense/Income),
  `SwFilterChip` + `SwFilterRow`, `SwCategoryTile`/`SwCategoryPicker`,
  `SwAccountCard`, `SwTransactionRow` (provenance chip), `SwBudgetRing`
  (Canvas `drawArc`), `SwImportStatTile`, `SwFab` (extended/compact
  animating), `SwBottomBar`, `SwTopBar` (w/ optional header action),
  `SwHero` (gradient + motif Canvas + scrim), `SwEmptyState`,
  `SwErrorState`, `SwLoading`/`SwSkeleton`, `SwBanner`
  (info/warning/error), `SwConfirmDialog`, `SwIcon` (name→Lucide
  drawable map).
- **Assets**: Lucide ×30 → `res/drawable` VectorDrawables; Fluent 3D ×4
  → `drawable-nodpi` WebP (`contentDescription=null` — decorative);
  `hero-landscape`/`topo-lines`/`spendwise-mark` → VectorDrawable where
  expressible, else `Canvas` (radial-gradient mask may need pre-bake or
  Canvas — decided at implementation, both acceptable); brand mark.
- **License discipline**: `THIRD_PARTY_NOTICES.txt` + per-asset
  LICENSE copies carried into the repo; unused manifest assets not
  carried over (license process — §5 + handoff §9).
- **Rejected**: a generic "design-system framework," dark theme (V1),
  CSS/React structure port, remote image/icon loading.

## 6. Accepted-interaction architecture

- **Home FAB (AD-019)**: `SwFab` extended↔compact derived from the
  Home list's `LazyListState` — `firstVisibleItemIndex==0 &&
  firstVisibleItemScrollOffset<threshold` → extended; else compact.
  `animateContentSize`/label alpha; respects reduced-motion
  (`Animatable` skipped under `ANIMATOR_DURATION_SCALE==0f`).
  *(Canonical rule = task §5: extended near top; R4's inverse trigger
  is superseded — mechanism identical either way.)*
- **Transactions**: `SwTopBar` + mint `+` icon action (contentDescription
  "Add transaction"); no FAB on this screen.
- **Filters**: one `SwFilterRow` — `horizontalScroll` + nowrap, edge
  fade; `SwFilterChip`s opening modal pickers — **Account** (list of the
  user's accounts), **Type** (options: All / Income / Expense — the
  `TRANSFER_*` wire values are never offered), **Category** (the
  `/api/categories` list). Selected value replaces label; brand-deep
  "on" style. A 4th **local-only** chip — provenance
  (Manual / Imported / Recurring) — filters the already-loaded list
  client-side; it scrolls within the same single row per the accepted
  design and is visibly scoped as local, never promising a server
  filter.
- **Transactions list**: `LazyColumn` keyed by `TransactionId`; honest
  end-note composable; pull-refresh triggers full reload.
- **Create transaction**: type switch → account chips → `SwAmountField`
  (currency chip locked to account) → category tiles (+Uncategorized)
  → optional hint field w/ exact non-persistence warning →
  "Recorded as today" read-only row → submit locks.
- **Import**: choose (account → type guidance → file) →
  `ImportProcessingScreen` (indeterminate, `BackHandler{}`) →
  `ImportResultScreen` (3 tiles + grouped rows + done).
- **Budgets hero**: labeled current-`YearMonth` explicitly; list may
  show other months — month context always text-visible.

## 7. Network stack (DECIDED)

**AD-005 — Retrofit + OkHttp.**

Why: de-facto Android standard; first-class multipart
(`@Multipart`/`@Part` streaming `RequestBody`); interceptor model fits
auth + error normalization; `MockWebServer` for tests; huge Compose-era
ecosystem support. Rejected: **Ktor client** (works, but Android
conventions/tooling thinner; extra engine config for Android; no
compensating benefit), **raw HttpURLConnection** (no), **Fuel/others**
(unmaintained).

Plumbing:

- One `OkHttpClient`: `AuthInterceptor` (adds `Authorization: Bearer`
  from `SessionManager`), `ErrorInterceptor` (non-2xx → `ApiException`
  carrying status + parsed `ProblemDetail`), debug-only
  `HttpLoggingInterceptor` (headers redacted, level HEADERS, never
  bodies).
- One `Retrofit` per `baseUrl` (BuildConfig-injected) + Kotlinx
  converter.
- Service interfaces per backend domain (`AuthApi`, `AccountsApi`,
  `TransactionsApi`, `DashboardApi`, `ImportsApi`, `RecurringApi`,
  `BudgetsApi`, `RulesApi`, `CategoriesApi`).
- `suspend` functions throwing `ApiException` on non-2xx — converted to
  `AppError` by a single `toAppError()` mapper at the repository
  boundary (single choke point; see §16).
- `BigDecimal` arrives via the custom serializer (§18) — never double.

## 8. JSON serialization (DECIDED)

**AD-006 — kotlinx.serialization + custom `KSerializer<BigDecimal>`.**

Why: official Jetpack direction; compile-time (no reflection, R8-safe);
required anyway for type-safe nav routes; precise control over numbers.
Rejected: **Moshi** (fine but reflection/codegen weight; losing
momentum), **Jackson** (heavy reflection, R8 friction, overkill).

`BigDecimalSerializer`: decode `JsonPrimitive.content` →
`BigDecimal(str)` (never `JsonPrimitive.double`); encode via
`JsonUnquotedLiteral`/`JsonEncoder.encodeJsonElement` so values emit as
a real JSON **number** (the wire type) — `toPlainString()` preserves the
value exactly; backend normalizes scale.

DTO nullability: every optional backend field is nullable Kotlin
(`category`, `endDate`, `targetCategory`, `reason`, `transactionId`,
`convertedAmount`, `detail`, `fields`…); no `lateinit`/non-null
defaults that could NPE on wire drift. Unknown keys ignored
(`ignoreUnknownKeys=true`).

## 9. Money / Currency / ID models (DECIDED)

**AD-007.**

```kotlin
// shape only
@JvmInline value class CurrencyCode(val code: String)   // 3-letter ISO, validated at boundary; unknown-but-valid passes through
data class Money(val amount: BigDecimal, val currency: CurrencyCode)
@JvmInline value class AccountId(val value: String)      // opaque server string — NOT java.util.UUID
// TransactionId, BudgetId, RuleId, RecurringId, ImportBatchId, UserId — same pattern
```

- **Wire**: DTO carries `amount: BigDecimal` + `currency: String`;
  mapper pairs them into `Money` at the network boundary.
- **Direction is separate**: `TransactionType` drives sign rendering;
  stored amount stays positive as sent. UI sign is presentation only.
- **Formatting** (`core/ui/format`): `Money.display()` →
  `amount.stripTrailingZeros().toPlainString()` grouped by
  `NumberFormat` + ` ` + `currency.code` — never `$`-style symbol
  guessing; tnum applied at typography level.
- **CurrencyCode**: syntactic check `^[A-Z]{3}$` at creation; unknown
  but valid codes pass through untouched (backend's open ISO-4217
  contract). `java.util.Currency` lookup is OPTIONAL (display name) —
  never required, never throws for unknown codes.
- **IDs as value classes over String**: server treats them as opaque
  UUID strings; keeping them strings is forward-compatible (a format
  change can't crash parsing) while types prevent cross-ID confusion.
  Serializable through nav args via their `value` string.

## 10. Enum forward-compatibility (DECIDED)

**AD-008 — Defensive enum mapping with `Unknown(raw)` fallbacks.**

- Wire DTOs keep `String`; mapper converts to Kotlin enums where the
  set is closed and meaningful: `TransactionType{INCOME, EXPENSE,
  TRANSFER_IN, TRANSFER_OUT, Unknown(raw)}` — `TRANSFER_*` ARE mapped
  (they can legitimately appear from internal transfers) and render as
  neutral "Transfer" provenance; `Unknown` renders as generic
  "Transaction", never mislabeled Income/Expense.
- `AccountType{CASH,BANK,Unknown}`, `ImportRowStatus{OK,DUPLICATE,
  FAILED,Unknown}`, `RecurringFrequency{WEEKLY,MONTHLY,Unknown}`,
  `BudgetStatus{WITHIN_BUDGET,OVER_BUDGET,Unknown}`,
  `RuleConditionType{CONTAINS,EQUALS,Unknown}`.
- `Category`: fixed-5 set known to V1 + `Unknown(code)` — renders the
  raw code; unknown categories in pickers never appear (pickers render
  the `/api/categories` list as returned).
- A screen must never crash on `Unknown` — worst case it renders a
  neutral label; a debug log (non-PII) flags it.

## 11. Date/time model (DECIDED)

**AD-009 — `java.time` only: `LocalDate`, `Instant`, `YearMonth`.**

- `occurredOn` exists on **no transaction model** — the wire doesn't
  return it; the app manufactures no dates (INV-013).
- `LocalDate`: recurring `nextDueDate`/`endDate`, CSV guidance text.
- `YearMonth`: budget `month`, dashboard `month` query param.
- `Instant`: JWT `expiresAt` (session bookkeeping only).
- "Recorded as today" = server's local day — displayed verbatim; no
  client timestamp is added.
- minSdk 26 (AD-014) → java.time available natively, **no desugaring**.

## 12. SDK / JDK / build policy (DECIDED policy, versions at M0)

**AD-014 — Toolchain policy.**

- `minSdk = 26` (Android 8.0): java.time native, VectorDrawable full
  support, ~95%+ device coverage; going lower buys nothing for a
  finance app and costs desugaring/drawable shims.
- `compileSdk`/`targetSdk`: latest stable at M0 start.
- AGP: latest stable at M0 (requires JDK 17); Kotlin: latest stable
  2.x; Compose: BOM-managed.
- Exact versions resolved at M0 against current releases — P4 selects
  libraries, not numbers (§33).
- Build types: `debug` + `release` only — no flavors. `baseUrl` via
  `BuildConfig` per type (debug→dev URL, release→prod URL — **required
  Human input**, see Questions). No secrets in BuildConfig. Release:
  R8 + resource shrinking; `usesCleartextTraffic=false`; a **debug-only**
  `networkSecurityConfig` may permit cleartext **solely to the dev host**
  (`10.0.2.2`/localhost) if Q3's dev URL is plain HTTP — release never
  allows cleartext. Backup rules exclude the token DataStore file.
- Lint/detekt: `lint` + `detekt` (warning-only — accepted tradeoff, not
  a hollow gate: `test`/`lint`/build are the failing gates; coverage
  verification is consciously deferred rather than faked).

## 13. Auth & session architecture (DECIDED)

**AD-010 / AD-011.**

```text
Login/Register ──► AuthRepository ──► AuthApi
                     │ success(token, expiresAt)
                     ▼
              SessionManager (singleton, StateFlow<Session>)
                ├── TokenStore.persist(token)     [Keystore-AES + DataStore]
                ├── InMemorySession { token, expiresAt }
                └── session events: SignedOut | Restoring | SignedIn | Expired

AuthInterceptor: adds Bearer for every request (if SignedIn)
Any response 401 ON AN AUTHENTICATED CALL ─► SessionManager.expire()
   (login/register 401s → inline form errors; logout 401 → ignored)
   ─► root effect: navigate SessionExpired (auth graph over main stack)
   ─► resume-login success: pop BOTH Login and SessionExpired entries
      → prior destination + prior VM state restored
   ─► if resume-login username ≠ stored username → no resume:
      popUpTo(0) → main graph root (identity-continuity guard)
   ─► cold login (not a resume) → popUpTo(0) → main graph
   ─► SessionExpired also offers Sign out → clear → popUpTo(0) → auth
Logout ─► best-effort POST /api/auth/logout ─► TokenStore.clear()
   ─► popUpTo(0) → auth graph
```

- **No refresh**: `expiresAt` may pre-warn (optional preemptive expiry)
  but a 401 on an authenticated call is always authoritative →
  `Expired` event → SessionExpired screen. Never a silent
  retry-with-new-token (INV-009).
- **TokenStore**: Preferences DataStore holding **token + expiresAt +
  last-username**; token value encrypted with an **Android Keystore
  AES/GCM key** via a small internal cipher class (~80 lines, standard
  pattern). `EncryptedSharedPreferences` is **deprecated**
  (androidx.security:security-crypto, 2025 — refs §40) and is NOT used.
  File excluded from Auto Backup. Tink-AEAD noted as a heavier
  alternative if a crypto lib is ever desired. In-memory copy lives
  only in `SessionManager`.
- **Restoring** on cold start: read token → absent → unauth graph;
  present but `expiresAt` already past → unauth graph directly (no
  flicker); present and unexpired → `SignedIn` (a 401 still expels at
  any time).
- **Session-boundary hygiene**: repositories hold session-scoped
  in-memory caches keyed to the session; on `SignedOut`/`Expired`/
  new `SignedIn` event from `SessionManager` they **invalidate all
  caches** — no user's data can leak across sessions on one device.
- **Form preservation (AD-011)**: the Create-Transaction nav entry
  stays on the back stack while SessionExpired+Login push on top; its
  ViewModel lives → after resume-login, pop back → `UiState` intact.
  The same mechanism preserves any back-stack form — the product
  *promise* is only the transaction draft (per accepted UX); other
  forms restoring is a harmless superset — their copy still reads
  "You will return to this screen." Process death → drafts lost
  (SavedStateHandle may keep scalar fields cheaply — optional nicety).
- **Resume vs cold login**: resume-login pops both Login and
  SessionExpired to restore the preserved stack; a fresh cold login
  uses `popUpTo(0)` → main graph — the Login route carries a
  `resume` flag to distinguish the two.
- Register: on success → navigate to Login w/ `username` prefill +
  confirmation banner (snackbar arg).
- Logout: confirmation dialog → best-effort call (401 fine) → clear →
  unauth root.

## 14. Repository architecture (DECIDED)

**AD-016 — Cohesive repositories over `suspend` API services.**

| Repository | Surface (real endpoints) |
|---|---|
| `AuthRepository` | register, login, logout, session persistence hooks |
| `AccountRepository` | `GET/POST /api/accounts`; `GET /{id}` wrapped for account-detail refresh; Account detail embeds `TransactionRepository.list(accountId)` |
| `TransactionRepository` | `GET/POST /api/transactions` (+filters) |
| `DashboardRepository` | `GET /api/dashboard?month=` |
| `ImportRepository` | `POST /api/imports` (multipart); `GET /api/imports/{id}` wrapped but **unused in V1** |
| `RecurringRepository` | list/get/create/deactivate |
| `BudgetRepository` | list/get/create + `/{id}/calculation` |
| `RuleRepository` | list/get/create/disable |
| `CategoryRepository` | `GET /api/categories` (in-memory cached per session) |

Shape: interface in `core/data/<name>/`, `XxxRepositoryImpl` beside it
(depending on `core/network` services) — flat, no per-endpoint repos.
Mapping happens **in the repository**: `suspend fun list(): List<Txn>`
returns domain models; failures propagate as `AppError`-typed
exceptions (via `toAppError()` at this boundary) that ViewModels catch
→ `UiState`. **Decided now**: throw + catch — no mixed
`Result`/exception conventions.

**Use-case policy**: ViewModel→Repository direct calls by default. A
named use-case class is justified ONLY for cross-repository or
multi-step orchestration. V1 expects **zero** — flagged candidates
watched: none meet the bar (import is one call; dashboard is one call).

## 15. DTO → domain → UI boundaries (DECIDED)

Two explicit model layers, not three:

- **`network` DTOs** (`*Dto`): mirror the wire — nullable transport
  fields, `String` enums/dates, `BigDecimal` amounts. Never imported by
  feature code.
- **`core/model` domain types**: `Money`, `CurrencyCode`, typed IDs,
  real enums w/ `Unknown`, `LocalDate`/`Instant`/`YearMonth`, non-null
  where the contract guarantees presence. ViewModels/UI consume only
  these.
- **UI formatting** at the composable edge via `core/ui/format`
  functions — no per-screen `XxxUiModel` clones unless a screen
  genuinely reshapes data (import result row is the one sanctioned
  screen-model).
- Mappers live beside repositories (`*Mappers.kt`), pure functions —
  JVM-testable.
- `ProblemDetail` → `ApiException(status, title?, fields?)` at the
  network interceptor — no DTO leakage upward.

## 16. Error architecture (DECIDED)

**AD-012 — sealed `AppError`, mapped once at the repository boundary.**

`ProblemDetail → ApiException` at the interceptor; `ApiException →
AppError` via a single `toAppError()` mapper invoked **at the repository
boundary** — repositories throw `AppError`-typed failures, ViewModels
catch → `UiState`. (One error representation reaches features; the
Result-vs-throw question is settled: **throw + catch in VM** — no mixed
convention.)

```kotlin
sealed class AppError {           // shape only
  object NetworkUnavailable;      // IOException/no connectivity
  object SessionExpired;          // 401 on authenticated calls — consumed globally, rarely reaches a screen
  data class Validation(fields: Map<String,String>);  // 400 + fields map
  object NotFound;                // 404 (possibly empty body)
  object Conflict;                // 409 (optimistic lock / unique constraints)
  object BadRequest;              // 400 without fields / swallowed detail
  object Server;                  // 5xx
  data class Unknown(t: Throwable);
}
```

Rules: map by **status + `fields` presence only** — never parse
`title`/`detail` text (INV: PARTIALLY_TYPED contract). `fields` keys
matched to form field names case-sensitively where known; unmatched
keys surface in a generic banner line. 404 → "…no longer available —
reload" copy patterns. 409 → "The data changed — reload and retry" or
the specific conflict copy (duplicate budget/rule-priority → inline).
Register-duplicate is an indistinguishable 400-without-fields → the
register form uses hedged copy: "the username may already be taken —
check the details." 401 on an authenticated call → `SessionExpired`
event, not a screen error (login/register 401s → inline form errors).
Unknown → generic "Something went wrong — try again" + retry affordance.

## 17. Retry / idempotency policy (DECIDED)

| Operation | Policy |
|---|---|
| GET (all reads) | 1 automatic retry on `NetworkUnavailable` w/ ~500ms backoff; manual Retry always offered; no auto-retry on 4xx/5xx |
| POST create (txn/account/recurring/budget/rule) | **NEVER auto-retry** — no idempotency key; submit-lock on tap; ambiguous failure → error + manual retry w/ dedup-risk copy where relevant (txn: "check your history first") |
| Register/Login | manual retry only |
| Import upload | NO auto-retry; interruption → explicit "re-upload is safe — already-imported rows are skipped" (dedup contract makes it true) |
| Logout | best-effort; local session always cleared |

Submit-lock: `isSubmitting` disables the action + back/cancel while a
write is in flight (per accepted UX).

## 18. Transactions data strategy (DECIDED)

- Full-list fetch on entry + pull-refresh (unbounded — accepted P3
  limitation; no Paging-3 facade, no fake incremental loading).
- Server filters (`accountId`/`type`/`category`) sent as query params;
  provenance filter (Manual/Imported/Recurring) is **local-only** over
  the loaded list — labeled as such in the model (`LocalFilter`).
- Client-side grouping by account is honest; sorting is NOT offered
  (no reliable order field exists — INV-014).
- Rows carry no date; detail screen = same row data (no `GET /{id}`
  exists) — detail ViewModel receives the loaded row via nav arg
  (`TransactionId` → look up in the repository's session cache, or
  pass the serialized row; decision: repository in-memory cache
  lookup by ID — clean back-nav behavior, refetch-safe).

## 19. Local data / cache policy (DECIDED)

**AD-013 — NO Room / no financial ledger persisted locally.
NETWORK_REQUIRED.**

- **In-memory only**: accounts list, transactions, dashboard,
  categories — caches inside repositories, keyed to the session and
  **invalidated on every session transition** (SignedOut / Expired /
  new SignedIn, §13); reload on screen entry/refresh.
- **DataStore persists exactly three things**: the encrypted token,
  its `expiresAt`, and the last-used username (login prefill + resume
  identity check). Nothing financial.
- Room rejected for V1: no sync/change-feed contract exists, so a local
  ledger would either be a lie (stale-as-truth) or a re-implementation
  of backend ownership. **Future trigger**: backend ships a
  sync/delta contract OR the Human authorizes read-model caching with
  explicit staleness UI — then re-evaluate Room.

## 20. Import architecture (DECIDED)

**AD-017 — System `OpenDocument` picker → streaming multipart upload →
synchronous blocking UI → typed result.**

```text
OpenDocument(arrayOf("application/pdf","text/csv","text/comma-separated-values",
                     "text/x-comma-separated-values","application/vnd.ms-excel",
                     "application/octet-stream"))
  → Uri → contentResolver: name (OpenableColumns.DISPLAY_NAME), size (SIZE)
  → client-side extension check (.csv/.pdf) + size guard (warn >~1MB backend cap, still attempt)
  → file selection held as in-screen state (never a nav arg — Uri is not a route arg)
  → InputStreamRequestBody(stream)  [okio-backed RequestBody — streams, no full-file RAM]
  → ImportsApi.import(file=MultipartBody.Part("file"), accountId=@Query)
  → ImportBatchSummaryResponse DTO → ImportResult domain
  → ImportResultScreen (tiles + grouped rows; row labels show wire rowNumber
    as-is — CSV rows are file-line-based, PDF rows report-based; copy
    says "row" without asserting a uniform numbering scheme)
```

Rules:

- No storage permission needed; no persistable URI grant (one-shot
  stream).
- `InputStreamRequestBody` streams; content-length set when
  `OpenableColumns.SIZE` resolves, else chunked — never reads the whole
  file into memory.
- Processing screen: indeterminate; `BackHandler` disabled; no cancel
  (no server cancel exists). App backgrounded mid-upload → outcome
  unknown → on resume, if the call is still in-flight keep processing;
  if the process died, the batch is orphaned (no list endpoint) →
  documented limitation; dedup makes re-upload safe and this is the
  accepted copy.
- 401 mid-upload → Session-expired flow; after re-login, user may
  re-upload (dedup-safe).
- No Android-side parsing of ANY file content (INV-005).
- `GET /api/imports/{id}` exists but without a list endpoint it's not a
  V1 screen — result shown once, then the ledger carries `Imported`
  provenance.

## 21. Supporting features (DECIDED boundaries)

- **Recurring**: `RecurringRepository`; create form locks `currency` to
  the chosen account (renders read-only) — makes defect D14
  unreachable; deactivate = confirm dialog; "new entries can take a
  moment to appear" copy (≤15-min scheduler); no run-now control.
- **Budgets**: `BudgetRepository`; hero = current `YearMonth` labeled
  explicitly; create: category + month + limit + currency — defaulted
  to the portfolio currency when uniform, **explicit user selection
  (constrained to portfolio currencies) when mixed**; detail uses
  `/calculation`; duplicate category+month → 409 → inline conflict
  copy; unfixable current-month note on detail.
- **Rules**: `RuleRepository`; create = condition type (Contains/
  Equals) + value + fixed-5 category + priority; `enabled` never
  offered (backend ignores it); disable w/ confirm; disabled rows
  shown inactive. Copy explains rules match the imported-statement
  description, and the manual hint isn't saved.

## 22. Coroutines / concurrency (DECIDED)

- Structured concurrency everywhere: `viewModelScope` for UI work;
  `suspend` repositories (no callbacks/RxJava).
- **Dispatchers via a `DispatcherProvider` abstraction**
  (`core/dispatchers`) injected into repositories/VMs — `Dispatchers.IO`
  for network/file I/O, `Default` for CPU mapping — never literal
  `Dispatchers.IO` in feature code; tests inject `StandardTestDispatcher`.
- Cancellation: screen-pop cancels VM work; uploads run under the
  Import VM — leaving mid-upload (allowed only by process death/back
  blocked) leaves an orphaned call that safely completes or fails.
- No `GlobalScope`, no `runBlocking`.

## 23. Dependency injection (DECIDED)

**AD-015 — Hilt.**

Why: official Google-recommended DI for Compose apps; `@HiltViewModel`
+ `hiltViewModel()` integrates cleanly with Navigation back-stack
entries (needed for the draft-preservation mechanism); compile-time
graph validation; `@TestInstallIn`/`@UninstallModules` for test
substitution; KSP-era build cost is modest for this size.

Rejected: **Koin** — runtime resolution, no compile-time graph check;
works, but offers no advantage here (senior used it — not a reason to
inherit); **manual DI / AppContainer** — viable for tiny apps, but the
VM-per-nav-entry + test-substitution story requires hand-rolled
factories that Hilt gives free; re-evaluate only if Hilt setup blocks
M0.

Scoping: `@Singleton` repositories/SessionManager/OkHttp; VMs per
nav-entry; assisted injection avoided in V1.

## 24. State restoration & lifecycle (DECIDED)

| Concern | Mechanism |
|---|---|
| Screen data | ViewModel + StateFlow (survives rotation) |
| Filter selections | SavedStateHandle in Transactions VM |
| Transaction draft | survives config change + session-expiry (back-stack VM); scalar fields optionally in SavedStateHandle |
| Other forms | lost on expiry/process death (accepted) |
| Token | DataStore (encrypted) — survives process death; `Restoring` state on cold start |
| Username prefill | DataStore |
| Financial data | NEVER persisted — always re-fetched (INV-015) |
| Top-level destination | Navigation restores; launch re-selects start by session |

Process death during import: documented limitation (§20); on next open
the user re-uploads (dedup-safe) — no fake status recovery.

## 25. Test architecture (DECIDED)

**AD-021 — JVM-first pyramid.**

| Layer | Scope | Tools |
|---|---|---|
| Pure unit | mappers (DTO→domain incl. enum/Unknown paths, BigDecimal, currency), `Money`/`formatters`, validation rules, `reduce`-style transitions | JUnit4 + Kotlin test assertions |
| Network | Retrofit services + ProblemDetail→ApiException, multipart call shape, interceptor auth header | MockWebServer |
| Repository | fake API → domain + error mapping, caching | hand-written fakes |
| ViewModel | UiState transitions, effects, filters, expiry events, submit-lock | coroutines-test + Turbine; fake repos |
| Compose UI | critical flows: login, create-txn states, txn list/filters, import states, session-expired resume, budgets ring | compose-ui-test-junit4 (androidTest, thin) |
| Integration-ish | session-expiry end-to-end w/ fake api | Robolectric optional — only if needed |

Policy: **fakes over mocks** for repositories/APIs (P1 lesson); MockK
allowed sparingly for edge cases. No UI test depends on network.
Screenshot testing deferred. Instrumentation kept minimal — JVM covers
~90% of logic.

## 26. Fake/mock policy (DECIDED)

- Production graph NEVER binds fakes — test sources only.
- `core/ui/preview` holds synthetic `UiState` fixtures for `@Preview`
  (not network fakes).
- No demo/mock mode in the shipped app.

## 27. Logging (DECIDED)

**AD-020.**

- `Timber` debug tree in debug builds; no logging framework in release
  (or a no-op tree).
- OkHttp `HttpLoggingInterceptor`: debug-only, `Level.HEADERS`,
  `redactHeader("Authorization")`, `redactHeader("Cookie")` — never
  `BODY` (financial payloads + multipart file bytes).
- Never log: passwords, token, file bytes, account/txn payloads, PII.
- Debug diagnostics allowed: route names, status codes, timings,
  AppError categories.

## 28. Security invariants (DECIDED)

- HTTPS only; `usesCleartextTraffic=false`.
- No embedded backend credentials, no API keys, no obfuscation theater.
- Token: Keystore-encrypted DataStore; memory in `SessionManager`;
  cleared on logout/expiry; excluded from backup.
- Backend authorization is authoritative; 404-on-foreign-id treated as
  normal.
- No client-side financial trust claims; no secret in BuildConfig.
- R8 shrinking+obfuscation on release (standard hygiene — not a
  security claim).

## 29. Performance (DECIDED rules)

- No heavy work in composables; mapping in VMs/repos off-main.
- `LazyColumn` keyed lists; stable `UiState` types; `remember`/
  `derivedStateOf` for derived collections.
- Money formatting memoized per row (`remember(money)`).
- Streaming upload (§20) — no full-file RAM.
- Unbounded lists are accepted (small V1 ledgers); skeleton > spinner
  for first-load.
- No premature perf infra (no baseline profiles/benchmarks in V1).

## 30. Accessibility (DECIDED)

- ≥48dp touch targets (M3 defaults; prototype's 44px floor preserved).
- contentDescriptions on every icon-only control ("Add transaction").
- Amounts: currency code + sign + word — screen-reader friendly
  (`semantics` merged).
- Focus order logical; visible focus; errors announced + linked.
- Reduced-motion respected via `ANIMATOR_DURATION_SCALE` (skeletons,
  pulse, FAB animation).
- Large-text resilience: currency suffix never wraps away; rows use
  flexible layouts.

## 31. Analytics / background work (DECIDED)

- **No analytics SDK, no crash-reporting SDK in V1** (P3 exclusion +
  privacy stance). Crash reporting = future Human decision.
- **No WorkManager** — synchronous import, server-scheduled recurring,
  no background sync contract. (Rejected — documented.)

## 32. Backend gaps (P4 status)

No new gaps found. BE-001…BE-011 (PRODUCT.md §41) stand; most impactful
for architecture: BE-002 (refresh token — would simplify session UX),
BE-001 (dates — would add a field to the txn model later; designed to
absorb without refactor since `occurredOn` is simply absent today).
P3's `YES_WITH_ACCEPTED_LIMITATIONS` verdict stands — nothing in P4
overturns it.

## 33. Dependency policy (chosen libraries; versions at M0)

| Library | Purpose | Why | Alternatives rejected |
|---|---|---|---|
| Compose BOM + Material3 | UI toolkit | official; matches accepted UX | XML views |
| Navigation Compose | routing | typed routes, VM-per-entry | hand-rolled, Cicerone |
| Lifecycle/ViewModel | state ownership | required for MVVM | — |
| Hilt (+KSP) | DI | official; nav-scoped VMs; test substitution | Koin, manual |
| Retrofit + OkHttp | HTTP | standard; multipart; interceptors | Ktor client |
| converter-kotlinx-serialization | Retrofit JSON | pairs w/ chosen serializer | Moshi conv. |
| kotlinx.serialization | JSON + nav routes | official; compile-time | Moshi, Jackson |
| kotlinx-coroutines | async | standard | RxJava |
| DataStore Preferences | token/username storage | current recommendation | SharedPrefs, EncryptedSharedPreferences (deprecated) |
| java.time | temporal | native at minSdk 26 | desugar (unneeded), ThreeTenBP |
| Timber | logging | tiny, tree model | Log direct |
| — no Coil/Glide | bundled assets only | `Image` decodes bundled WebP | remote loading unneeded |
| — no Room/Paging | no local ledger (AD-013) | — | — |
| — no WorkManager | no bg work (§31) | — | — |
| JUnit4, coroutines-test, Turbine, MockWebServer, compose-ui-test, (Robolectric opt.) | tests | minimal justified set | heavier stacks |

## 34. Post-P4 implementation roadmap (proposal; each gets a brief)

| MS | Goal | Main deliverables | Depends on | Acceptance gate |
|---|---|---|---|---|
| **M0** | Foundation | Gradle scaffold, toolchain, Hilt app, `SpendwiseTheme` skeleton, nav shell (auth+main graphs, placeholder routes), network plumbing (client/interceptors/ApiError), session/TokenStore, repo interfaces, test scaffold, lint/detekt, CI-less local gates | — | builds; JVM+Compose tests green; no feature UI |
| **M1** | Auth & Session | Welcome/Login/Register/Session-expired flows, auth interceptor live, resume-after-login, logout | M0 | register→login→session works vs dev backend |
| **M2** | Accounts + Design system core | accounts list/detail/create, Sw* components needed so far, heroes | M1 | real balances render; empty/error/loading states |
| **M3** | Transactions | list+filters, detail, create (all honest limits), provenance chips | M2 | record + browse works end-to-end |
| **M4** | Home/Dashboard | hero month totals, carousel, recent activity, mixed-currency state | M3 (needs txn list) | currency-safety rule verified |
| **M5** | Import | picker→multipart→blocking→result, dedup messaging | M2 (accounts) + file plumbing | CSV+TPBank import w/ real backend |
| **M6** | Planning | recurring (currency-locked), budgets, rules | M3/M4 | all supporting flows green |
| **M7** | Hardening | a11y pass, perf, error polish, release build config | all | full quality gates green |

Each milestone: brief → Flux → Sentinel → Argus → Human, per workflow.

## 35. M0 definition (exactly)

**M0 — Android Foundation** includes ONLY:

- Gradle/AGP/Kotlin/Compose scaffold (`:app` module), package skeleton
  per §2;
- Hilt bootstrap (`@HiltAndroidApp`, `@AndroidEntryPoint` activity);
- `SpendwiseTheme` skeleton: color/typography/spacing tokens from the
  handoff (component set lands incrementally per milestone);
- Navigation shell: NavHost + auth/main graphs + placeholder screens +
  bottom-bar visibility rules;
- Network plumbing: OkHttp+Retrofit wiring, auth+error interceptors,
  `AppError` model, Kotlinx setup + BigDecimal serializer;
- `SessionManager`/`TokenStore` skeleton (Keystore cipher);
- Repository interfaces (signatures only);
- Test scaffolding: 1 JVM test + 1 Compose test passing;
- Quality gates: `assembleDebug`, `test`, `lint`, `detekt` (warn-only).

**M0 explicitly does NOT implement**: login UI, accounts, transactions,
import, or any feature screen beyond placeholders. Optional smoke: a
health-endpoint call behind a debug-only trigger — permitted, not
required.

## 36. Quality gates (M0+)

- `./gradlew assembleDebug` — must pass.
- `./gradlew test` — must pass (unit tests fail the gate).
- `./gradlew lint` — errors fail.
- `detekt` — warnings allowed (visible, not gate-failing) per P1's
  hollow-gate lesson (gates fail loudly or don't exist).
- Compose compiler metrics — optional diagnostics.
- Release build check at M7 (`assembleRelease` w/ R8).
- No CI configured during P4 — local gates documented; CI = future
  decision.

## 37. Diagrams

### Layers / dependencies

```text
┌────────────────────────────── feature/* ──────────────────────────────┐
│ Route composable ─► ViewModel (StateFlow<UiState> + effects) ─► Screen │
└──────┬─────────────────────────────────────────────────┬─────────────┘
       │ calls suspend                                   │ renders
       ▼                                                 │
┌─ core/data: repositories (interfaces + impls) ─ maps DTO→domain ──────┐
└──────┬─────────────────────────────────────────────────┬────────────┘
       │ Retrofit services                                │ core/model:
       ▼                                                  │ Money, IDs,
┌─ core/network: *Api + DTOs + interceptors + ApiError ─  │ enums(+Unknown)
└──────┬─────────────────────────────────────────────────┴────────────┘
       ▼ HTTP/JSON (BigDecimal-safe)
   Spendwise Spring Boot backend (authoritative)

core/designsystem, core/navigation, core/session, core/ui cross-cut the
feature layer; app/ wires Hilt + entry points.
```

### Request flow (read)

```text
Screen intent → VM.action() → repo.suspendGet()
  → *Api (AuthInterceptor adds Bearer)
  → OkHttp → backend → 200 DTO
  → mapper → domain → UiState.Content → Screen
errors: non-2xx → ErrorInterceptor → ApiException{status, fields}
  → repo boundary toAppError() → VM catch → UiState.*Error
401 on an authenticated call → SessionManager.expire() → SessionExpired
```

### Auth / session flow

```text
Welcome → Login → AuthRepository.login()
  → token → TokenStore(Keystore-AES→DataStore) + SessionManager.SignedIn
  → nav: popUpTo(0) → main graph
401 → Expired → SessionExpired screen → Login → success → popBack
  → prior entry+VM intact → draft preserved (txn form only by design)
Logout → confirm → logout() best-effort → clear → popUpTo(0) → auth
```

### Import flow

```text
ImportChoose(accountId, file)
 → OpenDocument → Uri(name,size,ext)
 → InputStreamRequestBody → ImportsApi.import(multipart file, query accountId)
 → ImportProcessing (blocking, back disabled)
 → ImportBatchSummaryResponse → ImportResult{imported, duplicates,
   failed, rows[]}
 → ImportResultScreen → "Done" → Transactions refresh
interruption → "retry is safe — already-imported rows are skipped"
```

## 38. Architecture decision records (all PROPOSED)

| AD | Decision (short) |
|---|---|
| AD-001 | Single `:app` module, package boundaries; extraction triggers defined |
| AD-002 | MVVM Route→VM→stateless Screen; no mandatory reducer framework |
| AD-003 | Per-screen immutable `UiState` w/ discriminated status + orthogonal form/submit state + one-shot effects |
| AD-004 | Navigation Compose typed routes; auth+main graphs; expiry overlay preserving back stack |
| AD-005 | Retrofit + OkHttp network stack |
| AD-006 | kotlinx.serialization + custom BigDecimal serializer; `ignoreUnknownKeys` |
| AD-007 | `Money`/`CurrencyCode`/typed String-ID value classes |
| AD-008 | Defensive enum mapping w/ `Unknown(raw)` — never mislabel |
| AD-009 | java.time; `LocalDate`/`Instant`/`YearMonth`; no manufactured dates |
| AD-010 | `SessionManager` + Keystore-AES + Preferences-DataStore token (EncryptedSharedPreferences deprecated — rejected) |
| AD-011 | Session-expired resume via preserved back stack; txn draft restored, other forms not |
| AD-012 | Sealed `AppError` mapped at network boundary; status+`fields` only; no message sniffing |
| AD-013 | No Room / no local financial ledger; NETWORK_REQUIRED; DataStore = token+username only |
| AD-014 | minSdk 26 / latest-stable toolchain / debug+release / BuildConfig base URL / R8 |
| AD-015 | Hilt DI |
| AD-016 | Nine cohesive repositories; orchestration-only use-case rule (V1 expects zero) |
| AD-017 | `OpenDocument` + streaming `RequestBody` multipart; blocking processing, no cancel |
| AD-018 | `SpendwiseTheme` custom tokens over M3, light only; `Sw*` components; Lucide+Manrope+Fluent-3D assets w/ license discipline |
| AD-019 | Scroll-derived extended↔compact Home FAB; header `+` on Transactions; single-row filters |
| AD-020 | Timber debug-only + redacted-header HTTP logs; no body logging; no sensitive logging |
| AD-021 | JVM-first test pyramid; fakes over mocks; Turbine; MockWebServer; thin androidTest |
| AD-022 | No WorkManager / no analytics / no crash SDK in V1 |

Every AD: status=PROPOSED · HUMAN_APPROVAL_REQUIRED=YES · promotion to
`DECISIONS.md` happens at a later closeout task after the Human gate.

## 39. Open questions for THE HUMAN

| # | Question | Recommendation | If different |
|---|---|---|---|
| Q1 | Home hero label — "This month" (server-computed via dashboard) vs prototype's "Recorded totals"? | "This month" — honest (server scopes by stored occurredOn) and clearer | keep "Recorded totals" copy |
| Q2 | minSdk 26 acceptable? | yes (~95%+ devices; native java.time) | minSdk lower → add desugaring + drawable shims (cost) |
| Q3 | Dev + prod backend base URLs (needed at M0 for BuildConfig)? | Human supplies both | M0 blocked on config input |
| Q4 | Release signing/distribution ownership — who holds the release keystore / is Play distribution in scope? | Human owns keystore; V1 targets internal/direct install first | defer release signing; document in M7 |

## 40. Current-source references (verification log)

- androidx.security:security-crypto (EncryptedSharedPreferences,
  EncryptedFile, MasterKey) — **deprecated**; guidance points to plain
  DataStore + Keystore-generated keys (developer.android.com API
  reference, deprecated class notices; deprecation wave 2025).
- Navigation Compose type-safe `@Serializable` routes — official since
  Navigation 2.8.
- DataStore Preferences — current official recommendation over
  SharedPreferences.
- AGP 8.x requires JDK 17 — standard current requirement.
- Lucide (ISC), Manrope (OFL 1.1), Fluent Emoji (MIT) licenses —
  verified in prototype manifest, copies retained.

## 41. Limitations of this blueprint

- Pseudocode shapes are contracts for structure, not final APIs.
- Exact dependency versions deferred to M0 (anti-staleness policy §33).
- The 15-min session and one-shot import remain accepted product
  limitations — architecture makes them honest, not hidden.
- Process-death during import leaves an orphaned batch (no list
  endpoint) — documented, not fixed client-side.
- P3A's R4 report recorded the inverse FAB trigger to the task
  statement; the task statement is canonical (recorded in handoff §3)
  — mechanism identical either way.
