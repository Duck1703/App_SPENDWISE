# M12 — Transfers Between Own Accounts

```
STATUS: COMPLETE — external audit ACCEPTED (second cycle: 1 major +
  3 minor remediated in-milestone); THE HUMAN approved M12 and
  authorized M13 selection in the same directive
M12_DEFINED=YES
M12_STARTED=YES
M12_TECHNICAL_COMPLETE=YES
M12_READY_FOR_EXTERNAL_AUDIT=YES
M12_EXTERNAL_AUDIT_FINAL=ACCEPTED
M12_HUMAN_APPROVED=YES
TRANSFERS=APPROVED
M13_DEFINED=NO
M13_STARTED=NO
M12_HUMAN_AUTHORIZATION: EXPLICIT — Human authorized "select exactly
  one substantial, coherent M12 capability" plus "implement any
  bounded backend prerequisite required by M12" in one continuous
  delivery block ending at M12_READY_FOR_EXTERNAL_AUDIT.
BASELINE: 7f7be92 (post-M11-accepted)
BACKEND: D:\spendwise @ 8803396 — bounded REQ-BE-003 commit,
  com.spendwise.account only (TransferController, CreateTransferRequest,
  TransferResponse, pre-write same-currency guard in TransferService);
  no schema change (transfer_ref exists since V3), no mutation/read
  endpoints added. Human-owned dirty docs (README.md,
  HUONG-DAN-SU-DUNG.md, docs/SPENDWISE-REQ.md) untouched.
RELEASE: app-release-unsigned.apk 2,411,722 bytes —
  SHA256 d036e8e95cc688b2274baefb54d7c8a951cb6c9cc693dd1415144b4a7d062b28
```

## Evidence (final gates — all post-remediation)

- Backend: 17 transfer-focused tests green (11 controller IT + 4 slice
  + 2 service); full regression 310 tests / 1 known M11-documented
  `RecurringSchedulingJobTest` timing flake — unchanged by M12.
- Android JVM: **529/529** green (503 M11 baseline + 26 M12).
- detekt: 0 smells (220 files). lintDebug: clean. assembleDebug: green.
- Connected on physical Pixel 4 XL (Android 13, serial 99041FFBA006F0):
  **174/174 × 2 consecutive runs** (172 baseline + 2 remediation tests).
- Release: `assembleRelease` green; R8 retained 33 `$$serializer`
  classes incl. both new transfer DTOs + the route serializer; manifest
  unchanged (INTERNET only, cleartext=false).
- Reviews: [reviews/M12-SENTINEL-REVIEW.md](../reviews/M12-SENTINEL-REVIEW.md),
  [reviews/M12-ARGUS-REVIEW.md](../reviews/M12-ARGUS-REVIEW.md) —
  both PASS; findings table documents the 3 remediated minors.
- Handoff: [reviews/M12-EXTERNAL-AUDIT.md](../reviews/M12-EXTERNAL-AUDIT.md).

## Why this capability (canonical selection)

PRODUCT Q6 answers "Escalate transfers as BE-003 for post-V1?" with
**"Yes — post-V1 priority"**, and PRODUCT §41's Human-ordered subset is
BE-001 (shipped in M11) → BE-002 → BE-003. §22 records the deferred
decision: when a public endpoint exists, "transfers become a
first-class flow — the backend already proves the domain model
(atomic two-legged, shared `transferRef`, same-currency)". REQ-BE-003
(POST-V1-BACKEND-REQUIREMENTS) is the contract spec this milestone
implements.

Selected over the alternatives because:

- **BE-002 refresh token**: larger security surface (rotation,
  revocation, reuse detection); Q2 escalation was conditional on real
  re-auth pain, not unconditional.
- **BE-005 / BE-006 / BE-011 mutation clusters**: product semantics
  (edit vs void, archive behavior) still need Human calls — the
  mutation-truth ambiguity remains open.
- **BE-007 pagination/date-range**: PRODUCT marks it deferred until
  real ledgers show scale pain; not yet evidenced.
- **BE-010 persisted descriptions**: needs a schema migration; lower
  value than a proven domain capability.
- **FX**: product-gated, display-only — cannot represent financial
  truth.
- **GET-by-id / BE-008**: too thin for the authorized "substantial"
  milestone.

Transfers are the highest-priority *unblocked* gap: the domain is
proven (`TransferService` + `TransferServiceTest` already exercise
atomicity, balance movement, and rollback), the schema already
exists, and Android already renders `TRANSFER_*` rows correctly
(provenance chip, neutral direction label, offset-entry hidden) — the
only missing pieces are the public endpoint and the create flow.

## Human decisions (authoritative)

- `M12 = Transfers between own accounts` — a signed-in user moves
  money between two of their own accounts as one atomic paired write,
  surfaced as two ledger rows linked by a shared `transferRef`.
- Backend write authorized for REQ-BE-003 only: `TransferController`,
  `CreateTransferRequest`, `TransferResponse`, and the explicit
  same-currency pairing guard inside `TransferService`. Nothing else.

## User outcome

A signed-in user records "move 25.00 USD from Checking to Savings" in
one action. The server atomically creates `TRANSFER_OUT` +
`TRANSFER_IN` legs sharing one `transferRef`, moves both stored
balances in the same DB transaction, and the rows appear on Home,
Transactions, and Account-detail history as provenance="Transfer"
entries. No client-side simulation, no paired INCOME/EXPENSE fakery —
the ledger stays server-authoritative.

## Backend contract delivered (REQ-BE-003)

`POST /api/transfers` — authenticated (`anyRequest().authenticated()`
covers the path; 401 unauthenticated).

Request `CreateTransferRequest`:

```
{sourceAccountId, destinationAccountId, amount, currency?}
```

- `sourceAccountId`, `destinationAccountId`: `@NotNull` UUIDs.
- `amount`: `@NotNull @DecimalMin(0.00, inclusive=false)
  @Digits(17,2)`.
- `currency`: optional ISO code. Omitted → the **source** account's
  currency. Present-but-different → the IN leg's `CurrencyMismatch`
  → 400 (and the explicit pairing guard rejects differing
  source/destination account currencies up front).

Response `201 Created` (no `Location` — two resources share no single
URI):

```
{transferRef, outTransaction: TransactionResponse,
 inTransaction: TransactionResponse}
```

Both legs are the full M11-era `TransactionResponse` shape
(`occurredOn` = server today).

Error mapping (existing conventions):

- 401 unauthenticated.
- 404 — source or destination account unknown/foreign
  (`accountService.find` per the transaction-create convention:
  foreign behaves as not found).
- 400 — same account ids, non-positive/malformed amount, cross-
  currency pairing, request currency ≠ source currency.

Atomicity: `TransferService.transfer` is `@Transactional`; the IN leg
failure rolls the OUT leg back (proven by
`TransferServiceTest.rollsBackDebitWhenCreditLegFails`). The new
pairing guard rejects differing account currencies *before* any write
with `CurrencyMismatchException` (a `DomainException` → 400).

Unchanged: append-only ledger, no GET/PUT/PATCH/DELETE on transfers or
`GET /api/transactions/{id}`, `transferRef` stays off the transaction
wire shape (it is returned only as the pair's linkage key on this
endpoint's response), no peer-to-peer — both legs are resolved under
the caller's ownership only.

## Architecture decisions (M12-local)

- **`transfer()` lives on `TransactionRepository`** — a transfer is a
  ledger write that mutates the transaction list AND two account
  balances; keeping it on the ledger repository preserves the single
  `mutations` SharedFlow + the same invalidation pair
  (`cache.invalidate()` + `accountRepository.invalidate()`), so every
  surface that already observes ledger mutations refreshes correctly
  with zero new wiring.
- **Dedicated create screen, not a third type-toggle** — the transfer
  form has a different field shape (from-account + to-account, no
  category/hint/date). `TransferCreateRoute(fromAccountId: String? =
  null)` mirrors `TransactionCreateRoute` incl. the account-detail
  prefill pattern.
- **Entry points**: Transactions header gains a small transfer icon
  (`ic_lucide_arrow_right_left`) beside "+" — the money-movement
  surface — and Account detail gains a "Transfer" action that
  prefills the source account. No FABs, no fake ledger rows.
- **Zero list/detail changes** — `TRANSFER_*` rendering, provenance
  chips, the client-side "Transfer" provenance filter, and the
  hidden-offset rule on transfer rows already shipped in M0–M3.
- **Currency locked to the source account** — same locked-currency
  pattern as transaction create; the request always sends the source
  account's ISO code explicitly rather than relying on the server's
  omission default.
- **No transfer type-filter chip** — the server `type` param accepts a
  single enum; the existing client-side provenance filter already
  isolates transfer rows. A dual-enum type filter would be a contract
  change for negligible value.

## Scope exclusions (explicit non-goals)

- No transfer detail screen beyond the existing transaction detail
  (rows are first-class ledger entries already).
- No paired-leg navigation/UI (no GET-by-transferRef exists; the
  linkage is server-side truth exposed via `transferRef` on the
  create response only).
- No transfer edit/cancel/reversal (append-only ledger).
- No cross-currency transfers, peer-to-peer transfers, scheduled or
  recurring transfers.
- No date selection on transfers (REQ-BE-003 spec carries no date;
  legs stamp server `occurred_on` = today).

## Evidence (filled at closeout)

- Backend focused tests: TBD
- Backend full regression: TBD
- Android JVM: TBD
- Connected physical: TBD
- Release/serializer/manifest: TBD
- Sentinel/Argus verdicts: TBD
