# Spendwise Android

Android client for the Spendwise Spring Boot backend.

## Workspace relationship

| Location | Path | Mode |
| --- | --- | --- |
| Reference Android (senior project) | `D:\app_spendwise\kotlin-accelerator-ai` | READ ONLY |
| Product Android (this repository) | `D:\app_spendwise\spendwise-android` | READ / WRITE |
| Spendwise backend | `D:\spendwise` | READ ONLY to Android team |

## Current phase

**P0 — Governance Bootstrap**

This repository currently contains governance and project-control artifacts only.
There is intentionally no Android application source, no Gradle configuration, and no
build scaffolding yet.

## Next phase

**P1 — Senior Android Reference Architecture Audit** — a read-only, evidence-backed
analysis of `D:\app_spendwise\kotlin-accelerator-ai` producing
[project-control/ANDROID-BASELINE.md](project-control/ANDROID-BASELINE.md).

## Governance

- Agent entrypoint: [AGENTS.md](AGENTS.md)
- Workflow contract: [agent-system/WORKFLOW-CONTRACT.md](agent-system/WORKFLOW-CONTRACT.md)
- Workspace boundaries: [project-control/WORKSPACE-BOUNDARIES.md](project-control/WORKSPACE-BOUNDARIES.md)
- Product principles: [project-control/PRODUCT.md](project-control/PRODUCT.md)
- Roadmap: [project-control/ROADMAP.md](project-control/ROADMAP.md)
- Decision log: [project-control/DECISIONS.md](project-control/DECISIONS.md)

No architecture decisions (dependency injection, networking, persistence, module
structure, state management, UI architecture) have been made yet. They will be
established only after the P1 reference audit, the P2 backend contract audit, and the
P4 architecture blueprint.
