# M11 — Transaction Occurrence Dates

```
STATUS: COMPLETE — external audit ACCEPTED (0 blocker / 0 major);
  Human-approved via conditional authorization
M11_TECHNICAL_COMPLETE=YES
M11_READY_FOR_EXTERNAL_AUDIT=YES
M11_EXTERNAL_AUDIT_FINAL=ACCEPTED
M11_HUMAN_APPROVED=YES
M11_STATUS=COMPLETE
TRANSACTION_DATES=APPROVED
M12_DEFINED=NO
M12_STARTED=NO
M11_HUMAN_AUTHORIZATION: EXPLICIT — Human authorized "M11 — TRANSACTION
  DATES" as one large delivery block including a bounded backend write
  for REQ-BE-001 (occurredOn on create/read).
BASELINE: 517bce9 (post-M10-remediation)
BACKEND: D:\spendwise @ bc6e0bb "feat: expose occurredOn on transaction
  create/read (REQ-BE-001)" — bounded write, account package only, no
  schema change (transactions.occurred_on NOT NULL exists since V5).
  User-owned README/docs left uncommitted there.
```

## Human decisions (authoritative)

- `M11 = Transaction dates` — user-selectable occurrence date on create,
  server date on list/detail, date-only semantics end to end.
- Backend write authorized for REQ-BE-001 only (three DTO/mapper files
  in `com.spendwise.account`; no mutation endpoints, no `GET /{id}`,
  no schema migration, no other backend surface).

## User outcome

A signed-in user creating a transaction can choose the day it happened
(defaults to today); list rows and the read-only detail show the real
server-persisted date — the same `occurred_on` the ledger stores —
localized for display, never drifted by timezone.

## Backend contract delivered (REQ-BE-001, commit `bc6e0bb`)

- `CreateTransactionRequest.occurredOn` — **optional** ISO
  `yyyy-MM-dd` `LocalDate`. Omission preserves the pre-M11 contract
  exactly: the server stamps `LocalDate.now()` (the domain/command
  defaulting was already in place).
- `TransactionResponse.occurredOn` — **required** on every read
  (list and create echo); the column is NOT NULL so it can never be
  absent.
- Sane bound: manual-create dates beyond `now()+1` are rejected at the
  mapper boundary (`DomainException` → 400). Past dates unbounded —
  backdating is legitimate and the import path already accepts
  arbitrary statement dates. The +1 slack absorbs clients one
  timezone ahead of the host.
- A 6-field `CreateTransactionRequest` overload preserves source-level
  compatibility for existing Java call sites (omission = `null` =
  server-stamped today).
- Unchanged: append-only ledger, no `GET /api/transactions/{id}`, no
  date filters, ownership scoping via account lookup/owner-scoped list,
  `transferRef`/`description` still not returned.
- Backend tests (`TransactionMapperTest`, `TransactionControllerTest`,
  `TransactionControllerSliceTest`): explicit date persists end-to-end,
  omission → today, far-future → 400, malformed date → 400, response
  serialization, ownership isolation.

## Architecture decisions (M11-local)

- **Wire `String`, domain `LocalDate`**: DTOs carry the ISO string;
  `RemoteTransactionRepository` is the single conversion point
  (`LocalDate.parse` in, `LocalDate.toString()` out) — same convention
  as `RecurringTemplate`. No `Instant`/`Timestamp`/`ZonedDateTime`
  anywhere on the path.
- **Picker conversion is UTC-anchored** (`DateFormat`): Material3
  `DatePickerState` speaks epoch-millis at UTC start-of-day. Both
  directions pin to `ZoneOffset.UTC` — a device in any zone picks the
  exact calendar day shown, never ±1 (proven in `DateFormatTest`
  under +14:00 and -11:00).
- **Display is locale-aware**: `ofLocalizedDate(MEDIUM)` on the device
  locale — ISO never leaks to the UI; parsing stays ISO.
- **Create always sends the picked date** — the app never relies on
  the server fallback; backward compatibility exists at the backend
  boundary for *other/older* callers, not for this client.
- **Server response is the truth after create** — the returned
  `occurredOn` populates the created row; no local date inference.
- **Draft preservation**: the picked date is ordinary form state —
  it survives an ambiguous write/session expiry like amount/hint, and
  clears with the rest on success.

## Scope exclusions

Date filters on list (deferred to REQ-BE-007 with pagination), edit/
delete/void/transfer, `GET /api/transactions/{id}`, search, FX,
refresh tokens, transaction mutations, M12.

## IA / UX

Create: "Date" field between Amount and Category — read-only
`OutlinedTextField` shape matching the account dropdown, full-field
tap target opens a Material3 `DatePickerDialog`; merged semantics
announce "Date, selected \<localized\> — double-tap to choose".
List row: date added as the quiet third text line
(category → account → date hierarchy) and appended to the merged
row content description. Detail: `Date` field between Account and
Currency in the existing `DetailField` list.

## Quality gates (M11 acceptance)

- Backend: focused slice/controller/mapper tests + full suite (the
  pre-existing `RecurringSchedulingJobTest` timing flake is a baseline
  failure, unchanged).
- Android JVM: `DateFormatTest` (UTC anchoring, locale, round-trips
  incl. leap day/epoch), repository date-in/out + malformed-date,
  contract body/response assertions, VM date default/pick/submit/
  draft tests.
- Compose/androidTest: date field + merged CD, picker opens/selects/
  cancels, row date, detail date.
- `testDebugUnitTest`, `lintDebug`, detekt, `assembleDebug`,
  `assembleRelease`, serializer scan, connected suite on the physical
  Pixel 4 XL (99041FFBA006F0).

## Quality-gate evidence

- **Backend (D:\spendwise @ `bc6e0bb`)**: focused suite 44/44 —
  `TransactionMapperTest` 4/4 (explicit/omitted/future), `Transaction-
  ControllerTest` 8/8 (explicit date persists end-to-end, omission →
  server today, far-future → 400, ownership), `TransactionController-
  SliceTest` 7/7 (explicit/malformed binding, response serialization),
  export/import regressions. Full suite: 295 tests, 1 failure — the
  pre-existing `RecurringSchedulingJobTest` timing flake, identical at
  the baseline; unrelated to dates.
- **Android JVM**: `testDebugUnitTest` 503/503 (47 classes), 0
  failures — +10 M11 tests over the 493 baseline: `DateFormatTest`
  (UTC anchoring proven under +14:00/-11:00, MEDIUM-style localized
  output, ISO↔picker round-trips incl. leap day and epoch boundary),
  repository explicit-date outbound + response mapping + malformed-
  date failure, contract body/response date assertions, VM date
  default/pick/submit/draft tests.
- **Static**: `lintDebug` clean; detekt 212 Kotlin files, 0 smells;
  `assembleDebug`, `assembleRelease` (unsigned, minified) and android-
  test compilation green.
- **Connected**: 162/162 on physical Pixel 4 XL (99041FFBA006F0) —
  second consecutive full green run after all fixes; includes the
  end-to-end Material3 date-picker path (dialog opens, day cell
  select drives `LocalDate` → ViewModel → request, cancel is a no-op),
  row CD with appended date, detail Date field.
- **Reviews**: [reviews/M11-SENTINEL-REVIEW.md](../reviews/M11-
  SENTINEL-REVIEW.md) and [reviews/M11-ARGUS-REVIEW.md](../reviews/
  M11-ARGUS-REVIEW.md) — both PASS, 0 blocker / 0 major; no
  remediation required.
- **Release**: `app-release-unsigned.apk` SHA-256
  `3cf9247bc0d9492d01f37127fadda9c72979047db5231761a5991a26d4cbcb62`.
  Serializer inventory: all 30 `$$serializer` classes for
  `@Serializable` classes retained unobfuscated in release mapping —
  including `TransactionDto$$serializer` and
  `CreateTransactionRequestDto$$serializer` (no new `@Serializable`
  types added; a field was added to existing DTOs). Merged release
  manifest: INTERNET only, `usesCleartextTraffic="false"`.

## Status

Complete. Backend contract change committed (`bc6e0bb`, bounded to
REQ-BE-001); Android vertical slice committed (`7fa51f8`); all gates
green on the physical device (connected 162/162 ×2). The independent
external audit re-derived every claim and ACCEPTED with 0 blocker /
0 major / 3 notes — see
[reviews/M11-EXTERNAL-AUDIT.md](../reviews/M11-EXTERNAL-AUDIT.md)
alongside the Sentinel and Argus reviews. Human-approved under the
conditional authorization carried by the audit task. M12 is not
authorized.
