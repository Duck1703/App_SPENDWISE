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

| M9 | Ledger Export (server-generated CSV → SAF save) | COMPLETE — external audit ACCEPTED (1 major remediated: sweep/live-artifact race; 1 minor: save() generation binding), human-approved via conditional authorization (see [milestones/M9-LEDGER-EXPORT.md](milestones/M9-LEDGER-EXPORT.md), [reviews/M9-EXTERNAL-AUDIT.md](reviews/M9-EXTERNAL-AUDIT.md), [reviews/M9-SENTINEL-REVIEW.md](reviews/M9-SENTINEL-REVIEW.md), [reviews/M9-ARGUS-REVIEW.md](reviews/M9-ARGUS-REVIEW.md)) |
| M10 | Server-Backed Import History (REQ-BE-004 list + per-batch report) | COMPLETE — external audit remediated + all gates green (JVM 493/493, lint/detekt, release, connected 158/158 ×2 on physical Pixel 4 XL); backend `401e99c` + `22efd60` under bounded-write authorization (see [milestones/M10-IMPORT-HISTORY.md](milestones/M10-IMPORT-HISTORY.md)) |
| M11 | Transaction Occurrence Dates (REQ-BE-001 `occurredOn` on create/read) | COMPLETE — external audit ACCEPTED (0 blocker / 0 major / 3 notes; JVM 503/503, lint/detekt, release, connected 162/162 ×2 on physical Pixel 4 XL); backend `bc6e0bb` under bounded-write authorization; human-approved via conditional authorization (see [milestones/M11-TRANSACTION-DATES.md](milestones/M11-TRANSACTION-DATES.md), [reviews/M11-EXTERNAL-AUDIT.md](reviews/M11-EXTERNAL-AUDIT.md), [reviews/M11-SENTINEL-REVIEW.md](reviews/M11-SENTINEL-REVIEW.md), [reviews/M11-ARGUS-REVIEW.md](reviews/M11-ARGUS-REVIEW.md)) |
| M12 | Transfers Between Own Accounts (REQ-BE-003 `POST /api/transfers` + Android create flow) | HUMAN_APPROVED — COMPLETE (external audit ACCEPTED after a second cycle: 1 major + 3 minor remediated in-milestone; JVM 533/533, connected 174/174 ×2; backend `8803396` + `fd2ca1b` under bounded-write authorization) (see [milestones/M12-TRANSFERS.md](milestones/M12-TRANSFERS.md), [reviews/M12-EXTERNAL-AUDIT.md](reviews/M12-EXTERNAL-AUDIT.md), [reviews/M12-SENTINEL-REVIEW.md](reviews/M12-SENTINEL-REVIEW.md), [reviews/M12-ARGUS-REVIEW.md](reviews/M12-ARGUS-REVIEW.md)) |
| M13 | Session Refresh / Token Renewal (REQ-BE-002 `POST /api/auth/refresh`, rotating refresh token, logout revocation, single-flight client renewal) | HUMAN_APPROVED — COMPLETE (external audit ACCEPTED: 0 blocker / 0 major, 5 minor remediated in-audit; JVM 570/570, connected 178/178 ×2, backend 330 tests with 1 pre-existing env flake proven file-identical to M12 baseline; backend `f1ac79a` + bounded test additions under human authorization) (see [milestones/M13-SESSION-REFRESH.md](milestones/M13-SESSION-REFRESH.md), [reviews/M13-EXTERNAL-AUDIT-VERDICT.md](reviews/M13-EXTERNAL-AUDIT-VERDICT.md), [reviews/M13-SENTINEL-REVIEW.md](reviews/M13-SENTINEL-REVIEW.md), [reviews/M13-ARGUS-REVIEW.md](reviews/M13-ARGUS-REVIEW.md)) |
| M14 | Budget Lifecycle (REQ-BE-011 `PATCH`/`DELETE /api/budgets/{id}` + Android edit/delete on budget detail) | COMPLETE — external audit **ACCEPTED**, human-approved via conditional authorization; physical Pixel 4 XL gate PASSED (180/180 ×2 full connected runs, incl. edit/delete flow tests on-device); every contract/safety gate proven (wire-level M13 no-replay, real-tx collision atomicity, ledger untouched, delete→recreate); JVM 596/596, backend 350 tests with 1 documented baseline flake, release verified, emulator 180/180 ×2 supplementary (see [milestones/M14-BUDGET-LIFECYCLE.md](milestones/M14-BUDGET-LIFECYCLE.md), [reviews/M14-EXTERNAL-AUDIT.md](reviews/M14-EXTERNAL-AUDIT.md)) |
| M15 | Scalable Transaction History (REQ-BE-007 `GET /api/transactions/page` + lazy-paged Transactions tab + From/To date filter) | COMPLETE — external audit **ACCEPTED**, human-approved via conditional authorization; physical Pixel 4 XL gate PASSED (186/186 ×2 final-source + 186/186 ×2 preliminary); audit remediated 1 Major + 3 Minor in-task (ebd966a, backend ef08539); backend 15/15 contract + 365 regression (1 documented baseline flake), JVM 617/617, release R8 verified (see [milestones/M15-TRANSACTION-HISTORY.md](milestones/M15-TRANSACTION-HISTORY.md), [reviews/M15-EXTERNAL-AUDIT.md](reviews/M15-EXTERNAL-AUDIT.md), [reviews/M15-SENTINEL-REVIEW.md](reviews/M15-SENTINEL-REVIEW.md), [reviews/M15-ARGUS-REVIEW.md](reviews/M15-ARGUS-REVIEW.md)) |
| M16 | Recurring Template Lifecycle (REQ-BE-012 `PATCH /api/recurring-transactions/{id}` + Android edit flow on recurring detail) | COMPLETE — external audit ACCEPTED, Human-approved. Audit remediated 1 Major (recurring `@Version` lost-update vs scheduler) + minors in-task; all gates re-green on final source (backend 383/383, Android JVM 653/653, lint/detekt/release/R8 green, physical 193/193 ×4, installed+launched). M17 is not defined (see [milestones/M16-RECURRING-LIFECYCLE.md](milestones/M16-RECURRING-LIFECYCLE.md), [reviews/M16-EXTERNAL-AUDIT.md](reviews/M16-EXTERNAL-AUDIT.md)) |
| M17 | Transaction Notes (REQ-BE-010 — persist `description` on `Transaction` + response/export surfaces; Android: note input reframed + detail display) | COMPLETE — external audit ACCEPTED. Delivery: backend `ca5b380..9c123f7` (additive `VARCHAR(255)` `V12`, entity-normalized, returned on all reads + CSV column); Android `445ffb0`+ (wire/domain/UI rename, detail note block). Independent audit remediated 2 Majors in-task (surrogate-safe Unicode cap; CSV formula-injection neutralization) + minors; accepted heads backend `752eac3`, Android `deccaf2`. Final gates: backend 397/397, Android JVM 658/658, lint/detekt/release/R8 green, physical 196/196 ×2, installed+launched (see [milestones/M17-TRANSACTION-NOTES.md](milestones/M17-TRANSACTION-NOTES.md), [reviews/M17-EXTERNAL-AUDIT.md](reviews/M17-EXTERNAL-AUDIT.md)) |
| M18 | Transaction Search (REQ-BE-007 residual — server-side `q` on `GET /api/transactions/page` over persisted `description` + Android search field) | COMPLETE — external audit ACCEPTED, Human-approved. M15 deferred `q` as near-fake while no searchable text was persisted; M17 delivered BE-010, unblocking it. One additive optional param composing the existing M15 spec (owner-scoped, blank-ignored, ≤255, LIKE-escaped, before pagination) + Transactions-tab search field (debounced apply, clear, results/empty/error/retry). Independent audit remediated 1 Minor in-task (superseded-append `loadingMore` flag — EXT-M18-1) and added 12 backend + 2 Android adversarial tests (real-HTTP decode proof, full-history reach, lone-metachar literals, shared-term owner counts). Accepted heads: Android `dfda980`, backend `d8554f0`. Final gates: backend 419/419, Android JVM 674/674, lint 0e/22w, detekt 0, release R8 green, physical Pixel 4 XL 200/200 ×2, install+launch (see [milestones/M18-TRANSACTION-SEARCH.md](milestones/M18-TRANSACTION-SEARCH.md), [reviews/M18-EXTERNAL-AUDIT.md](reviews/M18-EXTERNAL-AUDIT.md)) |
| M19 | Account Lifecycle (REQ-BE-006 — `PATCH /api/accounts/{id}` rename + `archived` flag; archived hidden from entry pickers, still in ledger/balances; Android edit screen + archived badge + picker exclusion) | COMPLETE — external audit ACCEPTED, Human-approved. Backend `342e85d` (sparse PATCH, V13 `archived`, owner 404, `@Version` 409); Android edit screen (`AccountEditRoute`), archived badges on list/detail/dashboard, picker exclusion in all four entry surfaces, mutation-repaint fix. SENTINEL PASS (1 Minor remediated), ARGUS PASS. Independent audit remediated 1 Minor in-task (name-validation divergence vs create — EXT-M19-1 `(?s)` fix at backend `9970c3e`) + pinned 4 Android edges (`c50ebd4`); Jackson scalar coercion pinned as observed contract. Final gates on accepted source: backend 452/452, Android JVM 722/722, lint 0e, detekt 0, release R8 green, physical Pixel 4 XL 213/213 ×2, install+launch (see [milestones/M19-ACCOUNT-LIFECYCLE.md](milestones/M19-ACCOUNT-LIFECYCLE.md), [reviews/M19-EXTERNAL-AUDIT.md](reviews/M19-EXTERNAL-AUDIT.md)) |
| M20 | Offsetting Entry (PRODUCT §21 / S10 / J14 — guided compensating-entry correction: "Create offsetting entry" on INCOME/EXPENSE detail → prefilled inverse-type create form → canonical `POST /api/transactions`; TRANSFER_* excluded; archived-source hidden) | COMPLETE — independent external audit ACCEPTED, Human-approved. Zero backend production change (contract tests at `1768a72`, strengthened `4ced814`); Android offset mode on create (`offsetTransactionId` route arg, re-fetched source, prefill-once, authoritative archived re-check) + detail action + eligibility guards; verbatim §21 honest copy. Audit remediated EXT-M20-1 Major (silent account substitution on options re-run → honest drop) + EXT-M20-2 Minor (stale archived flag → fresh resolve) at `b0a359b`. Final gates on accepted source: backend 458/458, Android JVM 745/745, lint 0e, detekt 0, release R8 + lintVital green, physical Pixel 4 XL 218/218 ×2, install+launch (see [milestones/M20-OFFSETTING-ENTRY.md](milestones/M20-OFFSETTING-ENTRY.md), [reviews/M20-EXTERNAL-AUDIT.md](reviews/M20-EXTERNAL-AUDIT.md)) |

**Canonical sequence M0–M8 fully delivered and release-accepted.** V1
was validated on emulator AND physical hardware (135/135 connected ×2
each, R8 release smoke on-device, TEE-backed Keystore confirmed). Any
post-V1 work (backend-gated capabilities BE-001…BE-011 per PRODUCT §41)
requires a future milestone authorized by THE HUMAN.

**M9 (Ledger Export) is COMPLETE** — export-only scope delivered and
externally audited. **M10 (Server-Backed Import History) is COMPLETE** —
REQ-BE-004 landed backend-side under bounded-write authorization
(backend `401e99c`, `22efd60`), Android history surfaces green on all
gates including physical-device connected runs (import-pointer store
remains REJECTED). **M11 (Transaction Occurrence Dates) is COMPLETE** —
REQ-BE-001 backend contract landed under bounded-write authorization
(backend `bc6e0bb`), the Android vertical slice passed every gate
including two full physical-device connected runs (162/162 ×2), and the
independent external audit ACCEPTED it with 0 blocker/major findings
([reviews/M11-EXTERNAL-AUDIT.md](reviews/M11-EXTERNAL-AUDIT.md));
human-approved via the audit's conditional authorization. **M12
(Transfers Between Own Accounts) is READY_FOR_EXTERNAL_AUDIT** —
REQ-BE-003 delivered end-to-end under the Human's explicit M12-block
authorization (see
[milestones/M12-TRANSFERS.md](milestones/M12-TRANSFERS.md)): backend
`8803396` exposed `POST /api/transfers` atomically, the Android
create flow shipped with honest ambiguous-write handling, Sentinel and
Argus both PASS (3 minors remediated inside the milestone), every gate
re-ran green including two physical-device connected runs (174/174 ×2),
and [reviews/M12-EXTERNAL-AUDIT.md](reviews/M12-EXTERNAL-AUDIT.md)
packages the evidence — the audit ACCEPTED and THE HUMAN approved M12.

**M13 (Session Refresh / Token Renewal) is COMPLETE** — REQ-BE-002,
the last undelivered member of the canonical priority subset
(BE-001/BE-002/BE-003): external audit ACCEPTED and human-approved
(see [milestones/M13-SESSION-REFRESH.md](milestones/M13-SESSION-REFRESH.md),
[reviews/M13-EXTERNAL-AUDIT-VERDICT.md](reviews/M13-EXTERNAL-AUDIT-VERDICT.md)).

**M14 (Budget Lifecycle — edit + delete) is COMPLETE — external audit
ACCEPTED and Human-approved.** REQ-BE-011 delivered end-to-end under
the Human's explicit M14 block: backend `472b8fd` (PATCH + DELETE
`/api/budgets/{id}`) + audit tests `4ed4f81`, Android `1412860` +
audit remediation `acb807c` + connected-test fix `841b04c`. The audit
falsified every blocker/major-class invariant — ownership, collision
atomicity in real transactions, ledger untouched, delete→recreate
(the formerly "permanently unfixable" wrong budget), M13 no-replay
wire-proven for both new verbs, server-truth reload, session privacy,
release safety — 0 blocker / 0 major. The mandatory physical gate
passed on the Pixel 4 XL (Android 13): **180/180 ×2 consecutive full
connected runs** including the edit-dialog/save and delete-confirm
flow tests; supplementary emulator evidence 180/180 ×2 on AVD API 36.
M14 is approved; M0–M14 are COMPLETE
(see [milestones/M14-BUDGET-LIFECYCLE.md](milestones/M14-BUDGET-LIFECYCLE.md),
[reviews/M14-EXTERNAL-AUDIT.md](reviews/M14-EXTERNAL-AUDIT.md)).
**M15 (Scalable Transaction History — REQ-BE-007 pagination +
date-range) is COMPLETE** — external audit ACCEPTED and
Human-approved (see
[milestones/M15-TRANSACTION-HISTORY.md](milestones/M15-TRANSACTION-HISTORY.md),
[reviews/M15-EXTERNAL-AUDIT.md](reviews/M15-EXTERNAL-AUDIT.md)).

**M16 (Recurring Template Lifecycle — REQ-BE-012 template edit) is
COMPLETE — external audit ACCEPTED and Human-approved.** Backend
`eeb2c2f` + audit hardening `133fe18`/`ca5b380` + Android `f8d855e`:
bounded `PATCH /api/recurring-transactions/{id}` (amount,
category/clear, frequency, nextDueDate, endDate/clear — all optional,
≥1 required) with a complete Android edit flow on recurring detail.
Edits affect future generation only; ledger rows never rewritten.
Independent external audit remediated 1 Major (recurring-entity
`@Version` — scheduler/edit lost-update, now 409 via the existing
handler) + minors in-task; all gates re-green on final source:
backend 383/383, Android JVM 653/653, lint 0 errors, detekt 0,
release/R8 + serializer + manifest green, physical Pixel 4 XL
193/193 ×4, installed + launched
(see [milestones/M16-RECURRING-LIFECYCLE.md](milestones/M16-RECURRING-LIFECYCLE.md),
[reviews/M16-EXTERNAL-AUDIT.md](reviews/M16-EXTERNAL-AUDIT.md)).
M0–M16 COMPLETE; **M17 (Transaction Notes — REQ-BE-010 persisted
description) is COMPLETE — external audit ACCEPTED.** Delivery backend
`9c123f7` + Android `25db8ab`; the independent external audit found and
remediated 2 Majors internal reviews missed (UTF-16 surrogate-split
truncation on unbounded import text; CWE-1236 spreadsheet-formula
injection in CSV export) plus minors (Android take() surrogate boundary;
a racy FIFO-ordered storm test now header-keyed and deterministic).
Accepted final heads: backend `752eac3`, Android `deccaf2`; all gates
re-green on final source — backend 397/397, Android JVM 658/658, lint
0 errors, detekt 0, release/R8 + serializer + manifest green, physical
Pixel 4 XL 196/196 ×2, installed + launched
(see [milestones/M17-TRANSACTION-NOTES.md](milestones/M17-TRANSACTION-NOTES.md),
[reviews/M17-EXTERNAL-AUDIT.md](reviews/M17-EXTERNAL-AUDIT.md)).
**M18 (Transaction Search — REQ-BE-007 residual `q`) is COMPLETE —
external audit ACCEPTED, Human-approved.** Server-side `q` on
`GET /api/transactions/page` over the persisted M17 `description`,
composing all M15 filters before pagination; Transactions-tab search
field on Android. The independent audit found and remediated 1 Minor
the internal reviews missed (EXT-M18-1 — superseded-append
`loadingMore` flag stuck after a failed refetch, fixed `dfda980`) and
added adversarial proof: real-HTTP percent-decoding on random-port
Tomcat, full-history search reach, lone LIKE-metacharacter literals,
shared-term owner-scoped counts, Unicode/validation-order pinning
(backend `d8554f0`). Final gates on accepted source: backend 419/419,
Android JVM 674/674, lint 0e/22w, detekt 0, release R8 green, physical
Pixel 4 XL 200/200 ×2, installed + launched (see
[milestones/M18-TRANSACTION-SEARCH.md](milestones/M18-TRANSACTION-SEARCH.md),
[reviews/M18-EXTERNAL-AUDIT.md](reviews/M18-EXTERNAL-AUDIT.md)).
**M19 (Account Lifecycle — REQ-BE-006) is COMPLETE — external audit
ACCEPTED, human-approved.** `PATCH /api/accounts/{id}` rename +
`archived` flag (hidden from entry pickers, still in ledger/balances);
Android edit screen + picker exclusion + archived badges. Independent
external audit found one Minor (name-validation divergence vs create —
`(?s)` DOTALL fix at backend `9970c3e`) and pinned four Android edge
behaviors (`c50ebd4`). Final gates on accepted source: backend 452/452,
Android JVM 722/722, lint 0e, detekt 0, release R8 green, physical
Pixel 4 XL 213/213 ×2, installed + launched (see
[milestones/M19-ACCOUNT-LIFECYCLE.md](milestones/M19-ACCOUNT-LIFECYCLE.md),
[reviews/M19-EXTERNAL-AUDIT.md](reviews/M19-EXTERNAL-AUDIT.md)).
**M20 (Offsetting Entry — PRODUCT §21/S10/J14) is COMPLETE — external
audit ACCEPTED, human-approved.** The Human authorized one complete M20
delivery block; selection resolved the BE-005-gated correction need via
the canonical §21 compensating-entry remedy (append-only, no product
decision consumed). Delivered: "Create offsetting entry" on
INCOME/EXPENSE detail → prefilled inverse-type create form → canonical
`POST /api/transactions` — the original row untouched. TRANSFER_* legs
and archived-source rows refuse; verbatim honest copy on both surfaces.
The independent audit found + remediated one Major (EXT-M20-1: silent
account retarget on options re-run) and two Minors (EXT-M20-2 stale
archived flag → authoritative re-resolve; EXT-M20-3 backend test gaps —
inverse direction + real `q` search pin) before closeout. Final gates on
accepted source: backend 458/458, Android JVM 745/745, lint 0e, detekt 0,
release R8 + lintVital green, physical Pixel 4 XL 218/218 ×2, install +
launch (see [milestones/M20-OFFSETTING-ENTRY.md](milestones/M20-OFFSETTING-ENTRY.md),
[reviews/M20-EXTERNAL-AUDIT.md](reviews/M20-EXTERNAL-AUDIT.md)).
**Next action is the Human's M21 decision.** Remaining product-gated
capabilities (true transaction edit/void — BE-005's PATCH-vs-void
decision, FX) stay gated.

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
