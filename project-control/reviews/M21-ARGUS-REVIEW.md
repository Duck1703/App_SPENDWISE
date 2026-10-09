# M21 Argus Review — Personal V1 LAN Release & Deployment Hardening

```
REVIEW_ROLE=ARGUS (Independent QA Reviewer)
REVIEW_MODE=post-implementation diff review + live verification evidence
VERDICT=CHANGES_REQUIRED→PARTIAL (one delivered defect found & fixed in
        review; one environmental BLOCKER remains a Human gate)
```

## Findings

| ID | Sev | Finding | Disposition |
|---|---|---|---|
| M21-A1 | **MAJOR** | `ApiBaseUrl.validate` ran eagerly at `Application.onCreate` via `@ApiBaseUrl` provision → **every** build without a configured URL crashed at launch, including the entire hermetic instrumented suite (`connectedDebugAndroidTest` died in `SpendwiseApplication.onCreate`). Fail-fast at graph construction was the wrong boundary. | **FIXED** — validation moved to the wire boundary: release `Retrofit` construction validates eagerly; debug constructs from any syntactically valid URL; an OkHttp interceptor + `SessionRefresher.refreshUrl` re-validate before dialing and surface rejection as `IOException` (transport failure callers already handle — an unchecked throw inside the async path crashed the process on first iteration of this fix). Verified: connected suite 224/224 (3 assume-skips) on Pixel. |
| M21-A2 | **BLOCKER** | Device↔host L2 isolation: PC SSID `FPT Telecom-7A47`, Pixel SSID `Sky House II - T3A`; same /24 but ARP INCOMPLETE both directions and PC cannot reach a third LAN client. Per-SSID/client isolation at the AP — below the Windows firewall; not fixable in code. | **HUMAN GATE** — join Pixel to `FPT Telecom-7A47` or disable AP isolation; then re-verify ARP + ports 8081/8443 |
| M21-A3 | MINOR | Release APK is **unsigned** unless the `SPENDWISE_RELEASE_*` env set is complete — an unsigned APK cannot be installed at all, so the "genuinely installable" gate still needs the Human keystore. Partial env correctly fails configuration (verified). | ACCEPTED — matches Q4 Human-owned signing; runbook documents |
| M21-A4 | MINOR | PowerShell scripts initially contained UTF-8 punctuation (em/en dashes); PS 5.1 reads ANSI → smart-quote bytes terminated strings early → parse failure. | **FIXED** — all script files normalized to ASCII |
| M21-A5 | MINOR | First `restore-spendwise-db.ps1` run swallowed `CREATE DATABASE` failure (role lacked CREATEDB) and reported success text before pg_restore failed. | **FIXED** — admin user creates DB with `OWNER $DbUser`; exit code checked |
| M21-A6 | NOTE | `SessionRefresherTest.a session boundary mid-flight discards the renewed credentials` observed failing once (40-test filtered run) then green on rerun — real-thread + `takeRequest` race where the in-flight apply can land before `signOut` bumps the generation. Semantics under test are correct; timing-sensitive. | WATCH — pre-existing race window, not introduced by M21; flagged for external auditor |
| M21-A7 | NOTE | `spendwise-m10-db` publishes `0.0.0.0:55432` — Postgres reachable from the LAN when L2 isolation is lifted. App never needs it, but the DB is broader-exposed than necessary. | HUMAN ACTION — republish `127.0.0.1:55432:5432` (their container); runbook documents |
| M21-A8 | NOTE | Generated NSC correctly trusts `@raw/spendwise_lan_ca` (public CA cert committed; private key outside repo). Trust is scoped to the configured host only — a mispointed release URL fails TLS rather than silently trusting. | VERIFIED correct |
| M21-A9 | NOTE | Instrumentation tests execute in the app process → the app's per-variant NSC governs `LiveBackendSmokeTest`'s OkHttp calls. No separate test-manifest override needed. | VERIFIED by reasoning + crash trace (app NSC was applied) |
| M21-A10 | NOTE | `check(!releaseSigningPartial)` runs at configuration time for **all** build types — a partial signing env breaks even `assembleDebug`. Deliberate (partial config is a defect, never silently unsigned) but worth noting for CI. | ACCEPTED |

## Verified-correct items

- Two-layer URL policy: Gradle task fails release on placeholder/`http:`;
  runtime validator fails before any wire byte; 10 unit tests pin the
  matrix (placeholder, credentials, non-root path, public-HTTP,
  malformed, RFC-1918 boundaries).
- Merged manifests: base `usesCleartextTraffic="false"` intact; per-variant
  generated NSC; release config shows `cleartextTrafficPermitted="false"`
  with scoped trust anchors.
- No secrets committed: only `spendwise_lan_ca.pem` (public cert) under
  `res/raw`; `*-key.pem`, `*.keystore`, `*.env` gitignored; env example
  carries placeholders only.
- Backend untouched (`D:\spendwise` read-only respected): LAN bind, TLS,
  and DB URL all arrive via env — relaxed binding, zero code diff.
- Backup/restore proven at data level (counts, IDs, transfer_ref pairing,
  sums, Unicode, flyway V1–V13) — not merely exit-code success.
- `retryOnConnectionFailure(false)` still intact — financial POSTs are
  never auto-replayed.
- Refresh path remains bare-client, no interceptors/logging — tokens
  cannot reach log lines; `LiveBackendSmokeTest` never prints tokens.

---

## FOLLOW-UP 2026-10-09 (independent re-check post-unblock)

- **Level A re-verified on wire**: Pixel `192.168.1.30` → PC
  `192.168.1.236:8081` real HTTP 200; smoke 3/3; journeys 18/18 — no
  mock, no adb reverse, synthetic identities only.
- **Level B verified on-device**: release APK (v2 signature) trusted
  the LAN CA via generated release NSC; login + account-create +
  authoritative server read-back over `https://192.168.1.236:8443/`.
  OpenSSL matrix: valid chain+IP SAN accepted; absent CA and wrong
  hostname both rejected. Swagger surface disabled via env.
- **New finding → fixed in-session**: LiveJourneyTest class-assumption
  suite-integrity defect (M21-S8) — corrected and proven by two
  consecutive full connected runs, 0 failures each.
- **Still open (Human-owned, honestly reported)**:
  - `POSTGRES_LAN_EXPOSED` — container still publishes `0.0.0.0:55432`;
    confirmed reachable from the Pixel. Loopback republish or a firewall
    deny needs Human authorization; documented in handoff.
  - Production signing is env-wired and proven with an ephemeral key —
    `SIGNED_WITH_HUMAN_KEY` remains NO until the Human supplies their
    keystore. No debug-key release was substituted.
- No secrets in repo; private CA key/keystore/env remain under
  `~/.spendwise-lan-tls/` (outside Git). Backend working tree untouched.
