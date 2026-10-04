# M0 Argus Review — Spendwise Android Foundation

REVIEW_MODE: **ISOLATED** (independent QA review of the M0 implementation
against `ARCHITECTURE.md` AD-001…AD-022, INV-001…INV-015, and the M0
acceptance criteria — reviewed as shipped, not as described).

Reviewer: ARGUS · Date: 2026-10-04 · Subject: pre-commit worktree of
`D:\app_spendwise\spendwise-android`

## Method

Read every created source file (32 Kotlin/resource files + 9 Gradle/config
files), re-ran the quality gates, cross-checked each AD/INV against the
code, and probed for silent scope creep, invented behavior, dead
affordances, and financial/logic shortcuts.

## Verified claims (spot-checked in code)

- **No feature creep**: no repository interfaces, no service interfaces,
  no screens beyond auth/main placeholders; four destinations render
  `FoundationPlaceholder` with no FAB, no headers, no actions. INV clean.
- **Money**: `Money(BigDecimal, CurrencyCode)`; zero `Float`/`Double`
  anywhere in `src/main` (grep-verified); `BigDecimalSerializer` uses
  `JsonUnquotedLiteral` → wire text `{"amount":0.01}` confirmed by test.
- **401 split**: `toAppError()` maps 401→`SessionExpired` **and**
  documents that auth endpoints must not route their 401 through it;
  `SessionManager.onAuthenticationExpired` doc repeats the rule.
- **Token security**: ciphertext-only persistence; keystore alias
  `spendwise_session_key`, AES/GCM/NoPadding, 256-bit, random IV per
  encrypt, `datastore/` excluded in both backup rule files.
- **Logging**: Timber plant behind `BuildConfig.DEBUG`; OkHttp logger
  HEADERS + 3 redacted headers; no `println`/`Log` in sources.
- **Base URL**: provider chain property → env → `example.invalid`
  default; normalized trailing `/`; BuildConfig field on `defaultConfig`
  (applies to both build types).
- **Gates re-run by reviewer**: `assembleDebug`, `testDebugUnitTest`
  (36/36), `lintDebug` (0 errors/18 info warnings), `assembleRelease`
  (unsigned R8 OK), `compileDebugAndroidTestKotlin`, `detekt` (0 findings
  with project config) — all PASS.
- **Package discipline**: exactly the P4 layers; no invented layers, no
  stray `data/`/`feature/` stubs.
- **A11y floor**: nav icons carry `contentDescription`, semantic colors
  defined but never used alone, `TouchTargetMin` token present.

## Findings

### MINOR-1 — `Session.Expired` path discards last-username

`SpendwiseApp` maps `Expired` → `SessionExpiredPlaceholder(onSignInAgain =
onSignOut)`, and `SessionManager.signOut()` calls `TokenStore.clear()` —
wiping the username that `clearToken()` deliberately preserves. In M0 this
path is unreachable (no login exists), but the wiring contradicts the
AD-011 resume-UX intent if copied forward.
**Required action**: M1 must route Expired → re-login that retains
username + stack (per AD-011), not `signOut()`. Recorded in the milestone
deferrals. Acceptable for M0 as unreachable structural placeholder.

### MINOR-2 — Crypto layer has no executable test yet

`KeystoreCipher` (AES/GCM) and `DataStoreTokenStore` (encrypt→DataStore)
are only compile-verified; `SessionManager` is tested via `FakeTokenStore`.
A wrong GCM spec (tag size, IV handling) would not be caught by JVM tests.
**Required action**: add an instrumentation (or Robolectric) round-trip
test at the first milestone where a device/emulator is available.
**Acceptable for M0** — honestly reported, fakes-based coverage is the
specified JVM-first shape, and `adb devices` shows no attached target.

### NOTE-1 — `BigDecimalSerializer` non-JSON fallback emits a string

Non-JsonEncoder output is `encodeString(toPlainString)` — a quoted string,
diverging from the JSON-number contract. Only Json is used in-app;
harmless, but a future non-JSON format would silently change shape.

### NOTE-2 — `SpendwiseMainShell` tab-selection is top-level only

`hasRoute<T>()` per tab won't mark parents selected once nested routes
(M3+ detail/import screens) exist. Expected refactor point; acceptable as
the shell is single-level in M0.

### NOTE-3 — `ErrorInterceptor` also throws on unhandled 3xx

If OkHttp ever surfaces a non-followed redirect, it becomes
`ApiException(3xx)`→`Unknown`. Correct-fail behavior; unlikely path.

### NOTE-4 — Monochrome launcher icon reuses the foreground vector

Adequate placeholder (tinted card+coin); a dedicated glyph may be wanted
when brand assets land in M2.

### NOTE-5 — Lint informational notices (18)

`NewerVersionAvailable`/`GradleDependency` (Kotlin 2.4.20, OkHttp 5.5.0,
detekt 1.23.8…), `ObsoleteSdkInt` on `mipmap-anydpi-v26`, plus a few
others — all informational; pins are deliberate (AGP-9-embedded Kotlin
2.3.x compiler compatibility; `mipmap-anydpi` without v26 is not a valid
resource dir on this AGP, verified by a failed resource-link attempt).

### NOTE-6 — `Money` exposes no arithmetic

Deliberate (INV: no client-side balance math); DTO→domain conversion in
later milestones must not grow a `plus`/`sum` without a currency check.

### NOTE-7 — `Session.Expired` carries only `username`

Re-login prefill is the intended consumer; expiresAt is dropped from the
state on expiry — fine, the dead token has no further use.

### NOTE-8 — `android:configChanges` covers the standard Compose set

Six flags including `uiMode` — matches template defaults; review if a
configuration-sensitive screen lands later.

### NOTE-9 — `versionName "0.1.0-m0"` / `versionCode 1`

Foundation versioning; a versioning policy can be set before the first
shipped build.

## Counts

```
BLOCKER=0
MAJOR=0
MINOR=2
NOTE=9
UNRESOLVED_BLOCKERS=0
UNRESOLVED_MAJORS=0
ARGUS_VERDICT=PASS
```

## Statement

The M0 foundation faithfully implements the approved P4 architecture
within the approved scope: it compiles, is genuinely tested (36 JVM tests,
evidence-backed), never touches the backend, and preserves every INV
invariant checked. The two MINORs are documented deferrals with named
owners (M1 wiring; first instrumentation-capable milestone), not silent
gaps. **PASS — recommend Human review.**
