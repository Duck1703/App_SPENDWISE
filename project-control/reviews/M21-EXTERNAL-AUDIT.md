# M21 — INDEPENDENT EXTERNAL AUDIT

```
REVIEWER=Devin (SWE-2 Max) — independent external auditor
INDEPENDENCE_BOUNDARY=Auditor did not implement M21; all delivery
  reports/Sentinel/Argus claims treated as unverified claims and
  re-verified against source, runtime, signed artifact, and DB.
AUDIT_MODE=adversarial re-verification, fresh executions where safe
START_SHA_ANDROID=6c2f0909b128fc3157ecd9306ce149da375676ff
START_SHA_BACKEND=4ced814c3e45068b1c7dfc115c3ddcf59ecb7a34
M20_PREDECESSOR=20fbe6b (M20-accepted audit closeout) — VERIFIED in
  ancestry: 20fbe6b is an ancestor of the M21 delivery HEAD.
M21_DIFF_SCOPE=6 modified + 22 added files; production source limited to
  ApiBaseUrl.kt (new), NetworkModule.kt, SessionModule.kt,
  SessionRefresher.kt, per-variant manifests, build.gradle.kts, LAN CA
  public cert. Zero feature/financial-logic changes — scope discipline PASS.
```

## 1–5. Requirements + preliminary findings (recorded BEFORE reading reviews)

Canonical docs read first (AGENTS, WORKFLOW-CONTRACT, PRODUCT, ROADMAP,
BACKEND-CONTRACT, ARCHITECTURE, DECISIONS, M21 brief). Source diff inspected
before opening any review document.

INITIAL_INDEPENDENT_FINDINGS (verbatim, before reconciliation):

| ID | Sev | Finding | Disposition |
|---|---|---|---|
| AUD-N1 | NOTE | `build-signed-release.ps1` injects keystore passwords into process env; a Gradle daemon forked under it can retain `SPENDWISE_RELEASE_*_PASSWORD` in its env block until daemon exit, past the script's `finally` cleanup. | CONFIRMED — local-process hygiene only; no disk/log persistence. Recommend `gradlew --stop` after signing or documenting the residual window. |
| AUD-N2 | MINOR | TLS env-var name mismatch: `generate-lan-tls.sh` writes `SPENDWISE_TLS_STORE_PASSWORD` into `lan-tls.env`, but `start-backend.ps1` and `spendwise-lan.env.example` consume `SPENDWISE_TLS_KEYSTORE_PASSWORD`. A verbatim merge fails startup (loud, not silent). | CONFIRMED — deployed system unaffected (operator resolved it). Align names or document the rename. |
| AUD-N3 | NOTE | `ApiBaseUrl` placeholder check is exact host match — `https://example.invalid./` (trailing dot) bypasses it. Consequence: dead endpoint only, no security impact. | CONFIRMED — edge-case note. |
| AUD-N4 | NOTE | URL-policy interceptor validates the `BuildConfig` constant, not `request.url()` — a foreign-host request through the shared client would pass this check. Bounded anyway by NSC cleartext policy, domain-scoped CA trust, and OkHttp's cross-host `Authorization` stripping. | CONFIRMED — design boundary documented. |
| AUD-N5 | NOTE | `assembleRelease` with zero `SPENDWISE_RELEASE_*` env produces an unsigned APK silently (documented as intentional smoke-build path); partial set fails loudly. | CONFIRMED — documented behavior; the audited artifact itself is Human-signed and hash-verified. |
| AUD-N6 | NOTE | `backup-spendwise-db.ps1`/`restore-spendwise-db.ps1` interpolate operator params into cmd/SQL unescaped (`$DbName`, `$TargetDb`). Operator-controlled args; restore lacks `--single-transaction` (acceptable into fresh targets). | CONFIRMED — robustness note for Human-run tooling. |
| AUD-N7 | MINOR | Reporting inaccuracy: the closeout report transcribed `222/201/0/21` (numbers of the BUGGY pre-fix run). Authoritative XML on disk: `tests=239, failures=0, errors=0, skipped=21` → 218 executed+passed. | CONFIRMED — doc slip only; real evidence stronger than reported. |

## 6–12. Delivery findings reviewed afterward

Read after independent inspection: `M21-SENTINEL-REVIEW.md`,
`M21-ARGUS-REVIEW.md`, `M21-EXTERNAL-AUDIT-HANDOFF.md`. Their claims
reconciled against my findings:

- S8 (UTP count collapse) — CONFIRMED FIXED. Authoritative result XML shows
  239 tests with all 18 journey tests enumerated individually (no collapse).
- A1 (launch crash on unconfigured URL) — CONFIRMED FIXED, independently
  re-verified at runtime: a debug build with `SPENDWISE_API_BASE_URL=
  https://example.invalid/` launched to the welcome screen (process alive)
  and a sign-in attempt surfaced "No connection" as a handled error —
  no process crash.
- A7 (Postgres `0.0.0.0:55432`) — CONFIRMED FIXED: `docker port` shows
  `127.0.0.1:55432`, `netstat` shows only `127.0.0.1:55432 LISTENING`,
  Pixel TCP connect to `:55432` timed out (exit 124).
- A2/S1 (L2 isolation) — RESOLVED by Human (Pixel on same SSID now;
  whole audit ran over the live link).
- S7/A6 (SessionRefresher flake) — not observed across 3 fresh full
  suite executions in this audit; generation-checked apply verified in
  source (`sessionGeneration` compare at apply + `incrementAndGet` on
  sign-out). Watch item stands; no blocker.
- No review claim was found overstated; one report-number slip (N7) found.

New audit-time findings beyond delivery reviews: AUD-N8, AUD-N9, AUD-N10.

## 7. Signed release provenance — VERIFIED

- `app/build/outputs/apk/release/app-release.apk` built 2026-10-09 18:07
  from worktree matching `0ac8272`; the only later commit `6c2f090`
  (18:23) touches `M21-EXTERNAL-AUDIT-HANDOFF.md` +
  `scripts/lan/build-signed-release.ps1` — no app source. The binary
  therefore corresponds to final production source.
- `sha256sum` independently recomputed:
  `6c04a93e1bae7f9c214cd882a19980fe50ea3df47995c6ff59c572cb4fa81998`
  (2,493,178 bytes) — matches the reported hash.
- The byte-identical artifact was pulled from the Pixel and re-verified;
  preserved outside the repo at
  `D:\spendwise-backups\app-release-HUMAN-SIGNED-6c04a93e.apk`.

## 8. Human signing identity — VERIFIED

- `apksigner verify --print-certs`: **Verifies**; v2=true; v1/v3/v3.1/
  v3.2/v4=false; SourceStamp=false; **1 signer**.
- Signer DN: `CN=duc, OU=duckt, O=duckt, L=da nang, ST=da nang, C=dn`;
  RSA-4096.
- Cert SHA-256: `29042817a471b8c0969da0d1f0fcfa3a3c5ac39da76bbc06cf08b43002911403`
  — matches the reported Human fingerprint.
- Cert validity: 2026-10-09 → 2056-10-01 (~30y; `keytool -validity 10950`).
- Keystore `C:\Users\Lenovo\.spendwise-release\spendwise-release.jks`
  exists outside Git; Human acknowledged encrypted backup
  (`HUMAN_KEY_BACKUP_CONFIRMED=YES`). Human-ownership attribution rests on
  the Human's own statement + key location + cert subject — as far as an
  audit can verify without handling secrets.
- Not the ephemeral verification key (different DN/fingerprint).
- No signing secrets in source, build files, or committed material;
  partial env set fails configuration loudly.

## 9. Signature-scheme compatibility — PASS

`minSdk=26` ≥ 24 → v2-only signing covers the entire declared device
range. `versionCode=1`, `versionName=1.0.0`, `applicationId=
com.spendwise.android`, `targetSdk=36` — verified via aapt badging.

## 10. Release manifest/NSC — inspected inside the APK (binary)

- `usesCleartextTraffic=0`; `networkSecurityConfig` present;
  `debuggable` absent (false); `extractNativeLibs=0`.
- Shipped `network_security_config.xml` (aapt xmltree): one
  domain-config, `cleartextTrafficPermitted=false`, domain `192.168.1.236`
  `includeSubdomains=false`, trust anchors = system + `@raw/spendwise_lan_ca`
  only for that domain. No base-config override; everything else uses
  system defaults + manifest cleartext=false.
- Debug NSC (generated per-variant): cleartext permitted ONLY for the
  configured API host and only when its scheme is http.
- No private key, keystore, or env file inside the APK
  (`DebugProbesKt.bin` is normal Kotlin metadata).

## 11. API URL fail-closed — PASS

- Build-time: release `GenerateNetworkSecurityConfigTask` fails on
  placeholder or non-https; runtime: `ApiBaseUrl.validate` enforced at
  release Retrofit construction + on the OkHttp wire boundary
  (IOException, not crash) + `SessionRefresher.refreshUrl`.
- Live runtime check: placeholder-URL debug build launches; sign-in
  attempt → graceful "No connection" banner, process alive.
- Embedded-credential URLs, non-root paths, non-http(s) schemes,
  public-HTTP debug, placeholder — all rejected; 10-case JVM suite pins
  the matrix.

## 12. TLS trust + hostname verification — PASS (fresh)

- Live chain captured: `[0] CN=DESKTOP-HT4HNOC` (SAN
  `IP:192.168.1.236, IP:127.0.0.1, DNS:localhost, DNS:DESKTOP-HT4HNOC`,
  valid 2026-10-09 → 2029-01-11) issued by `[1] CN=Spendwise LAN CA`.
- Issuer CA SHA-256 `E1:E0:BE:13…:67` — **byte-identical to the committed
  `spendwise_lan_ca.pem`** the app pins.
- Matrix (Python ssl, independent of curl/schannel):
  valid CA + correct SAN → TLS_OK; wrong hostname → REJECTED
  (hostname mismatch); untrusted CA → REJECTED (self-signed in chain);
  plain HTTP → TLS port → 400 rejection.
- Swagger surface disabled; `/actuator/health` 200 with `db:UP, ssl:UP`.

## 13. LAN exposure — PASS with one MINOR

- Backend socket bound to `192.168.1.236:8443` (NIC-specific, not 0.0.0.0).
- PostgreSQL `127.0.0.1:55432` only; Pixel→55432 **BLOCKED** (TCP timeout);
  Pixel→8443 connects.
- No HTTP debug listener currently running (8081 down; HTTPS mode only).
- AUD-N8 (MINOR): inbound reachability is provided by a broad
  Public-profile program rule (`Java(TM) Platform SE binary →
  C:\Program Files\Java\jdk-23\bin\java.exe`, any local port), not the
  runbook's scoped 8443/Private rule. Actual exposure bounded by the
  NIC-specific bind, but the rule is broader than documented intent —
  recommend replacing with the scoped rule (Human/admin action).
- AUD-N9 (NOTE): pre-existing non-Spendwise listeners exist
  (`node :443` on 0.0.0.0, `sub2api` loopback) — outside M21 scope,
  recorded for posture completeness.
- No public/tunnel/port-forward exposure found within verifiable scope.

## 14–15. PostgreSQL isolation + data preservation — PASS

- `docker port spendwise-m10-db 5432` → `127.0.0.1:55432`; netstat
  loopback-only; Pixel TCP blocked; backend `db:UP` over loopback.
- Named volume reused; Flyway V1–V13 all `success`; row inventory grew
  only by synthetic test data (users 7→14 across audit+delivery runs —
  consistent with preservation + additive test writes, zero loss).

## 16. Backup/restore — fresh independent exercise PASS

- `backup-spendwise-db.ps1` → `spendwise_m21-20261009-184715.dump`
  (28.9 KB, 67 TOC entries, in-container `pg_restore -l` verified).
- `restore-spendwise-db.ps1` → fresh `m21_audit_restore_check`
  (refused non-empty path; `--no-owner --no-privileges`).
- Data parity: users 14=14, accounts 15=15, transactions 44=44,
  transfer legs 8=8, flyway 13=13, **0 FK orphans**, `sum(amount)
  3454160.68` identical, 4 Unicode-description rows identical.
- Disposable audit DB dropped after verification; no real DB touched;
  no backup deleted.

## 17. Real Pixel release E2E — PASS (independently executed)

- Signed release installed (`Success`), pulled back, byte-identical
  hash — installed package IS the audited artifact.
- Auditor-executed release-UI write: created account `audx19` (VND,
  Cash) via the release UI → verified row in PostgreSQL for
  `m21qa-8f4g2x`; account detail rendered server-side values.
- Fresh HTTPS auth probes: register 201, login 200, isolated account list.
- Client-side validation fails closed (empty amount refused, red banner).
- Session persistence across relaunch verified; sign-out → welcome →
  sign-in cycle verified in delivery and re-observed.
- PHYSICAL_PIXEL=YES (Pixel 4 XL, API 33) · REAL_BACKEND=YES ·
  REAL_POSTGRES=YES · MOCK_SERVER=NO · ADB_REVERSE=NO.
- Note: synthetic `adb input` to the Compose currency field was flaky;
  financial writes were independently verified server-side (DB rows),
  which is the stronger evidence anyway.

## 18. J1–J14 live matrix — fresh run over HTTPS, PASS

Executed by auditor on final source against the HTTPS deployment
(debug-instrumented build pointed at `https://192.168.1.236:8443/`,
`spendwise.liveBackend=1`; tests run in-app under the generated debug NSC
which trusts the pinned LAN CA):

```
tests=239  failures=0  errors=0  skipped=0   (2026-10-09T12:02:56)
```

All 21 live tests executed with ZERO skips — including J1–J14 and
x01–x04 — over real TLS. Fresh rows observed in PostgreSQL after the run
(transactions 44→56; new synthetic users). Live suite verified against the
real authenticated-HTTPS wire path, which is stronger evidence than the
delivery's HTTP:8081 runs.

## 19. Financial-integrity regressions — no regression found

- Backend production code is unchanged since M20-acceptance (HEAD
  `4ced814`); fresh full suite 458/458 on the same code + the fresh
  HTTPS live matrix exercises J5/J6 balances, J7 paging, J8–J10 import
  dedup, J11 dashboard totals, J12 recurring, J13 budget calc,
  J14 offsetting correction, x01 transfer legs, x02 isolation,
  x03 Unicode, x04 export — all green.
- M20 append-only offset verified in source (live source-account
  re-fetch, archived-source refusal, no silent substitute, prefill-once)
  and at runtime (original EXPENSE row unmodified beside INCOME offset).
- M13 no-replay verified in source (`retryOnConnectionFailure=false`,
  mutations never auto-replayed, submit lock, generation-discarded
  renewal) — unchanged by M21.

## 20. Session adversarial checks — PASS (live)

Fresh probes over real HTTPS: cross-user account read → **404**;
cross-user transaction list → **404**; forged write into a foreign
account → **404**; garbage bearer → **401**; own resources → 200.
Refresh rotation + generation-discard verified in code + J3 executed
live. No cross-session token path found: refresh client is bare
(no interceptors/logging), renewal is generation-checked.

## 21. Test-count reconciliation — RESOLVED

- `239` = true suite size (all discovered tests; authoritative XML).
- `222` = received-result count of the PRE-FIX run: the class-level
  assumption collapsed 18 journey tests into ~1 synthetic result
  (239 − 17 = 222). That was the defect Sentinel logged as S8.
- Post-fix runs produce all 239 results; hermetic runs skip exactly the
  21 live-opt-in tests (3 smoke + 18 journeys) — zero unexpected skips.
- The delivery's final report mis-transcribed `222/201/0/21`; the real
  final numbers are `239/218-pass/0-fail/21-skip` (AUD-N7).
- UTP's "Finished 260 tests" progress line = 239 cases + 21 assumption
  events — event counting, not a count discrepancy.
- MISSING_MANDATORY_TESTS=0 · UNEXPECTED_SKIPS=0.

## 22–23. Fresh regressions (auditor-executed, final source)

- Backend: `./mvnw test` → **458 tests, 0 failures, 0 errors, 0 skips**
  (real loopback PostgreSQL; Hibernate exercised live).
- Android JVM: `testDebugUnitTest --rerun-tasks` → **755, 0/0/0**.
- `lintDebug`, `detekt`, `assembleDebug`, `compileDebugAndroidTestKotlin`,
  `assembleRelease`, `lintVitalRelease`, `minifyReleaseWithR8` — all
  `BUILD SUCCESSFUL`. Serializer keeps verified in `proguard-rules.pro`
  + proven empirically (release E2E deserializes real JSON).

## 24. Physical Pixel twice — PASS (fresh, final source)

| Run | tests | pass | fail | skip | note |
|---|---|---|---|---|---|
| hermetic #1 | 239 | 218 | 0 | 21 | expected live-gated skips |
| hermetic #2 | 239 | 218 | 0 | 21 | identical |
| live-enabled | 239 | 239 | 0 | **0** | full matrix over HTTPS |

Pixel 4 XL, API 33. Debug installs signed with debug key; the signed
release was uninstalled for instrumentation runs and **reinstalled
afterward** (byte-identical hash re-verified on-device).

## 25–26. Installation & key safety

The existing signed package was removed only to permit debug-variant
instrumentation (same applicationId, different signature — unavoidable)
and the Human-signed APK was reinstalled afterward; its hash re-verified
on-device. No keystore/password/private key was touched, printed, or
moved. No permanent key was replaced or recreated.

## 27. Remaining Minors/Notes (nonblocking)

- MINOR AUD-N2 — TLS env-var name mismatch between
  `generate-lan-tls.sh` (`SPENDWISE_TLS_STORE_PASSWORD`) and
  `start-backend.ps1`/env template (`SPENDWISE_TLS_KEYSTORE_PASSWORD`).
- MINOR AUD-N7 — final delivery report mis-transcribed the connected
  suite count (`222/201` vs authoritative `239/218`).
- MINOR AUD-N8 — backend inbound allowed by broad Public-profile
  program rule, not the scoped runbook rule (NIC bind bounds exposure).
- NOTES — AUD-N1 (daemon env retention window), AUD-N3 (trailing-dot
  placeholder edge), AUD-N4 (constant-vs-request URL check), AUD-N5
  (unsigned-release path), AUD-N6 (ops-script param interpolation),
  AUD-N9 (pre-existing foreign listeners), AUD-N10 (adb input flake).

## 28. Preservation

- Backend Human-owned files untouched (`README.md` modified-state +
  `HUONG-DAN-SU-DUNG.md`, `docs/SPENDWISE-REQ.md`, `BAO-CAO-DU-AN.md`
  untracked — pre-existing Human worktree state, not modified by audit).
- Read-only reference repos untouched; forbidden fixture dir not accessed.
- No push, no tag, no reset/clean/stash/amend. Synthetic data only.

## 29–30. Verdict

```
INITIAL_VERDICT=FINDINGS (0 BLOCKER / 0 MAJOR at initial inspection)
INITIAL_BLOCKERS=0 INITIAL_MAJORS=0 INITIAL_MINORS=2 INITIAL_NOTES=5
FINAL_BLOCKERS=0 FINAL_MAJORS=0 FINAL_MINORS=3 FINAL_NOTES=5
REMEDIATION_COMMITS=NONE (no Blocker/Major required remediation)
FINAL_REVIEW_AFTER_REMEDIATION=NOT_REQUIRED

M21_EXTERNAL_AUDIT_FINAL=ACCEPTED
M21_READY_FOR_HUMAN_FINAL_SIGNOFF=YES
M21_HUMAN_APPROVED=NO        — Human decision, not delegated
SPENDWISE_PERSONAL_V1_COMPLETE=NO — awaits Human signoff
```

Recommendation: the M21 LAN release + deployment hardening is defensible
for Personal V1 — signed Human-owned release, HTTPS-only release path with
verified private-CA trust + hostname checking, loopback-only PostgreSQL,
independently re-verified backup/restore, and a 239-test physical-device
suite (plus a zero-skip live journey matrix over real HTTPS) all green.
Three minors are documentation/hardening polish that do not block signoff
but should be queued as post-V1 hygiene.

NEXT_ACTION=HUMAN_FINAL_PERSONAL_V1_SIGNOFF
