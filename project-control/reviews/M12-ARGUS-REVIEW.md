# M12 Argus Review — Transfers Between Own Accounts

```
VERDICT: PASS (0 blocker / 0 major / 3 minor / 2 notes)
ROLE: Independent QA reviewer — adversarial pass over backend 8803396
  and Android diff 7f7be92..working-tree against PRODUCT/CONTRACT/
  DECISIONS and the M12 brief, not a re-run of Sentinel's
  test-engineering lens.
```

## Adversarial checks

**Invented-surface audit.** Backend diff adds exactly `POST
/api/transfers` + two DTOs + a pre-write currency-pairing guard — no
GET/PUT/PATCH/DELETE, no idempotency key, no FX call, no schema change
(Flyway untouched — verified). Android adds one Retrofit method, one
route, one feature package, two affordances. Absent and verified: no
local paired income/expense simulation, no `transferRef` reuse as a
list filter param, no client-side ledger writes, no offline transfer
queue, no automatic retry anywhere on the path.

**Financial authority.** The client never moves balances: repository
`transfer()` fires one POST, decodes the server's own leg rows, then
invalidates BOTH caches (`SessionCache.invalidate()` +
`accountRepository.invalidate()`) so the next read is a server read.
No `Money` arithmetic exists in the transfer path — the only
client-side math is `BigDecimal` parse for input validation
(requirement: strictly positive, ≤2dp — mirroring `@Digits(17,2)`,
`@DecimalMin(0.00, inclusive=false)` server-side).

**Ambiguous-write honesty.** `NetworkUnavailable` AND
`SessionEndedException`-after-response both map to `FormError.Ambiguous`
— "couldn't confirm whether this transfer was recorded — check your
transactions before trying again". No path claims success on a lost
response; no path claims failure that could hide a landed write. The
submit lock (`isSubmitting` on button+cancel+amount; VM early-return)
prevents double-taps — verified on-device.

**Ownership/isolation.** Server-side: both accounts resolved via
`findByIdAndUserId` — foreign IDs → 404 (no existence leak, tested both
directions). Client-side: destination picker never renders the source
account (semantics-verified — one `Checking` node total, menu adds
none); `onDestinationChange` has a belt-and-suspenders equal-id return;
repo `require()` is the final invariant. Request `currency` is locked
to the source account's code — a mismatched destination currency is
rejected client-side with a named field error, and would 400
server-side anyway.

**Session/generation safety.** `transfer()` mirrors `create()`
byte-for-byte: generation capture → request → dual invalidation →
generation re-check → `SessionEndedException` before `_mutations`
emission — a dead-session response can never publish as a fresh
mutation. Mid-submit `AppError.SessionExpired` releases the lock and
preserves the draft for same-user resume. `loadOptions` expiry arms
`optionsExpired`; `onVisible` re-runs it. The route is pushed over the
preserved shell — the session-expired overlay doesn't kill the form's
back-stack entry.

**Pairing integrity.** Same-currency is now a pre-write service guard
(fails before either leg is persisted) — the prior implicit
second-leg rollback still exists as backstop; atomicity test-proven
(rollback leaves zero rows). `transferRef` = one UUID shared by both
legs and echoed only on the 201 — never on list reads, so nothing can
depend on a fabricated linkage.

**Nav/back-stack honesty.** Post-create pops to Transactions-tab
(when pushed over the shell) via the existing
`KEY_OPEN_TRANSACTIONS_TAB` flag, or back to account-detail whose
mutations subscription reloads the (now server-fresh) history — the
user always lands on data the server produced.

## Findings

| Sev | Item | Disposition |
| --- | --- | --- |
| minor | Stale destination could equal the fallback source after a reload | Fixed pre-verdict + regression test |
| minor | Cross-currency pair surfaced a generic error, not a clear rejection | Fixed — `CrossCurrency` field error + tests |
| minor | Account-detail Transfer visible with one account, dead-ending | Fixed — gated on `hasTransferPeer` (cache-first, failure→hidden, session-expiry still propagates) |
| note | `hasTransferPeer` reads the account cache — a just-deleted peer can still show the button, then the submit 404s into `AccountUnavailable` + picker reload | Accepted — degrades to an honest error, never a crash or fake state |
| note | Repo-level `require(legs differ)` would crash on a programmer error | Accepted — unreachable via UI; server independently validates |

## Known baseline exception (unchanged by M12)

Backend full regression: 310 tests, 1 failure —
`RecurringSchedulingJobTest.firingTheScheduledMethodTwiceForTheSamePeriodCreatesExactlyOneTransaction`
(expected 1, was 2). Same timing flake documented in the accepted M11
baseline; unrelated to the transfer path (no scheduling code touched).

No invented endpoints, no client-side financial truth, no silent
writes. M12 is honest against CONTRACT and PRODUCT §22.
