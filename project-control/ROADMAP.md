# ROADMAP.md — Spendwise Android

High-level phase plan. Milestone briefs are created per phase by ATLAS using
[templates/MILESTONE-BRIEF.md](templates/MILESTONE-BRIEF.md).

## Phases

| Phase | Name | Status |
| --- | --- | --- |
| P0 | Agent Company + Workspace Governance | COMPLETE (see [milestones/P0-BOOTSTRAP.md](milestones/P0-BOOTSTRAP.md)) |
| P1 | Senior Android Reference Architecture Audit | HUMAN_APPROVED (see [milestones/P1-SENIOR-ANDROID-REFERENCE-AUDIT.md](milestones/P1-SENIOR-ANDROID-REFERENCE-AUDIT.md), [ANDROID-BASELINE.md](ANDROID-BASELINE.md)) |
| P2 | Spendwise Backend Contract Audit | HUMAN_APPROVED (see [milestones/P2-SPENDWISE-BACKEND-CONTRACT-AUDIT.md](milestones/P2-SPENDWISE-BACKEND-CONTRACT-AUDIT.md), [BACKEND-CONTRACT.md](BACKEND-CONTRACT.md), [reviews/P2-ARGUS-REVIEW.md](reviews/P2-ARGUS-REVIEW.md)) |
| P3 | Spendwise Android Product Blueprint | READY_FOR_HUMAN_REVIEW (see [milestones/P3-SPENDWISE-ANDROID-PRODUCT-BLUEPRINT.md](milestones/P3-SPENDWISE-ANDROID-PRODUCT-BLUEPRINT.md), [PRODUCT.md](PRODUCT.md), [reviews/P3-ARGUS-REVIEW.md](reviews/P3-ARGUS-REVIEW.md)) |
| P4 | Android Architecture Blueprint | NOT_STARTED |
| — | **HUMAN GATE** — architecture + product approval before any implementation | — |
| M0 | Android Foundation | PROVISIONAL_UNTIL_P4 |
| M1 | Authentication & Session (register/login/logout/expiry) | PROVISIONAL_UNTIL_P4 |
| M2 | Accounts (list/detail/create, balances) | PROVISIONAL_UNTIL_P4 |
| M3 | Transactions (list/filters/detail, income/expense entry, offset correction) | PROVISIONAL_UNTIL_P4 |
| M4 | CSV / TPBank PDF Import (select/upload/result) | PROVISIONAL_UNTIL_P4 |
| M5 | Dashboard / Home (balances + month summary) | PROVISIONAL_UNTIL_P4 |
| M6 | Planning (recurring, budgets, categorization rules) | PROVISIONAL_UNTIL_P4 |
| M7 | Hardening / UX / Testing | PROVISIONAL_UNTIL_P4 |

## Important

- **Everything after P2 is PROVISIONAL** until THE HUMAN approves the P3
  product blueprint and P4 architecture blueprint.
- P1, P2, and P3 are evidence phases: they produce documents, not
  application code. The M-sequence above was refined by P3's product
  blueprint (authentication precedes all verticals; transfers and other
  backend-gated capabilities carry explicit dependencies BE-001…BE-011
  in [PRODUCT.md](PRODUCT.md) §41 and have **no scheduled milestone**
  until the backend contract exists and THE HUMAN authorizes it).
- No Android production code may be written before the HUMAN GATE is passed.
- M0–M7 implementation requirements are intentionally undefined here; each gets a
  milestone brief only when reached.
