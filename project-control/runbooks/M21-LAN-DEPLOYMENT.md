# M21 — LAN Deployment Runbook (Personal V1)

Operator runbook for running Spendwise on a private LAN: a Windows PC hosts
the Spring Boot backend + PostgreSQL; a physical Android phone connects over
Wi-Fi. No internet exposure, no VPS, no tunnels.

## Topology (as deployed)

```
Pixel 4 XL (192.168.1.63) ──Wi-Fi──> 192.168.1.236
                                      ├── :8081 HTTP  (debug builds, synthetic data only)
                                      └── :8443 HTTPS (release builds — private-CA cert)
                                          java -jar spendwise-0.1.0-SNAPSHOT.jar
                                              SERVER_ADDRESS=192.168.1.236
                                              │ JDBC → localhost:55432
                                          docker: spendwise-m10-db (postgres:16-alpine)
```

- Phone and PC must be on the same Wi-Fi SSID — and "same subnet" is NOT
  enough. Observed on this network: PC on `FPT Telecom-7A47`, Pixel on
  `Sky House II - T3A`, both `192.168.1.0/24`, yet traffic is dead in BOTH
  directions (Pixel ARP shows `192.168.1.236 INCOMPLETE` — the AP drops
  client-to-client frames at L2). This is per-SSID/client isolation, below
  the Windows firewall; no host-side change fixes it.
  **Fix: put both devices on the same SSID** (Pixel → `FPT Telecom-7A47`),
  or disable AP/client isolation in router admin. Verify before continuing:
  `ping <PC-IP>` from the phone and an ARP entry of type REACHABLE.
- `192.168.1.236` is THIS machine's address — find yours with `ipconfig`
  under "Wireless LAN adapter Wi-Fi → IPv4 Address". If DHCP assigns a
  different IP later: rebuild the app URL, regenerate the TLS cert, and
  update the env file. A router DHCP reservation is recommended (router
  admin — Human task, not scripted here).

## One-time setup

### 1. Firewall + network profile (ADMIN — Human runs once)

The Wi-Fi network profile is `Public`, which silently drops inbound
connections. In an **elevated** PowerShell:

```powershell
# Mark the home Wi-Fi trusted-Private (your call — do this only on a LAN
# you control):
Set-NetConnectionProfile -InterfaceAlias "Wi-Fi" -NetworkCategory Private

# Narrow inbound rules — Private profile only, TCP, app ports only.
New-NetFirewallRule -DisplayName "Spendwise Backend HTTP (LAN dev)" `
  -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8081 `
  -Profile Private -RemoteAddress 192.168.1.0/24

New-NetFirewallRule -DisplayName "Spendwise Backend HTTPS (LAN)" `
  -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8443 `
  -Profile Private -RemoteAddress 192.168.1.0/24
```

Rollback: `Remove-NetFirewallRule -DisplayName "Spendwise Backend HTTP (LAN dev)"`
(and the HTTPS variant), then `Set-NetConnectionProfile -InterfaceAlias
"Wi-Fi" -NetworkCategory Public` to restore the stricter posture.

PostgreSQL port 55432 stays unopened by design — the app never talks to the
DB directly. (Pre-existing note: the `spendwise-m10-db` container publishes
`0.0.0.0:55432`; if you want it loopback-only, republish as
`127.0.0.1:55432:5432` — a Human change to their own container.)

### 2. Secrets env file

```powershell
cd D:\app_spendwise\spendwise-android\scripts\lan
copy spendwise-lan.env.example spendwise-lan.env
# edit spendwise-lan.env: LAN host, DB name/user/password, JWT secret
# (generate: openssl rand -base64 48), TLS keystore path+password.
```

`spendwise-lan.env` is gitignored. Never commit it.

### 3. TLS material (Level B, once)

```bash
# Git Bash on the backend PC:
bash scripts/lan/generate-lan-tls.sh 192.168.1.236
cp ~/.spendwise-lan-tls/ca-cert.pem \
   D:/app_spendwise/spendwise-android/app/src/main/res/raw/spendwise_lan_ca.pem
# merge lan-tls.env values into spendwise-lan.env
```

## Daily operation

```powershell
# 1. DB (already runs via Docker Desktop): confirm
docker ps --filter name=spendwise-m10-db

# 2. Build once (or after backend updates)
cd D:\spendwise; ./mvnw package -DskipTests

# 3. Start the service (TLS on/off is driven by SPENDWISE_TLS_KEYSTORE
#    being set or unset in spendwise-lan.env - one connector per launch)
cd D:\app_spendwise\spendwise-android\scripts\lan
.\start-backend.ps1    # env file decides HTTP :8081 or HTTPS :8443

# 4. Verify from the PC
curl http://192.168.1.236:8081/actuator/health     # HTTP mode
curl --cacert ~/.spendwise-lan-tls/ca-cert.pem `
     https://192.168.1.236:8443/actuator/health    # HTTPS mode (real verify)

# 5. Phone: install + run
cd D:\app_spendwise\spendwise-android
./gradlew installDebug -Pspendwise.api.base.url=http://192.168.1.236:8081/
./gradlew installRelease -Pspendwise.api.base.url=https://192.168.1.236:8443/
```

## Stop / restart

Ctrl+C the service window (or close it). Refresh tokens are process-local:
every restart logs all users out — expected fail-closed behavior; just sign
in again. Data survives in Postgres.

## Backup & restore

```powershell
# Backup (archives land in D:\spendwise-backups — outside all repos):
scripts\db\backup-spendwise-db.ps1 -DbName spendwise_m21

# Verify-restore into a THROWAWAY database (never into the live one):
scripts\db\restore-spendwise-db.ps1 `
  -DumpFile D:\spendwise-backups\<file>.dump -TargetDb spendwise_restore_check
```

Recommended personal cadence: backup after any day you enter data, and
always before a backend upgrade. Keep ~7 rotating files; delete manually —
no auto-purge is wired.

**Real recovery** (documented, Human-run): stop the backend → create a fresh
DB → `restore-spendwise-db.ps1` into it → point `SPENDWISE_DB_URL` at the new
DB → start the backend → log in and eyeball balances → only then retire the
old DB. Never `--clean` a live database.

## Troubleshooting

| Symptom | Check |
|---|---|
| App shows "No connection" | PC on? `docker ps` → DB up? Backend console running? Same Wi-Fi SSID on phone? |
| `curl` from PC fails | backend bound to `192.168.1.236`? (`netstat -ano | findstr 8081`) |
| PC `curl` OK, phone fails | SAME SSID on both? (subnet match is not enough — observed L2 isolation between `FPT Telecom-7A47` and `Sky House II - T3A`). Then: firewall rule created? profile still Public? |
| Phone ARP shows PC `INCOMPLETE` | L2 isolation — the AP blocks client-to-client. Same-SSID join or disable AP isolation; NOT a firewall problem |
| HTTPS handshake error on phone | cert regenerated after IP change? app built after `ca-cert.pem` copied? |
| App worked, now fails after reboot | DHCP gave the PC a new IP — re-derive `ipconfig`, regenerate cert + rebuild app, or set router reservation |

## What this runbook deliberately does NOT do

- No auto-start service registration (optional Human decision).
- No scheduled backup task (cadence is yours; scripts are idempotent).
- No router/DHCP changes, no public exposure, no cloud anything.
