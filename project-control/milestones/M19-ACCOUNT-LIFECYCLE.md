# M19 — Account Lifecycle (rename + archive/unarchive)

```
STATUS: TECHNICALLY COMPLETE — ready for external audit
M18_COMPLETE=YES
M19_DEFINED=YES
M19_STARTED=YES
M19_TECHNICAL_COMPLETE=YES
M19_READY_FOR_EXTERNAL_AUDIT=YES
M19_EXTERNAL_AUDIT=NOT_RUN
M19_HUMAN_APPROVED=NO
M19_HUMAN_AUTHORIZATION=EXPLICIT (M19 delivery block)
CANONICAL_REQUIREMENT=REQ-BE-006 — `PATCH /api/accounts/{id}`
  (rename; optional `archived` flag); archived semantics:
  hidden from pickers, still in ledger/balances.
ACCOUNT_LIFECYCLE=DELIVERED
M20_DEFINED=NO
M20_STARTED=NO
SPENDWISE_STATUS=M19_READY_FOR_EXTERNAL_AUDIT
NEXT_ACTION=ONE_LARGE_INDEPENDENT_EXTERNAL_M19_AUDIT
BASELINE: Android e58dbbd (M18-accepted) · Backend d8554f0 (M18-accepted)
DELIVERY: Backend 342e85d · Android eb88888(governance) +
  08a39ec(impl) + c349edb(remediation+reviews)
COURSE_SYNC_DEFERRED=YES
```

## Delivery evidence (final gates, all on final source)

| Gate | Result |
|---|---|
| Backend full regression | **433/433** (419 → +14: `AccountUpdateContractTest` 13, `AccountOptimisticLockTest` 1) |
| Android JVM regression | **718/718** (674 → +44) |
| lint / detekt | 0 errors (22 warnings, baseline parity) / 0 smells |
| assembleDebug / assembleRelease + R8 + lintVital | PASS; M19 types verified retained in release mapping |
| Physical Pixel 4 XL (99041FFBA006F0) ×2 on final source | **213/213 PASS** twice (200 → +13) |
| Install + launch | streamed-install Success; `MainActivity` topResumed |
| SENTINEL | PASS — 0B/0M/1m(remediated: mutation-repaint gap)/2N |
| ARGUS | PASS — 0B/0M/0m/3N |

## Handoff

`project-control/reviews/M19-EXTERNAL-AUDIT-HANDOFF.md` is the audit
brief — scope, invariants, falsification targets, reproduction commands.

## Selection rationale (decision record)

Candidates re-evaluated against CURRENT canonical sources
(`PRODUCT.md`, `POST-V1-BACKEND-REQUIREMENTS.md`,
`POST-V1-CAPABILITY-MATRIX.md`, `ROADMAP.md`, M9–M18 milestone records —
all completed features removed from candidacy) and the actual backend
surface (`d8554f0`).

Requirement ledger after M18: BE-001 (dates), BE-002 (refresh), BE-003
(transfers), BE-004 (import list), BE-007 (pagination+search), BE-010
(descriptions), BE-011 (budgets), BE-012 (recurring edit) are all
DELIVERED. Remaining: BE-005, BE-006, BE-008, BE-009.

| Candidate | Scores (priority+value+backend+android+test+leverage+physical) − (ambiguity+security+schema+history+scope) | Verdict |
|---|---|---|
| **REQ-BE-006 account edit/archive** | 4+3+4+5+3+2+2=23 − (1+1+2+1+2)=7 → **16** | **SELECTED**: canonical pending requirement whose contract the register itself spells out — `PATCH` rename + `archived` flag, "hidden from pickers, still in ledger/balances". Bounded additive migration, append-only ledger untouched, complete Android journey (typo fix, retire a closed account, restore it). |
| REQ-BE-005 transaction correction | 5+5+2+4+3+3+2=24 − (3+1+3+4+3)=14 → **10** | Deferred: the register explicitly leaves "PATCH vs void" as a *product decision* (`"product decision which"` — POST-V1-BACKEND-REQUIREMENTS §BE-005), and either shape redefines how balances/statistics derive from ledger rows. Human-level product decision, not deliverable autonomously. |
| REQ-BE-009 hardening cluster | 14 − 6 → **8** | Deferred: recurring-currency validation, typed errors, stats policy — a mixed grab-bag, not one coherent user capability; register notes it "can ride any backend release". |
| Profile management (`/me`) | 14 − 13 → **1** | Deferred: no `/me` exists; PRODUCT marks POST_V1 and the identity contract would be invented wholesale (§29 ambiguity cap). |
| FX conversion display | 12 − 10 → **2** | Deferred: provider is a placeholder (`success:false` typical); product verdict was "no real value yet". |
| Categorization-rule lifecycle beyond disable | 16 − 10 → **6** | Deferred: create/list/get/disable already shipped (S21–S23); no canonical REQ exists for rule edit/re-enable — would be invented scope. |
| Remaining recurring lifecycle | — | Deferred: create/list/get/edit(M16)/deactivate complete; pause/skip are not in the canonical register. |
| Category CRUD | — | Rejected: categories are a fixed server enum by Product decision; no requirement exists. |
| REQ-BE-008 multipart size | — | Rejected: trivial config knob, no user journey — violates "not a tiny milestone". |

## User outcome

On an account's detail screen the user can **rename** the account
(fix a typo, reflect a bank rebrand) and **archive** it (the wallet was
closed). An archived account keeps its full ledger — transactions,
transfers, budget math, search results, export — and stays visible in
the Accounts tab and its own history with an honest "Archived" badge,
but it disappears from every *entry* picker (new transaction, transfer
from/to, recurring template, import target) so new money cannot be
recorded against it by accident. **Unarchive** restores eligibility.
The balance and history are never rewritten — archive is an
operational flag, not a ledger event.

## Backend contract (bounded M19 change)

New endpoint; additive schema column:

`PATCH /api/accounts/{id}`

```json
{ "name": "…"?, "archived": true|false? }
```

Semantics (each derived from an existing convention — M16's
`PATCH /api/recurring-transactions/{id}` shape is the template):

- Sparse patch: `name` and `archived` are both optional; **at least one
  must be present** — an empty body is `400`, not a silent no-op.
- `name` when present: `@NotBlank`, `@Size(max = 255)` — identical rule
  to account creation (`CreateAccountRequest`).
- `archived`: boxed `Boolean`; `true` archives, `false` unarchives.
  Both directions legal, idempotent value-wise (archiving an already
  archived account returns it unchanged — a PATCH is a state write,
  not an event).
- Owner scope: `findByIdAndUserId` — foreign or missing id → `404`,
  indistinguishable (R24/R25).
- Optimistic locking: `accounts.version` already exists (V2 convention)
  — a PATCH racing another PATCH **or a concurrent ledger write that
  bumps the same account's version** yields `OptimisticLockingFailure`
  → the existing `ApiExceptionHandler` maps it to a retryable `409`.
  The conflict is truthful: the loser reloads and retries, never a
  silent overwrite.
- Response: `200` full `AccountResponse`, which gains `archived`
  (additive field — older clients ignore it).
- Immutable by omission: `type`, `currency`, `openingBalance`,
  `balance` have no writable patch fields.
- Auth: same JWT filter as every `/api/**`; `401` unauthenticated.
- Errors: `400` empty body / blank name / over-255 name;
  `404` foreign-or-missing; `409` optimistic conflict; `401`.
- Schema: **one additive migration** — `V13__account_archived.sql`:
  `ALTER TABLE accounts ADD COLUMN archived BOOLEAN NOT NULL DEFAULT
  FALSE` (existing rows become active — the only honest default).
  Backward compatible; no data rewrite; no index (flag is read, not
  queried).

### What archive deliberately does NOT do (pinned semantics)

- Does **not** hide the account from `GET /api/accounts` or
  `GET /{id}` — `archived=true` is returned so clients can badge it.
- Does **not** exclude the account's rows from transactions, page,
  search, dashboard math, budgets, or export — history is unchanged.
- Does **not** block server-side writes (`POST /transactions` etc. on
  an archived id still succeed) — the flag is presentation/entry-point
  semantics, not a ledger freeze. Recorded as a Note/falsification
  target for external audit rather than invented as a barrier.

## Android scope

- `AccountDto` + `Account` domain: `archived: Boolean` (default `false`
  keeps any cached/older decode honest).
- `AccountsService.update`: `@PATCH("api/accounts/{id}")` with
  `UpdateAccountRequestDto(name?, archived?)` — sparse body (only the
  changed field serialized; nulls omitted exactly like the M16
  recurring patch).
- `AccountRepository.update(...)`: generation-stamped like `create`;
  cache row replaced with the returned entity; `mutations` emits so
  every account surface reloads. M13 rules: a PATCH is never
  auto-replayed; pre-send expiry refresh allowed once.
- `AccountDetailScreen`: "Edit" affordance → new
  `EditAccountDestination` (`accounts/{id}/edit`). On an archived
  account the Add-transaction/Transfer actions hide — recording into
  an archived account contradicts the flag's meaning.
- `EditAccountScreen/ViewModel`: name field (prefilled, trimmed,
  ≤255, non-blank → inline error) + archive switch with honest inline
  copy explaining ledger preservation; sparse PATCH;
  409 → honest "changed elsewhere — reload" state; 404 → reload;
  submit lock; no auto-retry; ambiguous-outcome copy; stale guards
  mirroring `EditRecurringViewModel`.
- `AccountsScreen`: `Archived` badge on archived rows; row still
  opens detail (history intact).
- Entry pickers exclude `archived` (the canonical semantics):
  `CreateTransactionViewModel`, `CreateRecurringViewModel`,
  `CreateTransferViewModel` (both ends), `ImportViewModel` target.
- History surfaces keep archived accounts: Transactions account
  filter and Export account filter are *lookup* pickers, not entry
  pickers — hiding them would hide history.
- Dashboard account card: gains the same `Archived` badge — it still
  carries a real balance.

## Invariants protected

- Append-only ledger: archive/ rename mutates account *metadata* only;
  no ledger row, balance, or history changes. `openingBalance`/
  `balance` have no patch surface.
- M13: PATCH never auto-replays; pre-send expiry → at most one refresh
  then a single transmission.
- M15/M18: search/page/filter machinery untouched; archived accounts'
  transactions remain fully searchable.
- M16: `@Version` on `Account` predates M19 — concurrent
  patch-vs-patch and patch-vs-balance-write both conflict truthfully;
  no whole-entity overwrite (dirty fields only via managed entity).
- M17: descriptions unrelated to account naming; untouched.
- Cross-user: `findByIdAndUserId` on the write path — a foreign id is
  404, never mutated. Session-generation stamping unchanged.

## Non-goals

- No account delete/hard-delete, no account type/currency/opening
  edits, no reorder, no merge.
- No server-side write blocking on archived accounts (pinned Note).
- No REQ-BE-005 transaction correction, no M20, no course sync.

## Test strategy

Backend (real Postgres, `AccountUpdateContractTest`): rename only,
archive only, unarchive, rename+archive together, empty body → 400,
blank name → 400, name >255 → 400, foreign id → 404, unknown id →
404, unauthenticated → 401, optimistic conflict → 409 (two interleaved
saves), immutable fields rejected by omission (type/currency/balance
unchanged after patch), `archived` visible on list+get, existing rows
read `archived=false` post-migration, ledger untouched (transaction
rows + balance bytes identical pre/post archive).

Android: wire test (sparse PATCH body — absent fields omitted, method,
path; 400/401/404/409 mapping), repository (cache write-through,
generation stamp, mutations emission, A→B), ViewModel (prefill,
validation, submit lock, 409 reload path, stale success/error),
Compose (edit entry, archive confirm, badge, picker exclusion),
physical connected suite ×2.
