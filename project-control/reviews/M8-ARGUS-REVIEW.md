# M8 ARGUS Review — V1 Release Acceptance & Device Validation

Reviewer role: **ARGUS** (independent QA), per
`agent-system/agents/argus.md`. Fresh read of the full M8 diff, the
connected-test reports, the merged release manifest, the release APK
binary, and the milestone's own scope constraints. Focus: whether the
runtime evidence is real (not asserted), whether test remediations were
corrections rather than weakenings, whether any product code regressed,
and whether M8 stayed inside its validation-only mandate.

## Verdict

```
BLOCKER=0
MAJOR=0
MINOR=1 (fixed in-milestone)
NOTE=4
M8_ARGUS=PASS
```

## Independent re-verification (not trusting prior reports)

| Claim checked | Finding |
| --- | --- |
| Device evidence is real | VERIFIED — `Spendwise_M8_Acceptance` AVD (API 36, x86_64) exists; connected run artifacts (`TEST-*.xml`) show 135 executed/0 failed on `emulator-5554`; release APK was installed (`Incremental Install Success`), launched (PID alive), navigated, and driven through sign-in on-device. |
| Test defects vs product defects | VERIFIED — all 33 initial failures traced to test sources: wrong matcher (`onNodeWithText` on `contentDescription`/merged rows), apostrophe-charset literals, missing `@AndroidEntryPoint` host, label-vs-field targeting, off-viewport nodes, a swallowed `isSubmitting` parameter. Each fix preserves or strengthens the assertion (CD assertions on merged rows are *stricter* than the original text lookups). No product accessibility was removed to make tests pass — `clearAndSetSemantics` rows intact. |
| M8 scope discipline | VERIFIED — diff confined to `app/src/androidTest/**` + `strings.xml` typography + governance artifacts. One product-side change: apostrophe normalization (17→curly), cosmetic and consistent with the file's dominant convention. No new screens, no features, no M9. |
| New runtime coverage is honest | VERIFIED — `SessionRuntimeM8Test` exercises the real `KeystoreCipher`/`DataStoreTokenStore` (production classes, not fakes): GCM tamper rejection, plaintext-token-never-on-disk via raw file bytes, cold-start re-open, key-invalidation→signed-out, `clearToken` vs `clear` semantics. The DataStore singleton constraint was respected via unique files + cancelled scopes, not worked around. |
| Session/privacy posture on device | VERIFIED — `SpendwiseAppTest` 9/9 on real runtime: restoring→welcome→signed-in→expired overlay (no stacking)→same-identity resume restores destination→different-identity resets to Home→sign-out→welcome. Storage-level wipe proven. `USER_A_M8_DATA_VISIBLE_TO_B=NO` holds at both UI and disk layers. |
| Release hardening still holds | VERIFIED at the binary — 28/28 `$$serializer` names in `classes.dex` of the final unsigned release; release build installed and exercised: typed-route navigation + Retrofit error path work under R8 (no `Serializer not found` / no crash). |
| Manifest/security audit | VERIFIED — merged release manifest: `usesCleartextTraffic=false`; INTERNET only (+ androidx signature receiver); exported = launcher only; startup provider `exported=false`; profileinstaller receiver guarded by `DUMP`; `datastore/` excluded from cloud/device-transfer/full backup (production `preferencesDataStoreFile` lands inside `datastore/` — exclusion actually covers it). Timber `BuildConfig.DEBUG`-gated; OkHttp HEADERS debug-only with Authorization/Cookie/Set-Cookie redacted. |
| Boundary discipline | VERIFIED — backend `8194daa` and reference `5f9622c` untouched; no dependencies added; no signing material committed (dev-signed APK is a local artifact under `build/`, gitignored); no remote/tag/push. |
| Post-V1 analysis fidelity | VERIFIED — `POST-V1-BACKEND-GATES.md` diffs the 23-call API surface against 27 backend mappings and correctly identifies 3 backend-ready unconsumed GETs (import report, transaction export, FX convert) plus the genuine gaps (no PUT/PATCH/DELETE on transactions/accounts/budgets/categories; no refresh-token). "Can't be edited" copy remains honest both ends. |

## Findings

### MINOR-1 — apostrophe inconsistency in `strings.xml` (FIXED)

17 resources used `\'`, 24 used `’` — user-invisible as a defect but
inconsistent typography, and the direct cause of 8 test literal
mismatches. Normalized to `’` (file's dominant convention). Fixed in
M8; verified by the green 135-test run.

### NOTE-1 — connected instrumentation was a blind spot through M7

The suite was added incrementally but never executed on a runtime until
M8, which let 33 latent matcher defects accumulate. Now green; the cost
of keeping it green is low because the suite runs in ~6 min on the M8
AVD. Recommend the external audit note that **compile-only test
evidence is insufficient going forward**.

### NOTE-2 — real-backend E2E still outstanding

No backend infra/secrets are available, so end-to-end validation is
structural (contract diff + MockWebServer + honest-error mapping on
device). This is an environment limit, not a defect — flagged for the
external auditor rather than hidden.

### NOTE-3 — lazy-list performance verified behaviorally, not profiled

Lists render and scroll on-device (135 tests incl. scroll assertions);
no frame-timing measurement. Acceptable on emulator (SwiftShader is
unrepresentative); a physical-device pass would be the only meaningful
measurement and was not authorized.

### NOTE-4 — emulator-only runtime

All device evidence is from `Spendwise_M8_Acceptance` (AVD). Physical
hardware (real TEE attestation, OEM IME/backup behaviors) remains
unexercised — explicitly within M8's "emulator if possible" mandate.

## Requirement fidelity (M8 brief)

Every §Canonical scope item has runtime evidence: emulator obtained →
instrumentation executed → Keystore/DataStore validated → cold start /
session restore / expiry / reauth validated → cross-user privacy
validated → V1 flows on device → IME / large text / accessibility →
lazy-list behavior → minified release / R8 / serializers / typed routes
→ manifest / permissions / backup / cleartext / logging audit →
blocker+major remediation (none needed) → regression coverage added →
full JVM + connected + release gates → Sentinel + Argus →
post-V1 backend gates → stop at external audit gate.

## Verdict detail

```
M7_COMPLETE=YES
V1_FEATURE_SCOPE_COMPLETE=YES
M8_TECHNICAL_COMPLETE=YES (pending this review's acceptance)
V1_RELEASE_ACCEPTANCE=PASS
M8_ARGUS=PASS
M9_DEFINED=NO
M9_STARTED=NO
```

Ready for the external M8 release audit. Nothing blocks handoff.
