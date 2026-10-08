# Post-V1 Backend Requirements — contract-level only (analysis)

**STATUS: PROPOSED_ONLY — REQUIREMENTS DOC — NO BACKEND IMPLEMENTATION**

Backend `D:\spendwise` @ `8194daa` remains authoritative and untouched.
Each item states the *contract need*, not internal class design.
Ordered by unlock value.

## REQ-BE-001 — Transaction date in the public contract — **DELIVERED (M11)**

- Gap: `occurredOn` persisted but absent from `CreateTransactionRequest`
  and `TransactionResponse`.
- Contract: optional `occurredOn` (ISO `LocalDate`) on create (default
  server-today, preserve current defaulting); add `occurredOn` to every
  transaction read (list, detail).
- Validation: reject future-dated beyond a sane bound; reject pre-ledger
  bounds is product-call.
- Unlock: date display on rows, date entry/back-dating, date filters,
  honest import/reconciliation views. CSV export already proves the
  data exists.
- Delivered (backend `bc6e0bb`): `occurredOn` optional on
  `CreateTransactionRequest` (omission → server `LocalDate.now()`;
  dates beyond tomorrow → 400 at the manual-create boundary) and
  required on `TransactionResponse` (column NOT NULL). Date filters
  remain deferred under REQ-BE-007.

## REQ-BE-004 — Import batch list — **DELIVERED (M10)**

- Gap: `ImportBatchRepository.findAllByUserId` exists, uncalled.
- Contract: `GET /api/imports` → owner-scoped list of batch summaries
  (batchId, fileName, importedAt, counts; row detail stays behind `/{id}`),
  ordered by importedAt desc.
- Unlock: true server-side import history; removes any need for
  device-local batch pointers.
- Delivered: `ImportReportController.list` + `ImportReportService.listForUser`
  + `ImportBatchListItem` + grouped `ImportRowRepository.countByBatchIdIn`;
  `ImportHistoryListTest` (5 tests — ownership isolation, ordering, counts,
  `200 []` empty, 401, no `rows` key). No schema change.

## REQ-BE-003 — Public transfer endpoint — **IN_DELIVERY (M12)**

- Gap: `transferRef` persisted; `TransferService` internal-only.
- Contract: `POST /api/transfers` {fromAccountId, toAccountId, amount,
  currency?} creating the paired rows with shared `transferRef`;
  atomic; owner-scoped 404s; validation on cross-currency pairing.
- Unlock: first-class transfer UX; paired-transaction display already
  anticipated by the data model.
- M12 scope (field names shipped as `sourceAccountId` /
  `destinationAccountId`): `201` with `{transferRef, outTransaction,
  inTransaction}` — both legs are full `TransactionResponse`s;
  `currency` optional, defaults to the source account's; same-account /
  cross-currency pairing / non-positive amount → 400; unknown-or-
  foreign accounts → 404. Explicit same-currency pairing guard added
  inside `TransferService` (rejects before any write; the existing
  rollback test remains green).

## REQ-BE-005 — Transaction correction (edit or void)

- Gap: append-only ledger.
- Contract: either `PATCH /api/transactions/{id}` (bounded field set) or
  `POST /api/transactions/{id}/void` creating a linked reversal —
  product decision which; must be owner-scoped and audit-preserving.
- Unlock: true correction UX replacing the offset-entry stopgap.

## REQ-BE-006 — Account edit/archive

- Contract: `PATCH /api/accounts/{id}` (rename; optional `archived`
  flag). Define archived-account semantics: hidden from pickers, still
  in ledger/balances.

## REQ-BE-011 — Budget edit/delete — **DELIVERED (M14)** — external audit code-complete; physical-runtime closeout gate pending

- Contract: `PATCH /api/budgets/{id}` (amount/period) and/or
  `DELETE /api/budgets/{id}`. Must resolve the unique category+month
  constraint for "wrong current-month budget" fix.
- M14 shape: `PATCH {limit?, period?}` (≥1 field → 400 otherwise;
  currency/category immutable — delete+recreate is the remedy);
  owner-scoped 404; collision → 409; `DELETE` → 204 hard delete
  (budgets are planning objects, not ledger — no schema change).

## REQ-BE-002 — Refresh token — **DELIVERED (M13)**

- Contract: refresh-token issuance at login, rotation on use,
  revocation list (extend `TokenBlacklist` concept), reuse detection,
  logout revokes both. Security-sensitive: threat-model first.
- Note: V1's 15-min + reauth UX is honest and functional — value is
  convenience, not correctness. Medium-large effort; sequence after
  value items.
- M13 shape: opaque 256-bit rotating refresh token stored as a
  SHA-256 hash in a process-local allowlist (fail-closed on restart —
  the documented `TokenBlacklist` posture); fixed 30-day family
  lifetime; rotated-token replay revokes the family;
  `POST /api/auth/refresh` (permitAll) + logout accepts an optional
  `{refreshToken}` body so a dead access token can still revoke the
  refresh credential. No schema migration.

## REQ-BE-007 — Pagination + filters

- Contract: `Pageable` on `GET /api/transactions` + `from`/`to` date
  params (needs REQ-BE-001's contract exposure for semantic alignment).
- Trigger: deferred until real ledgers show load pain (PRODUCT §39
  already records this assumption).

## REQ-BE-008 — Multipart size

- Contract: raise `spring.servlet.multipart.max-file-size` (or app
  equivalent) beyond ~1 MB with a clear 413 contract instead of a 500.
- Trivial; can ride any backend release.

## REQ-BE-009 — Hardening cluster

- Recurring currency = account currency validation (defect D14 —
  currently unreachable via the app's currency lock, still a
  server-side hole); typed error codes for domain failures; mixed-
  currency statistics policy; batch status field if async import ever
  lands.

## REQ-BE-010 — Persisted description/notes — **DELIVERED (M17, pending external audit)**

- Contract: `description` column on `Transaction` + request/response
  fields (currently accepted but dropped after rule-matching).
- Schema change; medium.
- M17 shape (see [milestones/M17-TRANSACTION-NOTES.md](milestones/M17-TRANSACTION-NOTES.md)):
  additive nullable `VARCHAR(255)` (`V12`); request field unchanged
  (`@Size(255)`); `TransactionResponse.description` added; blank →
  null; CSV export gains a `description` column; no edit surface
  (REQ-BE-005 stays gated); rules input unchanged.

## REQ-BE-012 — Recurring template edit — **DELIVERED (M16, audit-accepted)**

- Gap: templates are create/list/detail/deactivate only; a changed
  amount/schedule/category forces deactivate + recreate.
- Contract: `PATCH /api/recurring-transactions/{id}` with optional
  `amount`, `category`/`clearCategory`, `frequency`, `nextDueDate`,
  `endDate`/`clearEndDate`; ≥1 field required else 400; conflict pairs
  (value + its clear flag) → 400; owner-scoped 404; 200 full template.
  `account`/`direction`/`currency`/`active` immutable (DELETE stays
  the only active-state transition).
- Semantics: edits apply to future generation only — the scheduler
  reads template fields at fire time; generated ledger rows are
  never rewritten. Bounded schema change added at M16 external audit:
  `V11__recurring_version.sql` (`version` column for `@Version`
  optimistic locking — closes the scheduler/edit lost-update).
- Unlock: true recurring maintenance UX replacing the
  deactivate+recreate stopgap (M14 lifecycle precedent).

## Explicitly NOT requested

- FX totals/rate-of-record: product-gated (V1 mixed-currency stance);
  the `fx/convert` endpoint's display-only semantics (scale-2, no
  timestamp) are insufficient for ledger math regardless.
- New auth surfaces (change-password, account-deletion): real products
  need them eventually; not in any near-term Android milestone.
