# M19 SENTINEL REVIEW — Account Lifecycle (rename + archive)

```
ROLE: SENTINEL (Test Engineer)
MILESTONE: M19 — REQ-BE-006: PATCH /api/accounts/{id}
ANDROID_RANGE: e58dbbd..working tree (vertical slice + tests)
BACKEND_RANGE: d8554f0..342e85d (bounded prerequisite: PATCH + V13)
REVIEW_MODE: adversarial self-audit per workflow contract; all evidence
             reproduced from live gates, not assumed.
VERDICT: PASS — 0 Blocker, 0 Major, 1 Minor (remediated), 2 Notes
```

## 1. Test evidence (re-executed for this review)

| Gate | Result |
|---|---|
| Backend focused M19 | `AccountUpdateContractTest` **13/13** + `AccountOptimisticLockTest` **1/1** — real MockMvc + Postgres (`spendwise-m10-db`, port 55432, env from `.env.local`) |
| Backend full regression (`mvn test`) | **433/433**, 0 failures, 0 errors, 0 skipped (419 M18 baseline → +14) — surefire XML aggregate. NOTE: an earlier run in a shell lacking `SPENDWISE_DB_PASSWORD`/`SPENDWISE_JWT_SECRET` produced 286 context-load errors — environmental (FATAL: password authentication failed), not product; rerun with `.env.local` green. |
| Android JVM full regression (`testDebugUnitTest`) | **716/716**, 0 failures, 0 skipped (674 M18 baseline → +42) — XML report aggregate |
| Android lint (`lintDebug`) | PASS — **0 errors**, 22 warnings (identical count to the M18 accepted baseline; none in M19 files) |
| Detekt | **0 code smells** (231 files analyzed) |
| Debug / Release builds | PASS; `assembleRelease` green incl. `lintVitalRelease` + `minifyReleaseWithR8` |
| Instrumentation compile | `compileDebugAndroidTestKotlin` green |
| Physical Pixel 4 XL connected suite — run 1 | **213/213 PASS** (200 M18 baseline → +13 screen tests) |

## 2. Requirement → test mapping (backend contract)

`AccountUpdateContractTest` (13 tests, MockMvc + real Postgres) +
`AccountOptimisticLockTest` (1 test, two raw `EntityManager`s — the
proven M16 pattern; a same-persistence-context arrangement cannot
produce a real lost-update):

| Acceptance surface | Covering test |
|---|---|
| Rename | `renameOnly` |
| Archive | `archiveOnly` |
| Unarchive | `unarchiveRestoresActive` |
| Both fields | `renameAndArchiveTogether` |
| Empty body → 400 (not silent no-op) | `emptyPatchIsRejected` |
| Blank/whitespace name → 400 | `blankNameIsRejected` |
| Name > 255 → 400 | `oversizedNameIsRejected` |
| Foreign id → 404 (owner scope) | `foreignAccountIsNotFound` |
| Unknown id → 404 | `unknownAccountIsNotFound` |
| Unauthenticated → 401 | `unauthenticatedUpdateIsRejected` |
| Immutable identity — type/currency/openingBalance/balance unchanged after patch | `immutableIdentityFieldsAreUntouched` |
| `archived` visible on list + detail responses | `archivedIsExposedOnListAndDetail` |
| Ledger preserved — transaction rows + balance bytes identical pre/post archive | `archiveLeavesLedgerAndBalanceUntouched` |
| Lost-update race → 409 | `AccountOptimisticLockTest.concurrentPatchLoserGets409` |

## 3. Requirement → test mapping (Android)

| Acceptance surface | Covering test |
|---|---|
| Wire: sparse PATCH body — absent fields omitted, method, path, bearer | `update sends only the name…`, `update sends only the flag…`, `update carries both…` (`AccountsServiceContractTest`) |
| Wire: 404 foreign, 409 conflict | `update on a foreign id surfaces as 404`, `a lost update race surfaces as 409` |
| `archived` decodes on responses | `list decodes the archived flag` |
| Repository: sparse passthrough, cache write-through, mutations emit, 409/404 mapping | `update sends the sparse body…`, `update replaces the cached row…`, `update emits one mutation signal`, `update 409 maps to Conflict`, `update on a foreign id maps to NotFound` (`AccountRepositoryTest`) |
| VM: prefill from server truth (`refresh=true`) | `prefills name and archived from the loaded account` |
| VM: sparse patch assembly — name-only / archive-only / unarchive / both / none | dedicated cases in `EditAccountViewModelTest` |
| VM: validation — blank blocks save, whitespace ≠ change, trimmed send | `a blank name blocks save…`, `whitespace-only name edit is not a change`, `the sent name is trimmed` |
| VM: submit lock, mid-save snapshot isolation, Saved event | `double submit is locked…`, `a field edited mid-save cannot contaminate…`, `success emits the Saved event` |
| VM: error paths — ambiguous mid-write, dead-generation, 409 conflict, 404 reload, server field error, server 5xx | dedicated cases in `EditAccountViewModelTest` |
| VM: stale load success/failure cannot clobber newer | `a stale load failure cannot clobber…`, `a stale load success cannot clobber…` |
| VM: session-expired load → resume re-runs | `an aborted load after session expiry re-runs on resume` |
| Picker exclusion — transaction, transfer (both ends), import target, recurring template | `archived accounts are excluded…` in `CreateTransactionViewModelTest`, `CreateTransferViewModelTest`, `ImportViewModelTest`, `RecurringViewModelsTest`; `all-archived … produce the create-first state` |
| Transfer-peer gate counts active only | `hasTransferPeer is false when the only peer is archived` |
| Mutation repaint — list + detail reload on account writes | `an account mutation reloads the list`, `an account mutation reloads the detail` |
| Screen: form prefill, switch semantics, save gating, banners, not-found, retry, honest archive copy | `EditAccountScreenTest` (11 on-device tests) |
| Screen: archived detail keeps history, hides entry actions; edit entry point | `archived_showsStatusAndHidesEntryActions`, `editAction_opensTheEditorForThisAccount` (`AccountDetailScreenTest`) |

## 4. Findings

### MINOR-1 — REMEDIATED (during delivery, pre-review): account writes did not repaint list/detail on pop-back

`AccountDetailViewModel` collected only `transactionRepository.mutations`;
`AccountsViewModel` collected nothing. A rename/archive saved from the
editor would pop back to a stale name/badge until the next manual
refresh. **Fix:** both VMs now collect `accountRepository.mutations`
(the same signal `HomeViewModel` already consumed). Regression:
`an account mutation reloads the list` / `an account mutation reloads
the detail`. This also closes the pre-existing staleness of the list
after account create.

### NOTE-1 — Server-side writes to an archived account are not blocked

`POST /api/transactions` on an archived id still succeeds — the flag is
presentation/entry-point semantics, not a ledger freeze (pinned in the
milestone doc as deliberate). The Android app enforces exclusion at
every entry surface (pickers + detail action buttons). A non-app client
could still write against an archived account. Recorded as the
canonical semantics per REQ-BE-006 wording ("hidden from pickers"),
not silently hardened into a write barrier.

### NOTE-2 — Backend env sensitivity (process)

The backend suite reads `SPENDWISE_DB_PASSWORD`/`SPENDWISE_JWT_SECRET`
from the shell environment (`.env.local`). A run without them fails
en masse at context load — looks alarming (286 errors) but is purely
environmental. Documented here so future gate reruns source the file.

## 5. Residual risks (for ARGUS)

- Name `trim()` is client-side only; the server stores verbatim (parity
  with create — convention, not a defect).
- `ImportUiState.Empty` when *all* accounts are archived reuses the
  create-first copy — honest (unarchiving restores eligibility) but
  could read as "no accounts exist".
