# P3 Argus Product Review

ARGUS_REVIEW_MODE = **ISOLATED** (independent reviewer, no drafting context)

## Scope

Independent product review of
[../PRODUCT.md](../PRODUCT.md) — the P3 Spendwise Android Product Blueprint —
against the approved [../BACKEND-CONTRACT.md](../BACKEND-CONTRACT.md) (all 43
sections re-verified), [../ANDROID-BASELINE.md](../ANDROID-BASELINE.md),
[../DECISIONS.md](../DECISIONS.md), [../ARCHITECTURE.md](../ARCHITECTURE.md),
[../ROADMAP.md](../ROADMAP.md), and
[../WORKSPACE-BOUNDARIES.md](../WORKSPACE-BOUNDARIES.md).

## Inputs reviewed

- PRODUCT.md (824 lines pre-reconciliation, read in full)
- BACKEND-CONTRACT.md — endpoint/field/error claims cross-checked
- ANDROID-BASELINE.md — senior-reference leakage check
- Governance docs — boundary and evidence-rule check

## V1 coherence review

**PASS** — auth → account → record/import → balances → review → correct →
return is a complete loop; every leg has real endpoints.

## Backend-truth review

**PASS** after reconciliation — all endpoints, DTOs, filters, and error
shapes verified accurate; the one wire-undeliverable claim (F01) was
corrected.

## Screen/contract traceability review

**PASS** after reconciliation — nav graph corrected (register→login,
Welcome added, hidden Export placeholder removed); all 25 screens trace to
real capability or are explicit omissions.

## Product-gap review

**PASS** — every P2 gap has a treatment; budget edit/delete gap added to
the board; 0 PRODUCT_BLOCKERs verified under the stated rule.

## Scope-creep review

**PASS** — 25 screens, 4 destinations, supporting features tucked under
More; escalated to THE HUMAN via Q4/Q5.

## UX honesty review

**PASS** — offset-entry restricted to INCOME/EXPENSE and presented as
compensating (not undo/delete); "Today" is read-only; dateless rows are
explicit; import is blocking with no progress claim; duplicates ≠ errors.

## Findings and dispositions

| ID | Sev | Finding | Disposition |
|---|---|---|---|
| P3-F01 | **MAJOR** | Encrypted-PDF error copy asserted a specific cause the indistinguishable 400 cannot deliver | **FIXED** — §24 now folds encryption/scans/other-banks into static supported-formats guidance; any `.pdf` 400 → generic "not supported" + guidance |
| P3-F02 | MINOR | Capability-class counts wrong (stated 13/4/11/4/6) | **FIXED** — corrected to 18/4/10/5/5 (42 rows) |
| P3-F03 | MINOR | Nav graph showed Register→root (no token on register) | **FIXED** — graph now routes Register → Login (prefilled) → root |
| P3-F04 | MINOR | "Welcome" surface absent from graph and inventory | **FIXED** — Welcome node added; S25 added to inventory |
| P3-F05 | MINOR | Hidden "Export placeholder" violated the no-teaser rule | **FIXED** — removed from V1 graph; absence rule stated |
| P3-F06 | MINOR | BE-ID reference errors (BE-009 vs BE-008; description→BE-001) | **FIXED** — §40→BE-008; description→BE-010; §9 footnote clarifies priority subset |
| P3-F07 | MINOR | Budget edit/delete missing from gap board and BE register | **FIXED** — board row + BE-011 added |
| P3-F08 | MINOR | CSV spec had spaces; BOM/sign/currency semantics undocumented | **FIXED** — exact header `occurredOn,amount,currency,description,sourceTxnId`, no-BOM, signed amounts, currency-match, `sourceTxnId` optional, `description` ≤40 chars — all stated |
| P3-F09 | MINOR | "local ordering" claimed though no sort field exists | **FIXED** — reworded to grouping/filtering |
| P3-F10 | MINOR | "4 V1_SUPPORTING families under More" off by one | **FIXED** — 3 under More + txn detail under Transactions |
| P3-F11 | MINOR | §20 validation cited nonexistent "name" field | **FIXED** — "amount/account presence" |
| P3-F12 | MINOR | Contract citations drift (PD-013 §30, PD-015 §29) | **FIXED** — PD-013→§33/BG-017; PD-015→§22/BG-018+§5 |
| P3-F13 | MINOR | "More" labeled V1_SUPPORTING while hosting core Import | **FIXED** — relabeled V1_CORE (hosts supporting surfaces) |
| P3-F14 | NOTE | "any 401" unscoped; register-duplicate indistinguishable; expired-logout unspecified | **ADOPTED** — §16 scoped to authenticated calls, hedged duplicate copy, added logout-discards-regardless line |
| P3-F15 | NOTE | Type filter unconstrained; TRANSFER_* would be always-empty | **ADOPTED** — §19 specifies All/Income/Expense only |
| P3-F16 | NOTE | Breakdown excludes uncategorized spending unexplained | **ADOPTED** — §17 footnote added; claim scoped to month-accuracy |
| P3-F17 | NOTE | "Today" is the server's local day | **ADOPTED** — §20 footnote; flagged for BE-001 |
| P3-F18 | NOTE | "Cancel-safe" implies nonexistent server cancel | **ADOPTED** — S13 renamed to "wait only; interruption per §23" |
| P3-F19 | NOTE | Optimistic-lock 409 unmapped | **ADOPTED** — CONFLICT_409 state added to §31 |
| P3-F20 | MINOR | ROADMAP status lines stale; no P3 milestone record | **FIXED** — milestone file + ROADMAP updates produced at reconciliation |
| P3-F21 | NOTE | Borderline architecture vocabulary in PD rationale | **ADOPTED** — P1-input cells reworded to product terms; "FAB"→"persistent primary action" |
| P3-F22 | NOTE | Verdict endorsement + compound-effect recommendation | **ADOPTED** — §10 now names the compound honesty bar + CSV-occurredOn mitigation |
| P3-F23 | NOTE | Polish bundle (offset copy, budget unfixable, disabled rules, query param, identity precision) | **ADOPTED** — all five applied |

## Proposed-decision challenge

PD-001–PD-015 challenged against the contract: all are deliverable or
explicitly gated; PD-004's offset-entry semantics verified safe (exact
arithmetic inverse via `applyIncome`/`applyExpense`, INCOME/EXPENSE-only);
PD-007's uniform-currency dashboard rule verified provably safe
(currency-match is enforced on every write path); PD-009's currency lock
makes the recurring defect unreachable for app-created templates.

## Boundary review

- BACKEND_MODIFIED = **NO**
- REFERENCE_MODIFIED = **NO**
- ANDROID_CODE_CREATED = **NO**
- ARCHITECTURE_FINALIZED = **NO** — §42 lists product constraints only

## Verdict

**PASS**

- BLOCKER = 0 · MAJOR = 1 · MINOR = 13 · NOTE = 9
- BLOCKERS_RESOLVED = YES (none existed)
- MAJORS_RESOLVED = YES (F01 fixed and re-verified)

The YES_WITH_ACCEPTED_LIMITATIONS readiness verdict is defensible,
conditional on THE HUMAN accepting the compound-limitation bar via
Q1/Q2.
