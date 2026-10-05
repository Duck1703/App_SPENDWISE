# M6 External Audit Summary — Planning (Recurring / Budgets / Rules)

Independent external audit of the M6 vertical slice, per the overnight
pipeline contract. Audit performed **read-only** on the committed range
before any remediation decision.

## Audit scope

- **Audited HEAD**: `efd34c3f27cd2f39c3bae663b2dab7b940c8bbaf`
- **Audited range**: `5c0f344` (M5 human closeout) .. `efd34c3` — 63
  files, 14 test files.
- **Branch**: `main`, worktree clean at audit start.
- **Backend verified at**: `8194daa9959f2d568dc13ab0f211787195efc79d`
  (read-only; `BudgetController`/`BudgetService`,
  `RecurringTransactionController`/`RecurringTransactionService`,
  `CategorizationRuleController`/`CategorizationRuleService`,
  `ApiExceptionHandler` inspected at source).
- **Canonical scope**: ROADMAP M6 — "Planning: recurring transactions,
  budgets, categorization rules". No M7 leakage in the diff (no
  reports/insights/notifications surfaces; no new dependencies; no
  backend or reference modifications).

## Findings

### Verdict: `M6_INITIAL_EXTERNAL_AUDIT=PASS`

| ID | Severity | Finding |
| --- | --- | --- |
| EXT-M6-NOTE-1 | NOTE | Detail VMs (`BudgetDetailViewModel`, `RuleDetailViewModel`, `RecurringDetailViewModel`) carry a dead `load(initial: Boolean)` parameter and no `loadSerial` guard — **identical to the M2 `AccountDetailViewModel` / M3 `TransactionDetailViewModel` precedent**. Single-entity refreshes are idempotent reads of the same resource; consistent convention, no action. |
| EXT-M6-NOTE-2 | NOTE | Delivery report claimed "20 Compose tests"; actual is **18** `@Test` methods across `BudgetScreensTest`/`RecurringScreensTest`/`RuleScreensTest` (6 each). All nine screens covered; count was overstated, coverage was not. |
| EXT-M6-NOTE-3 | NOTE | `MoreScreen` root `Column` has no `verticalScroll` — 4 rows + divider + sign-out ≈ 350dp, could clip at extreme font scales. Pre-existing M5 pattern (was 2 rows), cosmetic only. |

No blockers, no majors, no minors.

## Backend contract fidelity — verified at source

| Contract point | Backend truth (`8194daa`) | Client behavior | Verdict |
| --- | --- | --- | --- |
| Budget endpoints | `GET /api/budgets`, `GET /{id}`, `GET /{id}/calculation`, `POST /api/budgets` | Retrofit calls match exactly; **no update/delete invented** | PASS |
| Budget request | `category`, `period` (`YYYY-MM`), `limit` (`@Digits(17,2)` positive), `currency` | `CreateBudgetRequestDto` field-for-field; client validates `YYYY-MM`, positive ≤17int/2frac `BigDecimal` | PASS |
| Budget conflicts | duplicate category+period → `409`; unknown category / malformed period → `400` | 409 → `FormError.Conflict` with honest copy; 400 → field errors | PASS |
| Budget calculation | server-derived `spent`, `remaining`, `percentage`, `status` | rendered verbatim; progress bar derives **only** from server `percentage` (display ratio, `toFloat()` for geometry); zero client arithmetic | PASS |
| Budget edit/delete | **does not exist** (BE-011) | honest read-only banner on list + detail ("can't be edited or deleted yet — a limit applies until its month ends") | PASS |
| Recurring endpoints | `GET` list/detail, `POST`, `DELETE /{id}` = soft deactivation returning updated template | matches; delete keeps screen showing deactivated state; generated-ledger copy honest | PASS |
| Recurring enums | `direction` ∈ {`INCOME`,`EXPENSE`}, `frequency` ∈ {`WEEKLY`,`MONTHLY`} | wire `UNKNOWN` armor on read; `require()` guards block `UNKNOWN` writes | PASS |
| Recurring currency | **no server-side account-currency check** (drift D14 — template can poison generation) | client locks `amount.currency` to selected account's currency; no free currency picker | PASS (mitigated) |
| Rule endpoints | `GET` list/detail, `POST`, `DELETE /{id}` = soft-disable returning `enabled=false` | matches; disable keeps screen showing disabled state | PASS |
| Rule create request | `conditionType`, `conditionValue`, `targetCategory`, `priority`; `enabled` **defaults server-side** | request DTO omits `enabled` entirely | PASS |
| Rule ordering | server-ordered by ascending `priority` | client preserves wire order, never re-sorts | PASS |
| Rule conflicts | duplicate user+priority → `409`; unknown category/condition → `400` | 409 → `CreateRuleState.PriorityError.Conflict` field-level; 400 → field errors | PASS |
| Dates | `nextDueDate`/`endDate` ISO local-date; `endDate` optional | ISO `yyyy-MM-dd` text fields with client parse validation; `endDate` omitted when blank | PASS |
| Foreign account | unknown/foreign `accountId` on recurring create → `400` | account chosen from loaded options only | PASS |
| 401 | `ErrorInterceptor` → `SessionExpired` | uniform: reads arm `expiredMidLoad` + preserve content; writes surface honest error | PASS |

## Areas audited — all PASS unless noted above

| Area | Evidence |
| --- | --- |
| No M7 leakage | `git diff 5c0f344..efd34c3 --name-only`: 63 files, all planning-scoped; no reports/insights/notifications code |
| Session generation | every repository call captures `sessionGeneration` pre-request, re-checks post-response, throws `SessionEndedException` on mismatch |
| Latest-wins | list VMs use `loadSerial` + superseded-response drop; detail VMs follow M2/M3 convention (NOTE-1) |
| Same-user resume | `expiredMidLoad` armed on `SessionEndedException`/401 mid-load; `onVisible()` re-runs aborted loads; writes never replayed |
| Different-user isolation | auth success rebuilds nav graph → VMs disposed; generation counter invalidates in-flight results |
| Write safety | `isSubmitting`/`isDisabling`/`isDeactivating` set synchronously before launch; `retryOnConnectionFailure(false)`; no auto-retry on any POST/DELETE |
| Ambiguous outcomes | timeout-on-write → `FormError.Ambiguous` with honest "may have been created" copy; `onVisible` reload refreshes truth |
| No client ledger | zero client-side due-date computation, generation simulation, or spend math; `budgets` list shows only limit (no invented spend) |
| Money correctness | `BigDecimal` throughout; sole `toFloat()` is `percentage/100` for progress-bar geometry (display-only, server-derived numerator) |
| Soft-delete honesty | deactivate → "This can't be undone" (accurate — no reactivate endpoint); rule disable copy explains existing transactions keep categories |
| UI states | every screen: Loading skeleton / typed Network·Server·Generic error + retry / Empty + CTA / Content + pull-to-refresh + refresh-error banner |
| Accessibility | `clearAndSetSemantics` row content-descriptions; `progressBarRangeInfo` on budget bar; `TouchTargetMin`/`heightIn(min=72dp)` rows; dialogs labelled |
| Static safety | zero `println`/`Log`/`GlobalScope`/`runBlocking`/`.message`/message-sniffing across the entire planning slice |
| Navigation | 9 typed routes + More-hub rows below Import; `popBackStack` for back, typed-route `navigate` for forward |
| Boundary preservation | backend `8194daa`, reference `5f9622c` untouched |

## Fresh gate run — committed tree, audit-time

```
assembleDebug=PASS
testDebugUnitTest=PASS (419/419, 0 failures — report-verified, not claimed)
lintDebug=PASS (0 errors, 20 informational warnings)
compileDebugAndroidTestKotlin=PASS
assembleRelease=PASS
detekt=PASS (0 smells)
connectedAndroidTest=NOT_RUN (adb devices: no device attached)
```

## Final verdict

```
M6_INITIAL_EXTERNAL_AUDIT=PASS
BLOCKER=0
MAJOR=0
MINOR=0
M6_CORE_CORRECTNESS=PASS
M6_FINANCIAL_CORRECTNESS=PASS
M6_SECURITY_PRIVACY=PASS
M6_CONTRACT_FIDELITY=PASS
M6_TEST_EVIDENCE=PASS (count note only)
M6_EXTERNAL_AUDIT_FINAL_VERDICT=ACCEPTED
```
