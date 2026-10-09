# M21 External-Audit Handoff — Personal V1 LAN Release & Deployment Hardening

```
HANDOFF_STATUS=PREPARED_WITH_OPEN_HUMAN_GATE
M21_READY_FOR_EXTERNAL_AUDIT=NO   ← L2 network isolation blocks Level A/B
AUDITOR_MAY_START_ON=code diffs, scripts, runbook, reviews, baseline
                     results, backup/restore evidence (all LAN-independent)
AUDITOR_MUST_WAIT_FOR=device-dependent evidence (live smoke, J1–J14,
                      on-device HTTPS, installable-release launch)
```

## Heads under audit

| Repo | HEAD | Note |
|---|---|---|
| `D:\app_spendwise\spendwise-android` | see `git log -1` at audit time (M21 delivery commit atop `53f180f`) | writable |
| `D:\spendwise` | `4ced814c3e45068b1c7dfc115c3ddcf59ecb7a34` | **read-only, unmodified** — all LAN/TLS config via env |
| `D:\app_spendwise\kotlin-accelerator-ai` | `5f9622c59ce602ce33c98ee633ba799f903b67e1` | untouched |

## What M21 delivered

1. **Fail-closed API URL policy** — two layers:
   - Build-time (`app/build.gradle.kts`): `assembleRelease` fails on
     placeholder (`example.invalid`), `http:`, or missing host; a partial
     `SPENDWISE_RELEASE_*` signing env set fails *configuration* for all
     build types.
   - Runtime (`ApiBaseUrl.validate`): enforced at the wire boundary —
     release `Retrofit` construction eagerly, plus an OkHttp interceptor
     and `SessionRefresher` refresh URL before any byte is sent, surfaced
     as `IOException`. Pinned by `ApiBaseUrlTest` (10 cases, in the
     755/755 suite).
2. **Generated per-variant Network Security Config**
   (`GenerateNetworkSecurityConfigTask`): no committed IPs — scoped to
   whatever host the build URL configures. Debug permits cleartext to
   that host only; release forbids cleartext and trusts system CAs +
   `@raw/spendwise_lan_ca` (public cert committed; **private key in
   `~/.spendwise-lan-tls/`, never in repo**). Base manifest keeps
   `usesCleartextTraffic="false"`.
3. **Human-owned release signing wiring**: `SPENDWISE_RELEASE_KEYSTORE_PATH/
   KEY_ALIAS/STORE_PASSWORD/KEY_PASSWORD` env → `spendwiseRelease`
   signingConfig; absent → unsigned APK (Android refuses install);
   partial → configuration failure.
4. **LAN ops scripts** (`scripts/`): `start-backend.ps1` (env-file driven,
   HTTP :8081 or HTTPS :8443 single connector), `generate-lan-tls.sh`
   (private CA + SAN'd server cert + PKCS12, all outside repo),
   `backup-spendwise-db.ps1` / `restore-spendwise-db.ps1` (custom-format
   dumps, TOC-verified, non-empty-target refusal, `--no-owner
   --no-privileges`).
5. **Runbook**: `project-control/runbooks/M21-LAN-DEPLOYMENT.md`.
6. **Live-smoke instrumentation**: `LiveBackendSmokeTest` — assume-gated
   (`-e spendwise.liveBackend 1`), synthetic credentials only, real wire
   path; inert in the default suite.

## Verified evidence (LAN-independent)

- **Baselines**: backend 458/458; Android JVM 755/755; lint PASS; detekt
  0 smells; `assembleRelease`+R8+lintVital PASS; androidTest compile clean.
- **Connected suite on Pixel 4 XL (API 33)**: 224 tests, **0 failures** —
  includes the 3 live-smoke assume-skips.
- **Host-local live API** (native backend on `192.168.1.236:8081`,
  isolated `spendwise_m21` DB): health UP/db UP; unauth 401; register/
  login/account/expense/income/transfer-pair/rule/budget/recurring all
  201; server-computed balances exact (main 4905.25, cash 350.00);
  Unicode description persisted.
- **Backup→restore**: `spendwise_m21-20261009-150518.dump` (23.2 KB, TOC
  verified) → fresh `spendwise_m21_restore` — row parity on all 10
  tables, transfer pair linked via `transfer_ref`, 0 FK orphans, sums
  exact, flyway V1–V13 success, Unicode byte-exact.

## OPEN HUMAN GATE — must be cleared before device evidence exists

**PC is on SSID `FPT Telecom-7A47`; the Pixel is on `Sky House II - T3A`.**
Same `192.168.1.0/24` but the AP isolates clients across SSIDs: Pixel ARP
for `192.168.1.236` = `INCOMPLETE`; PC→Pixel ping also fails; PC cannot
reach a third LAN client. Layer-2 — no host firewall or code change can
fix it.

**Human action (choose one):**
1. Connect the Pixel to `FPT Telecom-7A47` (the PC's SSID) — preferred;
   zero config changes.
2. Or disable client/AP isolation in router admin for `Sky House II - T3A`.

**Then (elevated PowerShell on the PC, one time):**
```powershell
Set-NetConnectionProfile -InterfaceAlias "Wi-Fi" -NetworkCategory Private
New-NetFirewallRule -DisplayName "Spendwise Backend HTTP (LAN dev)" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8081 -Profile Private -RemoteAddress 192.168.1.0/24
New-NetFirewallRule -DisplayName "Spendwise Backend HTTPS (LAN)" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8443 -Profile Private -RemoteAddress 192.168.1.0/24
```

**Post-unblock verification** (runbook §Troubleshooting):
`ping 192.168.1.236` from Pixel → ARP entry REACHABLE →
`adb shell toybox nc -z 192.168.1.236 8081` → then `./gradlew
connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.spendwise.liveBackend=1
-Pspendwise.api.base.url=http://192.168.1.236:8081/`.

## Second-order items the auditor should check

- Postgres `0.0.0.0:55432` publish (pre-existing; runbook documents
  republishing `127.0.0.1:55432:5432` — Human's container, their call).
- Signed-release install: needs the Human keystore + HTTPS listener
  (`generate-lan-tls.sh` output merged into `spendwise-lan.env`).
- `SessionRefresherTest` mid-flight boundary test flaked once — timing
  race documented as M21-S7/M21-A6; watch-listed, not a regression.
- Swagger/OpenAPI exposure: backend `application.properties` exposes
  `health,metrics` actuator only; confirm no swagger-ui on the LAN path.

## Explicit non-claims

- No live device-to-backend traffic has been observed yet (L2 blocked).
- J1–J14 on the physical device is **not** evidenced — do not accept it
  as done.
- `M21_READY_FOR_EXTERNAL_AUDIT=YES` is NOT declared. The milestone stays
  `IN_DELIVERY` until Level A + Level B evidence exist post-unblock.
