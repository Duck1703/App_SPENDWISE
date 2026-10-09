# M20 SENTINEL REVIEW — Offsetting Entry

```
ROLE: SENTINEL (Test Engineer)
MILESTONE: M20 — PRODUCT §21 offsetting entry (S10 → S11, journey J14)
ANDROID_RANGE: e1f149d..working tree (implementation + tests)
BACKEND_RANGE: 9970c3e..1768a72 (contract-pinning tests only — zero
               production change)
REVIEW_MODE: adversarial self-audit per workflow contract; all evidence
             reproduced from live gates, not assumed.
VERDICT: PASS — 0 Blocker, 0 Major, 1 Minor (remediated), 3 Notes
```

## 1. Test evidence (re-executed for this review)

| Gate | Result |
|---|---|
| Backend offset contract (`TransactionOffsetContractTest`) | **5/5** — real MockMvc + Postgres (`spendwise-m10-db`, env from `.env.local`) |
| Android JVM — touched classes | `CreateTransactionViewModelTest` **35/35**, `TransactionDetailViewModelTest` **11/11**, `AppRoutesTest` **12/12**, `TransactionsServiceContractTest` **25/25**, `TransactionRepositoryTest` **30/30** |
| Android JVM full regression (`testDebugUnitTest`) | **742/742**, 0 failures, 0 errors, 0 skipped (722 M19 baseline → +20) |
| Android lint (`lintDebug`) | PASS — 0 errors |
| Detekt | **0 code smells** |
| `assembleDebug` / `compileDebugAndroidTestKotlin` | PASS |

## 2. Requirement → test mapping

### Backend (`TransactionOffsetContractTest`, MockMvc + real Postgres)

| §21 acceptance surface | Covering test |
|---|---|
| Inverse row restores the balance; both rows persist | `anOffsettingEntryRestoresTheBalanceAndKeepsBothRows` |
| Offset row is an ordinary MANUAL row (no link field) | same test — `sourceRef` prefix + `description == null` asserted |
| Original row byte-identical after offset (append-only) | same test — id/type/amount/category/occurredOn/sourceRef re-read |
| Both rows in list, page (`totalElements`), export | `bothRowsAppearInListPageSearchAndExport` |
| Foreign-account offset body → bare 404 (R24/R25) | `anOffsetCannotTargetAForeignAccount` |
| Archive is not a server-side write freeze | `offsetOnAnArchivedAccountStillWritesViaRawApi` |
| Partial correction = ordinary smaller inverse | `aPartialCorrectionIsJustAnOrdinarySmallerInverse` |

### Android network (`TransactionsServiceContractTest`)

| Surface | Covering test |
|---|---|
| The offset POST is the canonical body — no `offset`/`originalId`/`link` field ever leaves the client | `an offsetting entry posts the canonical body — no offset fields on the wire` |

### Android repository (`TransactionRepositoryTest`)

| Surface | Covering test |
|---|---|
| `findById` resolves the offset source from the detail-warmed cache — zero extra request, byte-identical | `offset source resolves from the warmed detail cache — no extra request` |
| The offset write is ordinary `create()`: both caches invalidate, one `mutations` emission, original still resolves after refetch | `the offset write is an ordinary create — caches drop and one mutation emits` |

### Android ViewModel (`CreateTransactionViewModelTest`)

| §21 surface | Covering test |
|---|---|
| EXPENSE source → prefilled INCOME + account/amount/category + `offsetSourceId` | `offset prefills the inverse type and mirrors account amount category` |
| INCOME source → EXPENSE (inverse both ways) | `an income source prefills expense — the arithmetic inverse both ways` |
| Uncategorized source → `category == null` | `an uncategorized source prefills no category` |
| Offset wins over a stale `accountId` route arg | `offset wins over a stale accountId route arg` |
| TRANSFER_IN/OUT + UNKNOWN source → honest `optionsError`, no prefill | `a transfer or unknown source refuses the prefill honestly` |
| Archived source account (gone from picker) → `optionsError` | `an archived source account refuses the prefill` |
| Missing source row → `optionsError` | `a missing source row fails options honestly` |
| Dead-generation on the source lookup → no publish; `onVisible` resume prefills | `dead-generation on the source lookup arms resume and prefills after` |
| Offset submit posts the inverse via canonical `create` (exact call args) | `offset submit posts the inverse entry through the canonical create` |
| Submit lock + no replay hold in offset mode | `offset mode still locks double-taps and never auto-replays` |
| `loadOptions` re-run refreshes options without clobbering edits (MIN-1 fix) | `a loadOptions re-run refreshes options without clobbering edits` |
| Vanished offset account falls back honestly, draft untouched | `a re-run still drops a vanished offset account honestly` |

### `TransactionDetailViewModelTest`

| Surface | Covering test |
|---|---|
| INCOME/EXPENSE on a live account → `offsetEligible` | `income and expense on a live account are offset-eligible` |
| TRANSFER_*/UNKNOWN → hidden | `transfer legs and unknown rows are never offset-eligible` |
| Archived account → hidden | `an archived account hides the offset action` |
| Account lookup failure → hidden, detail still renders | `an unresolved account hides the offset action but keeps the detail` |

### Route (`AppRoutesTest`)

| Surface | Covering test |
|---|---|
| `offsetTransactionId` survives NavController string round-trip; absent on normal create | `transaction create route round-trips an offset source id` |

### Instrumentation (compiled; device run at CHECKPOINT H)

| Surface | Covering test |
|---|---|
| Action visible + verbatim honest copy; tap carries the source id | `offsetEligible_showsTheActionWithHonestCopy_andClickCarriesTheId` |
| Never labeled undo/delete/edit | `offsetEligible_neverReadsAsUndoDeleteOrEdit` |
| Hidden entirely when ineligible | `offsetIneligible_hidesTheAction_entirely` |
| Create form shows the honest banner only in offset mode | `offsetMode_showsTheHonestConsequenceBanner` / `normalCreate_hasNoOffsetBanner` |

## 3. Adversarial challenges run

- **Replay/concurrency**: `isSubmitting` lock covers offset submits
  (double-tap test); ambiguous failure → `CreateTxnFormError.Ambiguous`,
  no auto-retry anywhere in the path (POST has no idempotency contract).
  Session expiry mid-submit releases the lock and preserves the whole
  prefilled draft. **PASS**
- **Generation staleness**: `findById` for the source runs inside
  `loadOptions` — `SessionEndedException` arms `optionsExpired` and
  publishes nothing; `SessionExpired` 401s take the same path. **PASS**
- **Picker-consistency**: archived-account source → `optionsError`
  (defensive depth behind the detail gate). **PASS**
- **False-green check**: the inverse-type assertion reads the domain
  enum, not the raw string; the wire pin asserts absence of invented
  fields in the serialized body. **PASS**

## 4. Findings

### MIN-1 (Minor, REMEDIATED — `062e728`)

`loadOptions()` is also invoked by the submit path's account-404
handler and by the options-error retry. The first version re-ran the
source lookup + prefill unconditionally, so a re-run silently
overwrote the user's post-prefill edits to type/amount/category.
Fixed: the §21 prefill applies exactly once (guarded by
`offsetSourceId != null`); re-runs only refresh options and revalidate
the selected account against the live picker. Two pins added.

### Notes (accepted, no code change)

- **NOTE-1**: after a successful offset the user lands back on the
  *source* detail (previous stack entry), consistent with every other
  pushed create flow; the underlying list reloads via `mutations`. The
  new row is visible after backing out — no double-offset affordance
  appears because the source row is unchanged (append-only).
- **NOTE-2**: if the source account disappears between detail and the
  create form's reload, `accountId` falls back to the first live
  account — identical semantics to the existing `accountId` prefill.
  The submit lock + server revalidation still hold.
- **NOTE-3**: `occurredOn` and `description` are intentionally NOT
  prefilled — §21's prefill list is type/account/amount/currency/
  category only. The offset defaults to today with an empty note; the
  user may edit both.

## 5. Verdict

PASS. Every §21 acceptance point has a failing-capable pin at the layer
that could actually regress it; the append-only + no-replay +
session-generation invariants are exercised, not assumed.
