# MILESTONE BRIEF — <MILESTONE-ID>: <NAME>

Owner: ATLAS
Status: DRAFT | APPROVED | IN_PROGRESS | READY_FOR_HUMAN_REVIEW | CLOSED
Authorized by: THE HUMAN — <date/reference>

## Objective

<One paragraph: what this milestone achieves and why.>

## Starting state

- Repository SHA/state: <sha | description>
- Preconditions: <evidence/documents that must exist first>

## Sources of truth

<Links: PRODUCT.md sections, ANDROID-BASELINE.md, BACKEND-CONTRACT.md,
ARCHITECTURE.md, DECISIONS.md IDs, prior milestone records.>

## Allowed write scope

<Exact paths/modules this milestone may create or modify.>

## Forbidden scope

<Explicitly out-of-bounds paths, files, and behavior.>

## Architecture constraints

<Decisions from ARCHITECTURE.md / DECISIONS.md that bind the implementation.>

## Backend contract dependencies

<Endpoints/DTOs from BACKEND-CONTRACT.md this milestone relies on; note any
suspected gaps up front.>

## Implementation tasks

1. <task>
2. <task>

## Acceptance criteria

- [ ] <criterion — testable>
- [ ] <criterion — testable>

## Required tests

<Test scope SENTINEL must cover: units, mappers, repositories, ViewModels, UI,
edge cases — derived from acceptance criteria.>

## QA criteria

<What ARGUS must verify beyond tests: scope fidelity, architecture fidelity,
contract alignment, security-relevant concerns.>

## Stop conditions

<Milestone-specific conditions in addition to the contract's global stop
conditions in ../../agent-system/WORKFLOW-CONTRACT.md.>

## Expected final report

- Implementation handoff (FLUX) — template: IMPLEMENTATION-HANDOFF.md
- Test report (SENTINEL) — template: TEST-REPORT.md
- QA report (ARGUS) — template: QA-REPORT.md
- ATLAS reconciliation summary + verdict: PASS | PARTIAL | BLOCKED
- Human gate required: YES | NO
