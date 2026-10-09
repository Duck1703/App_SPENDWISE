# M21 — Personal V1 LAN Release & Deployment Hardening

```
STATUS: IN_DELIVERY
M20_COMPLETE=YES
M21_DEFINED=YES
M21_STARTED=YES
M21_HUMAN_AUTHORIZATION=EXPLICIT (M21 delivery block — LAN release hardening)
M21_HUMAN_APPROVED=NO
M21_EXTERNAL_AUDIT=NOT_RUN
M22_DEFINED=NO
M22_STARTED=NO
BASELINE: Android 49f1960 (docs-only descendant of M20-accepted 20fbe6b)
          Backend 4ced814 (M20-accepted; production unchanged since M19)
DEPLOYMENT_DECISION=LOCAL WINDOWS PC / PRIVATE LAN (Human-explicit)
CANONICAL_SCOPE=POST-M20 audit P0-1 connectivity + P1-1 signing +
  P1-2 backup/restore + P1-3 live E2E + P1-4 runbook
SPENDWISE_STATUS=M21_IN_DELIVERY
NEXT_ACTION=DELIVERY → INTERNAL REVIEWS → INDEPENDENT EXTERNAL AUDIT
```

## Scope

The Post-M20 gap audit (`reviews/POST-M20-PRODUCT-COMPLETION-GAP-AUDIT.md`)
proved every feature lane is delivered or gated, and the only thing standing
between "fully tested" and "usable personal V1" is release/deployment
readiness. M21 closes exactly that — and nothing else.

**Two completion levels (must not be blurred):**

- **LEVEL A — private-LAN development connectivity.** Prove the physical
  Pixel talks to the real Spring Boot backend + real PostgreSQL over the
  LAN. Debug build may use narrowly scoped HTTP for synthetic data only.
  LEVEL A PASS ≠ LEVEL B PASS.
- **LEVEL B — Personal V1 release readiness.** Installable release that
  connects over authenticated HTTPS with hostname verification, Human-owned
  signing, real backup/restore, real end-to-end smoke, full regression.

## Non-goals (explicit)

No BE-005, BE-008, BE-009, rules-edit, profile, category CRUD, FX, account
hard-delete, password recovery, M22 anything. No VPS, no public hosting,
no tunnels, no router changes, no public exposure.

## Discovered topology (evidence, not assumptions)

| Fact | Value |
|---|---|
| Windows host | `DESKTOP-HT4HNOC`, Wi-Fi `192.168.1.236/24` (MediaTek MT7921) |
| Network profile | **Public** — inbound firewall rules need admin; profile must move to Private OR rules must be Public-scoped (Human gate) |
| Pixel 4 XL | `192.168.1.63/24`, SSID `Sky House II - T3A` — same subnet, **different SSID from PC** |
| PC SSID | `FPT Telecom-7A47` (BSSID `02:fb:c2:e4:7a:47`) — **NOT the same SSID as the Pixel** |
| L2 connectivity | **BLOCKED**: Pixel ARP shows `192.168.1.236 INCOMPLETE`; PC→Pixel ping also times out; PC cannot reach a third client either. Same-subnet but cross-SSID → per-SSID/client isolation at the AP. Below the firewall — Human network action required (same-SSID join or disable AP isolation) |
| Port 8080 | OCCUPIED by `sub2api` (Docker) → backend uses **8081 (HTTP debug)** and **8443 (HTTPS release)** |
| Port 443 | occupied (Docker) |
| PostgreSQL | `spendwise-m10-db` container (postgres:16-alpine), published `0.0.0.0:55432→5432` — **already LAN-reachable; flagged as pre-existing exposure** |
| Java | 23.0.2 on PATH — native `java -jar` is the supported runtime |
| Backend image | `spendwise-app:latest` exists (compose path remains documented alternative) |
| Shell elevation | NOT elevated — firewall/profile ops are Human-run commands |

## Architecture

```
Pixel 4 XL ──Wi-Fi──> 192.168.1.236:8443 (HTTPS, private-CA cert, app-scoped trust)
                      java -jar spendwise (SERVER_ADDRESS=192.168.1.236)
                              │ JDBC loopback
                      spendwise-m10-db :55432 (container, unchanged)
```

Debug alternative (Level A): `:8081` HTTP to the same host — cleartext
permitted ONLY for the configured API host, debug variant only, via a
**generated** Network Security Config (no committed IP addresses).

## Key design decisions

1. **Zero backend file changes.** `SERVER_ADDRESS`, `SERVER_PORT`,
   `SERVER_SSL_*` all map via Spring relaxed binding — LAN service is a
   run command + env, not a code diff.
2. **Generated NSC** — `build.gradle.kts` task renders
   `network_security_config.xml` per variant from the configured API host:
   debug → cleartext allowed to exactly that host; release → cleartext
   forbidden, trust = system CAs + the pinned Spendwise-LAN private CA
   (public cert committed as `@raw`; **private key never in repo**).
3. **Base-URL validation at two layers**: Gradle fails release builds on
   placeholder/`http:`/`example.invalid`; a runtime validator
   (`ApiBaseUrl`) fails fast on malformed/non-https release URLs —
   belt-and-suspenders, unit-tested.
4. **Private CA** in `~/.spendwise-lan-tls/` (Human-controlled machine →
   Human-controlled key); server cert SAN = `IP:192.168.1.236`. IP change
   = cert regeneration (documented; static-DHCP recommendation included).
5. **Human gates (pause points):** Wi-Fi profile→Private + scoped inbound
   firewall rule (admin); optional keystore creation for signed release
   (wiring + exact instructions provided regardless).

## Gates

- Baselines: backend 458/458 PASS, Android JVM **755/755 PASS**
  (745 + 10 new `ApiBaseUrlTest`), lint PASS, detekt PASS (0 smells),
  release+R8 PASS, androidTest compile clean.
- New tests: URL validator JVM suite; live-smoke instrumentation test
  (Assume-gated on `spendwise.liveBackend=1`, never runs in the default
  suite — suite stays hermetic).
- Physical Pixel: `connectedDebugAndroidTest` on Pixel 4 XL (API 33) —
  **224 tests, 0 failures** (3 live-smoke cases correctly assume-skipped).
  Live smoke itself BLOCKED on L2 connectivity — see table.
- Backup/restore: **VERIFIED** — `pg_dump -Fc` → outside-repo archive
  (23.2 KB, 67 TOC entries, `pg_restore -l` readable) → restore into
  isolated `spendwise_m21_restore` (fresh DB, `--no-owner
  --no-privileges`) → data-level parity: users 1/1, accounts 2/2,
  transactions 7/7, transfer legs 2/2 linked via `transfer_ref`, budgets
  1/1, rules 1/1, recurring 1/1, flyway 13/13 (V1–V13 success), zero FK
  orphans, sums exact, Unicode description byte-exact.
- Sentinel + Argus + independent external audit handoff.

## Live backend verification so far (host-local)

Backend ran natively on `192.168.1.236:8081` against isolated
`spendwise_m21` DB: health UP (db=PostgreSQL UP), unauth API → 401,
register/login/account/expense/income/transfer/rule/budget/recurring all
201 through the real API; server-side balances correct (5000 − 37.50
expenses − 250 transfer + 200 income − 7.25 = 4905.25 on main; 350.00
cash). Pixel-side execution awaits the L2 unblock above.
