# M8 External Release Audit — independent adversarial review

Auditor: **external release audit** (per workflow contract — adversarial,
evidence-first; Sentinel/Argus read only after the independent verdict).

Audited range: `948a2ff..5780d61` (M8 governance → M8 delivery).
Continued on physical hardware after the emulator session paused.

## Device evidence — independently reproduced

| Environment | Detail |
| --- | --- |
| Fresh audit AVD | `Spendwise_M8_ExternalAudit` — API 36, x86_64, cold boot, wiped data |
| Physical device | Google Pixel 4 XL (coral) — Android 13 / API 33, arm64-v8a, locale `vi-VN` |
| Connected RUN 1 | **135/135 PASS** on fresh audit AVD (and simultaneously on prior AVD) |
| Connected RUN 2 | **135/135 PASS** — repeatable, no state/order leakage |
| Font-1.3 suite run | 3 failures → test-source fixes → **135/135 PASS at font_scale=1.3** |
| Physical RUN 1 | 16 locale-separator failures → test-source fixes → **135/135 PASS** |
| Physical RUN 2 | **135/135 PASS** on Pixel 4 XL |

## Test-diff audit (948a2ff..5780d61)

All 14 changed androidTest files audited hunk-by-hunk; every correction
traced to a test-source defect with the original requirement preserved or
strengthened:

| Cluster | Sites | Verdict |
| --- | --- | --- |
| `onNodeWithText`→`onNodeWithContentDescription` on merged/icon rows | ~15 | STRONGER — asserts the a11y property itself |
| Apostrophe literals → `getString(R.string.…)` | 8 | STRONGER — can't drift from the resource |
| Plain `ComponentActivity` → real `@AndroidEntryPoint MainActivity` host | 9 | STRONGER — faithful host, `hiltViewModel` resolves |
| IME `performTextInput` → direct `SetText` semantics action | 3 | EQUIVALENT — same `onValueChange` contract (IME-path realism noted) |
| Off-viewport/lazy → `performScrollTo` + populated state | 5 | EQUIVALENT/STRONGER |
| Swallowed `isSubmitting` forwarded | 1 | STRONGER — now actually tests the submitting state |
| `Home` double-match → ≥1-node assertion | 2 | EQUIVALENT |
| `strings.xml` product diff | 17 pairs | VERIFIED apostrophe-only (`\'`→`’`), no wording changes |

**TEST_CORRECTIONS_WEAKENED = 0.**

## External findings (what internal review missed)

### EXT-M8-01 — MINOR (FIXED): "TEE" overclaim

Milestone doc and a test comment claimed the emulator Keystore key was a
"real TEE key". Probe (`KeyInfo.securityLevel`) on the emulator returned
`0 = SOFTWARE`. Corrected: production comment + test comment + milestone
wording now say Android Keystore; **on the physical Pixel 4 XL the same
probe returns `1 = TRUSTED_ENVIRONMENT`** — real hardware TEE-backing is
now proven, and the emulator's software level is recorded honestly.

### EXT-M8-02 — MINOR (FIXED): flaky DataStore cold-start test

`tokenStore_coldStartReadsPersistedSession` cancelled the first scope and
recreated a DataStore over the same file without joining the cancellation
— racy, failed once under font-1.3 scheduling pressure. Fixed with
`cancel()` + `job.join()`. Production unaffected (single @Singleton store).

### EXT-M8-03 — MINOR (FIXED): font-fragile assertions at 1.3×

`CreateTransactionScreenTest` ("Recorded as today", submit click) assumed
all controls in-viewport at font 1.0. Fixed with `performScrollTo` —
form scrolls correctly; acceptance criterion (reachable via scroll) met.

### EXT-M8-04 — MINOR (FIXED): locale-hardcoded money/currency literals

16 physical-device failures: tests baked en-US group/decimal separators
(`1,250,500 VND`, `−25.50`) and `US Dollar` display names. Production
deliberately formats via `Locale.getDefault()` (EXT-MIN-2 — correct by
design; vi-VN renders `1.250.500`, `−25,50`, `USD — Đô-la Mỹ`). Fixed via
new `TestFormats` helper deriving expectations from the same production
formatters — assertions stay exact, now locale-portable. **This is the
strongest external catch: the suite only passed on en-US devices.**

### NOTE — process restart remains PARTIAL (documented constraint)

True process-kill/relaunch with a seeded session is not achievable without
a production backdoor: the instrumentation APK runs under a different UID,
and AndroidKeyStore keys are UID-namespaced — test-seeded ciphertext is
physically undecryptable by the app process. This is correct hygiene.
Persistence/restore is proven at the store level (fresh DataStore +
fresh cipher over the same file); session-graph routing is proven at the
UI level (9/9 `SpendwiseAppTest`); SessionManager restore logic is JVM-
covered. PARTIAL is accepted with this reason on record.

### NOTE — IME breadth

Login verified at runtime on both emulator and physical (Gboard,
`mInputShown=true`, controls reachable). Remaining forms carry
`verticalScroll().imePadding()` (source-verified) and are exercised by
the connected suite; physical runtime confirmation per-form is limited
by auth gating (no safe dev backend) — honest level: PASS with Login
proven end-to-end at runtime.

## Independent security re-verification

- Merged release manifest: `usesCleartextTraffic=false`; exported =
  MainActivity(LAUNCHER) + DUMP-guarded profileinstaller receiver;
  provider `exported=false`; INTERNET + signature receiver perm only.
- `datastore/` excluded from cloud/device-transfer/full backup; the real
  `spendwise_session.preferences_pb` lands inside it.
- Timber only under `BuildConfig.DEBUG`; OkHttp HEADERS debug-only with
  Authorization/Cookie/Set-Cookie redacted. No body logging.
- No tracked secrets/keystores; no unapproved deps.

## Independent release re-verification

- `isMinifyEnabled=true`, `isShrinkResources=true`, no signingConfig.
- DEX scan on a fresh build: **28/28 `$$serializer`** — independently
  derived source list (21 wire DTOs + 7 data-class routes; 16 object
  routes correctly use ObjectSerializer, not miscounted). MISSING=0.
- Temp-signed release smoke on the **physical Pixel 4 XL**: launch →
  Welcome renders (Manrope) → Login typed route → masked input → submit
  → honest "No connection" banner. Zero R8/Hilt/serializer crash.
- Release SHA-256 (post-remediation build):
  `0141d60326b76dae3de7a43aed8013681946f96709b7ca0961ca8841da808c7b`.

## Post-remediation gates

| Gate | Result |
| --- | --- |
| JVM unit tests | 422/422, 0 fail, 0 skip |
| Physical connected RUN 1 / RUN 2 | 135/135 / 135/135 |
| Audit-AVD connected (pre-phone) | 135/135 ×2, plus 135/135 @font 1.3 |
| assembleDebug / assembleRelease | PASS / PASS |
| lintDebug / detekt | 0 errors / 0 issues |
| Keystore functional (tamper/invalidation) | PASS on phone + emulator |
| Keystore security level | TRUSTED_ENVIRONMENT (phone), SOFTWARE (emulator) |

## Comparison with internal reviews

Sentinel (PASS 0/0/1/4) and Argus (PASS 0/0/1/4) were accurate on scope
discipline, matcher integrity, manifest/security, and boundary discipline.
External audit adds: the TEE overclaim (now hardware-verified AND
corrected), the DataStore race flake, font-1.3 fragility, and 16
locale-literal failures that en-US emulators structurally cannot catch.

## Verdict

```
BLOCKER=0
MAJOR=0
MINOR=4 (all remediated: doc wording, 3 test-source fixes)
NOTE=2 (process-restart PARTIAL by design; IME breadth honest level)
M8_EXTERNAL_RELEASE_AUDIT=ACCEPTED
```
