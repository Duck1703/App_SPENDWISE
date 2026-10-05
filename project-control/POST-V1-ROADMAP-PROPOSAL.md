# Spendwise Post-V1 Roadmap Proposal

**PROPOSED_ONLY — NOT_CANONICAL — HUMAN_APPROVAL_REQUIRED**

Written by the M8 external release audit after independent verification
of `POST-V1-BACKEND-GATES.md` against backend source @ `8194daa`. This
document proposes sequencing only; it creates no milestone.

## Current baseline

```
M0–M8 complete — V1 release acceptance APPROVED (external audit)
Android: 135/135 connected (emulator + physical), 422/422 JVM,
         R8 release smoke on real hardware, TEE-backed Keystore
Backend: 8194daa — authoritative, untouched
```

## Independently verified backend-ready opportunities

Verified against Spring controllers — pure consumers, additive reads:

| Endpoint | Backend | Android today | Value | Risk |
| --- | --- | --- | --- | --- |
| `GET /api/imports/{id}` | `ImportReportController` ✓ | one-shot result, no list | report detail for past batch — needs local batch-id persistence (no list endpoint exists → BE-004 partially gated) | LOW |
| `GET /api/transactions/export` | `CsvExportController` ✓ | absent | CSV export via SAF save — no permission needed | LOW |
| `GET /api/fx/convert` | `FxController` ✓ | deliberately refused (honest "mixed currencies") | opt-in FX totals — **product-ethics decision**: conversion implies a rate of record | MEDIUM (honesty framing) |

## Independently verified backend-gated opportunities

Backend has **zero PUT/PATCH** on accounts/transactions/budgets/rules/
categories and no refresh-token — verified at source level:

| BE item | Gap | Unlock |
| --- | --- | --- |
| BE-001 | `occurredOn` unsettable+unreturned | date display/entry/back-dating — **highest user value** |
| BE-002 | 15-min JWT, no refresh | fewer forced re-logins |
| BE-003 | `TransferService` internal-only | first-class transfers |
| BE-004 | import `/{id}` only, no list | real import history |
| BE-005 | append-only transactions | correction/void UX (offset-entry exists V1 as stopgap) |
| BE-006 | no account edit/archive | rename/hide |
| BE-007 | dead Pageable, 3 filters | large-ledger browse/search |
| BE-008 | ~1 MB multipart cap | large statements |
| BE-009 | recurring currency defect + typed errors + batch status | hardening cluster |
| BE-010 | no `description` persistence | notes |
| BE-011 | no budget edit/delete | fix wrong current-month budgets |

## Recommended sequencing (candidate phases — PROPOSED ONLY)

- **Candidate P1 (backend-ready only):** CSV export + per-batch import
  report detail (with local batch-id retention). Zero backend changes.
- **Candidate P2 (needs backend):** BE-001 dates — display then entry.
  Highest user value; unblocks grouping/filters (BE-007 partial).
- **Candidate P3 (needs backend):** BE-002 refresh sessions + BE-003
  transfers + BE-005/006/011 mutation surface (corrections, account/budget
  management) — the "full editing" cluster.
- **Candidate P4 (needs backend):** BE-009 hardening + BE-010 notes +
  BE-008 multipart + BE-004 import list.

Rationale: front-load zero-backend-readiness wins; BE-001 gates most
user-visible value; mutation cluster shares backend effort.

## Human decisions required before any canonical M9

1. Whether FX-converted totals can ever be honest enough for Spendwise
   (the V1 "never mix currencies" stance was deliberate).
2. Whether backend work is in-scope for this program at all — every
   high-value item after P1 requires it.
3. Priority order between UX depth (dates/search/notes) and mutation
   breadth (edits/transfers).
4. Production signing + backend deployment posture — V1 is
   internal/direct-install only today.
