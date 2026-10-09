# M19 ARGUS REVIEW — Account Lifecycle (rename + archive)

```
ROLE: ARGUS (Independent QA Reviewer)
MILESTONE: M19 — REQ-BE-006: PATCH /api/accounts/{id}
ANDROID_RANGE: e58dbbd..working tree
BACKEND_RANGE: d8554f0..342e85d
REVIEW_MODE: adversarial review of the actual diff; every claim probed
             against source, not delivery prose.
VERDICT: PASS — 0 Blocker, 0 Major, 0 Minor, 2 Notes
```

## 1. Probes executed

### Ownership / privacy
- `PATCH` resolves via `findByIdAndUserId` — foreign id and missing id
  both return bare `404` (`AccountService.update`,
  `AccountController.update`). Contract tests cover both. **PASS**
- Response adds `archived` — additive field; older clients tolerate it
  (`ignoreUnknownKeys`). No new identity fields exposed. **PASS**
- JWT filter unchanged; unauthenticated PATCH → 401 (tested). **PASS**

### Financial safety
- `type`, `currency`, `openingBalance`, `balance` have **no patch
  surface** — the request record physically cannot carry them; a
  contract test asserts the row's ledger/balance bytes are identical
  after rename+archive. **PASS**
- Archive is not delete: `GET /{id}`, `GET /list`, transactions,
  search, dashboard, export all still see the account. Backend tests +
  the Android surfaces preserve this (filter/account-name lookups
  deliberately keep archived rows). **PASS**
- No client-side balance computation introduced anywhere in the diff.
  **PASS**

### Concurrency
- `accounts.version` (`@Version`, V2) is reused — a PATCH losing to a
  concurrent PATCH *or* a ledger write on the same row throws
  `OptimisticLockingFailureException` → existing handler → retryable
  `409`. `AccountOptimisticLockTest` forces two real persistence
  contexts (the single-`@Transactional` arrangement silently shares the
  managed entity — the first draft of that test proved it false-green).
  **PASS**
- Android: 409 surfaces as `FormError.Conflict` with truthful copy;
  the mutation is never auto-replayed; `isSubmitting` locks the double
  tap; the in-flight snapshot can't be contaminated by later typing.
  All covered. **PASS**

### Session/generation safety
- Repository `update()` captures the generation before dispatch, writes
  the returned entity into the cache only while it lives, and throws
  `SessionEndedException` afterward — the VM surfaces it as Ambiguous
  (the write landed; hiding that would lie). Stale-load serial guard
  tested both directions. **PASS**

### Picker exclusion sweep (the audit-relevant surface)
Grepped every `state.accounts`/`accountRepository.list()` consumer:

| Surface | Treatment | Verdict |
|---|---|---|
| Create transaction picker | filtered (`filterNot archived`) | correct — entry |
| Transfer source+destination | filtered | correct — entry |
| Recurring template account | filtered | correct — generates future entries |
| Import target | filtered | correct — writes ledger rows |
| Account detail Add/Transfer buttons | hidden when archived | correct — bypasses the picker |
| hasTransferPeer gate | counts active only | correct — lone archived peer mustn't show the affordance |
| Transactions account filter | unfiltered | correct — history lookup |
| Export scope picker | unfiltered | correct — history export |
| Recurring/budget name lookups | unfiltered | correct — display |
| Accounts list / dashboard / detail | shown with Archived badge | correct — balances are real money |

### Honest-copy check
- "Hidden when picking an account for new entries. Its history and
  balance stay — nothing is deleted." — accurate, distinguishes archive
  from delete. **PASS**
- Ambiguous-outcome copy ("reopen the account to check") matches the
  no-replay mutation policy. **PASS**
- 409 copy ("updated in another session — reopen") is honest. **PASS**

## 2. Findings

### NOTE-1 — Reload-on-pop via mutation signal (verified fixed)

SENTINEL MINOR-1 (account writes didn't repaint list/detail) was
confirmed real and verified fixed: both VMs collect
`accountRepository.mutations`. `update()` emits it. No echo risk —
`list()`/`findById()` don't emit. Verified by reading the flows.

### NOTE-2 — Archived account remains writable via raw API

Deliberate per REQ-BE-006 ("hidden from pickers" — not a ledger
freeze). All *client* entry points exclude it; the boundary is
documented as a falsification target for external audit. If the Human
wants archive to also reject server-side writes, that is a contract
decision for M20+ — not silently hardened here.

### NOTE-3 — `ImportUiState.Empty` copy when all accounts archived

The import screen says the user needs an account — technically they may
need to *unarchive* one. Bounded cosmetic risk; left as-is (the empty
state is still truthful — no *active* account exists).

## 3. Checks deliberately not flagged

- Empty-patch 400 returns a generic ProblemDetail (no fields map) —
  matches M16 convention; the client never sends one anyway.
- `name` stored verbatim (no server trim) — identical convention to
  account create; the client trims. Parity, not a defect.
- Archived accounts still count toward `hasTransferPeer` *exclusion* —
  handled (active-only count).
