# M7 — External Audit Summary

Role: independent external audit (equivalent of M6-EXTERNAL-AUDIT-SUMMARY).
Audited range: `b9d85039dc56aab251fe65ea09c87576344e8f42..54993edfcbfaacf23dd1208da4bb0c511e9d42dc`
(16 files: 11 production, 1 new JVM guard test, 4 docs — all hardening-scoped;
zero product-feature / M8 leakage).

## Initial verdict

M7_INITIAL_EXTERNAL_AUDIT=**FINDINGS** — 0 blocker, 0 major, 1 minor, 4 notes.

| ID | Severity | Finding |
| --- | --- | --- |
| M7-EXT-01 | MINOR | Residual eager grouped-card lists: `AccountsScreen`, `BudgetsScreen`, `RulesScreen`, `RecurringListScreen` still render every row inside one lazy `item` via `Column + forEachIndexed` — the same idiom M7 fixed for the ledger/history/import rows. Domain-bounded (user-curated accounts/budgets/rules/templates; backend returns complete small lists), so realistic jank/OOM risk is low — unlike the unbounded ledger. A lazy conversion cannot preserve the reviewed grouped-card visual (a card cannot wrap lazily-realized items), so this is a visual-consistency question, not a correctness defect. **Deferred**: revisit if list growth becomes real or design approves per-row cards. |
| M7-EXT-02 | NOTE | Import `Empty`/`Error` surfaces are centered non-scrolling blocks (established M5 pattern; could clip only under extreme font scale on these compact states). Not an M7 change; consistent with auth/import modal surfaces. |
| M7-EXT-03 | NOTE | "Six non-auth forms" IME claim is slightly imprecise: exactly five editable-field create forms (account/transaction/budget/recurring/rule) + auth via shared `AuthComponents`; `ImportScreen` carries `imePadding` but has no text input (inert). Coverage is complete either way — every editable field host is IME-aware. |
| M7-EXT-04 | NOTE | Service interface names (`AuthService`, `PublicEndpoint`) are obfuscated in release — verified safe: `retrofit.create(X::class.java)` keeps classes by reference, `-keepattributes *Annotation*` retains method annotations Retrofit parses; identical risk profile since M1. |
| M7-EXT-05 | NOTE | `BigDecimalSerializer` survives renamed (`-> d5.a`, `INSTANCE` kept) — `with=` serializers are compile-baked into `childSerializers()`; name obfuscation is harmless. |

## §7–12 Release minification — independently falsified

Mechanism verified at library bytecode: `converter-kotlinx-serialization-3.0.0`
`Factory.responseBodyConverter(Type,…)` calls `Serializer.serializer(Type)` —
reflective name-based lookup for every service method.

Expected serializer set derived independently (not copied): **28 classes**
generate `$$serializer` (22 wire DTOs + 6 data-class typed routes). The 16
`@Serializable` *object* routes produce no `$$serializer` — they resolve to
`ObjectSerializer` with compile-baked serialName constants (verified present
in DEX) + `INSTANCE` references. Zero @Serializable enums/sealeds exist.

Falsification performed (pre-M7 ruleset temporarily restored):

| Ruleset | `$$serializer` names in release DEX |
| --- | --- |
| pre-M7 (`b9d8503` rules) | **0** |
| M7 (`54993ed` rules) | **28/28** |

EXPECTED_SERIALIZERS=28, FOUND_SERIALIZERS=28, MISSING_SERIALIZERS=0.
Parent class names and all 16 object-route names also survive; the defect
and the fix are causally proven. Typed-nav route serializers (6 data-class
routes) covered by the same `**$$serializer` keep; object routes are safe
via ObjectSerializer. Worktree restored clean after falsification.

`ReleaseHardeningTest` guards the rules-file failure mode (rule removal);
`**$$serializer` covers future wire models without test changes.

## §13–18 Release config / security

- `isMinifyEnabled=true`, `isShrinkResources=true`, default
  `proguard-android-optimize.txt` + project rules; unsigned release per Q4.
- `versionCode=1`, `versionName="1.0.0"`; no stale `0.1.0-m0` refs anywhere.
- Backup: `data_extraction_rules` + `full_backup_content` both exclude
  `files/datastore/` — matches real `spendwise_session.preferences_pb` path.
- `usesCleartextTraffic=false` globally; no NSC shipped. Debug-only cleartext
  for a dev host is canonically pre-authorized (§36) only if Q3's dev URL is
  HTTP — shipped default is `https://example.invalid/`; no cleartext path
  exists in any built artifact.
- Permissions: INTERNET only; single exported launcher Activity (required).
- Logging: HEADERS-level OkHttp + Timber are debug-only; no body/secret dumps.
- Font: `manrope.ttf` (90KB variable font) survives resource shrinking
  (`res/-z.ttf` in APK).

## §19–29 IME / lazy lists / a11y

- Editable-field hosts enumerated: 5 create forms + auth scaffold — all
  IME-aware; ImportScreen inert. DOUBLE_INSET: none — `imePadding` composes
  correctly with Scaffold `innerPadding` (worst case = extra scroll room).
- Lazy conversions verified at source + keys: transactions `key={it.id}`
  contentType `"transaction"`; account history `key={it.id}`; import
  `fail-N`/`row-N` prefixes on per-batch-unique rowNumbers (no collision;
  `ImportResult` is a sibling branch — no nested-scroll pathology).
- `CategoryChoice`: `selectable` + `Role.RadioButton`, no inner clickable →
  single merged TalkBack announcement. MoreScreen scrolls. `TouchTargetMin=48dp`.
- All `IconButton`s labeled; 0 raw `collectAsState()` in production; LiveRegion
  banners; skeleton loading descriptions.

## §35–36 Error polish / sniffing

Zero `message.contains`/`detail.` business logic hits. Structured AppError
mapping intact; ambiguous-write honesty preserved.

## §50 Gates (fresh, committed tree)

- assembleDebug PASS; testDebugUnitTest **422/422** (0 fail/skip);
  lintDebug 0 errors / 20 warnings; compileDebugAndroidTestKotlin PASS;
  assembleRelease PASS; detekt PASS (warn-only). `adb devices` → none →
  CONNECTED_TESTS=NOT_RUN_NO_DEVICE.

## Final verdict

M7_EXTERNAL_AUDIT_FINAL=**ACCEPTED**

BLOCKER=0  MAJOR=0  MINOR=1  NOTE=4

M7_CORE_CORRECTNESS=PASS · M7_RELEASE_SAFETY=PASS · M7_SECURITY_PRIVACY=PASS
M7_ACCESSIBILITY_HARDENING=PASS · M7_PERFORMANCE_HARDENING=PASS
M7_TEST_EVIDENCE=PASS · RELEASE_SERIALIZER_CHECK=PASS
M7_TYPED_NAV_RELEASE_SAFETY=PASS
