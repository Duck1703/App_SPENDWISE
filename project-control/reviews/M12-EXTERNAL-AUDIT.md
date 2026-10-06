# M12 External Audit — Transfers Between Own Accounts

```
MILESTONE: M12 / REQ-BE-003 — public POST /api/transfers + Android
  first-class create flow (PRODUCT §22, Q6 post-V1 priority)
AUDITED_RANGE (Android): 7f7be926ec998e661ead38caf19e6c61da442d9a
  (accepted M11 baseline) .. M12 working tree
  (feature + governance — see commit list below)
BACKEND: D:\spendwise bc6e0bb67cae0366b12c5b03fd97915f90450619
  ..88033961c2c9a00d64091eec176d8a611028332c (one commit, account
  package only — bounded write authorized by the Human's M12 block)
SENTINEL: PASS — 0 blocker / 0 major / 4 minor notes
  (reviews/M12-SENTINEL-REVIEW.md)
ARGUS: PASS — 0 blocker / 0 major / 3 minor / 2 notes
  (reviews/M12-ARGUS-REVIEW.md); 3 minors remediated pre-verdict with
  regression coverage
M12_READY_FOR_EXTERNAL_AUDIT=YES
METHOD: contract re-derivation from PRODUCT/CONTRACT/requirements
  docs, wire-shape and error-mapping tests, adversarial self-review,
  fresh gate runs on the physical Pixel 4 XL.
```

## Scope delivered

**Backend `8803396`** — `feat: expose POST /api/transfers for
own-account moves (REQ-BE-003)`:

- `TransferController` — authenticated `POST /api/transfers`;
  owner-scoped account resolution (`findByIdAndUserId` → foreign or
  unknown IDs return **404**, never 403 — no existence leak);
  `currency` optional → defaults to the source account's ISO code.
- `CreateTransferRequest` — `@NotNull` both account IDs; `amount`
  `@NotNull @DecimalMin("0.00", inclusive=false) @Digits(17,2)`;
  `currency` free-form String → `Money.parseCurrency` wraps invalid
  ISO codes as `DomainException` → 400 (no 500 leak).
- `TransferResponse` — `{transferRef, outTransaction, inTransaction}`;
  both legs are full `TransactionResponse`s; `transferRef` (the pair's
  shared linkage key) appears only on this response.
- `TransferService` — added the explicit **pre-write same-currency
  pairing guard** (`CurrencyMismatchException` → 400 before either leg
  persists); pre-existing guards unchanged: same-account →
  `DomainException`→400, non-positive → 400, atomic `@Transactional`
  pair via the canonical `TransactionCreationService` path, shared
  `TRANSFER:<ref>:OUT|IN` `sourceRef`s.
- No schema migration (`transfer_ref` column exists since V3), no
  read/update/delete endpoints, no idempotency key, no FX conversion.
- Human-owned dirty docs (`README.md`, `HUONG-DAN-SU-DUNG.md`,
  `docs/SPENDWISE-REQ.md`) untouched and excluded from the commit.

**Android** (working tree on `7f7be92`):

- Wire: `CreateTransferRequestDto` / `TransferResponseDto` /
  `TransfersService` (`POST api/transfers`); `currency` always sent
  explicitly (locked to the source account — the omission default is
  never relied on); `amount` via `BigDecimalSerializer` → plain-decimal
  JSON number.
- Domain/repo: `TransferOutcome{transferRef, out, in}`;
  `TransactionRepository.transfer` mirrors `create` byte-for-byte —
  session-generation capture → request → **dual cache invalidation**
  (transactions + accounts) → generation re-check →
  `SessionEndedException` before any publish → `_mutations` emission.
  No local ledger arithmetic; the server's legs are the only truth.
- Feature: `CreateTransferViewModel` + `CreateTransferScreen` +
  `TransferCreateDestination`; typed `TransferCreateRoute(fromAccountId)`
  pushed over the preserved shell (same session-expired/draft contract
  as transaction create).
- Form behavior: source picker; destination picker **never offers the
  source**; currency locked display; same-account, cross-currency, and
  amount validation as clear field errors; submit lock; `fields{}`
  server errors map per-field; 404 → pickers reload +
  `AccountUnavailable`; `NetworkUnavailable` and
  dead-generation-after-response → **honest ambiguous copy** ("couldn't
  confirm whether this transfer was recorded — check your transactions
  before trying again"); no automatic retry of the non-idempotent write.
- Entry points: Transactions-header transfer icon only when ≥2
  accounts; account-detail "Transfer" action only when
  `hasTransferPeer` (cache-first `list()` read; mapped failure →
  hidden; `SessionExpired`/cancellation still propagate), prefilling
  the source. Post-create lands on the Transactions tab (shell path) or
  back on account detail — both refresh via the mutations signal.
- Legs surface through the existing transaction list/detail with the
  M0–M3 `TRANSFER_*` rendering — zero list/detail changes were needed.

## Gate evidence (all re-run after remediation)

| Gate | Result |
| --- | --- |
| Backend focused | 17/17 green (11 `TransferControllerTest`, 4 `TransferControllerSliceTest`, 2 `TransferServiceTest`) |
| Backend full regression | 310 tests — **1 known failure**: `RecurringSchedulingJobTest.firingTheScheduledMethodTwiceForTheSamePeriodCreatesExactlyOneTransaction` (expected 1, was 2) — the M11-documented timing flake; scheduling code untouched by M12 |
| `:app:testDebugUnitTest` | **529/529** (503 M11 baseline + 26 M12, incl. 2 remediation regressions) |
| `:app:detekt` | 0 smells / 220 files |
| `:app:lintDebug` | clean |
| `:app:assembleDebug` | green |
| `connectedDebugAndroidTest` × 2 | **174/174 both runs** — Pixel 4 XL, Android 13, serial 99041FFBA006F0 |
| `:app:assembleRelease` | green — `app-release-unsigned.apk` 2,411,722 bytes, SHA256 `d036e8e95cc688b2274baefb54d7c8a951cb6c9cc693dd1415144b4a7d062b28` |
| Release integrity | 33 `$$serializer` classes retained in R8 mapping incl. both transfer DTOs + `TransferCreateRoute`; manifest unchanged (`INTERNET` only, `usesCleartextTraffic=false`) |

## Review findings remediated inside the milestone

1. `loadOptions` could retain a `destinationAccountId` equal to the
   fallback-resolved source — now dropped on reconcile (VM test).
2. Cross-currency pairing would surface as a generic banner — now a
   named `CrossCurrency` field error citing the source currency
   (VM + screen tests).
3. Account-detail "Transfer" was unconditional — now gated on
   `hasTransferPeer` with failure-conservative hiding (screen test).

## Honest limitations (unchanged by M12)

- No idempotency key on the endpoint — ambiguity is surfaced, never
  auto-replayed.
- No transfer update/delete; legs are append-only and cannot be
  offset (a single-side offset would un-pair the atomic transfer).
- No insufficient-funds check server-side — the source may go
  negative (pre-existing domain rule, not M12-introduced).
- No currency conversion — cross-currency pairs are rejected.
- `transferRef` is not queryable (no GET by ref exists).

## Audit guidance

- Verify wire shape via `TransfersServiceContractTest` + the backend
  `TransferControllerTest` 201 response assertion.
- Verify no-invention via the diff scope above: one POST, one route,
  one feature package, two affordances — nothing else exists.
- Verify ambiguity honesty in `CreateTransferViewModel.submit` catch
  branches and the `transfer_create_error_ambiguous` string.
- The only backend dirty files post-M12 are the three Human-owned docs
  listed above — by design.
