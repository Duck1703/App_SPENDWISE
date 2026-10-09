# M19 EXTERNAL-AUDIT HANDOFF — Account Lifecycle (REQ-BE-006)

```
M19_READY_FOR_EXTERNAL_AUDIT=YES
M19_HUMAN_APPROVED=NO          (audit gate precedes human approval)
M19_EXTERNAL_AUDIT=NOT_RUN     (excluded from the delivery block)
SPENDWISE_STATUS=M19_READY_FOR_EXTERNAL_AUDIT
NEXT_ACTION=ONE_LARGE_INDEPENDENT_EXTERNAL_M19_AUDIT
M20_DEFINED=NO
M20_STARTED=NO
```

The independent M19 external audit has NOT been performed — this
document is the handoff into it. Auditors: treat every number below
as a claim to be falsified, not as evidence.

## Scope delivered

One bounded capability: **REQ-BE-006 — account edit/archive**.
`PATCH /api/accounts/{id}` performs a sparse owner-scoped update
(`name?`, `archived?`, at least one required). `archived` is an
operational flag — the account keeps its ledger, balances, history,
search hits, and export rows; it is excluded only from *entry* pickers
(new transaction, transfer from/to, recurring template, import target)
and from the archived account's own Add/Transfer affordances.
Unarchive restores eligibility. Balances are never recomputed; archive
is not delete; no ledger row is written.

Deliberately NOT in scope: account delete, type/currency/opening-balance
mutation, server-side write blocking on archived accounts (pinned
Note — flag is entry semantics, not a ledger freeze), REQ-BE-005
transaction correction (product-gated), M20, course sync.

## Baseline and commits

```
Android repo:  D:\app_spendwise\spendwise-android  (branch main)
  M18 head:    e58dbbd5638af4b1d8fa5259aa187ffb7cc5c85f  (accepted)
  M19 gov:     eb88888  docs(governance): define M19 account lifecycle
  M19 slice:   08a39ec  feat(accounts): M19 account edit/archive
  M19 remed.:  c349edb  fix(accounts): repaint on writes + reviews
  M19 closeout:<this commit — docs, handoff>
Backend repo:  D:\spendwise  (branch main)
  M18 head:    d8554f04a8ba6edcf3fd03ae441cd74617fc147f  (accepted)
  M19 delivery: 342e85d feat(accounts): add M19 PATCH /api/accounts/{id}
  Backend diff: 9 files (+357/−2); human-owned docs
                (README.md, HUONG-DAN-SU-DUNG.md, docs/SPENDWISE-REQ.md)
                untouched.
Reference:     D:\app_spendwise\kotlin-accelerator-ai — read-only, untouched.
```

## Backend contract (what changed)

```
PATCH /api/accounts/{id}        JWT, owner-scoped
  request:  UpdateAccountRequest{name?: string @Pattern(".*\\S.*")
                                 @Size(255), archived?: boolean}
            sparse — at least one field, else 400 (DomainException)
  response: 200 AccountResponse (full entity)
  errors:   400 empty patch / blank or >255 name
            404 foreign-or-unknown id (indistinguishable, R24/R25)
            409 OptimisticLockingFailure → retryable conflict
            401 unauthenticated
Response: AccountResponse gains `archived` (additive — older clients
          ignore it; Android DTO defaults false for pre-M19 payloads).
Entity:   accounts.archived BOOLEAN NOT NULL DEFAULT FALSE (V13 —
          additive, no rewrite, no index; existing rows active).
Optimistic lock: accounts.version (V2, pre-existing) — PATCH-vs-PATCH
          and PATCH-vs-ledger-write both conflict truthfully.
Immutable by omission: type, currency, openingBalance, balance have
          no patch surface.
Semantics pinned: archive does NOT hide the account from GETs, does
          NOT drop ledger/search/budget/export rows, does NOT block
          server-side writes (entry semantics, not a freeze).
```

## Android surface

- `AccountDto.archived` (default false), `UpdateAccountRequestDto`
  (sparse — nulls omitted via default `encodeDefaults=false`).
- `AccountsService.update` — `@PATCH("api/accounts/{id}")`.
- `AccountRepository.update(id, name?, archived?)` — generation-stamped,
  cache row replaced with the returned entity, `mutations` emits.
- `AccountEditRoute` + `EditAccountDestination` + `EditAccountScreen` +
  `EditAccountViewModel` — prefill via `findById(refresh=true)` (server
  truth, never cache), sparse diff (`hasChanges`), submit lock, no
  auto-replay, 409 → "Changed elsewhere" + reload semantics, ambiguous
  mid-write copy, session-expired resume.
- `AccountDetailScreen` — "Edit account" action; archived accounts show
  a Status/Archived field and hide Add/Transfer affordances.
- `AccountsScreen` row + `HomeScreen` card — `Archived` badge
  (a11y content-description included).
- Picker exclusion (`filterNot { it.archived }`): CreateTransaction,
  CreateTransfer (both ends), CreateRecurring, Import target.
  `hasTransferPeer` counts *active* accounts only.
- Lookup surfaces deliberately keep archived accounts: transactions
  filter, export scope, name lookups, account list, dashboard.
- Mutation repaint (SENTINEL MINOR-1, remediated): `AccountsViewModel`
  and `AccountDetailViewModel` now collect `accountRepository.mutations`
  — a saved rename/archive repaints on pop-back.

## Gate evidence (all re-executed on final source, not assumed)

| Gate | Result |
|---|---|
| Backend focused | `AccountUpdateContractTest` **13/13** + `AccountOptimisticLockTest` **1/1** (MockMvc + real Postgres `spendwise-m10-db`) |
| Backend full regression | **433/433**, 0 fail, 0 err, 0 skip (419 → +14; surefire XML aggregate) |
| Flyway | V13 validated + applied; column visible in all account SQL |
| Android JVM | **718/718**, 0 fail, 0 skip (674 → +44; XML aggregate) |
| `lintDebug` | 0 errors, 22 warnings (baseline parity) |
| `detekt` | 0 smells (231 files) |
| `assembleDebug`/`assembleRelease` | PASS — `minifyReleaseWithR8` + `lintVitalRelease` green |
| R8 retention | `UpdateAccountRequestDto`, `AccountEditRoute`, `EditAccountViewModel`, `AccountDto` present in release `mapping.txt` |
| Physical Pixel 4 XL (`99041FFBA006F0`) | **213/213 × 2** consecutive on final source, 0 fail, 0 skip |
| Install + launch | `adb -s` streamed install Success; `MainActivity` topResumed |

## Reviews

- `M19-SENTINEL-REVIEW.md` — **PASS**, 0 Blocker / 0 Major /
  1 Minor (remediated in-delivery) + 2 Notes.
- `M19-ARGUS-REVIEW.md` — **PASS**, 0 Blocker / 0 Major / 0 Minor /
  3 Notes.

## Findings carried into audit (documented, none hidden)

1. **Archived accounts remain writable server-side** (pinned contract
   note, not a defect): `POST /api/transactions` on an archived id
   succeeds — the flag is picker/entry semantics per REQ-BE-006
   wording. If the Human wants a write barrier, that is a contract
   change for a later milestone.
2. **SENTINEL MINOR-1 (remediated)**: account mutations did not repaint
   list/detail on pop-back — fixed via `mutations` collectors;
   regression tests added.
3. **`ImportUiState.Empty` when all accounts are archived** reuses the
   create-first copy — truthful (no *active* account exists) but could
   read as "no accounts exist".
4. **Server stores `name` verbatim** — no trim; parity with account
   create. The Android client trims before send.
5. **Backend env**: tests need `SPENDWISE_DB_PASSWORD` +
   `SPENDWISE_JWT_SECRET` (source `D:\spendwise\.env.local`); a run
   without them fails en masse at context load — environmental.

## Reproduction

```
Backend:  cd D:\spendwise && set -a && . .env.local && set +a &&
          ./mvnw -q test
Android:  cd D:\app_spendwise\spendwise-android &&
          ./gradlew :app:testDebugUnitTest :app:lintDebug :app:detekt \
                    :app:assembleDebug :app:assembleRelease \
                    :app:connectedDebugAndroidTest
Device:   Pixel 4 XL serial 99041FFBA006F0 (adb -s)
```
