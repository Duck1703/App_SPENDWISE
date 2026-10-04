# M2 ARGUS Review — Accounts + Design-System Core

Reviewer role: **ARGUS** (independent QA reviewer), per
`agent-system/agents/argus.md`. Independent pass over the M2 diff —
contract fidelity, financial-correctness invariants, session/cache
boundaries, navigation semantics, test meaningfulness, scope.

## Review focus

1. Backend accounts contract vs implementation at `8194daa` (source, not
   reports): endpoint set, DTO shapes, validation limits, error surface.
2. INV-012 / financial invariants: no client ledger, no cross-currency
   aggregation, BigDecimal-only.
3. Session integration: authenticated-401 expiry on pushed screens,
   cache isolation across identities, resume behavior.
4. Whether tests exercise the claimed behaviors (not just coverage
   theater).
5. M2 scope containment (accounts + design-system core only — no M3).

## Findings

### Blockers — none

### Majors — none

### Minors

- **MIN-1** A failed `refresh()` on a populated list replaces the
  visible content with the full-screen error state; the cached data is
  intact (retry restores it) but the flip is lossy. Honest — the failure
  is never hidden — but an inline banner over kept content would be
  kinder UX. Accepted; candidate polish for M3+.
- **MIN-2** `RemoteAccountRepository` cache read-modify-write
  (`cache = cache?.plus(x)`) is not atomic; concurrent writes could lose
  one entry. `@Volatile` makes publication safe and calls are
  UI-serialized in practice — accepted for M2.
- **MIN-3** The currency picker exposes the full JDK ISO-4217 set,
  including test/non-tender codes (`XTS`, `XXX`) that the server would
  400. Open-set is the product rule (PRODUCT §18) and server-side
  validation is the designed backstop — acceptable, mildly odd picker
  content.
- **MIN-4** A duplicate `POST /api/accounts` is possible if a create
  succeeds server-side but the response dies in transit and the user
  retries after resume — the backend has no idempotency key. Inherited
  contract limitation; surfaced honestly via the preserved form.

### Notes

- **NOTE-1** `isRefreshing` is plumbed through `Content` states but no
  pull-refresh affordance renders it yet; the refresh button in error
  states is the only retry UI. Harmless groundwork.
- **NOTE-2** `AccountDetailScreen` header title is the generic
  "Account"; the name lives in the hero. P3A-faithful enough — the hero
  carries identity.
- **NOTE-3** `topo_lines` (400dp decorative backdrop) trips a
  VectorRaster lint note; raster fallback only matters <API 24 and
  minSdk is 26. Informational.
- **NOTE-4** Unknown `AccountType` rows render a muted tile + generic
  "Account" label — deliberate open-set degradation, never a crash.
- **NOTE-5** Items corrected during the internal pass before this
  review, recorded for audit trail: initial 60-char name cap → backend's
  255; `isSubmitting` deadlock on SessionExpired → released;
  expired-mid-load skeleton-forever on resume → `onVisible` retry hook;
  field-label a11y binding + heading semantics; deprecated
  `hiltViewModel`/`menuAnchor` imports updated to current APIs.

## Verification performed

- Re-read `AccountController`, `AccountService`, `AccountResponse`,
  `CreateAccountRequest`, `AccountType` at backend `8194daa` — endpoint
  set, DTO fields, validation annotations, 404-empty-body, 201+Location.
- Confirmed **no** edit/delete/archive/transfer endpoints exist on the
  backend and none are declared client-side.
- Grepped the client for Float/Double in financial paths — none;
  `BigDecimalSerializer` carries wire fidelity; display rounding is
  `setScale(HALF_UP)` on a copy.
- Confirmed the hero computes a sum **only** when all balances share one
  `CurrencyCode` — no cross-currency path exists.
- Traced session-expiry on a pushed screen: `expiredOnStack` predicate →
  single overlay → same-identity resume pops expired+login → preserved
  create/detail entry re-composes → `onVisible` re-runs an aborted load;
  draft form fields survive in the surviving VM.
- Traced `cacheInvalidations` → repository drop on sign-out / expiry /
  different-identity; no account data can cross users.
- Re-ran all gates: `assembleDebug`, `testDebugUnitTest` (152/152),
  `lintDebug` (0 errors), `compileDebugAndroidTestKotlin`,
  `assembleRelease`, detekt (0 findings). `adb devices` empty —
  instrumentation honestly compile-only.
- Confirmed boundaries: backend `8194daa`, reference `5f9622c`,
  prototype untouched, private fixtures never accessed; no M3 surface
  (no transaction UI, filters, dashboard, import, planning).

## Verdict

```
ARGUS_VERDICT=FINDINGS
BLOCKER=0
MAJOR=0
MINOR=4 (all accepted, none blocking)
NOTE=5
M2_READY_FOR_HUMAN_AUDIT=YES
```
