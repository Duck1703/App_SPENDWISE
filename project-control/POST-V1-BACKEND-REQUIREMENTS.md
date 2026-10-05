# Post-V1 Backend Requirements — contract-level only (analysis)

**STATUS: PROPOSED_ONLY — REQUIREMENTS DOC — NO BACKEND IMPLEMENTATION**

Backend `D:\spendwise` @ `8194daa` remains authoritative and untouched.
Each item states the *contract need*, not internal class design.
Ordered by unlock value.

## REQ-BE-001 — Transaction date in the public contract (highest value)

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

## REQ-BE-004 — Import batch list

- Gap: `ImportBatchRepository.findAllByUserId` exists, uncalled.
- Contract: `GET /api/imports` → owner-scoped list of batch summaries
  (batchId, fileName, importedAt, counts; row detail stays behind `/{id}`),
  ordered by importedAt desc.
- Unlock: true server-side import history; removes any need for
  device-local batch pointers.

## REQ-BE-003 — Public transfer endpoint

- Gap: `transferRef` persisted; `TransferService` internal-only.
- Contract: `POST /api/transfers` {fromAccountId, toAccountId, amount,
  currency?} creating the paired rows with shared `transferRef`;
  atomic; owner-scoped 404s; validation on cross-currency pairing.
- Unlock: first-class transfer UX; paired-transaction display already
  anticipated by the data model.

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

## REQ-BE-011 — Budget edit/delete

- Contract: `PATCH /api/budgets/{id}` (amount/period) and/or
  `DELETE /api/budgets/{id}`. Must resolve the unique category+month
  constraint for "wrong current-month budget" fix.

## REQ-BE-002 — Refresh token

- Contract: refresh-token issuance at login, rotation on use,
  revocation list (extend `TokenBlacklist` concept), reuse detection,
  logout revokes both. Security-sensitive: threat-model first.
- Note: V1's 15-min + reauth UX is honest and functional — value is
  convenience, not correctness. Medium-large effort; sequence after
  value items.

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

## REQ-BE-010 — Persisted description/notes

- Contract: `description` column on `Transaction` + request/response
  fields (currently accepted but dropped after rule-matching).
- Schema change; medium.

## Explicitly NOT requested

- FX totals/rate-of-record: product-gated (V1 mixed-currency stance);
  the `fx/convert` endpoint's display-only semantics (scale-2, no
  timestamp) are insufficient for ledger math regardless.
- New auth surfaces (change-password, account-deletion): real products
  need them eventually; not in any near-term Android milestone.
