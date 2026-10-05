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
| M3 | Transactions (list/filters/detail, income/expense entry; read-only history per Human P3A override) | HUMAN_APPROVED — COMPLETE (external audit FINDINGS — remediated; see [milestones/M3-TRANSACTIONS.md](milestones/M3-TRANSACTIONS.md), [reviews/M3-EXTERNAL-AUDIT-SUMMARY.md](reviews/M3-EXTERNAL-AUDIT-SUMMARY.md), [reviews/M3-SENTINEL-REVIEW.md](reviews/M3-SENTINEL-REVIEW.md), [reviews/M3-ARGUS-REVIEW.md](reviews/M3-ARGUS-REVIEW.md)) |
| M4 | Home / Dashboard (hero month totals, carousel, recent activity, mixed-currency state) | HUMAN_APPROVED — COMPLETE (external audit FINDINGS — remediated; see [milestones/M4-HOME-DASHBOARD.md](milestones/M4-HOME-DASHBOARD.md), [reviews/M4-EXTERNAL-AUDIT-SUMMARY.md](reviews/M4-EXTERNAL-AUDIT-SUMMARY.md), [reviews/M4-SENTINEL-REVIEW.md](reviews/M4-SENTINEL-REVIEW.md), [reviews/M4-ARGUS-REVIEW.md](reviews/M4-ARGUS-REVIEW.md)) |
| M5 | CSV / TPBank PDF Import (select/upload/result) | EXTERNAL AUDIT ACCEPTED — remediated (`0db8b3a`), pending human sign-off (see [milestones/M5-CSV-PDF-IMPORT.md](milestones/M5-CSV-PDF-IMPORT.md), [reviews/M5-EXTERNAL-AUDIT-SUMMARY.md](reviews/M5-EXTERNAL-AUDIT-SUMMARY.md)) |
| M6 | Planning (recurring, budgets, categorization rules) | EXTERNAL AUDIT ACCEPTED — no remediation needed (0 blockers/majors/minors), pending human sign-off (see [milestones/M6-PLANNING.md](milestones/M6-PLANNING.md), [reviews/M6-EXTERNAL-AUDIT-SUMMARY.md](reviews/M6-EXTERNAL-AUDIT-SUMMARY.md), [reviews/M6-SENTINEL-REVIEW.md](reviews/M6-SENTINEL-REVIEW.md), [reviews/M6-ARGUS-REVIEW.md](reviews/M6-ARGUS-REVIEW.md)) |
| M7 | Hardening / UX / Testing | COMPLETE — external audit ACCEPTED (0 blockers/majors; 1 deferred minor), human-approved via conditional authorization (see [milestones/M7-HARDENING.md](milestones/M7-HARDENING.md), [reviews/M7-EXTERNAL-AUDIT-SUMMARY.md](reviews/M7-EXTERNAL-AUDIT-SUMMARY.md), [reviews/M7-SENTINEL-REVIEW.md](reviews/M7-SENTINEL-REVIEW.md), [reviews/M7-ARGUS-REVIEW.md](reviews/M7-ARGUS-REVIEW.md)) |
| M8 | V1 Release Acceptance & Device Validation | COMPLETE — external release audit ACCEPTED (0 blockers/majors; 4 minor remediated), human-approved via conditional authorization; verified on fresh audit AVD **and physical Pixel 4 XL** (see [milestones/M8-V1-RELEASE-ACCEPTANCE.md](milestones/M8-V1-RELEASE-ACCEPTANCE.md), [reviews/M8-EXTERNAL-RELEASE-AUDIT.md](reviews/M8-EXTERNAL-RELEASE-AUDIT.md), [reviews/M8-SENTINEL-REVIEW.md](reviews/M8-SENTINEL-REVIEW.md), [reviews/M8-ARGUS-REVIEW.md](reviews/M8-ARGUS-REVIEW.md), [POST-V1-BACKEND-GATES.md](POST-V1-BACKEND-GATES.md), [POST-V1-ROADMAP-PROPOSAL.md](POST-V1-ROADMAP-PROPOSAL.md)) |

| M9 | Ledger Export (server-generated CSV → SAF save) | HUMAN_AUTHORIZED — CURRENT (see [milestones/M9-LEDGER-EXPORT.md](milestones/M9-LEDGER-EXPORT.md), [M9-DECISION-BRIEF.md](M9-DECISION-BRIEF.md)) |

**Canonical sequence M0–M8 fully delivered and release-accepted.** V1
was validated on emulator AND physical hardware (135/135 connected ×2
each, R8 release smoke on-device, TEE-backed Keystore confirmed). Any
post-V1 work (backend-gated capabilities BE-001…BE-011 per PRODUCT §41)
requires a future milestone authorized by THE HUMAN.

**M9 (Ledger Export) is authorized and current** — the Human approved
export-only scope (import-pointer store and this-device import history
explicitly REJECTED; FX deferred; dates/mutations backend-gated).
M10 is not defined.

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
