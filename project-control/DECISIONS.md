# DECISIONS.md — Spendwise Android Decision Log

Lightweight decision log. Only confirmed decisions are recorded here — each with
an ID, the decision, and its authority. Decisions are append-only; reversals are
recorded as new decisions referencing the old ID.

## Confirmed decisions

| ID | Decision | Authority | Phase |
| --- | --- | --- | --- |
| D-001 | Workspace root is `D:\app_spendwise`. | THE HUMAN | P0 |
| D-002 | The senior Android project `D:\app_spendwise\kotlin-accelerator-ai` is reference-only (READ ONLY; never modified, never copied wholesale). | THE HUMAN | P0 |
| D-003 | The Spendwise backend `D:\spendwise` is read-only to the Android team; backend gaps are reported, never patched by Android agents. | THE HUMAN | P0 |
| D-004 | The new Android product repository is `D:\app_spendwise\spendwise-android`; it is the only writable location. | THE HUMAN | P0 |
| D-005 | The canonical agent system (`agent-system/**`, `project-control/**`) is tool/vendor independent; vendor-specific adapters may be generated later but are not source of truth. | THE HUMAN | P0 |
| D-006 | The team has exactly four roles: ATLAS (lead/architect), FLUX (developer), SENTINEL (test engineer), ARGUS (independent QA). | THE HUMAN | P0 |
| D-007 | THE HUMAN is the final authority: architecture approval, milestone go/no-go, scope changes, backend change requests, releases, and governance exceptions. | THE HUMAN | P0 |
| D-008 | No Android production code is written before the reference audit (P1), backend contract audit (P2), product blueprint (P3), and an approved architecture blueprint (P4) — enforced by the roadmap human gate. | THE HUMAN | P0 |

## Explicitly undecided

Technology choices such as Hilt, Retrofit, Room, multi-module structure, MVI, and
Clean Architecture are NOT decided. They may be adopted only via P1/P2/P4
evidence and a recorded decision approved by THE HUMAN. See
[ARCHITECTURE.md](ARCHITECTURE.md).
