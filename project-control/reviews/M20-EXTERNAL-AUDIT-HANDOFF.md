# M20 EXTERNAL-AUDIT HANDOFF — Offsetting Entry (PRODUCT §21 / S10 / J14)

```
M20_READY_FOR_EXTERNAL_AUDIT=YES
M20_HUMAN_APPROVED=NO          (audit gate precedes human approval)
M20_EXTERNAL_AUDIT=NOT_RUN     (excluded from the delivery block)
SPENDWISE_STATUS=M20_READY_FOR_EXTERNAL_AUDIT
NEXT_ACTION=ONE_LARGE_INDEPENDENT_EXTERNAL_M20_AUDIT
M21_DEFINED=NO
M21_STARTED=NO
```

The independent M20 external audit has NOT been performed — this
document is the handoff into it. Auditors: treat every number below
as a claim to be falsified, not as evidence.

## Scope delivered

One bounded capability: **the §21 offsetting entry** — the append-only
ledger's sanctioned correction remedy. On an `INCOME`/`EXPENSE`
transaction detail screen, "Create offsetting entry" opens the create
form prefilled with the **inverse type**, same account, same amount,
same currency (locked to the account), and the same category when set.
Submitting posts an ordinary `POST /api/transactions`; the original row
is never touched. Both surfaces carry the spec's verbatim honest copy:
*"This adds an opposite entry. The original stays in history — income
and expense totals for the period will both be higher than the true
position."* The action is never labeled undo/delete/edit and is hidden
on `TRANSFER_*`/`UNKNOWN` rows and rows on archived accounts.

Deliberately NOT in scope: `PATCH`/`DELETE`/void on transactions
(REQ-BE-005 stays product-gated), a link/back-reference between the
pair, prefilled `occurredOn`/`description` (§21 does not list them —
offset defaults to today + empty note), backend production change,
schema change, new endpoint, M21, course sync.

## Baseline and commits

```
Android repo:  D:\app_spendwise\spendwise-android  (branch main)
  M19 head:    e1f149d99ef48192d63e83a76207395acc978e7e  (accepted)
  M20 gov:     a3c6af1  docs(governance): define M20 offsetting entry
  M20 slice:   d7a4422  feat(transactions): M20 offsetting entry
  M20 remed.:  062e728  fix: prefill-once + banner title (self-review)
  M20 reviews: e550f85  docs(review): SENTINEL + ARGUS
  M20 closeout:<this commit — milestone state + handoff>
Backend repo:  D:\spendwise  (branch main)
  M19 head:    9970c3e944a11e295115bb5787b83e1aca5c3f75  (accepted)
  M20 delivery:1768a72  test(transactions): pin M20 offsetting-entry
               contract composition — TEST FILE ONLY; production tree
               identical to the M19-accepted baseline.
Reference:     D:\app_spendwise\kotlin-accelerator-ai — read-only, untouched.
Worktree note: backend retains its three pre-existing human-owned
               dirty paths (README.md, HUONG-DAN-SU-DUNG.md,
               docs/SPENDWISE-REQ.md) — untouched by M20.
```

## What actually changed (Android)

- `AppRoutes.kt` — `TransactionCreateRoute` gains
  `offsetTransactionId: String?` (default null; wins over `accountId`).
- `TransactionDetailViewModel` — resolves the account object;
  `Content.offsetEligible = type ∈ {INCOME, EXPENSE} &&
  account?.archived == false`.
- `TransactionDetailScreen` — `onOffset(transactionId)` param;
  `OutlinedButton` + verbatim §21 caption when `offsetEligible`;
  banner text is now strictly true ("can't be edited.").
- `CreateTransactionViewModel` — offset branch inside `loadOptions()`:
  `findById` re-fetches the source (never trusts nav args);
  re-verifies INCOME/EXPENSE + pickable account (else honest
  `optionsError`); prefills inverse type / account / `toPlainString`
  amount / category; sets `offsetSourceId`. Prefill applies **once**
  (guard `offsetSourceId != null`) — re-runs refresh options + revalidate
  the account without clobbering edits. Submit path unchanged.
- `CreateTransactionScreen` — info banner ("Offsetting entry" + verbatim
  copy) when `offsetSourceId != null`.
- `TransactionDestinations` / `MainFlow` — `onOffset` →
  `navigate(TransactionCreateRoute(offsetTransactionId = id))`.
- `strings.xml` — `txn_detail_offset_action`, `txn_detail_offset_hint`,
  `txn_create_offset_title`, `txn_create_offset_banner`; updated
  `txn_detail_readonly`.
- DTOs / services / repositories: **zero wire change** (grep `offset`
  under `data/remote` → no hits; contract test asserts no invented
  field on the POST body).

## Adversarial challenge evidence

- **Ownership/session**: foreign-account offset body → bare 404
  (`anOffsetCannotTargetAForeignAccount`); source lookup is owner-scoped
  `findById`; a crafted/foreign route id fails closed to `optionsError`;
  `SessionEndedException`/401 on the lookup arm the resume contract and
  publish nothing (`dead-generation … prefills after`); mid-submit expiry
  releases the lock with the full draft preserved (inherited path,
  exercised by existing pins + offset double-tap/no-replay test).
- **Replay/concurrency**: `isSubmitting` lock covers offset submits
  (`offset mode still locks double-taps…`); ambiguous transport failure
  → `Ambiguous` copy, zero auto-retry at any layer.
- **Financial-history safety**: backend pins the original row
  byte-identical post-offset and the balance exactly restored by the
  inverse (`anOffsettingEntryRestoresTheBalanceAndKeepsBothRows`);
  both rows remain in list/page/export; repository pin proves cache
  invalidation + one `mutations` emission.
- **Eligibility race**: source turning TRANSFER_*/UNKNOWN or its
  account leaving the picker between detail tap and form load → honest
  `optionsError` (VM pins).
- **Edit-clobber**: `loadOptions` re-runs after prefill cannot overwrite
  user edits (MIN-1 remediated — `a loadOptions re-run refreshes options
  without clobbering edits`).

## Gate evidence (all re-executed on final source, not assumed)

| Gate | Result |
|---|---|
| Backend offset contract | `TransactionOffsetContractTest` **5/5** (MockMvc + real Postgres `spendwise-m10-db`, env via `.env.local`) |
| Backend full regression | **457/457**, 0 fail, 0 err, 0 skip (452 M19 baseline → +5; surefire XML aggregate) |
| Android JVM | **742/742**, 0 fail, 0 err, 0 skip (722 M19 baseline → +20; XML aggregate) |
| `lintDebug` | 0 errors |
| `detekt` | 0 code smells (231 files) |
| `assembleDebug` / `assembleRelease` | PASS — `minifyReleaseWithR8` + `lintVitalRelease` green |
| R8 retention | `offsetTransactionId` in release `mapping.txt`; `@Serializable` keep rules cover all routes |
| Manifest | INTERNET only; `usesCleartextTraffic=false`; DataStore excluded from backup + device transfer — unchanged, no new permissions |
| Release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` — 2,478,662 B, SHA-256 `07f9b7155d5c81badc2d601f3a63de9dc84b9af92cfcdef94b13b8bcceddc2a3` |
| Physical Pixel 4 XL (`99041FFBA006F0`) | `connectedDebugAndroidTest` **218/218 × 2** consecutive on final source (213 M19 baseline → +5 screen tests) |
| Install + launch | `app-debug.apk` streamed install Success; `MainActivity` topResumed |

## Reviews

- `M20-SENTINEL-REVIEW.md` — **PASS**, 0 Blocker / 0 Major /
  1 Minor (remediated in-delivery, `062e728`) + 3 Notes.
- `M20-ARGUS-REVIEW.md` — **PASS**, 0 Blocker / 0 Major / 0 Minor /
  3 Notes.

## Findings carried into audit (documented, none hidden)

1. **Post-create landing is the source detail**, not the Transactions
   tab — consistent with every pushed create flow; the list beneath
   reloads via `mutations`. The offset row is visible after backing
   out (SENTINEL NOTE-1).
2. **Offset source on a vanished account falls back to the first live
   picker entry** on a `loadOptions` re-run — identical semantics to
   the existing `accountId` prefill; submit lock + server revalidation
   still hold (NOTE-2).
3. **`occurredOn`/`description` are not prefilled** — §21's prefill
   list is type/account/amount/currency/category only. The offset
   defaults to today + empty note (deliberate, documented in the
   milestone).
4. **Offset-of-offset is legal** — no link tracking exists; each
   compensating entry is an honest independent row (milestone §decisions).
5. **Archived accounts remain writable server-side** — pre-existing
   pinned M19 contract note (entry semantics, not a freeze); M20 adds
   `offsetOnAnArchivedAccountStillWritesViaRawApi` confirming no new
   rejection was introduced.
6. **Backend env**: tests need `SPENDWISE_DB_PASSWORD` +
   `SPENDWISE_JWT_SECRET` (source `D:\spendwise\.env.local`) — a run
   without them fails en masse at context load; environmental.

## Reproduction

```
Backend:  cd D:\spendwise && set -a && . .env.local && set +a &&
          ./mvnw -q test
          (focused: -Dtest=TransactionOffsetContractTest)
Android:  cd D:\app_spendwise\spendwise-android &&
          ./gradlew :app:testDebugUnitTest :app:lintDebug :app:detekt \
                    :app:assembleDebug :app:assembleRelease \
                    :app:lintVitalRelease :app:connectedDebugAndroidTest
Device:   Pixel 4 XL serial 99041FFBA006F0 (adb -s)
UI path:  sign in → Transactions → open an EXPENSE row →
          "Create offsetting entry" → prefilled INCOME form → Save.
```

## Explicit non-claims

- M20 external audit: **not performed** — this document is the input.
- M20 human approval: **not granted**.
- M21: **not defined, not started**.
- Course-repository synchronization: **deferred** (not in the delivery
  block).
