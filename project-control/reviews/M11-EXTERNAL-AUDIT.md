# M11 External Audit — Transaction Occurrence Dates

```
AUDITED_RANGE (Android): 517bce9306c23bc7a8b601fa2bc53ef4d480823d
  ..3e55bd05cf1b2c2f9d0379bf922216906d0b7a04
  (feature 7fa51f8 + governance 3e55bd0, both audited separately)
BACKEND: D:\spendwise 22efd60ef765246772206e796c9ba1dc9dbad3b1
  ..bc6e0bb67cae0366b12c5b03fd97915f90450619 (6 files, account package)
INITIAL_VERDICT: FINDINGS (0 blocker / 0 major / 0 minor / 3 notes)
FINAL_VERDICT: ACCEPTED — every gate independently re-run and green.
METHOD: independent source review, falsification attempts, fresh gate
  runs on the physical Pixel 4 XL, baseline-flake reproduction in a
  detached worktree — Sentinel/Argus read only AFTER the verdict froze.
```

## Physical evidence (Pixel 4 XL, API 33, vi-VN, serial redacted)

Device awake, keyguard non-blocking, authorized debug/test install only.
Two consecutive full-suite runs against the live environment:

| Run | Executed | Pass | Fail | Skip |
| --- | --- | --- | --- | --- |
| 1 | 162 | 162 | 0 | 0 |
| 2 | 162 | 162 | 0 | 0 |

Reported `162/162` independently reproduced twice (result XML: 162/0/0/0).
Includes the end-to-end picker path (day cell → `LocalDate(2026-10-15)`
callback) and the merged-semantics row/detail assertions.

## Product / REQ-BE-001 re-derivation

`POST-V1-BACKEND-REQUIREMENTS.md` REQ-BE-001 is the canonical spec:
optional `occurredOn` (ISO `LocalDate`) on create with server-today
default preserved; `occurredOn` on every transaction read; and —
critically — **"Validation: reject future-dated beyond a sane bound;
reject pre-ledger bounds is product-call."** PRODUCT §19/§20 corroborate:
dateless rows were an accepted V1 limitation behind BE-001, "Today" was
the server's local day, ordering is explicitly unspecified.

## Future-date rule (`now()+1`) — the primary falsification target

`TransactionMapper.toCommand`: `occurredOn.isAfter(LocalDate.now()
.plusDays(1))` → `DomainException` → sanitized 400 (`ApiExceptionHandler`).

Classification: **CANONICAL, not invented.** REQ-BE-001 authorizes "a
sane bound"; `+1 day` is a defensible bound — it accepts a client whose
local calendar is one day ahead of the server clock (the exact
near-midnight/timezone adjacency PRODUCT §20 itself acknowledges) while
rejecting arbitrary far-future entries. The same `LocalDate.now()`
(system default zone) feeds both the bound and the omitted-date default
— a single consistent time source, no midnight predictability split.
The bound lives only in the manual-create mapper; `ImportRowProcessor`
and `RecurringGenerationService` build `TransactionCreationCommand`
directly with their own dates — verified: zero changes under
`imports/`/`recurring/`, so statement dates (arbitrary past) and
scheduled due dates (legitimately future) are unaffected.

## Backend contract (verified against source, not the report)

- `CreateTransactionRequest.occurredOn`: optional `LocalDate`, no bean
  constraint (structural only); absent/`null` → the pre-existing
  command default `occurredOn ?: LocalDate.now()` fires — pre-M11
  semantics byte-identical.
- 6-arg compat overload delegates with `null` — preserves source AND
  behavior for older call sites; kept (justified, documented).
- `TransactionResponse.occurredOn`: `LocalDate` appended — Jackson
  serializes ISO `yyyy-MM-dd` (slice test asserts `"2026-09-15"`).
- `transactions.occurred_on` `NOT NULL DATE` column pre-existed —
  **no schema change** (entity/comment unchanged since before M11).
- Exactly one `@GetMapping` (the collection) — **no `GET /{id}` added**;
  list still owner-scoped via `findAllByUserIdWithReferences` /
  `findAllByAccountIdAndUserIdWithReferences` + owner-checked account
  lookup → foreign account 404. Search criteria still exactly
  `account/type/category` — no date params, ordering unchanged
  (unspecified DB order, matching PRODUCT §19's honest stance).
- Malformed date (`"15/09/2026"`, empty, datetime-shaped, impossible
  calendar date) → `HttpMessageNotReadableException` → 400; never 500.
  Slice test proves `"15/09/2026"` → 400.

## Date-only / timezone semantics

- Wire type `String` (ISO), domain `java.time.LocalDate` both sides —
  no `Instant`/`Timestamp`/epoch on the economic-date path.
- `DateFormat.toPickerUtcMillis()` =
  `atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()`;
  inverse `Instant.ofEpochMilli(millis).atZone(UTC).toLocalDate()` —
  UTC-anchored both directions; device zone provably cannot shift the
  day (test mutates `TimeZone.setDefault` to Kiritimati +14 and Niue
  −11 mid-test and asserts identical millis/dates; round-trips leap
  day 2024-02-29, year boundary, epoch).
- Display `ofLocalizedDate(MEDIUM)` — verified on-device vi-VN;
  zero hardcoded en-US assertions in tests (all mirror `DateFormat`).
- Form default `LocalDate.now()` = the user's local "today" — correct
  draft semantics; the `+1` server bound is precisely the mechanism
  that absorbs client-ahead-of-server calendars.

## Authority / session safety

- Response `occurredOn` is required, non-null, no default — missing or
  malformed fails at `LocalDate.parse` inside the repository's
  `Exception` boundary → `AppError.Unknown`; **no fallback-to-today
  anywhere** (`occurredOn ?: LocalDate.now()` grep: zero hits in
  `app/src/main`).
- Create sends `occurredOn.toString()` (exact ISO); the created row is
  populated from the **response**, never re-derived.
- Same-user resume: `Session.Expired` pushes the overlay; the whole
  backstack (and the draft VM incl. `occurredOn`) survives; same-
  identity sign-in pops it — M8 device tests still green.
- Different-user sign-in → `popUpTo(0){inclusive=true}` destroys every
  VM + `emitInvalidation()` clears caches — no cross-user date leak.
- `SessionEndedException` dead-generation discard, submit lock, and
  no-auto-retry ambiguous-write path are byte-identical to M10; the
  ambiguous-write VM test now additionally asserts the picked date
  survives the failed submit for manual resubmission.

## Regressions

- **M9 export**: `CsvExportService` untouched (not in the diff); its
  `occurredOn` column predates M11; `CsvExportTest` 2/2 green.
- **M10 import history**: `ImportHistoryListTest` 6/6 backend;
  Android import surfaces green in both suites.
- **Recurring**: generation uses `dueDate` via a direct command ctor —
  untouched; ownership/repository recurring tests green.
- **`RecurringSchedulingJobTest` flake — CLASSIFIED**: reproduced the
  identical `expected:<1> was:<2>` failure at pre-M11 `22efd60` in a
  temporary worktree (two due templates each legitimately generating —
  a test-design defect predating M11; M11 touched zero recurring code).
  CONFIRMED_PREEXISTING_FLAKE; worktree removed, main repo untouched.
- Full backend suite: **295 tests, 1 failure** = that flake. All 19
  tests in the three M11-modified classes green (mapper 4, controller 8,
  slice 7).

## Test-integrity audit

Every new/modified test is strong — no tautologies found: the contract
test asserts the **literal** `"occurredOn":"2026-09-15"` inside the
exact request body plus a no-`T`-suffix guard; the repository malformed-
date test asserts failure rather than fabrication; the VM tests assert
the date actually reaches the recorded `createCalls`; the device picker
test performs a real day-cell click and asserts the `LocalDate`
callback value. Timezone tests mutate the default zone, not a passed
parameter. Fakes record the new parameter (`FakeTransactionRepository`
`CreateCall.occurredOn`) — assertions bind to the recorded value, not
the fake's output.

## Scope-leak search

No `GET /api/transactions/{id}`, no `from`/`to`/date params, no
pagination, no search, no PUT/PATCH/DELETE, no FX, no refresh token,
no M12 surface — grep-verified across both diffs; governance docs
mention M12 only as "not authorized".

## Findings (all notes — none blocking)

### EXT-M11-NOTE-1 — Wire-level "key absent" backward-compat not slice-tested

SEVERITY=NOTE LAYER=TEST. Omission → server-today is proven at the Java
level (`createWithOmittedOccurredOnKeepsTheServerTodayDefault` through
the 6-arg ctor) but no MockMvc case posts JSON with the key entirely
absent. Jackson absent→null binding makes this near-tautological; the
Java test covers the semantic. RESOLUTION=DEFERRED (cosmetic coverage).

### EXT-M11-NOTE-2 — Release APK hash is not reproducible across builds

SEVERITY=NOTE LAYER=GOVERNANCE. Fresh `assembleRelease` produced
SHA-256 `dcf2bad4…eb3` (2,404,318 B) vs the milestone-recorded
`3cf9247b…62` — unsigned APKs embed per-build zip timestamps; the hash
is a fingerprint of that artifact, not reproducible proof. Both builds
were verified against the same source HEAD with identical serializer/
manifest posture. RESOLUTION=ACCEPTED (documentation nuance).

### EXT-M11-NOTE-3 — Near-midnight windows in `LocalDate.now()`-relative tests

SEVERITY=NOTE LAYER=TEST. Mapper/VM tests compare `LocalDate.now()`
across two calls; a run straddling midnight could flake (~1-day-per-
run boundary window, same class as the known recurring flake). The
+1-day slack in the bound itself makes product-level flakes
essentially impossible. RESOLUTION=DEFERRED (matches existing test-
suite convention; no production defect).

Internal-review comparison: Sentinel's 3 minors (default-date snapshot,
picker year range allowing a server-400 pick, generic form-level error
for `occurredOn` 400s) and Argus's 2 minors + 1 note (compat-overload
value, mapper-scoped bound, day-cell semantics quirk) were all
independently re-derived here and confirmed **accepted-by-design** —
the bound stays server-authoritative (a client-side picker bound would
duplicate and risk drifting from server policy), the error mapping
matches the existing optional-field contract, and the overload
preserves documented source compatibility. No external-only defects;
no internal false positives.

## Gate evidence (all independently re-run)

- Backend: `mvnw test` 295/1-flake; `mvnw package` green (fat jar).
- Android JVM: `testDebugUnitTest` **503/503**, 0 failures (47 classes).
- Static: `lintDebug` clean; detekt 212 files / 0 smells;
  `compileDebugAndroidTestKotlin` green; `assembleDebug` green.
- Release: `assembleRelease` (minified) green; APK SHA-256
  `dcf2bad4244aa8758649453562c6089a94733d005a4e6c97e5c2a6cd70be8eb3`;
  serializer inventory: 30 `$$serializer` classes expected from source
  = 30 found in the release mapping incl. `TransactionDto$$serializer`
  and `CreateTransactionRequestDto$$serializer` (18 object routes
  correctly need none); `BigDecimalSerializer` retained via INSTANCE.
  Merged release manifest: INTERNET only, `usesCleartextTraffic=false`,
  backup rules unchanged. HEADERS-only OkHttp logging is debug-gated
  with Authorization redacted — no financial-payload logging.
- Connected: 162/162 ×2 on the physical Pixel 4 XL (API 33, vi-VN).

## Final verdict

```
M11_EXTERNAL_AUDIT_FINAL=ACCEPTED
BLOCKER=0 MAJOR=0 MINOR=0 NOTE=3
```

The semantic chain holds end to end: user picks a `LocalDate` → picker
preserves the exact calendar day (UTC-anchored) → wire is exactly
`yyyy-MM-dd` → backend accepts within the canonical sane bound →
persists `transactions.occurred_on` unchanged → returns it on every
read → Android displays it verbatim → no timezone shift, no cross-user
leak, no fabrication, no scope leak, no regression.
