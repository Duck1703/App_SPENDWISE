# AGENTS.md — Spendwise Android

Canonical entrypoint for any AI agent or AI-assisted tool working in this repository.
This file is intentionally tool/vendor neutral; it applies to every agent runtime.

## Mandatory startup procedure

Before performing ANY task in this repository:

1. Read [agent-system/WORKFLOW-CONTRACT.md](agent-system/WORKFLOW-CONTRACT.md) — the governing workflow contract.
2. Identify your assigned role (ATLAS, FLUX, SENTINEL, or ARGUS).
3. Read that role's canonical file under [agent-system/agents/](agent-system/agents/).
4. Read the relevant project-control artifacts under [project-control/](project-control/) — at minimum `PRODUCT.md`, `ROADMAP.md`, `WORKSPACE-BOUNDARIES.md`, and the active milestone brief.
5. Honor the workspace boundaries recorded in [project-control/WORKSPACE-BOUNDARIES.md](project-control/WORKSPACE-BOUNDARIES.md).
6. Do not assume prior architecture decisions that are not recorded in [project-control/ARCHITECTURE.md](project-control/ARCHITECTURE.md) or [project-control/DECISIONS.md](project-control/DECISIONS.md).
7. Stop on human-gate and stop conditions defined in the workflow contract.

## Canonical roles

| Role | Canonical file | Function |
| --- | --- | --- |
| ATLAS | [agent-system/agents/atlas.md](agent-system/agents/atlas.md) | Lead / Android Architect / Delivery Coordinator |
| FLUX | [agent-system/agents/flux.md](agent-system/agents/flux.md) | Android Developer |
| SENTINEL | [agent-system/agents/sentinel.md](agent-system/agents/sentinel.md) | Test Engineer |
| ARGUS | [agent-system/agents/argus.md](agent-system/agents/argus.md) | Independent QA Reviewer |

There are exactly four roles. Do not invent additional agents.

THE HUMAN is the ultimate authority. No agent may override THE HUMAN.

## Canonical flow

```
THE HUMAN → ATLAS → FLUX → SENTINEL → ARGUS → ATLAS → THE HUMAN
```

## Absolute workspace boundaries

- READ ONLY: `D:\app_spendwise\kotlin-accelerator-ai` (senior Android reference)
- READ ONLY: `D:\spendwise` (Spendwise backend)
- READ / WRITE: `D:\app_spendwise\spendwise-android` (this repository)

An Android task must never modify the reference Android project or the backend.
Backend gaps are reported, never silently patched.
