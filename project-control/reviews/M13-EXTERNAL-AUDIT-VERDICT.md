# M13 External Audit — Independent Verdict (frozen pre-comparison)

Audit basis: Android `f85fad3b` + backend `f1ac79ad` (M13 checkpoints).
Verdict frozen BEFORE reading `M13-SENTINEL-REVIEW.md`, `M13-ARGUS-REVIEW.md`,
`M13-EXTERNAL-AUDIT-HANDOFF.md`. Comparison section appended after.

## Findings (independent audit)

### Minors — all remediated inside this audit

| # | Finding | Remediation |
|---|---------|-------------|
| EXT-MIN-1 | No deterministic concurrent-refresh proof on backend | Added `concurrentRefreshAttemptsRotateOnce` + `concurrentDistinctFamiliesRotateIndependently` (real `ExecutorService` races) — `RefreshTokenServiceTest` |
| EXT-MIN-2 | Family-TTL non-extension was asserted only at a zero-day boundary | Added `rotationDoesNotExtendTheFamilyLifetime` with a 1-day clock + 2-day step — proves absolute TTL under real rotation |
| EXT-MIN-3 | No-replay invariant proven on a probe service, not the real financial paths | Added 4 wire-proof tests through real `TransactionsService`/`TransfersService` + a 401-storm single-flight proof — `SessionRefreshContractTest` 10/10 |
| EXT-MIN-4 | `PRODUCT.md` §6 still claimed "no refresh token" | Corrected to M13 reality |
| EXT-MIN-5 | `LoginResponseDto` data-class `toString()` could emit credential material if ever logged | Redacted `toString()` (parity with `Session`/`StoredSession` remediation) |

### Notes (accepted residuals)

| # | Note |
|---|------|
| EXT-NOTE-1 | Offline logout: refresh credential stays server-live until family TTL (≤30d). Bounded, refresh-only scope, encrypted at rest — documented §13 semantics. |
| EXT-NOTE-2 | `logout()` swallows `CancellationException` — end-state (local clear) still executes; cosmetic. |
| EXT-NOTE-3 | `RecurringSchedulingJobTest.firingTheScheduledMethodTwiceForTheSamePeriod…` fails deterministically in THIS environment (date-dependent; file md5-identical to M12-accepted baseline `fd2ca1b`). Pre-existing, unrelated to M13 scope. |

### Blockers / Majors: none found.

## Verification results (this audit)

- Backend `RefreshTokenServiceTest`: 11/11 (3 new).
- Backend full regression: 330 tests, 1 pre-existing env failure (above).
- Android JVM: 570/570.
- Pixel 4 XL (API 33) connected: 178/178 twice.
- detekt 0 smells / lint pass / assembleDebug pass / minified release pass.
- `app-release-unsigned.apk` SHA-256: `6d7cc64c1d2d38ff903e65b541972c839349bceec4831161ecb61a48ae45c4eb`.

## Adversarial claim checklist (all independently falsified → held)

1. One refresh token → one successor branch: rotation is atomic under lock; concurrent races proven.
2. Rotated-token replay → whole family revoked: proven incl. descendant kill.
3. 30-day family TTL absolute: proven non-sliding under rotation.
4. Logout revocation: presented refresh credential revoked; bearer `jti` blacklisted; permitAll + refresh-body authority works with dead access token.
5. Process-local store: honest (fail-closed on restart; documented).
6. Android single-flight: `ReentrantLock` + generation-checked apply; 401 storm → 1 wire refresh.
7. Stale-bearer 401 → `SessionRetryable`, not expiry.
8. Current-bearer 401 + dead renewal path → `SessionExpired`.
9. GET/HEAD retried at most once via marker header; POST never retried by authenticator.
10–12. `POST /api/transactions` + `POST /api/transfers` transmit exactly once — wire-proven (401-after-transmit = surface, renew for next attempt only; expired-access = renew-then-send-once).
13. Logout-during-refresh cannot resurrect: generation bump discards late renewal.
14. User A → User B: identity-change invalidation bumps generation; stale renewal discarded.
15. Cold-start `Restoring` parks until renewal resolves — no stale protected read.
16. Legacy refresh-less blob reads as legacy session; old re-auth path preserved.
17. `Session`/`StoredSession`/`LoginResponseDto` `toString` redacted; bare refresh client has no logging interceptor; DataStore file excluded from backup.
18. `@Serializable`-scoped ProGuard keep covers new DTOs; R8 release + login/refresh round-trips verified.

## Comparison vs prior reviews (read only after freezing the verdict above)

- `M13-SENTINEL-REVIEW.md` — PASS 0B/0M/3 Minor+Note. Its Minor-1
  (revoked-but-live bearer on logout orphans the refresh credential)
  is the same bounded residual family as EXT-NOTE-1 — consistent.
  Minor-2 (`isPublicCall` triplication) and the nullable-refresher
  test-seam note are cosmetic/by-design — agreed.
- `M13-ARGUS-REVIEW.md` — PASS 0B/0M/2 Minor/3 Note. A-1 (credential
  `toString`) was remediated in-milestone; EXT-MIN-5 extends the same
  defense to the wire DTO. A-2 = Sentinel Minor-1 (accepted residual).
  A-3 (map growth) mirrors TokenBlacklist posture — accepted. A-4
  (lock-held refresh blocking) is the single-flight design cost —
  accepted. A-5/A-6 match this audit's generation-safety proof:
  every path into Expired/SignedOut bumps the generation, so stale
  renewals are always discarded; same-identity re-login while SignedIn
  is unreachable.
- `M13-EXTERNAL-AUDIT-HANDOFF.md` — gate evidence consistent
  (counts grew only by this audit's additions: 327→330 backend,
  566→570 JVM; `RecurringSchedulingJobTest` failure identical and
  file-hash-proven pre-existing at M12 baseline `fd2ca1b`).

No contradictions; zero blockers, zero majors across all three
independent assessments.

## Final status

```
M13_EXTERNAL_AUDIT_FINAL=ACCEPTED
BLOCKER=0
MAJOR=0
MINOR=5 (all remediated in-audit: EXT-MIN-1..5)
NOTE=3 (bounded, documented residuals)
SESSION_REFRESH=APPROVED
M14_DEFINED=NO
M14_STARTED=NO
```
