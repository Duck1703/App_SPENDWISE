# ARCHITECTURE.md — Spendwise Android Architecture

**STATUS: NOT YET ESTABLISHED**

No architecture has been chosen. This document will be finalized only AFTER:

1. P1 — Senior Android Reference Architecture Audit
   ([ANDROID-BASELINE.md](ANDROID-BASELINE.md));
2. P2 — Spendwise Backend Contract Audit
   ([BACKEND-CONTRACT.md](BACKEND-CONTRACT.md));
3. P3 — Spendwise Android Product Blueprint (pending);
4. P4 — Android Architecture Blueprint, approved by THE HUMAN at the human gate.

Any technology or pattern choice recorded before that approval is unauthorized.
See [DECISIONS.md](DECISIONS.md) for what is actually decided — currently only
governance decisions D-001 through D-008.

## Explicitly undecided

Until P1/P2/P4 evidence exists, the following are NOT decided and must not be
treated as fact anywhere in this repository:

- DI framework (e.g., Hilt vs alternatives);
- networking stack (e.g., Retrofit vs alternatives);
- local persistence (e.g., Room vs none);
- module strategy (single-module vs multi-module);
- UI/state architecture (MVVM, MVI, Clean Architecture variants);
- navigation approach;
- any dependency choices.
