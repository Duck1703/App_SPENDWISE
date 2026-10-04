# P3 — Spendwise Android Product Blueprint

STATUS: **READY_FOR_HUMAN_REVIEW**

## Objective

Transform the approved P1 senior-Android baseline, the approved P2
backend contract, and the P0 product intent into one coherent,
evidence-backed mobile product blueprint answering: *"What should
Spendwise Android V1 actually be, given the product goal and the backend
that really exists today?"*

## Approvals relied upon

- P1 — HUMAN_APPROVED (commit `0583af6`)
- P2 — HUMAN_APPROVED (commit `18b559c`)

## Starting SHAs

| Repository | Branch | HEAD |
| --- | --- | --- |
| Product | main | `18b559cc0b6f1ff1b0853c8a8c906f404cc9cca9` |
| Backend (read-only) | main | `8194daa9959f2d568dc13ab0f211787195efc79d` |
| Reference (read-only) | master | `5f9622c59ce602ce33c98ee633ba799f903b67e1` |

Pre-flight: product clean; backend dirty state limited to the three known
user-owned docs; reference clean.

## Inputs

- `project-control/ANDROID-BASELINE.md` (P1, approved)
- `project-control/BACKEND-CONTRACT.md` (P2, approved)
- `project-control/PRODUCT.md` (P0-era product intent — replaced by this blueprint)
- Governance: `AGENTS.md`, `agent-system/**`, `project-control/WORKSPACE-BOUNDARIES.md`,
  `ROADMAP.md`, `ARCHITECTURE.md`, `DECISIONS.md`
- P1/P2 milestone and Argus-review evidence

## Scope performed

Product definition only. No Android/Gradle/Kotlin/Compose production
code; no architecture finalization; no backend or reference mutation; no
backend-gap implementation.

## Methodology

ATLAS synthesized P1+P2+intent → drafted the 44-section blueprint
(capability classification, backend-gap decision board, IA, navigation,
25-screen inventory, 14 journeys, error/empty/session state model,
terminology, financial-display rules, privacy/a11y/performance
expectations, non-goals, proposed decisions PD-001–PD-015, assumption
register, backend-enablement register BE-001–BE-011, P4 inputs, 7 open
Human questions) → ARGUS isolated review → ATLAS reconciliation of all
findings → this evidence record.

FLUX: NOT_APPLICABLE · SENTINEL: NOT_APPLICABLE (no implementation exists).

## Key product outcomes

- **Product statement**: authenticated personal cash-flow client —
  trustworthy balances, record/review income+expenses, TPBank/CSV
  statement import; backend is the single authoritative ledger.
- **V1 scope**: V1_CORE 18 · V1_SUPPORTING 4 · BACKEND_GATED 10 ·
  POST_V1 5 · EXPLICITLY_EXCLUDED 5 (42 classified capabilities).
- **Backend-gated (hidden, never faked)**: transfers, transaction
  edit/delete, account edit/delete, transaction dates (set+display),
  persisted descriptions, pagination, offline.
- **Correction model**: honest "offset entry" (inverse transaction)
  restricted to INCOME/EXPENSE — original stays visible.
- **Session model**: ~15-min JWT accepted for V1 with an explicit
  re-authentication interstitial that preserves unsent input.
- **Import model**: blocking synchronous upload, no progress %,
  dedup-safe retry messaging, OK/DUPLICATE/FAILED result hierarchy,
  honest TPBank+CSV-only promise.
- **Dashboard**: currency-labeled totals only on uniform-currency
  portfolios; honest mixed-currency state otherwise.
- **Secondary surfaces**: recurring (currency locked to account —
  defect-avoiding), budgets, categorization rules — under More.

## V1 backend readiness verdict

**YES_WITH_ACCEPTED_LIMITATIONS** — the defined V1 is fully deliverable
against today's contract; BE-001/BE-002/BE-003 are the highest-value
post-V1 enablements and are Human-gated via Q1/Q2/Q6.

PRODUCT_BLOCKER_COUNT = 0
BACKEND_GATED_COUNT = 10
REQUIRES_BACKEND_ENABLEMENT (priority subset) = 3 (BE-001, BE-002, BE-003)

## Navigation decision

TOP_LEVEL_DESTINATIONS = 4 — Home / Transactions / Accounts / More.
Primary action: "Add transaction" (persistent). Import: More + Home
secondary entry.

## Artifact metrics

- TOTAL_SCREEN_COUNT = 25 (all V1; 0 backend-gated screens built)
- USER_JOURNEY_COUNT = 14
- PROPOSED_PRODUCT_DECISIONS = 15 (PD-001…PD-015, all
  `HUMAN_APPROVAL_REQUIRED=YES`)
- BACKEND_ENABLEMENT dependencies = 11 (BE-001…BE-011)
- OPEN_HUMAN_QUESTIONS = 7
- PRODUCT_NON_GOALS = 10

## Argus review

`ARGUS_REVIEW_MODE=ISOLATED` —
[../reviews/P3-ARGUS-REVIEW.md](../reviews/P3-ARGUS-REVIEW.md)

- BLOCKER = 0 · MAJOR = 1 (F01 undeliverable error-diagnosis copy) ·
  MINOR = 13 · NOTE = 9
- All MAJOR + MINOR findings reconciled in PRODUCT.md; all NOTEs adopted.
- ARGUS_VERDICT = **PASS**

## Unresolved items (for THE HUMAN)

- Q1–Q7 in PRODUCT.md §43 — the material scope calls: dateless rows,
  15-min sessions, offset-entry correction, recurring/budgets/rules in
  V1, transfer escalation, one-shot import history.

## Outputs / files changed

- `project-control/PRODUCT.md` — replaced placeholder with the full blueprint
- `project-control/ROADMAP.md` — status lines + refined provisional sequence
- `project-control/milestones/P3-SPENDWISE-ANDROID-PRODUCT-BLUEPRINT.md` — this file
- `project-control/reviews/P3-ARGUS-REVIEW.md` — new

Unchanged (verified): `ANDROID-BASELINE.md`, `BACKEND-CONTRACT.md`,
`ARCHITECTURE.md`, `DECISIONS.md`, `WORKSPACE-BOUNDARIES.md`,
`agent-system/**`. No Android source, Gradle, or manifest files exist.

## Repository preservation

- Backend `D:\spendwise`: HEAD unchanged at `8194daa`; dirty state
  unchanged (3 known user-owned docs).
- Reference `D:\app_spendwise\kotlin-accelerator-ai`: HEAD unchanged at
  `5f9622c`; worktree clean.
- Product repo: only the 4 files above touched.

## Human gate

P3 is **READY_FOR_HUMAN_REVIEW** — not complete, not approved. THE HUMAN
decides: approve blueprint (incl. Q1–Q7), request changes, or redirect
the roadmap.

## Recommended next task

**P4 — Spendwise Android Architecture Blueprint** — per the conditional
rule: readiness is YES_WITH_ACCEPTED_LIMITATIONS, so B0 is not required
before P4. (B0-style enablements BE-001…BE-011 remain queued for the
Human to authorize separately.) Do NOT start P4 until P3 is
HUMAN_APPROVED.
