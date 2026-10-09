# M21 Sentinel Review — Personal V1 LAN Release & Deployment Hardening

```
REVIEW_ROLE=SENTINEL (Test Engineer)
SCOPE=M21 changes only (URL guards, generated NSC, signing wiring,
      LAN/TLS/backup scripts, live-smoke instrumentation test)
BASELINES=backend 458/458 PASS · Android JVM 755/755 PASS ·
          lint PASS · detekt PASS (0 smells) · androidTest compile clean ·
          assembleRelease/R8/lintVital PASS ·
          connectedDebugAndroidTest on Pixel 4 XL: 224 tests, 0 failures
          (3 LiveBackendSmokeTest cases correctly assume-skipped)
VERDICT=PASS_WITH_BLOCKED_ITEM (live on-device smoke cannot execute until
        the Human resolves L2 network isolation — see M21-S1)
```

## What was added and how it is proven

### `ApiBaseUrl` runtime validator + Gradle release gate

`app/src/test/.../ApiBaseUrlTest.kt` — 10 cases, all passing:

| Policy | Test evidence |
|---|---|
| HTTPS LAN accepted (release) | `https://192.168.1.236:8443/` → normalized OK |
| HTTPS hostname accepted (release) | `https://spendwise.lan/` OK |
| HTTP private-LAN accepted **debug only** | `http://192.168.1.236:8081/` debug=OK / release=reject |
| HTTP public host rejected even in debug | `http://203.0.113.10/`, `http://example.com/` rejected |
| `example.invalid` placeholder rejected everywhere | both build types reject |
| Blank / malformed / non-http(s) scheme rejected | `""`, `"   "`, `"not a url"`, `ftp://` all reject |
| Embedded credentials rejected | `https://user:pass@host/` rejects |
| Non-root path rejected | `https://host/api/` rejects (Retrofit would misresolve) |
| RFC-1918/loopback/link-local classification | boundary cases incl. `172.15.x` outside /12, `192.168.1.256` malformed octet |

Build-time layer additionally fails `assembleRelease` at task-execution
time on placeholder/`http:` URLs, and fails **configuration** on a
partial `SPENDWISE_RELEASE_*` env set — verified by inspection plus the
successful `assembleRelease` run with a real `https://` URL.

### `LiveBackendSmokeTest` (instrumentation)

- **Assume-gated**: skips unless `-e spendwise.liveBackend 1` AND the URL
  is not the placeholder → default connected suite stays hermetic.
- Exercises the real wire path from the device: health 200, unauth 401,
  register → login → account → transaction → read-back, server-side
  balance assertion (100.00 − 12.34 = 87.66 computed by the backend, not
  the client), logout revocation.
- Synthetic credentials per run; tokens never logged.
- **Not yet executed on-device** — blocked by L2 (finding M21-S1).

### Host-side live API verification (executed)

Performed against the natively running backend on `192.168.1.236:8081`
with the isolated `spendwise_m21` database — all through the real HTTP
API (no direct DB writes): register 201, login→JWT, account create 201,
3× EXPENSE + 1× INCOME + transfer pair + categorization rule + budget +
recurring all 201, Unicode description persisted exactly. Server
balances independently recomputed: 5000.00 − 37.50 − 250.00 + 200.00 −
7.25 = **4905.25** main / **350.00** cash — matches API response.

### Backup / restore verification (executed)

`backup-spendwise-db.ps1` → `spendwise_m21-20261009-150518.dump`
(23.2 KB, 67 catalog entries, `pg_restore -l` TOC verified inside the
container). `restore-spendwise-db.ps1` → fresh `spendwise_m21_restore`
(fresh CREATE DATABASE owned by the least-privileged role; refuses
non-empty targets; `--no-owner --no-privileges`).

Data-level parity (not just exit code): row counts identical across all
10 tables; flyway V1–V13 all `success`; transaction IDs intact (6 on the
test account); transfer pair joined via `transfer_ref`; zero orphaned
FKs; expense/income sums exact (44.75 / 200.00); Unicode description
(`Cà phê sáng - ăn uống test`) byte-exact.

## Findings

| ID | Sev | Finding | Disposition |
|---|---|---|---|
| M21-S1 | **BLOCKER** | Physical Pixel cannot reach the PC at L2: PC on SSID `FPT Telecom-7A47`, Pixel on `Sky House II - T3A`; same /24 but Pixel ARP entry for `192.168.1.236` is `INCOMPLETE`, PC→Pixel ping also fails, and the PC cannot reach a third client. Per-SSID/AP client isolation. **Human gate**: join the Pixel to `FPT Telecom-7A47` (or disable AP isolation). | OPEN — blocks Level A, live smoke, J1–J14, HTTPS device verification |
| M21-S2 | MINOR | `LiveBackendSmokeTest` asserts literal `"87.66"` inside a larger JSON string — brittle if server formatting changes (e.g. `87.660`). | ACCEPTED — synthetic fixture; brittleness is desirable here (detects format drift) |
| M21-S3 | MINOR | `ApiBaseUrlTest.expectReject` rethrows as `AssertionError` rather than `assertThrows` — non-idiomatic but correct and explicit about the rejected input. | ACCEPTED — clearer failure messages |
| M21-S4 | NOTE | `pg_dump` binary-safety uses `cmd /c` redirection instead of PS piping — verified non-corrupt via in-container TOC listing (67 entries parsed). | VERIFIED technique |
| M21-S5 | NOTE | HTTPS-on-device + live-smoke coverage unexercised until M21-S1 resolves; nothing in this milestone claims otherwise. | OPEN — recorded in milestone gates |
| M21-S6 | MINOR→FIXED | First implementation of URL validation ran eagerly in DI construction → crashed app launch and fake-backed graph tests (two iterations: provider-time crash, then process-killing unchecked throw inside OkHttp's async path). Final design validates at the wire boundary as `IOException`. | **FIXED + regression-proven by re-running the full connected suite green** |
| M21-S7 | NOTE | `SessionRefresherTest.a session boundary mid-flight discards the renewed credentials` flaked once under a filtered parallel run (in-flight apply raced signOut); green on isolated rerun and in the full suite. Semantics correct; watch-listed. | WATCH |

## Suite hygiene

- Default `connectedDebugAndroidTest` remains fully hermetic — no test
  can accidentally dial the LAN (assume-gate requires BOTH the
  instrumentation arg AND a non-placeholder URL).
- No test prints tokens, passwords, or secrets.
- No new flaky-prone constructs (no sleeps, no wall-clock assertions;
  timeouts only on network calls in the gated live test).

---

## FOLLOW-UP 2026-10-09 (post-L2-unblock) — appended, original findings preserved

| ID | Sev | Finding | Disposition |
| --- | --- | --- | --- |
| M21-S1 | BLOCKER→RESOLVED | Cross-SSID AP isolation blocked all device evidence. | **RESOLVED by Human** — Pixel moved to `FPT Telecom-7A47`; ARP REACHABLE; real HTTP 200 over direct Wi-Fi. |
| M21-S5 | NOTE→CLOSED | HTTPS-on-device + live smoke unexercised. | **CLOSED** — release APK over `https://192.168.1.236:8443/` signed in via UI, created account, server read-back verified. |
| M21-S8 | MINOR→FIXED | `LiveJourneyTest` class-level `@BeforeClass` assumption collapsed 18 tests into one "null" result — suite reported "expected 239, received 222" with 0 real failures. | **FIXED** (`6a5081d`) — per-test `assumeTrue` like `LiveBackendSmokeTest`; suite now completes: run#1 & run#2 both 0-fail on final source. |

## Live execution evidence (new)

- `LiveBackendSmokeTest` 3/3 executed, 0 skips — health, unauth 401,
  register→login→account→txn→balance→logout over LAN HTTP.
- `LiveJourneyTest` 18/18 executed, 0 skips — J1–J14 + x01–x04 vs real
  Spring Boot + `spendwise_m21` PostgreSQL, synthetic identity only.
- Fresh regressions: backend 458/458; Android JVM 755/755; lint/detekt/
  R8/lintVital green; final connected runs #1 and #2 both PASS.
- M21-S7 watch item: did not re-flake across two full suite runs.
