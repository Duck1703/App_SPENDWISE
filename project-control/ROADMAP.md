# ROADMAP.md — Spendwise Android

High-level phase plan. Milestone briefs are created per phase by ATLAS using
[templates/MILESTONE-BRIEF.md](templates/MILESTONE-BRIEF.md).

## Phases

| Phase | Name | Status |
| --- | --- | --- |
| P0 | Agent Company + Workspace Governance | COMPLETE (see [milestones/P0-BOOTSTRAP.md](milestones/P0-BOOTSTRAP.md)) |
| P1 | Senior Android Reference Architecture Audit | HUMAN_APPROVED (see [milestones/P1-SENIOR-ANDROID-REFERENCE-AUDIT.md](milestones/P1-SENIOR-ANDROID-REFERENCE-AUDIT.md), [ANDROID-BASELINE.md](ANDROID-BASELINE.md)) |
| P2 | Spendwise Backend Contract Audit | HUMAN_APPROVED (see [milestones/P2-SPENDWISE-BACKEND-CONTRACT-AUDIT.md](milestones/P2-SPENDWISE-BACKEND-CONTRACT-AUDIT.md), [BACKEND-CONTRACT.md](BACKEND-CONTRACT.md), [reviews/P2-ARGUS-REVIEW.md](reviews/P2-ARGUS-REVIEW.md)) |
| P3 | Spendwise Android Product Blueprint | HUMAN_APPROVED (see [milestones/P3-SPENDWISE-ANDROID-PRODUCT-BLUEPRINT.md](milestones/P3-SPENDWISE-ANDROID-PRODUCT-BLUEPRINT.md), [PRODUCT.md](PRODUCT.md), [reviews/P3-ARGUS-REVIEW.md](reviews/P3-ARGUS-REVIEW.md)) |
| P3A | Mobile UI/UX prototype (separate disposable repo) | HUMAN_ACCEPTED_FOR_P4 (design baseline captured in [P3A-DESIGN-HANDOFF.md](P3A-DESIGN-HANDOFF.md)) |
| P4 | Android Architecture Blueprint | HUMAN_APPROVED — COMPLETE (see [milestones/P4-SPENDWISE-ANDROID-ARCHITECTURE-BLUEPRINT.md](milestones/P4-SPENDWISE-ANDROID-ARCHITECTURE-BLUEPRINT.md), [ARCHITECTURE.md](ARCHITECTURE.md), [reviews/P4-ARGUS-REVIEW.md](reviews/P4-ARGUS-REVIEW.md)) |
| — | **HUMAN GATE** — PASSED (P4 approved; Android implementation authorized at M0) | — |
| M0 | Android Foundation (scaffold, toolchain, DI, theme skeleton, nav shell, network plumbing, session store, test scaffold, gates) | HUMAN_APPROVED — COMPLETE (see [milestones/M0-ANDROID-FOUNDATION.md](milestones/M0-ANDROID-FOUNDATION.md), [reviews/M0-ARGUS-REVIEW.md](reviews/M0-ARGUS-REVIEW.md)) |
| M1 | Authentication & Session (register/login/logout/expiry + resume) | HUMAN_APPROVED — COMPLETE (external audit ACCEPTED; see [milestones/M1-AUTHENTICATION-SESSION.md](milestones/M1-AUTHENTICATION-SESSION.md), [reviews/M1-EXTERNAL-AUDIT-SUMMARY.md](reviews/M1-EXTERNAL-AUDIT-SUMMARY.md), [reviews/M1-SENTINEL-REVIEW.md](reviews/M1-SENTINEL-REVIEW.md), [reviews/M1-ARGUS-REVIEW.md](reviews/M1-ARGUS-REVIEW.md)) |
| M2 | Accounts + design-system core (list/detail/create, balances, heroes) | HUMAN_APPROVED — COMPLETE (external audit FINDINGS — remediated; see [milestones/M2-ACCOUNTS-DESIGN-SYSTEM.md](milestones/M2-ACCOUNTS-DESIGN-SYSTEM.md), [reviews/M2-EXTERNAL-AUDIT-SUMMARY.md](reviews/M2-EXTERNAL-AUDIT-SUMMARY.md), [reviews/M2-SENTINEL-REVIEW.md](reviews/M2-SENTINEL-REVIEW.md), [reviews/M2-ARGUS-REVIEW.md](reviews/M2-ARGUS-REVIEW.md)) |
| M3 | Transactions (list/filters/detail, income/expense entry; read-only history per Human P3A override) | READY_FOR_EXTERNAL_AUDIT (see [milestones/M3-TRANSACTIONS.md](milestones/M3-TRANSACTIONS.md), [reviews/M3-SENTINEL-REVIEW.md](reviews/M3-SENTINEL-REVIEW.md), [reviews/M3-ARGUS-REVIEW.md](reviews/M3-ARGUS-REVIEW.md)) |
| M4 | Home / Dashboard (hero month totals, carousel, recent activity, mixed-currency state) | PLANNED |
| M5 | CSV / TPBank PDF Import (select/upload/result) | PLANNED |
| M6 | Planning (recurring, budgets, categorization rules) | PLANNED |
| M7 | Hardening / UX / Testing | PLANNED |

## Important

- P1–P4 are evidence/blueprint phases: they produce documents, not
  application code. THE HUMAN approved P4 and answered Q1–Q4
  (see the P4 milestone record); implementation is authorized beginning
  with M0. Each later milestone still requires its own brief and the
  per-milestone Human review recorded in that brief.
- The M-sequence was refined by P3's product blueprint
  (authentication precedes all verticals; transfers and other
  backend-gated capabilities carry explicit dependencies BE-001…BE-011
  in [PRODUCT.md](PRODUCT.md) §41 and have **no scheduled milestone**
  until the backend contract exists and THE HUMAN authorizes it).
- No Android production code existed before the HUMAN GATE was passed;
  M0 is the first milestone permitted to create it.
- M0–M7 implementation requirements are intentionally undefined here; each gets a
  milestone brief only when reached.
