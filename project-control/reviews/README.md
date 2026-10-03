# reviews/ — QA and Review Artifacts

This directory holds review artifacts produced during milestone execution:

- ARGUS QA reports (template: [../templates/QA-REPORT.md](../templates/QA-REPORT.md));
- SENTINEL test reports (template: [../templates/TEST-REPORT.md](../templates/TEST-REPORT.md));
- FLUX implementation handoffs (template:
  [../templates/IMPLEMENTATION-HANDOFF.md](../templates/IMPLEMENTATION-HANDOFF.md))
  when filed separately from the milestone record.

## Conventions

- Name files `<MILESTONE-ID>-<ARTIFACT>.md`, e.g. `M1-QA-REPORT.md`.
- Every artifact ends with an explicit outcome: `PASS` / `PARTIAL` / `BLOCKED`
  (and `READY_FOR_*` where the template requires it).
- Findings carry severities: `BLOCKER` / `MAJOR` / `MINOR` / `NOTE` — see
  [../../agent-system/WORKFLOW-CONTRACT.md](../../agent-system/WORKFLOW-CONTRACT.md).
- This directory contains reports only. It is not a place to fix anything.

## Current state

No reviews yet — no milestone has produced implementation work.
