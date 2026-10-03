# P0 — BOOTSTRAP: Agent Company + Workspace Governance

Owner: ATLAS
Status: COMPLETE — pending human acknowledgment
Outcome: PASS

## Objective

Establish canonical governance and workspace safety before any Android application
code is created.

## Acceptance criteria

- [x] Product repository exists at `D:\app_spendwise\spendwise-android`.
- [x] Git initialized safely on branch `main` (new repo — Case A; no prior history existed).
- [x] Canonical agent company exists under `agent-system/`.
- [x] Four roles defined: ATLAS, FLUX, SENTINEL, ARGUS — no fifth agent.
- [x] Workflow contract exists (`agent-system/WORKFLOW-CONTRACT.md`).
- [x] Workspace boundaries explicit (`project-control/WORKSPACE-BOUNDARIES.md`).
- [x] Senior project verified read-only; baseline recorded, untouched
      (`project-control/WORKSPACE-BASELINE.md`).
- [x] Backend verified read-only; baseline recorded, untouched.
- [x] Project-control skeleton exists (this directory).
- [x] Future audit docs (ANDROID-BASELINE, BACKEND-CONTRACT, ARCHITECTURE) are
      honest placeholders marked NOT YET ESTABLISHED.
- [x] No Android app code created (no `app/`, Gradle files, `src/`, manifest).
- [x] No reference/backend modification — HEAD and working-tree state verified
      unchanged after bootstrap.
- [x] Repository clean after initial governance commit.

## Evidence

- Starting state: `D:\app_spendwise\spendwise-android` did not exist (Case A —
  created fresh; no unrelated user work, no pre-existing Git history).
- Reference baselines before/after: see
  [../WORKSPACE-BASELINE.md](../WORKSPACE-BASELINE.md); re-verified at end of P0 —
  both HEADs and working trees unchanged.
- Files changed: governance skeleton only (see initial commit).
- Commit: `chore: bootstrap Android agent governance` (SHA recorded in P0 final
  report and `git log`).

## Human gate

**P0 completion does NOT authorize Android implementation.** No production code
may be written until the roadmap human gate (after P4) is passed.

## Next authorized task

After HUMAN approval:

**P1 — Senior Android Reference Architecture Audit**

Read-only deep analysis of `D:\app_spendwise\kotlin-accelerator-ai`, producing an
evidence-backed [../ANDROID-BASELINE.md](../ANDROID-BASELINE.md) with KEEP /
ADAPT / REJECT classification. P1 creates no Android production code.
