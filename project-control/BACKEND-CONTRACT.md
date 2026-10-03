# BACKEND-CONTRACT.md — Spendwise Backend Contract Audit

**STATUS: NOT YET ESTABLISHED**

This document will be produced by **P2 — Spendwise Backend Contract Audit**: a
read-only, evidence-backed analysis of `D:\spendwise` (the authoritative Spring
Boot backend).

P0 has not audited the backend API. No endpoints, DTOs, or formats are recorded
here yet, and none may be assumed.

## Expected future categories

When produced, the contract will document per endpoint (with source references):

- endpoint path;
- HTTP method;
- request DTO (fields, types, nullability);
- response DTO (fields, types, nullability);
- HTTP status codes;
- error response shape;
- validation rules;
- money / currency representation;
- date / time format;
- pagination behavior;
- upload behavior (CSV/PDF import);
- mobile impact notes.

## Backend gaps

Gaps discovered during P2 or later milestones follow the BACKEND GAP procedure in
[WORKSPACE-BOUNDARIES.md](WORKSPACE-BOUNDARIES.md): document, assess mobile
impact, propose minimal contract, STOP, escalate to THE HUMAN. The Android team
never modifies `D:\spendwise`.
