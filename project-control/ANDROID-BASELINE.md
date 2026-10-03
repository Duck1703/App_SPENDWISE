# ANDROID-BASELINE.md — Senior Reference Architecture Baseline

**STATUS: NOT YET ESTABLISHED**

This document will be produced by **P1 — Senior Android Reference Architecture
Audit**: a read-only, evidence-backed analysis of
`D:\app_spendwise\kotlin-accelerator-ai`.

P0 has not audited the reference project. Nothing in this file is a finding yet.

## Expected future categories

When produced, the baseline will document (with file/commit evidence):

- module structure;
- package structure;
- Compose architecture;
- navigation;
- state management;
- dependency injection;
- networking;
- repository pattern;
- DTO / domain / UI mapping;
- error model;
- coroutines / Flow usage;
- persistence;
- design system;
- testing approach;
- conventions;
- **KEEP / ADAPT / REJECT** classification per pattern — a senior pattern may be
  excellent yet still too complex or inappropriate for Spendwise.

## Guardrails for P1

- READ ONLY against `D:\app_spendwise\kotlin-accelerator-ai` (see
  [WORKSPACE-BOUNDARIES.md](WORKSPACE-BOUNDARIES.md)).
- The reference is a reference implementation, not a source to copy wholesale.
- P1 produces this document only — no Android production code.
