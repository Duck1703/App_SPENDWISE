# P2 — Spendwise Backend Contract Audit

Owner: ATLAS
Status: READY_FOR_HUMAN_REVIEW
Outcome: PASS (pending HUMAN approval)

## Objective

Deeply inspect `D:\spendwise` READ ONLY and map the real backend contract the
future Spendwise Android app can safely depend on — producing
`project-control/BACKEND-CONTRACT.md`. No Android code, no backend changes,
no architecture decisions.

## Human approval of P1

P1 (`0583af63`) was HUMAN APPROVED before P2 began; `ANDROID-BASELINE.md` is a
committed input only — P2 did not re-audit the reference and did not modify
`ANDROID-BASELINE.md`, `ARCHITECTURE.md`, `PRODUCT.md`, or `DECISIONS.md`.

## Starting state

- Product (`D:\app_spendwise\spendwise-android`): branch `main`, HEAD
  `0583af63ffa5caff6208309659c882573b3fc553` (P1 commit), worktree clean.
- Backend (`D:\spendwise`): branch `main`, HEAD
  `8194daa9959f2d568dc13ab0f211787195efc79d`; known user-owned dirty docs
  (`M README.md`, `?? HUONG-DAN-SU-DUNG.md`, `?? docs/SPENDWISE-REQ.md`) —
  excluded from evidence precedence, left untouched.
- Reference (`D:\app_spendwise\kotlin-accelerator-ai`): branch `master`, HEAD
  `5f9622c59ce602ce33c98ee633ba799f903b67e1`, clean.

## Audit scope

Static contract audit of the whole backend: topology, global HTTP behavior,
all 26 endpoints across 12 controllers, account/transaction/transfer
semantics, money/currency, date/time, identifiers, canonical write path,
categories/rules, recurring generation, import API (CSV + TPBank PDF),
dedup, provenance, pagination/filter/sort, dashboard, authentication and
ownership, error contract, validation, nullability, enum wire values,
response shapes, update/delete semantics, idempotency, concurrency,
connectivity, capability matrix, risk matrix, gaps, drift, traces.

## Read-only method

- File reads, directory listings, grep/glob, read-only git (`status`,
  `rev-parse`, `branch`, `ls-files`). No builds, tests, app runtime,
  migrations, Docker, HTTP calls — per the read-only execution policy.
- Four parallel read-only audit passes over disjoint areas (HTTP surface +
  DTOs + errors; money/transactions/transfers/write-path; import/CSV/PDF/
  dedup; auth/rules/recurring/dashboard/budget/fx/tests), then first-hand
  re-verification of critical anchors by ATLAS, then an independent isolated
  ARGUS review with end-to-end re-enumeration.
- Source-grounding enforced: every claim tagged VERIFIED / INFERRED /
  NOT_EVIDENCED / FRAMEWORK_DEFAULT_INFERENCE; dirty user docs excluded from
  evidence precedence.

## Evidence precedence

Controllers → DTOs/serialization → exception handlers/validation →
services/domain → repositories/migrations → tests → application config →
committed docs → comments. The three uncommitted user docs were not used.

## Endpoint coverage

Complete: 26 handlers / 12 controllers, all authenticated `/api/**` except
permitAll `{POST /api/auth/register, POST /api/auth/login, /actuator/health/**,
/v3/api-docs/**, /swagger-ui*}`. Absent endpoints verified by grep: no
PUT/PATCH anywhere; no transfer endpoint (internal `TransferService` only);
no transaction detail/update/delete; no account update/delete; no budget
update/delete; no import batch list; no refresh/`/me`; no statistics HTTP.

## Capability coverage

JWT auth (HS256, 15-min, no refresh); multi-user isolation (404-not-403);
fixed 5-category reference data + user rules engine; WEEKLY/MONTHLY
recurring via 15-min scheduler on the canonical write path; synchronous
CSV + TPBank-PDF import with two-tier dedup and row-level reporting;
single-month dashboard; display-only FX. Gaps recorded in §38 of the
contract (20 entries: 0 BLOCKER, 7 MAJOR, 9 MINOR, 4 NOTE).

## Outputs

- `project-control/BACKEND-CONTRACT.md` — full 43-section contract (replaced
  P0 placeholder).
- `project-control/reviews/P2-ARGUS-REVIEW.md` — independent review.
- `project-control/ROADMAP.md` — status lines only.
- This record.

## Gap count

BACKEND_GAP_COUNT=20 — BLOCKER 0 · MAJOR 7 · MINOR 9 · NOTE 4.
Highest-impact: no transfer endpoint (BG-001), append-only ledger (BG-002),
`occurredOn` unsettable/unreturned (BG-003), no pagination (BG-005), no
refresh token (BG-006), no idempotency keys (BG-008), thin error contract
(BG-010), no date-range/search filtering (BG-011).

## Drift count

BACKEND_CONTRACT_DRIFT_FINDINGS=16 (D1–D16) — includes the
CSV-`IOException`→500-vs-comment-400 mismatch, recurring foreign-account
400-vs-404, `enabled`-ignored-on-create, the recurring currency-mismatch
poison defect (D14), three different currency-mixing conventions (D16), and
two committed-doc inaccuracies (D15).

## Argus verdict

ARGUS_REVIEW_MODE=ISOLATED. Initial verdict PARTIAL (0 BLOCKER, 4 MAJOR,
10 MINOR, 6 NOTE). All 20 findings reconciled FIXED in the contract —
final verdict PASS after reconciliation.

## Unresolved findings

None. Runtime-only unknowns are labeled in §43 (embedded-container error
edge cases, live FX behavior) — RUNTIME_VERIFICATION_DEFERRED, not blockers.

## Boundary preservation

- Backend: HEAD unchanged (`8194daa`), status unchanged (only the three
  known dirty docs), no staging/commit/reset/clean/stash.
- Reference: HEAD unchanged (`5f9622c`), clean.
- Product: only `project-control/**` documentation modified/created.
- PRIVATE_FIXTURES_ACCESSED=NO.
- No remote created, no push, no tag.

## Final state

READY_FOR_HUMAN_REVIEW — P2 completion requires HUMAN approval per the
roadmap gate; this record does not authorize P3.

## Human gate

THE HUMAN must review `BACKEND-CONTRACT.md` and this record before P3 begins.

## Next authorized task

After HUMAN approval only:

**P3 — Spendwise Android Product Blueprint** — combines the approved P1
Android baseline + approved P2 backend contract + product intent to define
mobile scope, V1 boundaries, IA/navigation, screens, journeys, and
backend-gap product decisions. P3 creates no application code.
