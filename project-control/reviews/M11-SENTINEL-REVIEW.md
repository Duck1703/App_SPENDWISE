# M11 Sentinel Review — Transaction Occurrence Dates

```
VERDICT: PASS (0 blocker / 0 major / 3 minor notes)
SCOPE: backend D:\spendwise @ bc6e0bb (REQ-BE-001, account package only)
  + Android diff 517bce9..working-tree — TransactionDtos, Transaction
  domain, DateFormat, TransactionRepository, CreateTransaction{UiState,
  ViewModel,Screen,Destination}, TransactionRow/SwTransactions,
  TransactionDetailScreen, strings, fakes + tests.
BACKEND CONTRACT: optional occurredOn on create (omission → server
  LocalDate.now(), far-future > tomorrow → 400), required on every
  TransactionResponse (column NOT NULL). No GET /{id}, no date filters,
  ledger append-only — all unchanged.
```

## Contract fidelity

- `CreateTransactionRequestDto.occurredOn` is a required client field —
  the picked date is always on the wire as exact ISO `yyyy-MM-dd`
  (`LocalDate.toString()`, no formatter). MockWebServer asserts the
  literal `"occurredOn":"2026-09-15"` inside the exact request body
  and that no `T`/time suffix rides along.
- `TransactionDto.occurredOn` is required on reads — matches the NOT
  NULL column. Malformed/missing server values fail at the repository
  as `AppError.Unknown` — a fabricated date is never rendered
  (regression test: `"15/09/2026"` → failure, not a guessed date).
- Backend backward compatibility proven server-side: omission →
  `LocalDate.now()` (pre-M11 contract preserved via the domain
  defaulting + a 6-field Java overload); `> tomorrow` → 400; malformed
  → 400. Import/recurring paths bypass the manual-create mapper
  entirely and keep their own dates.

## Date-only semantics (the core M11 risk)

- **No `Instant`/`Timestamp`/`ZonedDateTime`/`System.currentTimeMillis`
  anywhere on the economic-date path** — domain is `LocalDate`, wire is
  the ISO string, UI is `LocalDate`.
- `DateFormat` picker conversion is **UTC-anchored**: Material3
  `DatePickerState` speaks UTC-midnight epoch-millis; both directions
  pin to `ZoneOffset.UTC`. `DateFormatTest` proves identical results
  under `Pacific/Kiritimati` (+14:00) and `Pacific/Niue` (−11:00), plus
  leap-day (2024-02-29), epoch, and year-boundary round-trips.
- Display is `ofLocalizedDate(MEDIUM)` on the device locale — verified
  on-device in `vi` (`4 thg 10, 2026` / `Thứ Năm, 15 tháng 10, 2026`);
  ISO never leaks; parsing stays ISO.
- `CreateTransactionUiState.occurredOn` defaults to `LocalDate.now()`
  at state construction — a draft snapshot, not a ticking clock.

## Authority / safety preservation

- `RemoteTransactionRepository.create` sends the selected date; the
  **server response** populates the created row — no local inference
  after a successful write.
- Session-generation guard, `SessionEndedException` dead-generation
  path, cache invalidation, `_mutations` emission, and
  no-auto-retry-on-ambiguous-write are **byte-identical** to M10 —
  only the new `occurredOn` parameter rides through.
- Append-only/read-only preserved: no edit/delete/void/transfer
  affordance anywhere; detail is still list-derived (no `GET /{id}`).
- Draft date survives ambiguous write + session expiry like every
  other field (VM test-proven).

## Accessibility / UX

- Date field = one merged semantics node (`clearAndSetSemantics` on
  the inner visual TextField + a full-field overlay) announcing
  "Date, selected \<localized\> — double-tap to choose a different
  day". On-device tests prove single-node semantics, dialog open,
  day-15 selection → exact `LocalDate(2026-10-15)` callback, and
  back-dismiss leaving the draft untouched.
- Row: date is a quiet third line inside the existing merged-row CD
  (`…, Manual, <localized date>`) — lazy `items(key, contentType)`
  untouched; filter row unchanged (still 4 chips, no date filter).
- Detail: `Date` `DetailField` between Account and Currency.
- Submit lock disables the date field like all other inputs.

## Findings

| Sev | Item | Disposition |
| --- | --- | --- |
| MINOR | Form `occurredOn` default is a construction-time `LocalDate.now()` snapshot; a form open across midnight keeps the entry date | Accepted — draft semantics; user can re-pick; server-bound contract unaffected |
| MINOR | DatePicker's own year range (1900–2100) allows picking a far-future date the server rejects with 400 | Accepted — server is the bound authority; the 400 surfaces as the honest form-level error (same bucket as currency/category); allows legitimate tomorrow-dated entries |
| MINOR | Server `occurredOn` validation errors map to the generic form error, not a field-level message | Accepted — RFC-7807 `fields` mapping only exists for amount/accountId today; consistent with existing optional-field behavior |

## Regression

M0–M10 untouched except: `Transaction` + `TransactionDto` gained a
required field (all construction sites updated — fakes, 4 androidTest
factories), `TransactionRepository.create` gained a required
`occurredOn` parameter (8 test call sites updated), the merged row CD
gained a `, <date>` suffix (5 CD assertions updated), and the stale
"Recorded as today" UI/test was replaced.

JVM `testDebugUnitTest`: **503/503** (baseline 493, +10 M11 tests).
Backend suite: 295 tests, 1 failure — pre-existing
`RecurringSchedulingJobTest` timing flake identical at baseline,
unrelated. Connected (Pixel 4 XL, API 33): **162/162** (baseline 158,
+4 device tests). detekt 212 files / 0 smells; lint clean.
