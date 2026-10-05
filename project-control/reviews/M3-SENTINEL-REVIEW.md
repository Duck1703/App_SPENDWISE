# M3 SENTINEL Review — Transactions

Reviewer role: **SENTINEL** (test engineer / verification), per
`agent-system/agents/sentinel.md`. Scope: correctness, contract fidelity,
test coverage, session/security integration, financial-correctness
invariants, P3A fidelity, scope discipline, quality gates for the M3
vertical slice.

## Verified contract (source-checked)

Backend `D:\spendwise` HEAD `8194daa` (read-only). `TransactionController`
/ `CategoryController`:

| Endpoint | Success | Failure surface |
| --- | --- | --- |
| `GET /api/transactions?accountId&type&category` | 200 `TransactionResponse[]` | 400 (bad enum / bad UUID); 404 (unknown/foreign accountId filter); 401 |
| `POST /api/transactions` `{accountId, type, amount, currency, category?, description?}` | 201 `TransactionResponse` | 400 + `fields{accountId,type,amount,currency,category,description}`; 400 (TRANSFER_* type); 404 (account gone); 401 |
| `GET /api/categories` | 200 `CategoryResponse[]` = `{value}` | 401 |

Wire shapes (`TransactionResponse` record): `{id: UUID, accountId: UUID,
type: String, amount: BigDecimal, currency: String,
category: String|null, sourceRef: String|null}`. `type` ∈ `{INCOME,
EXPENSE, TRANSFER_IN, TRANSFER_OUT}`; `amount` is strictly positive —
direction lives in `type`. **No `GET /{id}`, no PUT/PATCH/DELETE, no
pagination, no date field, no search** — the ledger is append-only and
read-only after creation. `description` is a rule-engine hint, never
persisted. Create validation: `accountId @NotNull`, `type @NotNull` enum,
`amount @NotNull @DecimalMin(value="0.00", inclusive=false)
@Digits(17,2)`, `currency @NotBlank`, `category` code-or-null,
`description @Size(255)`.

## Correctness checklist

| Check | Result |
| --- | --- |
| DTO field names/types match `TransactionResponse`/`CreateTransactionRequest` exactly | PASS |
| Only the three real server params are ever transmitted — `All` = omitted param, never a sentinel | PASS (`TransactionsServiceContractTest` + repo call-capture tests) |
| `Uncategorized` + provenance are honest client-side filters (never sent) | PASS (`serverCategory` mapping; provenance filters fetched rows only) |
| No `GET /{id}` — detail resolves from last fetched list, one unfiltered fetch on miss → `NotFound` | PASS (repo tests: cache-hit, miss→fetch→hit, miss→fetch→NotFound) |
| Manual create restricted to `INCOME`/`EXPENSE` — `require()` guard before the wire | PASS (VM + repo tests reject TRANSFER_*/UNKNOWN) |
| `BIGDECIMAL_ONLY` — BigDecimal serializer on wire, BigDecimal domain, comma/dot input normalized, ≤17 int / ≤2 frac digits enforced client-side | PASS (VM parse tests incl. boundary digits) |
| Positive amounts only; `+`/`−` and words are display-only | PASS (NotPositive test; `signedAmountText`/`amountColor`) |
| Server-authoritative balances — create invalidates txn + account caches, never computes locally | PASS (`successful create emits one mutation and re-lists fresh`; `create invalidates account cache`) |
| No automatic retry of the non-idempotent POST; ambiguous network failure → honest "couldn't confirm" copy | PASS (VM test asserts no re-issue; copy asserts) |
| Double-tap guard — second `submit()` during flight is a no-op | PASS (`second submit while in flight is ignored`) |
| Structured `fields{amount|accountId}` → inline errors; other field keys → honest form-level error; 404 on account → picker reload + `AccountUnavailable` | PASS (VM tests) |
| Blank hint → `description` omitted from the wire (`@EncodeDefault(NEVER)` + `isNotBlank` gate) | PASS (contract test asserts absent keys) |
| SessionCache generation-stamped; dead-generation writes throw `SessionEndedException` and never publish | PASS (`TransactionRepositoryConcurrencyTest`: stale write dropped, cross-session isolation) |
| `SessionEndedException` (a `CancellationException`) cannot kill the long-lived list collector | PASS (VM keeps collecting after an expired response) |
| Stale list responses can't overwrite newer filter state — `flatMapLatest` + monotonic `loadSerial` | PASS (`stale load result cannot overwrite a newer filter`) |
| `refresh()` emits even when filters are unchanged — nonce defeats StateFlow conflation | PASS (refresh re-issues `list`) |
| Expiry mid-load → overlay owns it; same-user resume re-runs the aborted load — **including** the account-name leg of a first load | PASS — regression found here (expiredMidLoad misclassified when expiry hit after `list()` but before name lookup) → **fixed**, covered by `SessionExpired on the account-name leg still reloads on resume` |
| Account-detail history is a non-fatal section with inline retry; mutations signal reloads it | PASS (`history failure degrades the section`, `history retry`, `mutation reloads`) |
| Unknown `type` on wire → `UNKNOWN`, never a crash; unknown category codes render humanized, never guessed | PASS (repo + label tests) |
| No date rendered or sent — backend returns none | PASS (no date field in DTO; count text states ordering honestly) |
| a11y: merged row CD with sign+word+code, filter chips announce selection, ≥48dp targets, currency-lock announced | PASS (Compose tests) |

## Test coverage (unit: 246 passing, 0 failures — XML-verified)

New M3 suites (+75 over the M2 closeout run of 171):

- **Wire contract** `TransactionsServiceContractTest` (7): paths, params
  (`accountId`/`type`/`category` present only when set), response DTO
  fidelity incl. BigDecimal + `sourceRef`, request DTO null-omission,
  bearer header, 401→expiry, `fields{}` mapping.
- **Repository** `TransactionRepositoryTest` (9): unfiltered/filtered
  list pass-through, always-fresh fetch, detail cache hit / miss→fetch /
  NotFound, create success → cache invalidation + single mutation event,
  create failure (400 fields/404/IO) propagation, transfer-type reject.
- **Concurrency** `TransactionRepositoryConcurrencyTest` (4):
  generation-stamp isolation — stale-generation list write discarded,
  `findById` never serves another session's rows, `SessionEndedException`
  on dead-generation publish, mutation suppressed on dead session.
- **Categories** `CategoryRepositoryTest` (3): `{value}` → code list,
  session-scoped caching, error mapping.
- **ViewModels** `TransactionsViewModelTest` (14),
  `TransactionDetailViewModelTest` (5),
  `CreateTransactionViewModelTest` (15), `AccountDetailViewModelTest`
  (+4 history cases): loading/empty/content transitions, server vs
  client filter split, stale-suppression, refresh nonce, expiry mid-load
  + resume (incl. the name-leg regression), NotFound, options expiry,
  client validation parity (required/invalid/non-positive/too-precise/
  too-large, comma decimals), submit lock, field-error channels,
  account-gone picker refresh, draft preservation.
- **Routes** `AppRoutesTest` (+1): `TransactionCreateRoute(accountId?)`
  and `TransactionDetailRoute(transactionId)` serialization.
- **M1+M2 regression** — all prior suites pass in the same run (171 →
  246 total, 0 failures).

Compose (androidTest — **compile-verified only**; `adb devices` empty,
no instrumentation run): `TransactionsScreenTest` (9),
`TransactionDetailScreenTest` (4), `CreateTransactionScreenTest` (8),
`AccountDetailScreenTest` (+2 history cases). All target stateless
composables — states, copy, row/chip semantics, callbacks, submit lock.

## Gates (this machine)

`assembleDebug` PASS · `testDebugUnitTest` PASS (**246/246**) ·
`lintDebug` PASS (0 errors; 20 warnings — same informational class as
M2: dependency-version notices + decorative `topo_lines` VectorRaster) ·
`compileDebugAndroidTestKotlin` PASS · `assembleRelease` PASS (R8 +
resource shrink) · detekt **0 findings**.

## Unresolved defects

| ID | Severity | Description | Owner |
| --- | --- | --- | --- |
| — | — | none — all findings resolved in pre-review reconciliation | — |

## Sentinel verdict

```
SENTINEL_VERDICT=PASS
UNIT_TESTS=246
UNIT_FAIL=0
CONTRACT_TESTS=PASS
REPOSITORY_TESTS=PASS
VIEWMODEL_TESTS=PASS
COMPOSE_TESTS=PASS (compile-verified; device run deferred — no device)
M1_M2_REGRESSION=PASS
FINANCIAL_CORRECTNESS=PASS
SECURITY_REVIEW=PASS
SCOPE_CHECK=PASS (transactions only; zero M4+ surface)
READY_FOR_ARGUS=YES
```
