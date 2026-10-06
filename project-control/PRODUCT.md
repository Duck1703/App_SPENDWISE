# Spendwise Android Product Blueprint

Produced by **P3 — Spendwise Android Product Blueprint**. Synthesizes the
approved P1 Android baseline, the approved P2 backend contract, and the
product intent — into a product definition only. It decides **what the
product is**, not **how it is built** (P4).

## 1. Status and authority

STATUS: **READY_FOR_HUMAN_REVIEW**

Inputs (approved, canonical):

- P1 → [ANDROID-BASELINE.md](ANDROID-BASELINE.md) — engineering reference,
  KEEP/ADAPT/REJECT classifications (no product template)
- P2 → [BACKEND-CONTRACT.md](BACKEND-CONTRACT.md) — authoritative backend
  contract at `D:\spendwise@8194daa`
- Product intent — the P0-era principles section this blueprint replaces,
  plus governance
- [ROADMAP.md](ROADMAP.md), [DECISIONS.md](DECISIONS.md), [ARCHITECTURE.md](ARCHITECTURE.md) (pending)

All product decisions in §39 are **PROPOSED** and require THE HUMAN's
approval (`HUMAN_APPROVAL_REQUIRED=YES`). Nothing here modifies
`DECISIONS.md` or `ARCHITECTURE.md`.

## 2. Product statement

**Spendwise Android is a mobile personal cash-flow client that lets one
authenticated user see trustworthy account balances, record and review
income and expenses, and import machine-readable bank statements — with the
Spendwise backend as the single authoritative ledger.**

## 3. Primary user

An individual who manages their own money across one or more cash/bank
accounts, wants quick mobile visibility of balances and recent activity,
records income/expenses by hand or imports TPBank-compatible statements,
and expects the numbers to be correct — not merely convenient. (No
demographics invented; behavior/job-based only.)

## 4. Core user jobs

Where backend capability allows (§6):

1. **J-AUTH** — sign up / sign in / resume securely.
2. **J-BALANCE** — "How much do I have, where?" (accounts + balances).
3. **J-RECORD** — log an expense or income in seconds.
4. **J-REVIEW** — scan transaction history and see what happened.
5. **J-IMPORT** — bring a bank statement into the ledger and understand
   what was imported, duplicated, or failed.
6. **J-SUMMARY** — "How did this month go?" (income/expense/category).
7. **J-CORRECT** — recover from a wrong entry honestly.
8. **J-PLAN** (supporting) — recurring items, budgets, categorization
   rules.

## 5. Product principles

1. **Financial truth over visual convenience** — never show a number the
   backend cannot vouch for; never silently mix currencies.
2. **Backend-authoritative ledger** — balances, dedup, categorization,
   recurring generation, and PDF/CSV parsing are server-owned; the app
   displays, it does not recompute.
3. **No fake capabilities** — if no endpoint exists, no UI pretends
   otherwise (no dead edit/delete buttons, no fake transfers).
4. **Explicit limitations over silent data loss** — missing dates, no
   edit/delete, and session expiry are surfaced honestly.
5. **Fast repeat action** — recording an expense is the primary
   one-tap path.
6. **Explicit error & recovery** — every failure has a visible state and
   a supported next action.
7. **Import provenance is visible** — imported vs manual vs recurring
   rows are distinguishable; duplicates ≠ errors.
8. **Minimal V1** — one coherent loop; supporting features tucked away;
   nothing merely because an endpoint exists.
9. **Progressive disclosure** — automation (rules, recurring, budgets)
   lives in a secondary area, not on the main path.
10. **Architecture does not dictate UX** — product surfaces map to user
    jobs, not to controllers.

## 6. Backend reality that shapes the product

(Authoritative source: BACKEND-CONTRACT.md — summarized, not re-audited.)

- **Auth**: JWT Bearer on everything except `register`/`login`; HS256;
  ~15-minute expiry; **no refresh token, no `/me`**; logout blacklists jti.
- **Ownership**: multi-user, owner-scoped, foreign ids → 404.
- **Accounts**: list/create only; server-owned `balance`; `CASH`/`BANK`;
  ISO-4217 currency fixed at creation; **no edit/delete**.
- **Transactions**: create (`INCOME`/`EXPENSE`) + filtered list
  (`accountId`/`type`/`category`); **no detail endpoint, no edit, no
  delete**; **`occurredOn` is neither settable nor returned**;
  **`description` is never persisted**; no pagination/search/date filter.
- **Transfers**: atomic two-legged domain — **no public endpoint**.
- **Import**: synchronous `POST /api/imports` (multipart part `file` +
  `accountId` **query param**); strict CSV schema; **TPBank + one synthetic
  PDF layout
  only**; no OCR; encrypted/image-only/arbitrary → 400; row-level
  `OK`/`DUPLICATE`/`FAILED` report; no batch list; ~1 MB multipart default.
- **Categories**: fixed 5 (`FOOD, TRANSPORT, HOUSING, ENTERTAINMENT,
  SALARY`), read-only; user **rules** (CONTAINS/EQUALS on description)
  run at creation on manual + imported rows.
- **Recurring**: WEEKLY/MONTHLY templates (INCOME/EXPENSE); create/list/
  get/deactivate; server generates occurrences every ~15 min; **currency
  not validated vs account** (defect D14 — mitigated in-app by locking
  currency to the account's).
- **Budgets**: category + month + limit; derived spent/remaining/status.
- **Dashboard**: `GET /api/dashboard?month=YYYY-MM` — totals, breakdown,
  accounts, budgets; **per-aggregation currency check** (same-type mix →
  400; income-vs-expense mix → 200 ambiguous, unlabeled).
- **Errors**: RFC-7807 ProblemDetail, PARTIALLY_TYPED; domain messages
  swallowed; `fields` map on validation 400s; empty-body 404/401.
- **Offline**: no sync/change-feed contract → **NETWORK_REQUIRED**.

## 7. V1 scope definition

**V1 = the smallest trustworthy cash-flow loop**:

authenticate → create an account → record or import activity → see true
balances and monthly position → review history → correct mistakes
honestly → return tomorrow and repeat.

Supporting automation (recurring, budgets, rules) is included because it
has complete, real APIs and strengthens the import/entry loop — without
entering the primary path.

## 8. Capability classification matrix

`V1_CORE` required for the loop · `V1_SUPPORTING` useful but secondary ·
`BACKEND_GATED` wanted but impossible today · `POST_V1` deferred ·
`EXPLICITLY_EXCLUDED` out of direction.

| Capability | Class | Evidence / rationale |
|---|---|---|
| register | V1_CORE | `POST /api/auth/register` |
| login | V1_CORE | `POST /api/auth/login` |
| logout / session expiry | V1_CORE | `POST /api/auth/logout`; 15-min JWT — real UX problem, handled honestly |
| dashboard (month summary) | V1_CORE | `GET /api/dashboard` — currency-safety rule §17 |
| account list | V1_CORE | `GET /api/accounts` |
| account creation | V1_CORE | `POST /api/accounts` |
| account editing | BACKEND_GATED | no endpoint → hidden |
| account deletion/archive | BACKEND_GATED | no endpoint → hidden |
| transaction list | V1_CORE | `GET /api/transactions` — no dates on rows (limitation surfaced) |
| transaction creation | V1_CORE | `POST /api/transactions` |
| income | V1_CORE | `type=INCOME` |
| expense | V1_CORE | `type=EXPENSE` |
| transaction detail | V1_SUPPORTING | rendered from loaded list data; no `GET /{id}` exists — detail view shows only list fields |
| transaction editing | BACKEND_GATED | append-only ledger → hidden; offset-entry remedy §21 |
| transaction deletion | BACKEND_GATED | append-only → hidden; offset-entry remedy |
| transaction filtering | V1_CORE | `accountId`/`type`/`category` — the only real filters |
| transaction search | POST_V1 | no server search; local text search rejected (too few displayed fields) |
| transaction date selection | BACKEND_GATED | `occurredOn` unsettable → read-only "Today" + BE-001 |
| transaction date display | BACKEND_GATED | `occurredOn` not returned → rows carry no date + BE-001 |
| transaction description/history | BACKEND_GATED | never persisted → honest "categorization hint" only + BE-010 |
| categories | V1_CORE | fixed 5 — picker on entry forms; no CRUD |
| category assignment | V1_CORE | `category` on create + rule engine |
| categorization rules | V1_SUPPORTING | create/list/get/disable; runs on manual + imports |
| budgets | V1_SUPPORTING | create/list/get/calculation; no edit/delete |
| recurring transactions | V1_SUPPORTING | full API; currency locked to account (defect mitigation) |
| subscriptions | EXPLICITLY_EXCLUDED | no distinct concept; recurring covers the intent |
| transfers | DELIVERED (M12) | `POST /api/transfers` — atomic own-account pair, shared `transferRef`; create flow + entry points shipped |
| CSV import | V1_CORE | strict schema, synchronous |
| PDF import | V1_CORE | TPBank-family + synthetic layouts only |
| TPBank multi-page | V1_CORE | verified support boundary |
| import result review | V1_CORE | `ImportBatchSummaryResponse` row report |
| duplicate reporting | V1_CORE | per-row `DUPLICATE` + counts |
| import history | M10 | server-backed list via `GET /api/imports` (REQ-BE-004 landed) + per-batch report via `/{id}` |
| export | POST_V1 | endpoint exists; mobile share flow deferred |
| FX conversion | POST_V1 | placeholder provider → usually `available:false`; no real value yet |
| pagination/infinite scroll | BACKEND_GATED | none server-side → honest unbounded list |
| offline usage | BACKEND_GATED | no sync contract → NETWORK_REQUIRED |
| push notifications | EXPLICITLY_EXCLUDED | no backend notification contract |
| analytics | EXPLICITLY_EXCLUDED | no contract + privacy stance |
| profile/identity mgmt | POST_V1 | no `/me`; `AuthResponse` carries only token+expiry — the typed username is the sole identity hint |
| local balance computation | EXPLICITLY_EXCLUDED | backend is authoritative |
| arbitrary bank PDFs / OCR | EXPLICITLY_EXCLUDED | unsupported server-side; promised nowhere |

Counts: **V1_CORE 18 · V1_SUPPORTING 4 · BACKEND_GATED 10 · POST_V1 5 ·
EXPLICITLY_EXCLUDED 5** (42 classified rows).

## 9. Backend gap decision board

Treatments: ACCEPT_V1_LIMITATION / HIDE_UNSUPPORTED_CAPABILITY /
DEFER_FEATURE / REQUIRES_BACKEND_ENABLEMENT / PRODUCT_BLOCKER.

| Gap | Treatment | Rationale |
|---|---|---|
| No public transfer endpoint | RESOLVED (M12, BE-003) | `POST /api/transfers` shipped; first-class transfer flow live — paired-manual-transaction fakery never used |
| No transaction edit | HIDE_UNSUPPORTED_CAPABILITY | no edit affordances anywhere |
| No transaction delete | HIDE_UNSUPPORTED_CAPABILITY + offset-entry remedy (§21) | no delete affordance; honest reversal supported by append-only semantics |
| No account edit/delete | HIDE_UNSUPPORTED_CAPABILITY | account cards carry no mutation controls |
| No budget edit/delete | HIDE_UNSUPPORTED_CAPABILITY | a wrong **current-month** budget is permanently unfixable (unique category+month blocks recreation); limitation surfaced on budget detail — BE-011 |
| `occurredOn` unsettable | ACCEPT_V1_LIMITATION + REQUIRES_BACKEND_ENABLEMENT (BE-001) | read-only "Today" on the form; no date picker |
| `occurredOn` not returned | ACCEPT_V1_LIMITATION + BE-001 | rows display no date; grouped-by-date rejected; highest-value enablement |
| `description` not persisted | ACCEPT_V1_LIMITATION | honest "categorization hint" field; not presented as ledger data |
| No pagination | ACCEPT_V1_LIMITATION | unbounded fetch; V1 ledgers are small; local grouping/filtering of fully-loaded data is honest (no meaningful sort field exists — no dates are returned) |
| No search | DEFER_FEATURE | no server search; local text search not useful on dateless rows |
| No date-range filter | DEFER_FEATURE | needs `occurredOn` returned + filter params (BE-001/BE-007) |
| JWT ~15-min, no refresh | RESOLVED (M13, BE-002) | rotating refresh token + single-flight renewal; re-auth interstitial remains for genuine session death |
| Partially typed errors | ACCEPT_V1_LIMITATION | map status + `fields`; never parse `detail` text (§31) |
| Synchronous import | ACCEPT_V1_LIMITATION | blocking progress surface; no progress %, no background claim |
| No import batch list | ACCEPT_V1_LIMITATION | result screen shown once; history = ledger rows with import provenance; BE-004 later |
| Dashboard mixed-currency | ACCEPT_V1_LIMITATION | §17 safety rule: labeled totals only on uniform-currency portfolios |
| Recurring currency defect | ACCEPT_V1_LIMITATION | form locks currency = account currency (defect never reachable); defect logged (BE-009) |
| FX limitations | DEFER_FEATURE | placeholder provider; POST_V1 |

**PRODUCT_BLOCKER count: 0** — no gap prevents a core journey or makes an
honest promise false. `REQUIRES_BACKEND_ENABLEMENT` rows above name the
**priority subset** (BE-001 dates, BE-002 sessions, BE-003 transfers);
§41 registers all enablement dependencies (BE-001–BE-011).

## 10. V1 backend readiness verdict

**YES_WITH_ACCEPTED_LIMITATIONS** — the V1 defined here is fully
deliverable and honest against today's contract. The highest-value
backend enablements (BE-001 dates, BE-002 session length, BE-003
transfers) are queued for the post-V1 roadmap and raised as Human
questions — none blocks the defined V1 loop.

**Compound-effect caveat**: dateless + server-order-unspecified rows,
~15-min forced re-auth, append-only correction, a one-shot import
report, and unbounded fetches **collectively** define the minimum honesty
bar of this V1. Each is surfaced openly rather than hidden; approving
Q1/Q2 (§43) is the effective go/no-go on that bar. Mitigating evidence:
imported CSV rows **do** carry real `occurredOn` values server-side and
drive month math — monthly totals are date-accurate even though rows
cannot display dates.

## 11. Information architecture

Authenticated root: **4 top-level destinations** — each maps to a
high-frequency job; secondary surfaces nest under More.

- **Home** — money position + month summary + fast actions.
- **Transactions** — the ledger: list, filters, detail, correction.
- **Accounts** — where money sits: list, balances, create.
- **More** — everything secondary: Import, Recurring, Budgets, Rules,
  Sign out.

Primary action **"Add transaction"** is a persistent action on Home and
Transactions. **Import** lives under More *and* as a secondary action on
Home (high-value but lower-frequency). Create Account is contextual
(Accounts tab + empty states).

## 12. Primary navigation

TOP_LEVEL_DESTINATIONS = **4**

| Destination | Purpose | Primary user job | Scope | Backend deps |
|---|---|---|---|---|
| Home | position at a glance | J-BALANCE, J-SUMMARY | V1_CORE | `/api/accounts`, `/api/dashboard` |
| Transactions | ledger review + entry | J-REVIEW, J-RECORD | V1_CORE | `/api/transactions` |
| Accounts | money containers | J-BALANCE | V1_CORE | `/api/accounts` |
| More | secondary surfaces | J-IMPORT, J-PLAN | V1_CORE (hosts supporting surfaces — Import and sign-out are core; recurring/budgets/rules are supporting) | imports, recurring, budgets, rules, logout |

## 13. Navigation graph

```
Unauthenticated
├── Welcome ─────────────→ Login | Register
├── Login ───────────────→ Authenticated root
└── Register ────────────→ Login (prefilled) → Authenticated root

Session-expired (interstitial over current state) → Login → resume

Authenticated (4 destinations + primary action)
├── Home
│   ├── quick action: Add transaction → Transaction Create
│   ├── secondary action: Import statement → Import flow
│   └── account row → Account detail
├── Transactions
│   ├── Filters (account / type / category)
│   ├── Transaction row → Transaction detail
│   │   └── "Offset entry" → Transaction Create (prefilled inverse)
│   └── Add transaction → Transaction Create
├── Accounts
│   ├── Create account
│   └── Account row → Account detail (filtered Transactions + balance)
└── More
    ├── Import statement → Import flow → Import result
    ├── Recurring → list → create → detail (deactivate)
    ├── Budgets → list → create → detail
    ├── Categorization rules → list → create → detail (disable)
    └── Sign out → confirm → Unauthenticated

(POST_V1 candidates such as Export are absent from the V1 graph entirely —
no placeholder or disabled teaser, per the GATED rule in §31.)
```

## 14. Screen inventory

| SCREEN_ID | Name | Purpose | Scope | Entry | Primary actions | Backend deps | States | Empty state | Error state | Gaps |
|---|---|---|---|---|---|---|---|---|---|---|
| S01 | Launch | session probe → route | V1_CORE | app start | none | none (local) | loading | — | none | — |
| S02 | Login | sign in | V1_CORE | unauth, expiry | submit, go-register | `POST /api/auth/login` | form/loading/401 | — | invalid creds → inline | 401 shape |
| S03 | Register | create account identity | V1_CORE | login link | submit | `POST /api/auth/register` | form/loading | — | 400/fields | duplicate→400 |
| S04 | Session expired | re-auth interstitial | V1_CORE | any 401 | re-login, sign-out | login | overlay | — | re-login fail | — |
| S05 | Home | balances + month + actions | V1_CORE | root tab | add-txn, import, open account | accounts, dashboard | loading/refresh/content/network | "Create your first account" | retry; mixed-currency notice | BG-013 |
| S06 | Accounts | list + create entry | V1_CORE | tab | create, open | `/api/accounts` | loading/content/refresh | "No accounts — create one" | retry | — |
| S07 | Account create | new account form | V1_CORE | Accounts, empty states | save | `POST /api/accounts` | form/submitting | — | 400/fields | — |
| S08 | Account detail | one account + its history | V1_CORE | Home/Accounts row | add-txn, import | `GET /api/transactions?accountId` + list data | loading/content | "No activity yet" | retry | BG-003 dates |
| S09 | Transactions | full ledger list | V1_CORE | tab, account detail | filter, open row, add | `GET /api/transactions` | loading/refresh/content | "No transactions — record or import" | retry | BG-003/005 |
| S10 | Transaction detail | row detail + offset | V1_SUPPORTING | list row | offset-entry | none (list data) | content | — | — | no `GET /{id}` |
| S11 | Transaction create | record expense/income | V1_CORE | FAB, account detail | type, account, amount, category, hint, save | `POST /api/transactions`, categories, accounts | form/submitting | — | 400/fields, 404 acct | BG-003/004 |
| S12 | Import — choose | account + file + format guidance | V1_CORE | More, Home, account | pick file, upload | `POST /api/imports` | form/uploading | — | 404 acct, 400 file-type | BG-009 size |
| S13 | Import — processing | blocking progress | V1_CORE | from S12 | wait only — no server-side cancel; interruption handled per §23 | synchronous call | processing | — | network/400/500 | synchronous |
| S14 | Import — result | outcome report | V1_CORE | from S13 | view rows, done | `ImportBatchSummaryResponse` | content/partial | all-zero rows | per-row reasons | BG-007/016 |
| S15 | Recurring list | manage templates | V1_SUPPORTING | More | create, open, deactivate | `GET /api/recurring-transactions` | loading/content | "No recurring items" | retry | D14 note |
| S16 | Recurring create | new template | V1_SUPPORTING | Recurring | save | `POST /api/recurring-transactions` | form/submitting | — | 400/fields | currency locked |
| S17 | Recurring detail | view + deactivate | V1_SUPPORTING | list row | deactivate (confirm) | `DELETE /api/recurring-transactions/{id}` | content | — | 404 | soft-delete |
| S18 | Budgets list | monthly budgets | V1_SUPPORTING | More | create, open | `GET /api/budgets` | loading/content | "No budgets — set one" | retry | no edit/delete |
| S19 | Budget create | new budget | V1_SUPPORTING | Budgets | save | `POST /api/budgets` | form/submitting | — | 400/409 duplicate | — |
| S20 | Budget detail | spend vs limit | V1_SUPPORTING | list row | — | `GET /api/budgets/{id}/calculation` | loading/content | — | 404/retry | — |
| S21 | Rules list | auto-categorization rules | V1_SUPPORTING | More | create, open | `GET /api/categorization-rules` | loading/content | "No rules — imports/manual entries may stay uncategorized" | retry | disabled rows shown as inactive (`enabled=false` returned) |
| S22 | Rule create | new rule | V1_SUPPORTING | Rules | save | `POST /api/categorization-rules` | form/submitting | — | 400/409 priority | `enabled` ignored |
| S23 | Rule detail | view + disable | V1_SUPPORTING | list row | disable (confirm) | `DELETE /api/categorization-rules/{id}` | content | — | 404 | soft-delete |
| S24 | More | secondary hub | V1_CORE | tab | navigate, sign-out | logout | content | — | — | — |
| S25 | Welcome | unauthenticated landing | V1_CORE | first launch | go-login, go-register | none | content | — | — | — |

TOTAL_SCREEN_COUNT=25 · V1_SCREEN_COUNT=25 (3 V1_SUPPORTING families —
recurring/budgets/rules — nest under More; transaction detail nests under
Transactions) · BACKEND_GATED_SCREEN_COUNT=0 — no gated screen is built;
gated capabilities are simply absent.

## 15. Screen → backend traceability

| Screen | User job | Endpoint/capability | Contract limitation | V1 status |
|---|---|---|---|---|
| S02 Login | auth | `POST /api/auth/login` | 15-min token | ships |
| S03 Register | auth | `POST /api/auth/register` | duplicate→400 | ships |
| S05 Home | balances+summary | `/api/accounts`, `/api/dashboard` | mixed-currency ambiguity | ships w/ §17 rule |
| S06–S08 Accounts | balances | `/api/accounts`, `?accountId=` | no edit/delete, no dates | ships |
| S09–S11 Txns | review+record | `/api/transactions` | append-only, no date, no pagination | ships w/ limits |
| S10 detail | inspect | list data only | no `GET /{id}` | ships |
| S12–S14 Import | import | `POST /api/imports`, `GET /api/imports/{id}` | sync, ~1MB, no batch list | ships |
| S15–S17 Recurring | plan | `/api/recurring-transactions` | soft-delete only, currency unlocked defect | ships w/ lock |
| S18–S20 Budgets | plan | `/api/budgets` + `/calculation` | no edit/delete | ships |
| S21–S23 Rules | automation | `/api/categorization-rules` | `enabled` ignored on create | ships |
| Transfer screens | — | **none** | **no endpoint** | **not built — BACKEND_GATED** |
| Search/date-filter UI | — | **none** | no contract | **not built** |
| Edit/delete affordances | — | **none** | append-only | **not built** |

## 16. Authentication/session experience

- **First launch** → unauthenticated root: Welcome → Login / Register.
- **Register** (username+password, min 8 chars — mirrors validation) →
  201 → forward into Login prefilled (product choice: register does not
  auto-authenticate — backend returns only id+username, no token).
  Register-duplicate → 400 **without** a `fields` map (indistinguishable
  from other domain 400s) → hedged copy: "the username may already be
  taken — check the details."
- **Login** → `AuthResponse{accessToken, expiresAt}` → authenticated root.
- **Token expiry during use** (M13+): a refreshable session renews
  silently (single-flight rotating refresh) — the interstitial appears
  only when renewal itself is rejected (refresh expiry/revocation/
  reuse) or was never available. A 401 on an authenticated call with a
  dead session still → **Session-expired interstitial**
  (modal, non-destructive): "Your session has expired — sign in again."
  Current screen + **unsent form input is preserved** (in-memory); after
  successful re-login the user returns to the same surface and may retry.
  Non-idempotent mutations are never auto-replayed after renewal — the
  draft stays and the user resubmits.
- **Logout** → confirm → `POST /api/auth/logout` (revokes the access
  `jti` + the presented refresh token, M13) → discard local session →
  unauthenticated root. The local session is discarded regardless of
  the remote result.
- **Relaunch with expired session** → if a live refresh token exists
  (M13+), one silent renewal attempt runs before the session resolves;
  legacy/no-refresh sessions go to unauthenticated root as before.
- **Copy**: never show the raw token; never say "you were logged out for
  security" — just the factual expiry.

## 17. Home/dashboard experience

Home answers: **"How much do I have, and how did this month go?"**

Composition (all real contract):

- **Account balances** — per-account `balance` + `currency` label
  (always currency-labeled; never summed client-side).
- **This month** — `GET /api/dashboard?month=<current YYYY-MM>`:
  totalIncome, totalExpense, category breakdown, budget status cards.
- **Quick actions** — Add transaction; Import statement.
- **Alerts** (secondary) — a *month-scoped* uncategorized count cannot be
  computed (no month filter on the list; `occurredOn` not returned) →
  omitted, not faked. Category breakdown shown with a footnote-level
  explanation that it excludes uncategorized spending — otherwise its sum
  silently undershoots `totalExpense` and reads as a bug.

**Mixed-currency safety rule (hard product rule)**:

- All accounts share one currency C → totals displayed **with the C
  label** (all ledger rows are provably C — transaction currency must
  match its account).
- Accounts have mixed currencies → the totals block shows an honest
  state: **"Combined totals unavailable — your accounts use different
  currencies"**; per-account balances still shown (safe).
- Dashboard 400 (same-type mixed currencies) → same honest state.

Other states: first-run (no accounts) → "Create your first account";
no activity this month → totals show 0.00 with label; loading →
skeleton; network fail → retry; 401 → §16.

## 18. Accounts experience

- **List**: name, type, `balance` + `currency`; tap → Account detail.
- **Create**: name, type (CASH/BANK), opening balance (≥0), currency
  (ISO picker — open set, validated server-side).
- **Detail**: header (name/type/balance/currency/opening) + that
  account's transactions (`?accountId=`).
- **No edit / delete / archive controls exist** — the backend offers
  none; account cards carry zero mutation affordances.
- **Empty account** → "No activity yet — record or import."
- Balance copy: "Balances are maintained by the server" is implicit —
  never invite client-side reconciliation.

## 19. Transactions experience

- **Rows**: direction (Income/Expense/Transfer-style labels where they
  appear), amount **with currency**, category (or "Uncategorized"),
  account name, and a provenance chip derived from `sourceRef`
  (`Manual` / `Imported` / `Recurring` / `Transfer`).
- **No date on rows** — the API does not return `occurredOn`; rows are
  dateless and grouped by account or flat list — **not** by day.
- **Ordering**: unspecified server order; V1 displays list order as
  returned and does not promise chronology.
- **Filters**: the three real params — account, type, category; plus a
  client-side toggle for provenance (honest: all data is loaded, local
  filtering of fully-loaded data is not fake search). Type filter options
  are **All / Income / Expense only** — `TRANSFER_*` values exist on the
  wire but can never occur in a public-API user's ledger, so offering them
  would be an always-empty choice.
- **No search box, no date-range, no sort controls** — unsupported.
- **Empty** → "Record an expense or import a statement."
- **Detail** (from row data): all list fields + `sourceRef`-based
  provenance + **"Create offsetting entry"** (§21).
- **Refresh**: pull-to-refresh reloads the full list (unbounded fetch is
  a known limitation, messaging kept honest at scale).

## 20. Transaction creation experience

Fields (all real): account (required picker), type (Expense default /
Income toggle), amount (>0, 2-dp, required), currency (**locked to the
selected account's currency** — backend enforces match), category
(optional picker of the 5 codes), and **"Categorization hint"
(optional)** — labeled honestly: *"helps auto-categorize — not saved in
history."*

- **Date**: **read-only "Today"** — no picker (server stamps the date;
  BE-001 queued). "Today" is the **server's local day** — near-midnight
  entries in another timezone may stamp an adjacent day; invisible in V1
  since dates never render, recorded for BE-001's future date display.
- **Validation**: client mirrors `@DecimalMin(>0)/@Digits(17,2)` and
  amount/account presence; server `fields` map surfaces per-field errors.
- **Submission**: disable form → 201 → return to origin list +
  success confirmation; **no client-side retry dedup exists — a double
  tap is guarded by disabling submit** (no idempotency contract; honest
  note §34).
- **Errors**: unknown account → 404 empty body → "Account no longer
  available — reload"; generic 400 → field-agnostic "Couldn't save —
  check the details" + `fields` where present.

## 21. Financial correction policy

Backend is append-only: no edit, no delete, no reversal API.

**Decision: V1 ships an explicit "Offset entry" correction** (option B —
justified by verified semantics):

- On a `Transaction detail` for `INCOME`/`EXPENSE` rows only: "Create
  offsetting entry" → opens Transaction create prefilled with the
  **inverse type**, same account, same amount, same currency, and
  (if set) the same category.
- Creates a real `POST /api/transactions` — balance effect is the exact
  arithmetic inverse (`applyIncome`/`applyExpense` on equal `Money` —
  verified safe in contract §12/§30).
- **Honest copy**: "This adds an opposite entry. The original stays in
  history — income and expense totals for the period will both be higher
  than the true position."
- `TRANSFER_*` legs cannot be offset — a single-side offset would
  un-pair the atomic transfer; the action stays hidden on them (M12
  ships no transfer update/delete either).
- **Not labeled "undo"/"delete"/"edit"** — it is a compensating entry,
  presented as such.
- Also recorded: true edit/delete remains BACKEND_GATED (BE-005) for
  post-V1.

## 22. Transfer product decision

TRANSFER_PUBLIC_API_AVAILABLE = **YES (M12)** — V1 status was
**BACKEND_GATED + DEFER_FEATURE**; resolved by REQ-BE-003.

V1 shipped no transfer UI — no entry point, no form, no paired
income/expense simulation (that would mint `INCOME`/`EXPENSE` rows, not
linked `TRANSFER_*` legs, and misrepresent the ledger). M12 delivered
the first-class flow the domain model already proved (atomic
two-legged, shared `transferRef`, same-currency pairing): `POST
/api/transfers` on the backend, and on Android a dedicated create
screen reachable from the Transactions header (≥2 accounts only) and
account detail (≥2 accounts, source prefilled). No transfer update or
delete exists — the legs stay append-only like the rest of the ledger.

## 23. Import experience

Flow (synchronous contract, honest UX):

1. **Choose account** (required) → 2. **Choose file** (system picker;
   `.csv`/`.pdf` only) → 3. **Confirm & upload** → 4. **Processing
   screen** (blocking: statement is being imported; no progress %, no
   background claim; user cannot navigate away — leaving would show the
   import as unknown, so the screen is modal) → 5. **Result screen**
   (§25).

- **Messaging**: what formats work (§24), file-size guidance (very large
  statements may be rejected — ~1 MB server cap), what happens to
  duplicates.
- **Interruption** (network loss mid-upload): "Import didn't finish —
  check connection and try again. **Re-uploading the same file is safe:
  rows already imported are skipped as duplicates.**" — true per dedup
  contract.
- **Failure**: 400 file-type/parse → "This file isn't supported" + the
  supported-formats guidance; 500 (structural CSV/size) → "Import
  failed — try again or check the file"; 404 account → "Account no
  longer available."
- **After success**: "Done" returns to origin; ledger/balances refresh
  via the standard list reload.

## 24. CSV/PDF support promise

User-facing copy (honest, not overly technical):

> "Import a **TPBank account statement (PDF)** or a **CSV file in the
> Spendwise format** — exact header `occurredOn,amount,currency,description,sourceTxnId`
> (no spaces, no BOM). The PDF must be the original machine-readable
> statement — **scans, photos, and password-protected files aren't
> supported**, and other banks' PDFs aren't supported yet."

- `.csv` — Spendwise CSV schema only. Format guidance shown in-product:
  header **exact** (case- and space-sensitive; a UTF-8 BOM also breaks
  it → 500); `occurredOn` ISO `yyyy-MM-dd`; **signed** amounts (negative
  = expense); `currency` must equal the chosen account's currency or the
  row fails; `sourceTxnId` optional (server generates one if blank);
  `description` optional, may be empty, max 40 chars, drives
  auto-categorization.
- `.pdf` — TPBank machine-readable statements (multi-page OK); **no
  OCR, no arbitrary-bank promise** anywhere in copy.
- Any `.pdf` rejection is the **same indistinguishable 400** (encrypted,
  image-only, wrong-layout, corrupt — the backend cannot tell the app
  which) → always shown as "This file isn't supported" **plus** the
  static supported-formats guidance above. The app never asserts a
  specific cause.

## 25. Import result experience

Hierarchy: **counts first, rows on demand**:

- `N imported · M duplicates · K failed` (+ total rows implied by list).
- Grouped lists: **Duplicates** (gray, "already imported — skipped"), **Failures**
  (with `reason` text where present), **Successes** (collapsed).
- **Duplicate ≠ error** — re-importing a file yields "everything already
  imported — nothing new," explicitly **not** a failure state.
- All-failed → "Nothing could be imported — check the file format."
- Row numbers reference the file's numbering (CSV counts file lines).
- No batch history: the result is the report; "imported" rows then
  appear in Transactions with the `Imported` chip.

## 26. Categories and categorization rules

- **Categories**: fixed set of 5 — appears as a **picker** on entry
  forms and as row labels; no category management surface (backend has
  none); "Uncategorized" is a first-class honest state.
- **Categorization rules** (V1_SUPPORTING under More): list shows
  condition (`contains`/`equals` text) → target category, priority;
  create form (condition type, value, category, priority); detail →
  **Disable** (confirm; soft-delete, recoverable only by recreate).
- **Explanation copy**: "Rules auto-assign a category to new manual
  entries and imported rows when you don't pick one. Existing entries
  never change." (Accurate: rules fire only inside
  `TransactionCreationService` when category is null.)
- `enabled` field on create is **not offered** (backend ignores it) —
  rules are always active at creation; disabling happens via detail.

## 27. Recurring product decision

**V1_SUPPORTING** — real value, complete API, acceptable surface.

- **List**: direction, amount+currency, category, frequency
  (Weekly/Monthly), next due, active/inactive.
- **Create**: account, direction, amount, currency (**locked to
  account's** — avoids the un-validated mismatch defect D14), category
  (optional), frequency, next due date, optional end date.
- **Detail**: deactivate (confirm) — explained as "stops future
  entries; history stays."
- **Honesty notes**: generation runs server-side up to ~15 min after a
  due date — "new entries appear automatically; there can be a short
  delay"; missed occurrences before an `endDate` are dropped (backend
  semantics — documented, not surfaced as UI copy).
- No "run now" control (no endpoint — honest omission).

## 28. Budget product decision

**V1_SUPPORTING** — simple, real, complementary to the month dashboard.

- **List**: category + month + limit + progress (`spent`, `remaining`,
  `%`, `WITHIN_BUDGET`/`OVER_BUDGET`) — from list + per-item
  `/calculation`.
- **Create**: category, month (`YYYY-MM`), limit, currency (locked to
  category-month convention — see note), no account scope.
- **Detail**: calculation readout + explicit note that a wrong
  **current-month** budget cannot be changed or removed (a new budget for
  the same category+month is rejected as a duplicate — it can only be
  superseded next month).
- **Honesty notes**: mismatched-currency expense rows are silently
  excluded from `spent` (backend semantics) — budget currency should
  match the account currency users actually spend in; the create form
  defaults currency to the portfolio currency when uniform. No
  edit/delete exists — budgets are create-and-view; a wrong budget can
  only be superseded next month (limitation surfaced in empty/help
  copy, not hidden).

## 29. FX/export product decision

- **FX** — **POST_V1**: the endpoint is display-only with a placeholder
  provider (`available:false` is the expected live state). Including it
  in V1 would surface a permanently-degraded feature. Revisit when a
  real provider is configured (backend enablement).
- **Export** — **POST_V1**: `GET /api/transactions/export` exists and
  could feed a share-sheet flow, but it is not part of the core loop;
  deferred.

## 30. Offline/network policy

**NETWORK_REQUIRED** — no sync/change-feed/idempotency-reconcile
contract exists; V1 does not promise offline usage.

- All screens require connectivity; network failure → explicit
  "No connection — retry" state (never a stale-data illusion).
- In-memory preservation of the current screen and unsent form input
  during a session expiry or retry is allowed (it's not offline
  editing — nothing is written as if synced).
- No offline ledger editing, no queued writes, no "will sync later"
  promises.

## 31. Loading/empty/error/session states

Canonical product states (P4 will map to technical states):

| State | Product behavior |
|---|---|
| INITIAL_LOADING | skeleton/spinner + label; never a frozen blank |
| REFRESHING | content stays + refresh indicator; no flicker to empty |
| EMPTY | honest message + one supported next action |
| CONTENT | data as returned; server order; currency labels always |
| NETWORK_ERROR | "No connection" + Retry; content preserved if shown |
| VALIDATION_ERROR | inline per-field from `fields` map; else single generic field note |
| SESSION_EXPIRED | interstitial → re-login → resume (§16) |
| SERVER_ERROR | "Something went wrong — try again"; never parse `detail` |
| CONFLICT_409 | rare concurrent-write clash (optimistic lock / unique constraint): "The data changed — reload and retry" |
| MIXED_CURRENCY | §17 honest totals state |
| IMPORT_PROCESSING | blocking, no % claim, idempotent-retry messaging |
| GATED | capability simply absent — never a disabled teaser |

## 32. Primary user journeys

| J | Trigger → outcome | Steps | Backend deps | Recovery | Limitations |
|---|---|---|---|---|---|
| J1 first launch/register | open app, no account | Welcome → Register → Login → Home empty | register, login | 400 fields → inline | no auto-login |
| J2 returning login | open app, expired | Login → Home | login | 401 → re-enter | — |
| J3 session expiry mid-use | API 401 | interstitial → re-login → resume screen | login | retry; form preserved | 15-min token |
| J4 first account | Home empty | create → appears → balance shown | `POST /api/accounts` | 400 fields | no edit/delete |
| J5 record expense | FAB | form → 201 → row appears | `POST /api/transactions` | 400 fields; 404 acct → reload | date=Today; no desc |
| J6 record income | FAB | same, type=Income | same | same | same |
| J7 inspect transactions | tab | list → filters → row detail | `GET /api/transactions` | retry | no dates/search/pages |
| J8 import CSV | More/Home | account → file → processing → result | `POST /api/imports` | safe re-upload; 400/500 copy | ~1MB; schema strict |
| J9 import TPBank PDF | same | same | same | same | TPBank only; no OCR |
| J10 re-import (duplicates) | same | result shows duplicates | dedup | "nothing new" state | — |
| J11 month review | Home | totals + breakdown + budgets | dashboard | mixed-currency state | ambiguous-mix hidden |
| J12 manage recurring | More | list → create → deactivate | recurring API | 400/404 | currency locked; ~15min lag |
| J13 budget check | More | create → detail calc | budgets API | 409 duplicate | no edit/delete |
| J14 correction | txn detail | offset entry → inverse row | `POST /api/transactions` | — | original stays visible |

USER_JOURNEY_COUNT = 14

## 33. Product terminology

Canonical user-facing terms (plain language; no internal class names):

| Concept | User-facing term |
|---|---|
| account | "Account" (Cash/Bank) |
| balance | "Balance" (always with currency) |
| income/expense | "Income" / "Expense" |
| transaction | "Transaction" / "entry" (copy may use "entry") |
| category | "Category" |
| categorization rule | "Auto-categorization rule" or "Rule" |
| import | "Import statement" |
| import batch | "Import" (singular event) |
| duplicate | "Already imported — skipped" |
| failed row | "Couldn't import" |
| recurring | "Recurring" (Weekly/Monthly) |
| budget | "Budget" (monthly, per category) |
| offset entry | "Offsetting entry" (never "undo"/"delete") |
| session expiry | "Session expired — sign in again" |
| provenance chip | "Manual" / "Imported" / "Recurring" / "Transfer" |

Never exposed: `sourceRef`, `TransactionCreationService`, DTOs,
ProblemDetail, jti, uq_txn_source_ref, endpoints.

## 34. Financial display rules

- Amount and currency **always together**; ISO code labels (VND, USD).
- Direction is unambiguous: "−" / "+" affordances **plus** text direction
  — never color alone (accessibility + the unsigned-amount model).
- Balances come only from `AccountResponse.balance` — the app never
  sums transactions into a balance.
- **Never combine currencies** — no cross-account totals; dashboard
  rule §17 applies.
- Duplicates never change balances and are never styled as errors.
- `TRANSFER_*` rows (own-account transfers, M12) display as "Transfer"
  provenance with neutral direction — not as income/expense.
- Percent display for budgets is server-computed scale-2 — displayed
  as returned.
- "No idempotency key" honesty: submit buttons lock on tap; the app
  never silently re-sends a create on ambiguity — it confirms with the
  user first.

## 35. Privacy expectations

- Never display the access token or any credential.
- Credentials never logged or shown in errors.
- Only the authenticated user's data is visible (server enforces; UI
  shows no user switcher or shared spaces).
- Imported bank statements are financially sensitive: no raw statement
  content is shown beyond row numbers/reasons; the file is not echoed
  back; filenames shown only in the user's own import result.
- Import result shows `reason` text as-is — the app does not enrich it
  with guessed explanations.

## 36. Accessibility/usability expectations

- Financial meaning never by color alone (direction chips carry text).
- All controls labeled; error text readable and specific where the
  contract allows (`fields` map).
- Loading never looks frozen (labeled progress, esp. synchronous import).
- Destructive/irreversible actions (logout, deactivate, disable) are
  visually distinct + confirmed.
- Large-text/reflow support is a future implementation requirement —
  noted for P4/UI phases.

## 37. Product performance expectations

- Core screens should feel responsive; lists are unbounded — if lists
  grow heavy, honesty copy ("loading your full history") rather than
  fake incremental loading.
- Import can take several seconds (synchronous parse of a statement) —
  the blocking screen sets that expectation up front.
- Dashboard is computed per request — show its own loading state.
- No millisecond SLAs invented; P4 may add technical budgets.

## 38. Product non-goals

| Non-goal | Rationale |
|---|---|
| Arbitrary bank PDFs / OCR | backend unsupported — would be a false promise |
| Transfers | no endpoint — gated, not excluded permanently |
| Transaction/account edit & delete | append-only ledger — honest omission |
| Offline-first / offline ledger | no sync contract |
| Local balance computation | backend is authoritative |
| Server search / date filters / pagination UI | no contract |
| Investment tracking, business accounting, family/shared accounts, AI advisor, receipt OCR | outside product intent |
| Push notifications | no backend notification contract |
| Analytics/tracking SDK promises | no contract + privacy stance |
| User-defined categories | backend fixed-5 (gated, not excluded) |

## 39. Proposed product decisions

`HUMAN_APPROVAL_REQUIRED=YES` on all.

| ID | Decision | Rationale | P1 input | P2 input | Alternative rejected | Impact |
|---|---|---|---|---|---|---|
| PD-001 | V1 = auth + accounts + income/expense entry + transaction review + TPBank/CSV import + month dashboard; secondary: recurring, budgets, rules | smallest coherent loop, all real APIs | KEEP/ADAPT framing | §33 capability matrix | feature-max V1 | scope controlled |
| PD-002 | 4 top-level destinations: Home / Transactions / Accounts / More | job-frequency mapping | "typed navigation concepts" kept conceptually | endpoint surface | mirror-controller IA | focused IA |
| PD-003 | Primary action = "Add transaction" (persistent on Home+Transactions); Import as secondary (More + Home) | entry is the highest-frequency action | "explicit state ownership / route wiring" precedent (concept only) | create endpoint | import-primary IA | fast repeat entry |
| PD-004 | Correction = "Offset entry" (inverse transaction), not edit/delete | honest + semantically safe reversal | — | append-only, positive+type model | no-correction V1; fake delete | recovery UX honest |
| PD-005 | No date picker; read-only "Today"; dateless rows | `occurredOn` unsettable+unreturned | — | §9/§11 contract | hidden date picker | honest limitation |
| PD-006 | "Categorization hint" field — honest non-persistence label | description feeds RuleEngine only | — | §11 contract | hide field; fake persistence | keeps rule value |
| PD-007 | Dashboard totals currency-labeled only on uniform-currency portfolios; else explicit mixed-currency state | per-aggregation 400/ambiguous 200 | — | §22/BG-013 | raw totals | no misleading numbers |
| PD-008 | Transfers fully hidden (no entry, no simulation) | no endpoint | — | BG-001 | paired-manual fake | ledger integrity |
| PD-009 | Recurring in V1 with currency locked to account | complete API; defect unreachable under lock | — | §15/D14 | POST_V1 recurring | safe inclusion |
| PD-010 | Budgets + rules in V1 under More | real APIs, low cost | — | §14/§22 | POST_V1 both | richer automation |
| PD-011 | Session expiry → interstitial → preserve unsent input → resume after re-login | 15-min JWT, no refresh | — | §6/BG-006 | silent expiry / data loss | honest session UX |
| PD-012 | Import = blocking synchronous surface + idempotent-retry messaging + duplicates-are-not-errors | sync API, dedup-safe | — | §16/§19 | background/async claim | truthful import UX |
| PD-013 | NETWORK_REQUIRED offline policy | no sync contract | — | §33 offline row/BG-017 | offline-first promise | no false promise |
| PD-014 | Errors map status+`fields`; never parse `detail`; 404/401 empty-body treated as state not message | PARTIALLY_TYPED errors | REJECT raw-message errors | §24 | message-sniffing | stable error UX |
| PD-015 | FX + Export deferred to POST_V1 | placeholder provider; non-core | — | §22 FX/BG-018 + §5 export row | include both | lean V1 |

## 40. Assumption register

| Assumption | Source | Confidence | Risk if wrong | Validation stage |
|---|---|---|---|---|
| V1 users accept dateless rows / "Today" entries | P3 judgment | MEDIUM | core-review journey feels broken → BE-001 required first | HUMAN gate |
| 15-min sessions acceptable with good re-auth UX | P3 judgment | MEDIUM | churn/frustration → BE-002 priority | HUMAN gate |
| Offset entry reads as honest correction to users | P2 semantics + P3 | MEDIUM-HIGH | confusing totals → UX revision | HUMAN gate / usability check |
| Ledgers stay small enough for unbounded lists | contract + intent | MEDIUM | slow loads → BE-007 priority | post-launch measurement |
| TPBank users have machine-readable PDFs | P2 layout support | MEDIUM-HIGH | import fails for real users → support copy accuracy | early user feedback |
| ~1 MB multipart cap fits real statements | FRAMEWORK_DEFAULT_INFERENCE | MEDIUM | import 500s → BE-008 | runtime verification |
| Currency-lock fully avoids recurring defect | P2 code path | HIGH | scheduler poison → recurring fails | implementation testing |
| Single-currency portfolios dominate | product intent | MEDIUM | dashboard totals hidden often | user feedback |

## 41. Backend-enablement dependencies

(Documented per the workspace backend-gap procedure — not implemented
here; each requires a separate Human-authorized backend task.)

| ID | Capability | Reason | Required before | Product impact |
|---|---|---|---|---|
| BE-001 | `occurredOn` settable + returned | unsettable + unreturned today | date-display/date-entry milestone (post-V1 or M3 if prioritized) | dates on rows, back-dating, date filters |
| BE-002 | refresh token / longer session | 15-min JWT only | post-V1 session improvement | fewer forced re-logins |
| BE-003 | public transfer endpoint | `TransferService` internal-only | transfer feature milestone | transfer UX possible |
| BE-004 | import batch list endpoint | **DELIVERED M10** — `GET /api/imports` owner-scoped summaries | import history feature | user-visible history |
| BE-005 | transaction edit/delete (or void) | append-only | post-V1 correction upgrade | true correction UX |
| BE-006 | account edit/archive | absent | post-V1 account management | rename/hide accounts |
| BE-007 | pagination + date-range/search | dead Pageable; 3 filters only | large-ledger UX | scalable browse |
| BE-008 | multipart size config | ~1 MB default | large statements | fewer 500s |
| BE-009 | recurring currency validation (defect D14) + mixed-currency stats + typed error codes + batch status | correctness gaps | hardening | safer recurring/dashboard/errors |
| BE-010 | persisted `description`/notes on transactions | never stored today | notes feature | user-visible entry notes |
| BE-011 | budget edit/delete | unique category+month blocks same-month fixes | post-V1 budget management | wrong current-month budgets fixable |

## 42. Inputs for P4

`P3_ARCHITECTURE_INPUT` — product constraints P4 must support (not
implementation guidance):

1. Unauthenticated ↔ authenticated root switching + session-expired
   interstitial with **unsent-input preservation** and resume-after-relogin.
2. Server-authoritative balances — no client-side balance math anywhere.
3. Multipart synchronous upload with a **blocking full-screen processing
   state**, cancellation/interruption honesty, and idempotent retry.
4. Import result as a **finite state surface** (OK/DUPLICATE/FAILED
   groups + counts) — not a poller.
5. Explicit DTO→domain→UI separation; nullable-field tolerance
   (`category`, `endDate`, `targetCategory`, `reason`, `transactionId`,
   `convertedAmount`); defensive enum-string mapping.
6. Unpaginated list strategy — fully-loaded local filtering only.
7. Typed/fallback error surfaces: ProblemDetail + `fields` map +
   empty-body 404/401 as first-class states; **no `detail`-string
   parsing**.
8. Capability-gated visibility — gated features absent without disabled
   teasers.
9. Navigation graph per §13 with 4 destinations + a persistent primary
   "add transaction" action.
10. Form discipline mirroring server validation; submit-lock on tap
    (no idempotency keys exist).
11. Currency pairing everywhere; uniform-currency dashboard gate.
12. `LocalDate`-as-server-local-day vs `Instant`-UTC distinction.
13. Provenance chip derivation from `sourceRef` prefixes — display only,
    never persisted interpretation.
14. Recurring create must lock `currency` to the account's (defect
    avoidance).

## 43. Open questions for THE HUMAN

| # | Question | Why it matters | Default recommendation | If different |
|---|---|---|---|---|
| Q1 | Ship V1 without transaction dates? | rows have no dates; entries always "Today" — biggest honesty-vs-completeness call | **Accept for V1** + make BE-001 the top enablement | → B0 enablement before transactions milestone |
| Q2 | Ship V1 with 15-min sessions? | re-login every ~15 min of use | **Accept** + BE-002 high priority | → escalate BE-002 pre-implementation |
| Q3 | Offset entry as the correction UX? | append-only honesty vs user expectation of "edit" | **Yes** — honest and safe | → no-correction V1 (more frustrating) |
| Q4 | Recurring in V1 (currency-locked)? | defect is mitigated but real backend bug exists | **Yes — supporting** | → POST_V1, cleaner scope |
| Q5 | Budgets + rules in V1 under More? | adds 6 screens | **Yes** | → POST_V1, leaner V1 |
| Q6 | Escalate transfers as BE-003 for post-V1? | core to money management; needs backend work | **Yes — post-V1 priority** | → transfers dropped from roadmap |
| Q7 | Import history: accept one-shot result for V1? | no batch list; local persistence is an architecture choice | **Accept** (result-only) + BE-004 later | → BE-004 pre-M4 or local history design |

## 44. Blueprint limitations

- P3 defines product only: no technical architecture, libraries, modules,
  storage, threading, or navigation implementation choices were made.
- Screen definitions are inventory-level — no visual design, layout,
  color, typography, or animation spec exists yet.
- Backend behaviors are taken from the approved P2 contract at
  `8194daa`; if the backend changes, affected sections need re-review.
- Runtime-verified wire bytes were not part of P2's static audit —
  labeled inferences carry forward.
- Usability judgments (offset entry clarity, dateless-row acceptance)
  are product hypotheses flagged for THE HUMAN, not validated findings.
