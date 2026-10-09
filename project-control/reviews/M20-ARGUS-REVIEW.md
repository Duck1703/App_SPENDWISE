# M20 ARGUS REVIEW — Offsetting Entry

```
ROLE: ARGUS (Independent QA Reviewer)
MILESTONE: M20 — PRODUCT §21 offsetting entry (S10 → S11, journey J14)
ANDROID_RANGE: e1f149d..062e728
BACKEND_RANGE: 9970c3e..1768a72
REVIEW_MODE: adversarial review of the actual diff; every claim probed
             against source and live gates, not delivery prose.
VERDICT: PASS — 0 Blocker, 0 Major, 0 Minor, 3 Notes
```

## 1. Probes executed

### Spec fidelity (PRODUCT §21, verbatim)

- Action label is exactly "Create offsetting entry" — grep across
  `strings.xml` and the transactions feature confirms no
  undo/delete/edit/reverse/correct label touches the flow. The only
  `Edit` strings are the M19 account surface. **PASS**
- Honest copy is the spec's verbatim sentence on BOTH surfaces (detail
  caption + create banner): *"This adds an opposite entry. The original
  stays in history — income and expense totals for the period will both
  be higher than the true position."* **PASS**
- `TRANSFER_*` legs excluded at BOTH layers: the detail VM gates
  `offsetEligible` on INCOME/EXPENSE, and the create VM re-verifies the
  fetched source — a hand-crafted route can't smuggle a transfer id
  into a prefill. **PASS**
- Inverse-type prefill, same account/amount/currency/category — the
  currency invariant holds *because the currency is locked to the
  account* and the account is prefilled, not copied onto the wire as a
  separate field. **PASS**
- Append-only preserved: the diff contains no mutation path for the
  original row; backend test re-reads the original byte-identical
  post-offset. **PASS**

### Wire / contract honesty

- `grep "offset" data/remote/**` → zero hits. `CreateTransactionRequestDto`
  unchanged; the wire pin asserts the serialized body carries no
  invented field. **PASS**
- No new endpoint, no link column — `POST /api/transactions` only.
  Backend gained a test file only (`TransactionOffsetContractTest`,
  204 LOC, 5 tests). **PASS**
- Post-V1 boundary check: no backend production source touched; the
  human-owned dirty docs (`README.md`, `HUONG-DAN-SU DỤNG.md`,
  `docs/SPENDWISE-REQ.md`) untouched; reference repo untouched. **PASS**

### Ownership / privacy / session

- Foreign-account offset body → bare 404, foreign balance untouched
  (backend test). The offset source lookup goes through owner-scoped
  `findById` — another user's id lands on `optionsError`, never a
  prefill. **PASS**
- Session expiry: source lookup `SessionEndedException`/401 →
  `optionsExpired` → no publish; resume prefills via `onVisible`
  (pinned). Mid-submit expiry → lock released, full draft (including
  prefilled fields + `offsetSourceId`) preserved; same-user resume
  doesn't clobber (prefill-once guard). **PASS**
- Different-identity resume clears the whole protected stack —
  unchanged, unregressed. **PASS**

### Financial-history safety

- `mutations` emission → Transactions list/Home reload — the offset row
  appears in history alongside the original. **PASS**
- `TransactionDetailScreen` still renders every field from the
  cached list row; detail does not refresh on return (the row is
  immutable — nothing to repaint). **PASS**
- No client-side balance arithmetic anywhere in the diff. **PASS**

### Replay / concurrency

- `isSubmitting` lock guards the offset POST (double-tap pin in offset
  mode). No automatic retry at any layer — ambiguous transport failure
  shows the honest "couldn't confirm" copy. **PASS**
- Multiple *user-initiated* offsets remain legal — tapping the action
  twice creates two compensating entries, each honestly recorded. That
  is the ledger's real semantics, not a bug. **PASS**

### UX truthfulness sweep

- Detail banner copy changed from *"can't be edited right now"* to
  *"can't be edited."* — strictly true now that the correction remedy
  exists alongside it. **PASS**
- Offset banner title reads "Offsetting entry" (state), not the
  imperative action label. **PASS**
- The offset button sits below the fold on small screens — reachable
  via scroll; instrumentation uses `performScrollTo`. The caption is
  visible copy, not hidden in a tooltip. **PASS**

## 2. Findings

### Notes (accepted — none rise to Minor)

- **NOTE-1**: eligibility is recomputed on every detail refresh —
  archiving the account in another session between loads correctly
  hides the action on next refresh. Stale eligible state cannot survive
  a reload.
- **NOTE-2**: `offsetTransactionId` arrives via typed-route args and
  survives process death; the source row is re-fetched, never
  deserialized from nav args — a crafted/foreign id fails closed to
  `optionsError`.
- **NOTE-3**: the source fetch rides inside `loadOptions` (one extra
  `findById`, cache-hit in practice — the row was just rendered on the
  detail screen). Cold-entry on a cleared cache does one unfiltered
  list fetch — honest, bounded, existing `findById` semantics.

## 3. Regression evidence re-verified

- Android JVM suite: **742/742** green — M0–M19 surfaces (pagination,
  search, optimistic lock, description persistence, account lifecycle,
  transfer pairing) unregressed.
- Backend offset contract suite: **5/5** on real Postgres; full backend
  regression re-run is scheduled at CHECKPOINT G.
- detekt 0 smells, lint 0 errors, `assembleDebug` clean.

## 4. Verdict

PASS. The implementation matches §21 point-for-point, stays inside the
append-only model, invents nothing on the wire, and fails closed on
every adversarial path probed.
