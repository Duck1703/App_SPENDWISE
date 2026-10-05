# M8 SENTINEL Review — V1 Release Acceptance & Device Validation

Reviewer role: **SENTINEL** (test engineer / verification), per
`agent-system/agents/sentinel.md`. Scope: verify that the M8 runtime
acceptance actually ran on a real Android runtime, that the failures it
exposed were remediated honestly (not weakened away), and that the
final evidence set is complete.

## What ran

- Emulator `Spendwise_M8_Acceptance` (Android 16 / API 36, x86_64,
  WHPX + SwiftShader, 1080×2400) — created per M8 §Environment policy.
- First full connected run: **126 tests / 33 failures** — every failure
  was a latent TEST-source defect (the suite had never executed on a
  device; M0–M7 shipped compile-only instrumentation evidence).
- Final connected run: **135/135 PASS, 0 fail, 0 skip** (126 remediated
  + 9 new `SessionRuntimeM8Test`).

## Defect triage (§17 policy)

| Class | Count | Resolution |
| --- | --- | --- |
| Wrong semantic property — `onNodeWithText` on `contentDescription` (`Show password`) or `clearAndSetSemantics`-merged rows/figures (transactions, rules, budgets, home, import tiles) | ~15 | Corrected matchers to `onNodeWithContentDescription` / CD-substring; no product semantics changed |
| Curly-vs-straight apostrophe literals vs resources | 8 | Replaced literals with `InstrumentationRegistry.getString(R.string.…)` — assertion now reads the same resource the UI renders |
| `SpendwiseAppTest` needs a `@AndroidEntryPoint` host (`hiltViewModel`) | 9 | `createAndroidComposeRule<MainActivity>` + `activity.setContent` in `onActivity` (test-APK hosts cannot be launched cross-process) |
| Field label vs field node — `SwTextField` renders label as separate Text; IME text-entry path no-ops on headless emulator | 3 | Field targeted by `contentDescription`; `SetText` semantics action invoked directly (same `onValueChange` contract) |
| Off-viewport / lazily-composed nodes | 4 | `performScrollTo` on lazy ancestors; one test given a required non-empty `accounts` (form only renders with an account) |
| Test helper swallowed `isSubmitting` param | 1 | Forwarded — now actually asserts the submitting state |
| Double-match `Home` (nav label + screen title) | 2 | All-nodes ≥1 assertion |

**Product defects found by device validation: 0 BLOCKER, 0 MAJOR.**

One MINOR product defect: `strings.xml` mixed `\'` and `’` apostrophe
styles (17 straight vs 24 curly) — normalized all 18 occurrences to
typographic `’`. User-visible only as consistent punctuation.

## Runtime evidence added (9 new tests)

`SessionRuntimeM8Test` — first real-hardware validation of AD-010:

- AES/GCM encrypt→decrypt round-trip on the real Keystore key.
- Tampered blob rejected by the GCM tag (`AEADBadTagException` path).
- Fresh `KeystoreCipher` decrypts existing blobs (cold start).
- `DataStoreTokenStore` save→load; **plaintext token never on disk**
  (raw file bytes asserted); cold-start store re-reads the session.
- Keystore key invalidation → `load()` returns null, not a crash.
- `clearToken` drops only the credential; `clear()` wipes identity —
  cross-user privacy reset at the storage layer.

## On-device manual evidence

- Minified release APK (debug-signed copy — production signing is
  Human-owned, untouched) installed and driven: welcome → login typed
  route → credentials → Retrofit call → honest `No connection` banner.
  **No serialization crash under R8.**
- `font_scale 1.3`: all login/welcome controls in viewport, targets
  intact; restored to 1.0 after.
- IME: `mInputShown=true` on field tap; `imePadding` keeps fields +
  submit visible above the keyboard.
- Merged release manifest: `usesCleartextTraffic=false`; INTERNET only
  (+ signature-level receiver perm); launcher-only exported activity;
  `datastore/` excluded from cloud/device-transfer/full backup.
- Logging: Timber only under `BuildConfig.DEBUG`; OkHttp HEADERS
  debug-only with Authorization/Cookie/Set-Cookie redacted.
- Release DEX: **28/28 `$$serializer`** names present verbatim.

## Regression accounting (M0–M7)

- 422/422 JVM tests PASS (unchanged count — no production logic edits).
- lint 0 errors / 20 warnings (baseline parity); detekt 0 issues.
- `assembleDebug` + `assembleRelease` PASS; test compile PASS.
- M7-EXT-01 (grouped-card eager lists) re-evaluated at runtime:
  bounded domain lists on Accounts/Budgets/Rules/Recurring rendered
  and scrolled correctly at 1080×2400 — remains MINOR/deferred.

## Remaining honest limits

- Real-backend E2E still unexecuted (no backend infra/secrets) —
  network paths proven through `example.invalid` honest-error mapping
  and MockWebServer suites; the contract is verified structurally.
- Physical-device validation is emulator-only by policy.
- Lazy-list *jank* not instrumented — runtime rendering verified, not
  frame-timing-profiled (emulator GPU is unrepresentative anyway).

## Verdict

**PASS** — BLOCKER=0, MAJOR=0, MINOR=1 (fixed), NOTE=4.

M8 device validation is real and complete for the environment. Ready
for ARGUS and the external M8 release audit gate.
