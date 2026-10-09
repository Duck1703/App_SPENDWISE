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

---

# FOLLOW-UP — LAN recovery & Level B evidence (2026-10-09)

## L2 recovery — RESOLVED

The Human moved the Pixel onto the PC's SSID. Re-verified topology:

- PC `FPT Telecom-7A47` (BSSID `02:fb:c2:e4:7a:47`) — `192.168.1.236`
- Pixel 4 XL `FPT Telecom-7A47` (same BSSID) — `192.168.1.30`
- Pixel ARP for `192.168.1.236` → **REACHABLE** (was INCOMPLETE)
- Pixel→PC real HTTP `GET /actuator/health` → **200 UP** (direct Wi-Fi,
  no adb reverse, no mock)
- `REAL_DEVICE_TO_HOST_TCP=PASS`; L2 isolation resolved by the SSID
  change alone — no firewall rule was required for port 8081 inbound
  (existing Java allow covered it); runbook firewall steps remain the
  documented fallback.

## Level A — PASS (debug HTTP, synthetic only)

- `LiveBackendSmokeTest` **3/3 executed, 0 skipped** over
  `http://192.168.1.236:8081/`: health, unauth 401, full register→login→
  account→transaction→server-balance→logout path.
- `LiveJourneyTest` **18/18 executed, 0 skipped**: J1–J14 canonical
  journeys + transfer-linked legs, foreign-user 404 isolation, Unicode
  byte-exact round-trip, ledger CSV export — all against real backend +
  PostgreSQL (`spendwise_m21`). J3 proves real token refresh; J10 proves
  re-import dedup (`duplicateCount:3`); J11 proves server-side month math
  in two single-currency scopes (USD Oct, VND Sep); x02 proves foreign
  account/transaction reads return 404.
- Found+fixed during finalization (`6a5081d`): a `@BeforeClass`
  assumption collapsed the journey class into one synthetic "null"
  result and broke UTP's expected/received count (suite reported
  "expected 239, received 222" with 0 real failures). Each test now
  gates individually, matching `LiveBackendSmokeTest`.

## Level B — PASS (HTTPS, ephemeral verification key)

- Backend on `https://192.168.1.236:8443/` (LAN TLS from
  `~/.spendwise-lan-tls/`, PKCS12 keystore env-driven, no secrets in
  repo). SANs: IP `192.168.1.236`, IP `127.0.0.1`, DNS `localhost`,
  DNS `DESKTOP-HT4HNOC`.
- TLS matrix (openssl `s_client -verify_ip`): correct CA + IP SAN →
  verify 0; no CA → rejected (19); wrong host → rejected (62).
- Swagger UI / api-docs disabled via env (`500` where spec would serve).
- Release APK built `-Pspendwise.api.base.url=https://192.168.1.236:8443/`,
  v2-signed with an **ephemeral dev-verification key** stored outside
  the repo (`~/.spendwise-lan-tls/m21-dev-release.jks`) — explicitly
  NOT the Human-owned production identity.
- `apksigner verify`: v2 scheme = true; installed to Pixel; UI-driven
  E2E: Welcome → Sign-in → Home → create account `rellan` → server
  read-back `GET /api/accounts` shows `rellan` (CASH, VND, 0.0).
  Release NSC trusted the pinned LAN CA — **RELEASE_REAL_BACKEND_E2E=PASS**.
- Release debuggable=false (release manifest); cleartext disabled;
  placeholder/`http` URLs rejected at build gate + wire boundary.

## Fresh regression totals (final source `6a5081d`)

- Backend `mvn test` (with `.env.local` env): **458 run, 0 fail, 0 err**
  — earlier `310 errors` run was an env-only failure (missing
  `SPENDWISE_DB_PASSWORD`/`SPENDWISE_JWT_SECRET` in a bare shell), not
  a code regression.
- Android `testDebugUnitTest --rerun-tasks`: **755 run, 0 fail, 0 err**.
- `lintDebug` PASS; `detekt` PASS (233 files, 0 smells).
- `assembleRelease` + `lintVitalRelease` + R8 PASS (signed build above).
- Final connected runs on final source: **run#1 PASS** (239 expected,
  21 assume-skips, 0 failed), **run#2 PASS** (identical).

## Still open — Human-controlled prerequisites

1. **PostgreSQL `0.0.0.0:55432` LAN publish** — confirmed reachable
   from the Pixel. `POSTGRES_LAN_EXPOSED=YES` currently. Two safe
   options (either is sufficient; both need Human authorization):
   - Republish loopback-only: recreate container with
     `-p 127.0.0.1:55432:5432` (named volume preserves data —
     `e2058c38…` → `/var/lib/postgresql/data`); or
   - Elevated deny rule: `New-NetFirewallRule -Direction Inbound
     -Action Block -Protocol TCP -LocalPort 55432 -Profile Any`.
2. **Human-owned release signing identity** — env-driven wiring is
   proven with an ephemeral key; production Personal V1 needs the
   Human's keystore via `SPENDWISE_RELEASE_*` env vars.
3. **Elevated firewall rules** (optional hardening): the runbook's
   Private-profile 8081/8443 rules were not required for evidence but
   remain recommended for a stable Private-profile policy.

---

# FOLLOW-UP 2 — Postgres loopback republish (2026-10-09)

Human granted conditional authorization for exactly one change:
`0.0.0.0:55432:5432` → `127.0.0.1:55432:5432`.

## Pre-change safety gates — all PASS

- Container `spendwise-m10-db` (id `f4c94a5e…`), image
  `postgres:16-alpine` sha256:`cf78e766`, **manual** (no Compose labels),
  restart policy `no`, bridge network, no healthcheck, stock entrypoint.
- Data volume: named `e2058c3801f8…7682` (local driver) →
  `/var/lib/postgresql/data`, RW — **persistent, identified, same after**.
- Workloads: only Spendwise-family DBs (`postgres`, `spendwise`,
  `spendwise_m21`, `spendwise_m21_restore`) — no unrelated services.
- Backend DB clients all dialed `127.0.0.1:55432` — loopback republish
  cannot break them.
- Fresh backups pre-change, both TOC-verified via `pg_restore -l`:
  - `spendwise_m21-20261009-172730.dump` (26.7 KB, 67 entries)
  - `spendwise-20261009-172737.dump` (727.9 KB, 66 entries)
  - under `D:\app_spendwise\backups\m21\`, outside all Git repos.
- Pre-change baseline `spendwise_m21`: users=7, accounts=10,
  transactions=30, transfer_ref legs=6, flyway=13.

## Change applied

`docker stop` → `docker rm` (no -v) → `docker run` same name, same
image digest, **same named volume**, same POSTGRES_* env — only the
publish address changed. New container id `47df6f7f…`.

## Post-change verification — all PASS

- `PortBindings = {"5432/tcp":[{"HostIp":"127.0.0.1","HostPort":"55432"}]}`
  — actual running config, not Compose source.
- Host listeners: `127.0.0.1:55432 LISTENING`; `0.0.0.0`/`[::]` entries
  **gone**.
- Pixel→`192.168.1.236:55432` → **connection times out (BLOCKED)**.
- Pixel→`192.168.1.236:8443` → connects; backend health 200, db UP.
- Data parity after recreation: users=7, accounts=10, transactions=30,
  transfer legs=6, flyway=13 — **identical**, `DATA_PRESERVED=YES`.
- Post-change live matrix rerun over HTTP:8081: **21/21 executed**
  (3 smoke + 18 journeys), 0 fail, 0 skip — real Pixel→backend→
  republished Postgres E2E intact.
- `POSTGRES_LAN_EXPOSED` → **NO**. `M21_POSTGRES_LOOPBACK_BIND=PASS`.

## Signing identity — still the only open gate

`SIGNED_WITH_HUMAN_KEY=NO` remains. No permanent Human keystore exists
on this machine; `SPENDWISE_RELEASE_*` env vars are unset. Prepared for
the Human: `scripts/lan/create-release-keystore.ps1` — interactive
keytool prompts (no passwords on CLI/in files), refuses in-repo paths,
prints only public cert metadata + the env-var names to set.

## Updated status

- `M21_LAN_L2=PASS` · `M21_PIXEL_DIRECT_WIFI=PASS` ·
  `M21_REAL_BACKEND_SMOKE=PASS` · `M21_J1_J14_LIVE=PASS`
- `M21_HTTPS_RELEASE=PASS` · `M21_SIGNED_RELEASE=PASS` (ephemeral) ·
  `M21_SIGNED_RELEASE_INSTALL=PASS` · `M21_RELEASE_REAL_BACKEND_SMOKE=PASS`
- `M21_BACKUP_RESTORE=PASS` (preserved) · `BACKEND 458/458` ·
  `ANDROID_JVM 755/755` · `LINT/DETEKT/R8=PASS` · `PIXEL ×2=PASS`
- `M21_POSTGRES_LAN_EXPOSURE=NO` — **PASS** (Follow-up 2: loopback-only
  `127.0.0.1:55432`, Pixel→55432 blocked, data identical)
- `SIGNED_WITH_HUMAN_KEY=YES` — **FAILING** (ephemeral key used;
  Human keystore setup script prepared, awaiting Human action)
- `M21_READY_FOR_EXTERNAL_AUDIT=NO` — ONE Human prerequisite remains:
  the permanent signing identity. All engineering-side gates are green;
  the milestone is otherwise audit-ready.
