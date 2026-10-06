# M11 Argus Review — Transaction Occurrence Dates

```
VERDICT: PASS (0 blocker / 0 major / 2 minor / 1 note)
ROLE: Independent QA reviewer — adversarial pass over backend bc6e0bb
  and Android diff 517bce9..working-tree against PRODUCT/CONTRACT/
  DECISIONS, not a re-run of Sentinel's test-engineering lens.
```

## Adversarial checks

**Invented-surface audit.** Diff contains: `occurredOn` field additions
(3 DTOs), one repository parameter, a Material3 `DatePickerDialog`,
row/detail text, strings, tests, docs. No `GET /api/transactions/{id}`,
no date/`from`/`to` query params on list, no pagination, no search, no
PUT/PATCH/DELETE, no FX call, no refresh-token path, no M12 surface —
all verified absent, not assumed. `GET /api/transactions` filter
params remain exactly `accountId`/`type`/`category`
(contract-test-proven: no `date` param on the wire).

**Financial authority.** The client never computes a date: domain
`Transaction.occurredOn` arrives only from `TransactionDto` via
`LocalDate.parse` at the single repository boundary; the form's
`LocalDate.now()` default is draft input, not displayed server data;
after a successful create the **response** date populates the row —
the UI never re-derives it. Malformed server dates fail loudly
(`AppError.Unknown` — regression-tested), never fabricate.

**Append-only / session safety.** `create` body is unchanged apart
from the new param: generation capture → request → dual cache
invalidation → generation re-check → mutation signal.
`SessionEndedException` semantics, the no-auto-retry POST rule, and
the preserved-draft ambiguous-write path are identical to M10
(`dead-generation submit` + `ambiguous` tests still green). Backend
atomicity untouched — `TransactionCreationService` is the same
single writer; the mapper only threads the date into the existing
command.

**Date-only integrity.** `Instant`/`Timestamp`/`ZonedDateTime` never
appear on the economic-date path. Picker↔domain conversion is
UTC-anchored on purpose and device-zone-proof (test switches the
default zone to ±14h/−11h mid-test). `ofLocalizedDate(MEDIUM)` is
display-only. The wire value is `LocalDate.toString()` — ISO by
definition, asserted byte-exact in MockWebServer tests.

**Ownership / isolation.** No list-shape change; `sourceRef`
provenance derivation unchanged; account ownership still enforced by
the backend's owner-scoped account lookup (404, not 403) — M11
touches nothing in that path.

**Accessibility.** The date control is exactly one semantics node —
verified on-device (single merged CD; inner visual TextField cleared).
During review the inner node's duplicate announcement was found and
fixed before this verdict. Large-font scroll coverage exercised via
`performScrollTo` in all date assertions.

## Findings

| Sev | Item | Disposition |
| --- | --- | --- |
| MINOR | `CreateTransactionRequest`'s Java compat overload is a compile-time convenience; wire-level backward compat already comes from optional JSON | Accepted — keeps older Java call sites source-compatible; documented in the record |
| MINOR | Server's far-future bound (`now()+1`) lives in `TransactionMapper`, reachable only via manual create | Accepted by design — import/recurring must accept their own dates; bound documented in contract §11 |
| NOTE | Day-cell semantics in M3 1.10 carry the spoken date as Text, not a numeric value — needed an unmerged-tree substring query | Test-infra detail; no product implication |

## Scope-exclusion verification

```
DATE_FILTERS_IMPLEMENTED=NO        PAGINATION_IMPLEMENTED=NO
SEARCH_IMPLEMENTED=NO              TRANSACTION_MUTATIONS_IMPLEMENTED=NO
TRANSACTION_GET_BY_ID_IMPLEMENTED=NO  FX_IMPLEMENTED=NO
REFRESH_TOKEN_IMPLEMENTED=NO       M12_STARTED=NO
```

## Evidence

JVM 503/503; connected 162/162 on Pixel 4 XL (API 33, vi locale —
locale-proofed expectations via `TestFormats.date`); backend suite
295/1-failure (pre-existing `RecurringSchedulingJobTest` flake,
baseline-identical); detekt 0 smells; lint clean; all changed files
inside the M11 slice + governance docs.
