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

*(populated during execution)*

## Deferred findings

- M7-EXT-01 (MINOR, deferred): grouped-card eager lists on
  Accounts/Budgets/Rules/Recurring — re-evaluated at runtime here.
