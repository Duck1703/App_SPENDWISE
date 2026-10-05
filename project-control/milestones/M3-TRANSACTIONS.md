# M3 — Transactions

Status: **HUMAN_APPROVED — COMPLETE**
(`M3_HUMAN_APPROVED=YES`; M4 authorized)

## Authorization

M2 closed by the Human closeout `c701715` recording
`M2_HUMAN_APPROVED=YES` and `M3_AUTHORIZED_TO_START=YES`, which opened
M3 per `ROADMAP.md`. Canonical scope: transaction list + filters +
read-only detail + income/expense entry + account-detail history —
PRODUCT §19/§20 with the Human P3A override (no offset-entry UI;
PRODUCT §21 edit/correct flow deferred — the ledger stays read-only).

## Backend contract verified (source of truth, read-only)

Backend `D:\spendwise` HEAD `8194daa`:

| Endpoint | Success | Failure surface |
| --- | --- | --- |
| `GET /api/transactions` `?accountId&type&category` | 200 `TransactionResponse[]` | 400 (bad enum/UUID); 404 (foreign account filter); 401 |
| `POST /api/transactions` `{accountId, type, amount, currency, category?, description?}` | 201 | 400 + `fields{}`; 404 (account gone); 401 |
| `GET /api/categories` | 200 `{value}[]` | 401 |

`TransactionResponse` = `{id, accountId, type, amount, currency,
category|null, sourceRef|null}`. **No `GET /{id}`, no update/delete,
no pagination, no date field, no search.** `type` ∈ `{INCOME, EXPENSE,
TRANSFER_IN, TRANSFER_OUT}`; manual create is INCOME/EXPENSE only.
`amount` is positive; direction lives in `type`. `description` is a
rule-matching hint, never persisted. Validation: `amount
@DecimalMin(0, exclusive) @Digits(17,2)`, `description @Size(255)`.

## Implemented

- **Domain**: `Transaction` (positive `Money`, server category code,
  `sourceRef` → `TransactionProvenance` derivation), `TransactionType`
  (`TRANSFER_*` + `UNKNOWN` open-set defense — never sent on create).
- **Remote**: `TransactionsService` (list/create — the three real
  params only), `CategoriesService`, `TransactionDto`,
  `CreateTransactionRequestDto`, `CategoryDto` — `BigDecimalSerializer`
  on money; `@EncodeDefault(NEVER)` keeps null optionals off the wire.
- **Repository**: `RemoteTransactionRepository` — always-fresh list;
  `SessionCache`-stamped last-list store backing list-derived `findById`
  (cache hit → one unfiltered fetch on miss → `NotFound`); `create`
  invalidates transaction + account caches (server-authoritative
  balances — never recomputed client-side) and emits `mutations`.
  `CategoryRepository` — session-cached `{value}` codes.
  `AccountRepository.invalidate()` — the cross-repo invalidation
  boundary.
- **ViewModels**: `TransactionsViewModel` (server-param query +
  client-side uncategorized/provenance passes; `flatMapLatest` +
  monotonic serial — stale responses never overwrite newer filters;
  nonce so refresh re-fires; mutation-driven reload; expiry/resume via
  `onVisible`), `TransactionDetailViewModel` (read-only list-derived),
  `CreateTransactionViewModel` (Expense default, account-prefill
  fallback, currency lock, BigDecimal-exact validation mirroring
  backend limits, submit lock, structured `fields{}` errors,
  account-gone picker refresh, ambiguous-failure honesty, draft
  preserved across expiry overlay).
- **UI**: `TransactionsScreen` (browse-first, mint header `+`,
  single-row filter bar — Account/Type/Category server params + Source
  client-side, pull-to-refresh, inline refresh-error banner, honest
  no-match state with clear-filters), `TransactionDetailScreen`
  (read-only fields actually returned: amount, category, account,
  currency, provenance — no date), `CreateTransactionScreen` (type
  segmented control, account picker, locked-currency amount, category
  tiles, optional hint, "Recorded as today"), account-detail history
  section (non-fatal, inline retry, prefilled add).
- **Design system**: `CategoryTile`, `SwFilterChip`,
  `ProvenanceBadge`, `TransactionRow`, `categoryLabel`/`typeLabel`/
  `provenanceLabel`/`signedAmountText`/`amountColor`; Lucide icon set.
- **Navigation**: typed `TransactionsRoute` (shell tab),
  `TransactionDetailRoute(transactionId)`,
  `TransactionCreateRoute(accountId?)` on the outer graph; post-create
  landing selects the Transactions tab via a single-shot
  savedStateHandle flag; expiry overlay covers pushed transaction
  flows; same-user resume preserves stack + draft.
- **Home**: minimal real Home screen with the scroll-aware
  extended↔compact add-transaction FAB (AD-019).

## Tests

`testDebugUnitTest`: **258/258 PASS** (+87 over M2's 171) — wire
contract (7), transaction repository (9), generation-stamp concurrency
(4), categories (3), ViewModels (46 + 4 history), routes (+1), network
config (1), plus the full M1+M2 regression in the same run. androidTest:
23 Compose cases across the transaction screens + updated account-detail
suite — **compile-verified; no device** (`adb devices` empty).
Real-backend E2E: not run (no local backend provisioned).

## Quality gates

`assembleDebug` PASS · `testDebugUnitTest` PASS (258/258) ·
`lintDebug` PASS (0 errors; 20 informational warnings — same class as
M2) · `compileDebugAndroidTestKotlin` PASS · `assembleRelease` PASS ·
detekt **0 findings**.

## Reviews

- Sentinel: [reviews/M3-SENTINEL-REVIEW.md](../reviews/M3-SENTINEL-REVIEW.md) — PASS.
- Argus: [reviews/M3-ARGUS-REVIEW.md](../reviews/M3-ARGUS-REVIEW.md) —
  PASS; 0 blockers, 0 majors (1 reconciled: session-expiry on a
  mid-refresh/name-lookup load misclassified resume state — fixed +
  locked by regression test), 3 minors reconciled, 6 notes.
- External audit:
  [reviews/M3-EXTERNAL-AUDIT-SUMMARY.md](../reviews/M3-EXTERNAL-AUDIT-SUMMARY.md)
  — FINDINGS (0 blockers, 1 major, 1 minor), remediated:
  `EXT-MAJOR-1` dead-generation (`SessionEndedException`) paths left
  pending state armed and skipped resume-retry across nine sites —
  including two permanently-locked submit locks; all sites now arm the
  resume flag and release pending state, submit paths mark the outcome
  ambiguous. `EXT-MIN-1` OkHttp transparent POST retry — disabled
  (`retryOnConnectionFailure=false`). +12 regression tests; all gates
  re-verified post-remediation.

## Scope exclusions honored

No transaction edit/delete/correct/offset UI (Human override), no
transfer creation (backend-gated), no date display or picking (backend
returns none), no pagination/search UI (unsupported), no dashboard,
import, budgets, recurring, or rules surfaces. No new dependencies.
Backend, reference, prototype untouched; private fixtures never read.

## Boundary preservation

- Backend: HEAD `8194daa` unchanged.
- Reference `D:\app_spendwise\kotlin-accelerator-ai`: unchanged.
- Prototype `D:\app_spendwise\spendwise-ui-prototype`: unchanged
  (read-only design source).

## External-audit gate (state at audit time — superseded by the Human gate below)

```
M3_SENTINEL=PASS
M3_ARGUS=PASS (0 blockers, 0 majors)
M3_EXTERNAL_AUDIT=FINDINGS → REMEDIATED (EXT-MAJOR-1 + EXT-MIN-1, see reviews/M3-EXTERNAL-AUDIT-SUMMARY.md)
M3_JVM_TESTS=258/258
M3_CONNECTED_INSTRUMENTATION=NOT_RUN_NO_DEVICE
M3_GATES=assembleDebug+testDebugUnitTest+lintDebug+compileDebugAndroidTestKotlin+assembleRelease+detekt: PASS
M3_READY_FOR_EXTERNAL_AUDIT=YES
M3_HUMAN_APPROVED=NO
M4_STARTED=NO
SAFE_TO_AUDIT=YES
```

Next action: **M4 — Home / Dashboard.**

## Human gate

`M3_EXTERNAL_AUDIT_VERDICT=FINDINGS` (`BLOCKER=0`, `MAJOR=1` —
EXT-MAJOR-1 dead-generation paths left pending state and skipped
resume-retry across nine sites, including two permanently-locked
submit locks; `MINOR=1` — EXT-MIN-1 OkHttp transparent POST retry).
Remediation `2f2b447` resolved both: every dead-generation path arms
the matching resume-retry flag and releases pending state, submit
paths mark the outcome ambiguous, and
`retryOnConnectionFailure=false` closes the transport-retry hole.
Verification recorded in `348de2b` and
[reviews/M3-EXTERNAL-AUDIT-SUMMARY.md](../reviews/M3-EXTERNAL-AUDIT-SUMMARY.md).

`M3_EXT_MAJOR_1=RESOLVED`, `M3_EXT_MIN_1=RESOLVED`,
`M3_POST_REMEDIATION_VERIFICATION=PASS`,
`M3_FINAL_JVM_TESTS=258/258`,
`M3_CONNECTED_INSTRUMENTATION=NOT_RUN_NO_DEVICE`,
`M3_HUMAN_APPROVED=YES`, `M3_STATUS=COMPLETE`,
`M4_AUTHORIZED_TO_START=YES`.
