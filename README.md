# Spendwise Android

Android client for the Spendwise Spring Boot backend.

## Workspace relationship

| Location | Path | Mode |
| --- | --- | --- |
| Reference Android (senior project) | `D:\app_spendwise\kotlin-accelerator-ai` | READ ONLY |
| Product Android (this repository) | `D:\app_spendwise\spendwise-android` | READ / WRITE |
| Spendwise backend | `D:\spendwise` | READ ONLY to Android team |

## Current phase

**M0 — Android Foundation** (awaiting Human review)

The repository now contains a compiling Android foundation: single `:app`
module, Compose + Material 3 shell, Hilt graph, typed Navigation routes,
Retrofit/OkHttp + kotlinx.serialization plumbing, precise `BigDecimal` JSON
handling, Keystore-encrypted session storage, and a JVM-first test suite.

No product features exist yet — the four top-level destinations are
foundation placeholders. See
[project-control/milestones/M0-ANDROID-FOUNDATION.md](project-control/milestones/M0-ANDROID-FOUNDATION.md)
for the milestone record and scope boundaries.

## Building

Requirements: JDK 17, Android SDK (platform `android-36`), `local.properties`
with `sdk.dir=…`. Then:

```
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew lintDebug              # Android lint
./gradlew detekt                 # static analysis (warn-only)
./gradlew assembleRelease        # unsigned release compile (R8)
```

### API base URL

Property-driven (Human decision Q3), resolved in order:
`-Pspendwise.api.base.url=…` → `SPENDWISE_API_BASE_URL` env var →
`gradle.properties` default `https://example.invalid/`.

The committed default is intentionally non-routable; no real backend URL is
recorded in this repository.

## Governance

- Agent entrypoint: [AGENTS.md](AGENTS.md)
- Workflow contract: [agent-system/WORKFLOW-CONTRACT.md](agent-system/WORKFLOW-CONTRACT.md)
- Workspace boundaries: [project-control/WORKSPACE-BOUNDARIES.md](project-control/WORKSPACE-BOUNDARIES.md)
- Product principles: [project-control/PRODUCT.md](project-control/PRODUCT.md)
- Architecture: [project-control/ARCHITECTURE.md](project-control/ARCHITECTURE.md)
- Roadmap: [project-control/ROADMAP.md](project-control/ROADMAP.md)
- Decision log: [project-control/DECISIONS.md](project-control/DECISIONS.md)
