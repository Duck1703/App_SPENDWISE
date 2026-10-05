# Spendwise Post-V1 Roadmap Proposal

**STATUS: PROPOSED_ONLY — CANONICAL: NO — HUMAN_APPROVAL_REQUIRED: YES**

Rewritten after the independent Post-V1 audit (backend source @ `8194daa`,
not prior documents). Creates no milestone.

## 1. Approved V1 baseline

`cefec23` — M0–M8 complete; V1 release acceptance APPROVED; 422/422 JVM,
135/135 connected on emulator + physical; minified release verified.

## 2. Product capability inventory

14 candidates: 3 backend-ready surfaces + 11 BE register items. Full
detail: `POST-V1-CAPABILITY-MATRIX.md`.

## 3. Backend readiness

| Capability | Verdict |
| --- | --- |
| CSV ledger export (`GET /api/transactions/export`) | **READY end-to-end** — auth'd, owner-scoped, `text/csv` attachment, optional account filter, plain-string precision, read-only |
| Import report detail (`GET /api/imports/{id}`) | **READY for detail** — rich row-level response; **discoverability gap**: no list → needs local batch pointers or REQ-BE-004 |
| FX conversion (`GET /api/fx/convert`) | **READY for a display converter only** — external rate, `setScale(2)`, no timestamp, `success:false` on failure. NOT suitable for ledger/totals math |

## 4. Backend gaps

All 11 BE items verified at source (`POST-V1-BACKEND-REQUIREMENTS.md`):
zero PUT/PATCH anywhere; 2 DELETEs (recurring, rules — consumed);
no refresh-token; no transaction `GET/{id}`; `occurredOn` persisted
but unexposed (contract-level gap); `findAllByUserId` exists uncalled
for import batches; `transferRef` persisted awaiting an endpoint;
`AdvancedStatisticsService` unwired.

## 5. Android readiness

Reusable: Retrofit service-per-domain pattern, `ImportBatchSummaryDto`
(already matches report shape), `OpenDocument` SAF picker (mirror:
`CreateDocument`), session-generation guard, typed nav, More surface
(home for these features), error mapping, BigDecimal model layer.
Needed new: one raw-body (`ResponseBody`) endpoint, a `CreateDocument`
save flow, optionally a small DataStore for import-batch pointers
(pointer-only, generation-safe, bounded, cleared cross-user).

## 6. Capability ranking

CSV export = highest value/zero-risk/ready. Import reports = high value
with one product decision. Everything else is backend-gated
(BE-001 dates = highest user value overall, smallest backend change)
or product-gated (FX).

## 7. Dependency graph

```
CSV export ──────────────► READY (no deps)
Import report detail ────► READY + [decision: local pointers]
                          OR blocked-by REQ-BE-004
FX converter ────────────► READY + [product decision: ship at all]
Dates (BE-001) ──────────► REQ-BE-001 ─► display ─► entry ─► filters(BE-007)
Transfers (BE-003) ──────► REQ-BE-003 (transferRef model ready)
Mutations (005/006/011) ─► REQ-BE-005/006/011
Refresh (BE-002) ────────► REQ-BE-002 (security-sensitive)
Pagination (BE-007) ─────► REQ-BE-007 (+BE-001 for date params)
Notes (BE-010) ──────────► REQ-BE-010 (schema)
Import history true ─────► REQ-BE-004 (thin)
Txn detail deep-link ────► NEW REQ: GET /api/transactions/{id}
```

## 8. Roadmap options

**OPTION A — Lowest-risk incremental:** M9 = CSV export only →
backend-dependent milestones start at dates. *Benefit:* zero product
decisions, smallest ship. *Tradeoff:* thin milestone; import reports
wait for REQ-BE-004.

**OPTION B — Highest immediate value (recommended):** M9 = "Ledger
Export & Recent Import Reports" — export + this-device import report
history via small pointer store → then BE-001 dates → mutation cluster.
*Benefit:* two coherent user-visible features, still zero backend work.
*Tradeoff:* requires the local-pointer product decision.

**OPTION C — Backend-first:** REQ-BE-001 + REQ-BE-004 land first, then
Android consumes dates + true import history. *Benefit:* no device-local
pointers ever; purest architecture. *Tradeoff:* nothing ships on
Android until backend releases; program stalls if backend isn't staffed.

## 9. Recommended strategy

**OPTION B.** It ships real user value now with zero backend
dependency, keeps every V1 architecture guarantee (read-only,
server-authoritative, generation-safe), and defers all gated work to
explicit backend-requirement docs rather than silent patches. If the
Human rejects local pointers, M9 degrades cleanly to Option A.

## 10. Proposed M9

**"M9 — Ledger Export & Recent Import Reports"** — see
`M9-DECISION-BRIEF.md` for the full draft contract.

## 11. Proposed follow-ups (PROPOSED_ONLY / NOT_CANONICAL)

- **Candidate M10 — Transaction dates** (REQ-BE-001): display
  `occurredOn` on rows/detail, then date entry/back-dating. Highest
  user value; contract-level backend add.
- **Candidate M11 — True import history** (REQ-BE-004) **or**
  **mutation cluster** (REQ-BE-005/006/011 + REQ-BE-003) depending on
  backend availability and the M9 pointer decision.
- **Candidate M12 — Session/mutations hardening:** REQ-BE-002 refresh,
  REQ-BE-007 pagination trigger, REQ-BE-009/010 backlog.

## 12. Backend prerequisite backlog

`POST-V1-BACKEND-REQUIREMENTS.md` — 10 requirement specs, REQ-BE-001
and REQ-BE-004 are the thinnest (contract-level only).

## 13. Explicit non-goals

- FX-converted totals anywhere in ledger/budget/home math.
- Any client-side financial aggregation or local financial truth.
- Any mutation UX before its backend endpoint exists.
- Fifth top-level tab; redesign of V1 IA.

## 14. Human decisions required

1. Local batch-pointer store for import reports (M9 scope question).
2. Is backend work in-scope for this program? (everything post-M9).
3. FX stance: keep V1 "never mix currencies" (recommended).
4. Ship a standalone FX converter tool? (optional; not in M9).
5. Backend delivery mechanism — who implements REQ-BE-* and when.
