# QA REPORT — M17 Persisted Transaction Descriptions

From: ARGUS
To: ATLAS (reconciliation), THE HUMAN (via ATLAS at gate)
Date: 2026-10-08
Evidence reviewed: `project-control/milestones/M17-TRANSACTION-NOTES.md`,
Android diff `f304c5b..445ffb0` + working tree, backend diff
`ca5b380..9c123f7`, `M17-SENTINEL-REVIEW.md`, live gate output.

## Scope fidelity

Authorized scope: persist the already-accepted `description` field
(backend), return it on read surfaces + CSV, rename the Android "hint"
semantics to a real note (send on create, display on detail). Nothing more.

Delivered = exactly that. **No scope creep**: no search, no transaction
mutation endpoint, no list-item note UI, no recurring/transfer note
invention (generated rows correctly carry `null`). The only judgment call —
capping unbounded import statement text at 255 instead of failing the row —
is documented in the milestone, the entity, and a contract test. Correct
call: the alternative regresses imports.

## Architecture fidelity

- Backend change is at the invariant point: entity normalization
  (`blank → null`, `strip`, cap 255) + one command pass-through in
  `TransactionCreationService`. All creation paths (manual, import,
  recurring, transfer) funnel through it — no per-caller duplication.
- The 8-arg constructor delegates `description = null` — keeps every
  test-fixture call site working; generated/legacy rows default correctly.
- Android follows the established chain: `TransactionDto → Transaction →
  repository → ViewModel → screen`. `hint → description` rename is total —
  zero stale references remain (verified by grep: only design-system
  `hint:` supporting-text params survive, unrelated).
- Detail note uses a new full-width block rather than the side-by-side
  `DetailField` — justified: 255-char text cannot fit a trailing-value row.
  Verified rendered whole on-device (max-length test).

## Reference alignment

N/A — kotlin-accelerator-ai is a different domain (quiz); nothing copied.
Untouched (read-only honored).

## Backend contract alignment

- Wire field unchanged (`description`, `@Size(255)`) — no request-side
  drift; older clients cannot break.
- `TransactionResponse` gained an **additive nullable** field — clients
  with `ignoreUnknownKeys` (verified: `list tolerates unknown future
  fields` test) are unaffected.
- CSV export gained a **trailing** column through existing RFC4180 quoting
  — export schema remains "not re-importable" (already documented).
- `BACKEND-CONTRACT.md` was stale in **6 places** after the change
  (invariants §7, field table, response shape, endpoint table, import-row
  semantics, export header, BG-004). All reconciled — see MAJOR-1.

## Findings

| ID | Severity | Area | Description | Recommended owner |
|---|---|---|---|---|
| A-1 | MAJOR | docs/contract | `BACKEND-CONTRACT.md` still documented pre-M17 behavior in 6 places (`description` "never persisted or returned"; old response shape; old CSV header; import-row "never persisted"; trace claim; BG-004 open). Contract drift of exactly the class M16's audit caught. **FIXED in this block** — all sites reconciled to M17 semantics with `9c123f7` evidence. | FLUX (fixed) |
| A-2 | MINOR | docs/contract | `PRODUCT.md` §6 living summary contradicted the contract (`description` never persisted; also stale M11/M15 claims in the same breath); capability-matrix + BE-010 register rows stale. **FIXED** — summary, matrix row, and BE-010 marked DELIVERED. V1 decision registers (PD-006, `ACCEPT_V1_LIMITATION`) intentionally left frozen per PD-005 precedent. | FLUX (fixed) |
| A-3 | NOTE | domain | `normalizeDescription` strips edges but preserves internal whitespace/newlines verbatim — consistent with "verbatim note" semantics; CSV quoting handles newlines. No action. | — |
| A-4 | NOTE | UX | Note is detail-only, not on list rows — matches milestone scope; a future milestone may want list previews. No action. | — |
| A-5 | NOTE | UX | Silent `take(255)` input truncation — consistent with all `MAX_*` guards in the app (none show counters). Documented by SENTINEL NOTE-1. No action. | — |

## Test evidence assessment

SENTINEL's evidence is credible and requirement-mapped: all REQ-BE-010
acceptance surfaces have a named test on each side of the wire — persistence,
create/list/page reads, null/blank/trim, 255/256 boundary, verbatim
special-chars + CSV escaping, import-cap, generated-row nulls, owner
isolation, auth boundary (backend); wire body/parse, blank omission, VM
submission + bound, detail content, note shown/hidden/max-length (Android).
Physical evidence: 194-run suite had exactly one failure — a **test-harness
defect** (`setContent` twice), fixed and split; focused re-run 8/8 PASS on
the Pixel. The found defect was in test code, not product code.

## Regression risk

Low and verified: append-only ledger untouched (no mutation path modified);
balance/reconciliation invariants don't read `description`; rule engine
input unchanged (raw request value still matched before persistence);
393/393 backend regression incl. all M9–M16 suites; 657/657 JVM; the only
contract change is additive. Flyway V12 is additive-nullable — zero risk to
existing rows. Remaining exposure: physical-suite ×2 on final source still
owed (one run done pre-doc-fixes; code changed since = 1-line blank guard +
test split — re-run required before handoff, already scheduled).

## Verdict

Verdict: **PASS** — 0 Blocker, 0 Major open (1 Major found + remediated:
contract-doc drift), 0 Minor open (1 fixed), 3 Notes.

## Recommended next action

ATLAS: findings A-1/A-2 already remediated in this block — no routing
needed. Proceed to final re-gates (backend + JVM + physical ×2 on final
source), then the external-audit handoff. Do not mark M17 externally
accepted — that gate belongs to the independent audit.
