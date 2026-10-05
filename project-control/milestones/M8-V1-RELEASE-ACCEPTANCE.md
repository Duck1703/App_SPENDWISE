# M8 — V1 Release Acceptance & Device Validation

Status: **CURRENT**
Starting SHA: `948a2ff` (M7 approved — canonical M0–M7 sequence complete)

## Human authorization

```
M7_STATUS=COMPLETE
M7_HUMAN_APPROVED=YES
M8_NAME=V1 Release Acceptance & Device Validation
M8_STATUS=CURRENT
M8_HUMAN_AUTHORIZATION=EXPLICIT (direct Human governance decision
  creating this milestone after the M0–M7 sequence completed)
M8_FEATURE_SCOPE=NONE_NEW
M9_STATUS=NOT_DEFINED
POST_V1_IMPLEMENTATION=NOT_AUTHORIZED
```

## Objective

Convert M0–M7's compile-time and static evidence into **actual Android
runtime evidence**: run the accumulated instrumentation suite on a real
runtime, validate Keystore/DataStore/session behavior, cross-user
privacy, full V1 feature flows, IME/large-text/accessibility, lazy-list
performance, and the minified release artifact. Remediate any
release-blocking defect found. M8 adds **no product features**.

## Canonical scope (owns)

- Android runtime acceptance; device/emulator instrumentation execution
- Secure storage validation (Keystore cipher, DataStore token store)
- Session runtime validation (cold-start restore, expired fail-closed,
  logout, expiry overlay, same-user reauth, different-user reset)
- Cross-user privacy (no User-A data visible to User-B — release gate)
- Full V1 integration flows (auth→accounts→transactions→import→planning)
- Release minification/R8/serializer/typed-route validation
- Manifest/permissions/backup/cleartext/logging security audit
- IME behavior, large-font behavior, accessibility runtime behavior
- Lazy-list/performance validation incl. M7-EXT-01 re-evaluation
- Release-blocking defect remediation + regression coverage
- Release-readiness documentation + Post-V1 backend-gate analysis only

## Forbidden scope (does NOT own)

No new product features: no transfer, transaction edit/delete,
correction/reverse/offset UX, transaction date input, search,
pagination, FX, export, profile editing, OCR, arbitrary bank PDFs,
analytics, push notifications, new auth modes. No backend changes.
No production signing material (Q4 — Human-owned). No M9.

## Environment policy (Human-authorized)

- Preferred: existing emulator/AVD; else ONE temporary AVD
  `Spendwise_M8_Acceptance` using an already-installed system image.
- No SDK downloads, no SDK upgrades, no user-AVD mutation, no new AVDs
  beyond the one temporary AVD (removable after evidence complete).
- No physical-device font changes; emulator font scale may be changed
  and must be restored.
- A temporary test-only keystore may be created outside the repo solely
  to smoke-install the unsigned release APK on the emulator; deleted
  after the smoke; never committed.

## Mandatory gates (must all pass before TECHNICAL_COMPLETE)

Device gates, session gates, V1 flow gates, accessibility/input gates,
release gates, M0–M7 regressions, Sentinel + Argus — per the governing
task. Compile-only evidence is NOT sufficient for M8.

## Evidence log

### Device

- AVD `Spendwise_M8_Acceptance` — Android 16 / API 36, x86_64
  (`sdk_gphone16k_x86_64`), WHPX + SwiftShader, 1080×2400. One temporary
  AVD per §Environment policy; removable after acceptance.

### Instrumentation suite

- First full run exposed 33 latent TEST-source defects (never executed
  on a device before M8): wrong semantic property (`onNodeWithText` on
  contentDescriptions and `clearAndSetSemantics`-merged rows/figures),
  curly-vs-straight apostrophe literals vs resources, app-level tests
  needing a `@AndroidEntryPoint` host, field-label vs field-node
  matching, off-viewport/lazy-list items, and helper state not
  forwarded. All remediated as test-source corrections; one MINOR
  product finding fixed (mixed apostrophe styles in `strings.xml` —
  normalized to typographic `’`; 18 literals).
- **Final: 135/135 connected tests PASS, 0 fail, 0 skip** on
  `Spendwise_M8_Acceptance` (includes 9 new `SessionRuntimeM8Test`
  runtime validations).

### Runtime evidence (on-device)

- **Keystore (§19):** AES/GCM round-trip on the real TEE key; GCM tag
  rejects a tampered byte; fresh `KeystoreCipher` decrypts existing
  blobs (cold-start); key invalidation reads as signed-out, not crash.
- **DataStore (§20):** save→load round-trip; plaintext token never on
  disk (raw file verified); a new store over the same file restores the
  session (cold-start); `clearToken` drops only the credential;
  `clear()` wipes identity for the next user.
- **Session graph (§20–22):** restoring→signed-out→signed-in→expired
  overlay (single, never duplicated)→same-identity resume restores
  destination→different-identity lands fresh Home→sign-out→welcome —
  all proven on-device via `SpendwiseAppTest` (9/9).
- **Cross-user privacy (§21):** `USER_A_M8_DATA_VISIBLE_TO_B=NO` —
  credential wiped by `clear()`; UI stack cleared to shell root on
  different-identity sign-in; no local financial data is held.
- **Minified release (§24):** unsigned release signed with the local
  debug keystore (Human-owned signing untouched; test-only signature),
  installed, launched: welcome→login typed route→credentials→Retrofit
  call→honest network banner. No crash, no serialization failure.
  28/28 `$$serializer` classes present in the release DEX.
- **Accessibility (§26):** merged-semantics rows/figures verified via
  135 tests; 48dp targets intact at font_scale 1.3 on-device; IME shows
  (`mInputShown`) with `imePadding` keeping fields+submit visible.
- **Manifest/security (§27):** `usesCleartextTraffic=false`; only
  INTERNET + signature-level receiver perm; exported components =
  launcher activity only (startup provider false; profileinstaller
  receiver guarded by DUMP); `datastore/` excluded from cloud backup,
  device transfer, and full backup; Timber planted only when
  `BuildConfig.DEBUG`; OkHttp logging debug-only with Authorization/
  Cookie/Set-Cookie redacted, never bodies.

### Final gates

| Gate | Result |
| --- | --- |
| JVM unit tests | 422/422 pass, 0 fail, 0 skip |
| Connected instrumentation | 135/135 pass, 0 fail, 0 skip |
| `assembleDebug` / `assembleRelease` | PASS / PASS |
| lintDebug | 0 errors, 20 warnings (baseline parity) |
| detekt | 0 issues |
| `compileDebugAndroidTestKotlin` | PASS |
| Release serializer retention | 28/28 `$$serializer` in DEX |
| Release on-device | launch + nav + submit, no crash |

### Defect inventory

- 0 BLOCKER, 0 MAJOR product defects found by device validation.
- 33 latent TEST-source defects remediated (regression coverage = the
  corrected suite itself + 9 new runtime tests).
- 1 MINOR product defect fixed: apostrophe inconsistency in
  `strings.xml` (17 resources normalized to `’`).
- M7-EXT-01 re-evaluated at runtime: acceptable (see deferred).

## Sentinel / Argus

- `project-control/reviews/M8-SENTINEL-REVIEW.md` — PASS (0/0/1/4)
- `project-control/reviews/M8-ARGUS-REVIEW.md` — PASS (0/0/1/4)
- `project-control/POST-V1-BACKEND-GATES.md` — analysis only, per scope

## Deferred findings

- M7-EXT-01 (MINOR, deferred): grouped-card eager lists on
  Accounts/Budgets/Rules/Recurring — re-evaluated at runtime here.
