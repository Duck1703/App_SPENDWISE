# ROADMAP.md — Spendwise Android

High-level phase plan. Milestone briefs are created per phase by ATLAS using
[templates/MILESTONE-BRIEF.md](templates/MILESTONE-BRIEF.md).

## Phases

| Phase | Name | Status |
| --- | --- | --- |
| P0 | Agent Company + Workspace Governance | COMPLETE (see [milestones/P0-BOOTSTRAP.md](milestones/P0-BOOTSTRAP.md)) |
| P1 | Senior Android Reference Architecture Audit | READY_FOR_HUMAN_REVIEW (see [milestones/P1-SENIOR-ANDROID-REFERENCE-AUDIT.md](milestones/P1-SENIOR-ANDROID-REFERENCE-AUDIT.md), [ANDROID-BASELINE.md](ANDROID-BASELINE.md)) |
| P2 | Spendwise Backend Contract Audit | Planned |
| P3 | Spendwise Android Product Blueprint | Provisional |
| P4 | Android Architecture Blueprint | Provisional |
| — | **HUMAN GATE** — architecture + product approval before any implementation | — |
| M0 | Android Foundation | Provisional |
| M1 | Accounts vertical slice | Provisional |
| M2 | Transactions vertical slice | Provisional |
| M3 | Create Income / Expense | Provisional |
| M4 | Transfers | Provisional |
| M5 | CSV / PDF Import | Provisional |
| M6 | Basic Dashboard | Provisional |
| M7 | Hardening / UX / Testing | Provisional |

## Important

- **Everything after P2 is PROVISIONAL** until the P1/P2 audits are complete and
  THE HUMAN approves the P3 product blueprint and P4 architecture blueprint.
- P1 and P2 are audit phases: they produce evidence documents, not application code.
- No Android production code may be written before the HUMAN GATE is passed.
- M0–M7 implementation requirements are intentionally undefined here; each gets a
  milestone brief only when reached.
