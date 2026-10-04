# DECISIONS.md — Spendwise Android Decision Log

Lightweight decision log. Only confirmed decisions are recorded here — each with
an ID, the decision, and its authority. Decisions are append-only; reversals are
recorded as new decisions referencing the old ID.

## Confirmed decisions

| ID | Decision | Authority | Phase |
| --- | --- | --- | --- |
| D-001 | Workspace root is `D:\app_spendwise`. | THE HUMAN | P0 |
| D-002 | The senior Android project `D:\app_spendwise\kotlin-accelerator-ai` is reference-only (READ ONLY; never modified, never copied wholesale). | THE HUMAN | P0 |
| D-003 | The Spendwise backend `D:\spendwise` is read-only to the Android team; backend gaps are reported, never patched by Android agents. | THE HUMAN | P0 |
| D-004 | The new Android product repository is `D:\app_spendwise\spendwise-android`; it is the only writable location. | THE HUMAN | P0 |
| D-005 | The canonical agent system (`agent-system/**`, `project-control/**`) is tool/vendor independent; vendor-specific adapters may be generated later but are not source of truth. | THE HUMAN | P0 |
| D-006 | The team has exactly four roles: ATLAS (lead/architect), FLUX (developer), SENTINEL (test engineer), ARGUS (independent QA). | THE HUMAN | P0 |
| D-007 | THE HUMAN is the final authority: architecture approval, milestone go/no-go, scope changes, backend change requests, releases, and governance exceptions. | THE HUMAN | P0 |
| D-008 | No Android production code is written before the reference audit (P1), backend contract audit (P2), product blueprint (P3), and an approved architecture blueprint (P4) — enforced by the roadmap human gate. | THE HUMAN | P0 |
| AD-001 | Single `:app` Gradle module with disciplined package boundaries; module extraction only on defined triggers (build pain, second developer, independent versioning, repeated boundary violations). | THE HUMAN | P4 |
| AD-002 | MVVM UI architecture: Route composable → ViewModel → stateless Screen; no mandatory reducer/state-machine framework (DRE-KT not adopted). | THE HUMAN | P4 |
| AD-003 | Per-screen immutable `UiState` data class with discriminated status + orthogonal `isSubmitting`/`isRefreshing`/form state + one-shot effects channel. | THE HUMAN | P4 |
| AD-004 | Navigation Compose with `@Serializable` typed routes; separate auth and main graphs; session-expired overlay preserves the main back stack. | THE HUMAN | P4 |
| AD-005 | Retrofit + OkHttp as the HTTP stack. | THE HUMAN | P4 |
| AD-006 | kotlinx.serialization for JSON (and nav routes) + custom `KSerializer<BigDecimal>` emitting JSON numbers; `ignoreUnknownKeys=true`. | THE HUMAN | P4 |
| AD-007 | `Money(BigDecimal, CurrencyCode)`; `CurrencyCode` value class (open ISO-4217, syntactic validation, non-crashing); typed String-based ID value classes. | THE HUMAN | P4 |
| AD-008 | Defensive enum mapping with `Unknown(raw)` fallbacks; unknown values never mislabeled or crash. | THE HUMAN | P4 |
| AD-009 | `java.time` only (`LocalDate`/`Instant`/`YearMonth`); no manufactured dates; minSdk 26 ⇒ no desugaring. | THE HUMAN | P4 |
| AD-010 | `SessionManager` + Preferences DataStore with token encrypted via Android Keystore AES/GCM; `EncryptedSharedPreferences` (deprecated) rejected. | THE HUMAN | P4 |
| AD-011 | Session-expired resume via preserved back stack; transaction draft restored (product promise), other forms by same mechanism; `expiresAt` persisted; identity-continuity guard on resume. | THE HUMAN | P4 |
| AD-012 | Sealed `AppError`; `ApiException→AppError` via `toAppError()` at the repository boundary; status+`fields` mapping only — no message sniffing. | THE HUMAN | P4 |
| AD-013 | No Room / no local financial ledger; NETWORK_REQUIRED; DataStore limited to token + expiresAt + username. | THE HUMAN | P4 |
| AD-014 | minSdk 26; latest-stable compile/target SDK, AGP, Kotlin, Compose BOM; debug+release only; `baseUrl` via BuildConfig; R8 release. | THE HUMAN | P4 |
| AD-015 | Hilt for dependency injection (KSP codegen). | THE HUMAN | P4 |
| AD-016 | Nine cohesive repositories; use cases only for cross-repository/multi-step orchestration (V1 expects zero); throw `AppError`-typed + catch in VM. | THE HUMAN | P4 |
| AD-017 | `OpenDocument` picker + streaming `RequestBody` multipart upload; blocking processing UI; dedup-safe retry messaging. | THE HUMAN | P4 |
| AD-018 | `SpendwiseTheme` custom tokens over Material 3, light only; `Sw*` component family; Lucide→VectorDrawable, Manrope font, Fluent-3D WebP illustrations with license discipline. | THE HUMAN | P4 |
| AD-019 | Scroll-derived extended↔compact Home FAB; Transactions header `+` action; single-row filters (Account/Type[All-Income-Expense]/Category + local provenance). | THE HUMAN | P4 |
| AD-020 | Timber debug-only logging; HTTP logs redact Authorization, never bodies; no sensitive values logged. | THE HUMAN | P4 |
| AD-021 | JVM-first test pyramid; fakes over mocks; coroutines-test + Turbine + MockWebServer + thin Compose UI tests. | THE HUMAN | P4 |
| AD-022 | No WorkManager, no analytics SDK, no crash-reporting SDK in V1. | THE HUMAN | P4 |

## Explicitly undecided

Technology choices such as Hilt, Retrofit, Room, multi-module structure, MVI, and
Clean Architecture are NOT decided. They may be adopted only via P1/P2/P4
evidence and a recorded decision approved by THE HUMAN. See
[ARCHITECTURE.md](ARCHITECTURE.md).
