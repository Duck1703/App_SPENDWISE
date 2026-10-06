# M12 Sentinel Review — Transfers Between Own Accounts

```
VERDICT: PASS (0 blocker / 0 major / 4 minor + 3 notes)
  — second-cycle independent findings folded in below; all remediated
    and gates re-run.
SCOPE: backend D:\spendwise @ 8803396 (REQ-BE-003: TransferController,
  CreateTransferRequest, TransferResponse, TransferService pairing guard
  + 15 new tests) + Android diff 7f7be92..working-tree — TransferDtos,
  TransfersService, TransferOutcome, TransactionRepository.transfer,
  NetworkModule, CreateTransfer{UiState,ViewModel,Screen,Destination},
  TransferCreateRoute, MainFlow/MainShell wiring, Transactions header +
  AccountDetail entry affordances, strings, fakes + tests.
BACKEND CONTRACT: POST /api/transfers only — authenticated, owner-scoped,
  atomic pair, shared transferRef on the 201 response. No GET/PUT/PATCH/
  DELETE, no idempotency key, no FX conversion — all verified absent.
```

## Contract fidelity (wire-level)

- `TransfersServiceContractTest` (MockWebServer + real Retrofit/
  interceptors): exact path `POST /api/transfers`, exact body
  `{"sourceAccountId","destinationAccountId","amount","currency"}` —
  `amount` serializes as a plain-decimal JSON number via
  `BigDecimalSerializer` (never a string, never scientific notation on
  the wire: `25.50`, `1` not `1.0E+2`-form input survives as the user's
  exact decimal).
- `currency` is always sent explicitly — the client never relies on
  the server's omission default, so a backend default change cannot
  silently redirect denomination.
- 201 → `TransferResponseDto{transferRef, outTransaction,
  inTransaction}` → `TransferOutcome` at the repository boundary; both
  legs decode through the unchanged `TransactionDto` path (M11-era
  shape incl. `occurredOn`).
- Error mapping proven on the wire: 401→`Session.Expired`, 400 with
  `fields{}`→`AppError.Validation`, 404→`NotFound`, 500→`Server`,
  transport failure→`NetworkUnavailable`; `CancellationException`
  propagates unwrapped.

## Test coverage vs requirements

**Backend (17 transfer-focused tests, all green):**
- `TransferControllerTest` (SpringBootTest + real filter chain + real
  DB): atomic paired creation moves both balances; both legs visible on
  the caller's ledger via the existing list; 401 unauthenticated;
  foreign-owned source AND destination → 404 (not 403, no ownership
  leak) in both directions; same-account → 400; cross-currency pair →
  400 rejected before any write; non-positive + overscaled amounts →
  400; missing fields/malformed bodies → 400; explicit request currency
  ≠ account currency → 400; omitted currency → source's.
- `TransferControllerSliceTest`: request binding/validation slices.
- `TransferServiceTest` (pre-existing): atomicity + rollback.

**Android JVM (focused files):**
- `CreateTransferViewModelTest` (17): option load/sort, prefill,
  stale-destination drop after reload resolves a different source,
  same-account guard, cross-currency clear rejection (no network call),
  amount parse bounds matching `@Digits(17,2)`/`DecimalMin`,
  submit-lock, `fields{}` → per-field errors, unknown-field validation
  → form error, 404 → pickers refresh + AccountUnavailable, server →
  Server, ambiguous paths (`NetworkUnavailable`,
  `SessionEndedException`-after-response) → `Ambiguous` honest copy,
  session-expired submit → lock release + draft preserved, success →
  `Event.Created` + dual invalidation + mutation emission.
- `TransactionRepositoryTest` transfer block + `ConcurrencyTest`:
  request shape, dual-cache invalidation, `_mutations` emission,
  dead-generation → `SessionEndedException`, API/IO/cancellation
  mapping — parity with `create`'s suite.
- `TransfersServiceContractTest`: wire shape + error mapping above.

**Connected (Pixel 4 XL, 172→174 tests, twice green):**
- `CreateTransferScreenTest`: form fields/actions, destination picker
  never offers the source (menu adds zero `Checking` nodes while the
  FROM field legitimately shows it), submit/cancel callbacks,
  submitting lock, one-account → need-two + create-account action,
  options-error retry, ambiguous banner copy, cross-currency error text.
- `TransactionsScreenTest`: transfer affordance appears only with ≥2
  accounts; `AccountDetailScreenTest`: Transfer prefills source, hidden
  when `hasTransferPeer=false`.

## Findings remediated during review

| Sev | Item | Disposition |
| --- | --- | --- |
| minor | `loadOptions` could leave `destinationAccountId` equal to a fallback-resolved source (prefill miss / post-404 reload) | Fixed — destination now drops when it equals the resolved source; regression test added |
| minor | Cross-currency pairing would surface as a generic banner — the brief requires *clear* rejection | Fixed — `AccountError.CrossCurrency` field error naming the source currency; VM + screen tests added |
| minor | Account-detail `Transfer` action exposed unconditionally vs the "≥2 accounts" entry-point rule | Fixed — `Content.hasTransferPeer` via a cache-first `list()` read; SessionExpired/cancellation still propagate; screen test for the hidden case added |
| note | `SwDropdownField` has no `enabled` param, so pickers stay interactive while submitting — identical to txn-create convention; submit-lock + VM-side same-account guard cover the mutation window | Accepted (convention parity) |

## Residual notes (non-blocking)

- No UI test exercises a successful end-to-end submit → repo →
  navigation (destination-level tests are screen-stateless by
  convention; the repo/VM seams are covered at their own layers).
- `require(sourceAccountId != destinationAccountId)` in the repository
  is a programmer-invariant assert (UI + VM guard it first) — a thrown
  `IllegalArgumentException` would crash rather than surface `AppError`;
  unreachable from the shipped UI.

## Second-cycle findings (independent external-review pass)

A post-verdict adversarial re-read falsified one test-integrity
assumption and found three coverage/consistency gaps. All frozen
before remediation and fixed inside the milestone:

| Sev | Item | Disposition |
| --- | --- | --- |
| major | `TransferServiceTest.rollsBackDebitWhenCreditLegFails` became vacuous once same-currency rejection moved pre-write: it now fails *before* leg 1 persists, so no test proved a mid-pair failure rolls back leg 1 | Fixed — test rewritten around a `@MockitoSpyBean` on `TransactionRepository` that makes `existsById` throw deterministically on leg 2's call (call #2), inside a non-transactional test so a real rollback boundary is exercised; asserts zero transaction rows and unchanged balances afterward |
| minor | Each transfer leg evaluated `LocalDate.now()` independently — a midnight-adjacent transfer could split `occurredOn` across the pair | Fixed — `TransferService` captures one `occurredOn` snapshot and passes it to both `TransactionCreationCommand`s |
| minor | Negative amount never hit the backend boundary (0 and 10.005 were covered; `-5.00` relied on `@DecimalMin` inference) | Fixed — `TransferControllerSliceTest` now posts `-5.00` and asserts 400 |
| minor | `hasTransferPeer` had screen-level coverage only — nothing proved the VM derives it from peer count or survives the failure/expiry paths | Fixed — `AccountDetailViewModelTest` gained 4 tests: ≥2 accounts→true, 1 account→false, peer-list failure→false without sinking the detail, SessionExpired→arms the resume contract and recovers true on `onVisible` |
| note | Different-user reauth can preserve inert draft text — inherited M8 convention; foreign IDs reconcile out on reload | Accepted (inherited convention, safe) |
| note | Dropdowns stay enabled during submit — same as txn-create; submit lock covers the mutation window | Accepted (convention parity) |
| note | Repo `require(legs differ)` is a crash-on-programmer-error invariant | Accepted — unreachable via UI; server independently validates |

Gates re-run after remediation: JVM suite green incl. new tests;
connected suite re-run on Pixel 4 XL.
