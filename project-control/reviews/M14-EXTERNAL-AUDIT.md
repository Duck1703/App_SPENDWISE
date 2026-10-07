# SPENDWISE M14 EXTERNAL AUDIT — BUDGET LIFECYCLE (REQ-BE-011)

```
ROLE: INDEPENDENT EXTERNAL AUDITOR (fresh session, adversarial mandate)
ANDROID_RANGE: b6b2751..acb807c (delivery 1412860 + governance b0036c6
               + checkpoint 097fcf0 + audit remediation acb807c)
BACKEND_RANGE: da147b8..4ed4f81 (delivery 472b8fd + audit tests 4ed4f81)
VERDICT: BLOCKED_PHYSICAL_RUNTIME — every code/contract/safety gate
         independently PROVEN (0 blocker / 0 major); the mandatory
         physical-device gate could not run (no device attached).
```

## 1. Checkpoints (verified, not assumed)

- Android `main` HEAD `097fcf0` clean before audit; all three claimed
  SHAs resolved; worktree clean.
- Backend `main` HEAD `472b8fd`; only the 3 expected human-owned dirty
  docs (`M README.md`, `?? HUONG-DAN-SU-DUNG.md`, `?? docs/SPENDWISE-REQ.md`) —
  untouched by this audit.
- Reference `kotlin-accelerator-ai` `5f9622c` — read-only honored.
- `SPENDWISE_DB_PASSWORD` sourced from `.env.local` for test runs only;
  never printed, copied, or committed.

## 2. REQ-BE-011 re-derived (canonical source, not implementation)

- Mutable fields: `limit` and/or `period` only; ≥1 field required else 400.
- Immutable: `category`, `currency` — delete+recreate is the remedy.
- Uniqueness: `(user_id, category_code, period)` — DB-enforced (V6).
- Collision → 409; foreign/missing → 404 (non-disclosing);
  `DELETE` → 204 hard delete; budgets are planning objects, ledger
  untouched; no schema change. REQ_BE_011_ALIGNMENT=PASS.

## 3. Physical first pass — MANDATORY GATE

```
DEVICE=NONE — `adb devices -l` empty across 5+ checks including an
adb server restart. Pixel 4 XL not attached.
PHYSICAL RUN_1/RUN_2: NOT EXECUTED.
M14_PHYSICAL_RUNTIME=BLOCKED

SUPPLEMENTARY RUNTIME (does NOT substitute for the physical gate):
AVD Spendwise_M8_Acceptance (API 36, emulator-5554):
  run 1: 180 tests, 1 failure — a REAL defect in the delivered
         connected test `detail_editOpensDialog_andSavePropagates`
         (assertIsDisplayed on "Edit budget" matched both the
         screen's Edit button and the dialog title → 2-node
         ambiguity). Fixed by asserting the dialog-only Save
         affordance (commit 841b04c).
  run 2: 180/180 PASS (post-fix)
  run 3: 180/180 PASS (independent clean rerun)
M14_EMULATOR_RUNTIME=PASS (180/180 ×2 consecutive, post-fix)
```

Per the audit mandate the missing physical-device evidence alone
forbids Human approval regardless of all other evidence. The two
clean emulator passes remove any residual runtime doubt but do not
satisfy the canonical physical-Pixel gate (all prior milestones
closed on physical hardware; no equivalent-evidence clause exists).

## 4. Falsification matrix — every claim attacked

### PATCH semantics — all PASS
- Limit-only, period-only, limit+period: persisted correctly (real API).
- Unspecified fields preserved: wire body asserted to OMIT absent keys
  (`encodeDefaults=false` verified against the wire, not the code).
- Empty patch `{}` → 400; `{"limit":null,"period":null}` → 400;
  `{"period":""}` / whitespace → 400 (AUDIT-ADDED proofs).
- `limit` 0/negative → 400; scale>2 → 400 (@Digits); `period` "October
  2026"/"2026-13"/"2026-1" → 400 via BudgetPeriod→DomainException.
- BigDecimal end-to-end (NUMERIC(19,2); Kotlin BigDecimal DTO; client
  validation mirrors: required/positive/≤2dp/≤17 digits).
- Unknown-field honesty (AUDIT-ADDED): `{"currency":"EUR"}` alone → 400
  (empty after Jackson drop); `{"currency":"EUR","limit":500}` → 200 with
  `currency:"USD"` — immutables can never be written by a patch.

### Ownership / non-disclosure — PASS
- Foreign PATCH → 404 + row verified untouched. Foreign DELETE → 404 +
  row present. Missing IDs → 404 both verbs. Foreign ≡ missing.
- Validation order: `{}`→400, `{limit:0}`→400 uniformly for ANY id
  (no existence leak); malformed-period to foreign → 404 (own → 400,
  observable only to the owner). No leak.

### Collision — PASS (strengthened by audit)
- PATCH into own existing (category,period) → 409.
- **Atomicity proven in a REAL transaction** (audit-added
  `BudgetLifecycleAtomicityTest`): `{limit:999, period:<colliding>}` →
  409 → GET → original 300.00/2026-11 byte-unchanged AND the collided
  target intact. NOTE: inside a class-level `@Transactional` MockMvc
  test the same check 500s — a shared-tx artifact (Postgres 25P02), not
  a production bug; proven so by construction.
- Cross-user false-collision: stranger's identical pair does NOT block
  my patch (audit-added). Same-user → 409; cross-user → 200.

### Delete — PASS
- Owner → 204; foreign → 404; missing → 404; second delete → 404.
- Removed from all three read surfaces (list/detail/calculation).
- **DELETE→RECREATE same (category,period) → 201** — the canonical
  PRODUCT.md defect ("permanently unfixable" wrong budget) is a passing
  test at the real API boundary.
- Delete→stale-PATCH → 404, no resurrection (audit-added).

### Ledger safety — PASS (audit-added proof)
- `budgetMutationsNeverTouchTheLedger`: real account + transaction
  seeded; PATCH(limit+period) + DELETE; `transactionRepository.count()`
  unchanged; `Account.balance()` byte-equal. Budgets hold no history;
  no FK touches the ledger.

### M13 session machinery — PASS (wire-proven at production path)
- Transmitted PATCH→401: requestCount=2 (1 PATCH + 1 refresh) — **never
  replayed**. Same for DELETE.
- Pre-send expired → 1 refresh + exactly 1 PATCH; 1 refresh + 1 DELETE
  (AUDIT-ADDED wire proofs).
- Refresh rejection → Session.Expired (pre-existing proofs intact).
- Dead-generation update/delete: emit no `mutations`, publish nothing —
  stale User-A result cannot surface to User B (repository-level proof
  with real `signOut()` mid-flight; VM-level: SessionEnded→Ambiguous).

### Server truth — PASS
- After PATCH: full `GET calculation` reload — no optimistic mutation.
- Period-change: audit-added proof that the new period's server figures
  land (spent 0→40.00 across the move; backend + VM level).
- After DELETE: `Deleted` event pops; `mutations` reloads the list.
- Ambiguous delete: refresh reconciles to server truth (gone→NotFound,
  live→Content); never auto-retried.

### Concurrency — documented PASS-safe
- No `@Version` on Budget → last-write-wins whole-row updates; a lost
  update lands a coherent prior-read state, never a field-hybrid.
- PATCH-vs-DELETE and DELETE-vs-DELETE yield valid orderings (row-lock
  serialization; loser sees 404). DB unique key is the collision
  backstop. Single-user product scope — accepted and documented.

### UI honesty — PASS after 2 audit minors
- Currency context: `budget_edit_currency_hint` verified present.
- EXT-M14-MINOR-1 FIXED: delete-confirm now names the budget
  (category + month) — Compose proof added.
- EXT-M14-MINOR-2 FIXED: saveEdit 404 closes the dialog (early return)
  before the NotFound reload — was a silent open dialog.
- Edit isSubmitting locks fields+buttons (no double-submit; second
  invocation guards on the flag). Delete confirm dismisses before
  firing; a theoretical second tap is server-safe (404→refresh).
- Errors: 400→field errors (limit only — backend supplies no period
  field, honestly unmapped→Generic); 409→conflict copy; network→
  "may already be updated" ambiguous; SessionRetryable→Generic matches
  established convention.
- Buttons carry text labels + contentDescription; destructive confirm
  is not color-only. Locale: BigDecimal+`,→.` parse, fixed period
  format — vi-VN safe. Large-font/IME: material AlertDialog (scrollable
  text region) — device-verification pending with the runtime gate.

## 5. Gate results (re-executed by this audit)

| Gate | Result |
|---|---|
| Backend focused lifecycle | **20/20** (13 delivered + 6 audit in-class + 1 audit atomicity class) |
| Backend full regression | **350 tests, 1 failure** = documented `RecurringSchedulingJobTest` baseline flake (343 + 7 audit tests) |
| Backend `mvn package` | PASS |
| Android JVM `--rerun-tasks` | **596/596** (592 + 4 audit tests). One earlier run caught a one-off interleaving flake in the pre-existing M13 `concurrent GETs 401 storm` test — passed isolated and in the clean rerun; unrelated to M14 |
| lint / detekt | clean / **0 smells** |
| assembleDebug / assembleRelease | PASS / PASS |
| Release APK (post-remediation) | `app-release-unsigned.apk` 2,431,950 B, SHA-256 `411382ff045377a0b96701b876ecfd7eb6f51aedb0f58dede98d7c9f5a72a4dc` |
| Serializers | 44 `@Serializable` in remote DTOs; `UpdateBudgetRequestDto`+`BudgetDto` kept in mapping.txt |
| Manifest | INTERNET + self receiver permission only; `usesCleartextTraffic="false"`; datastore excluded from backup + device-transfer |
| Sensitive logging | none in M14 surfaces |
| Connected — physical | **BLOCKED — no device attached** |
| Connected — emulator | **180/180 ×2 consecutive** after fixing a genuine delivered-test defect (ambiguous dialog-title assertion; commit `841b04c`) |

## 6. Internal-review comparison

SENTINEL reviewed: claims accurate; their S-2 note (connected N/A) is
the same gap this audit escalates per mandate. ARGUS reviewed: A-1
currency fix confirmed in source; A-3 both-fields note confirmed as
correct-by-contract. Handoff reviewed: counts (592→343 tests etc.)
reproduced. INTERNAL_FINDINGS_CONFIRMED=3; EXTERNAL_ONLY_FINDINGS=6
(minors+notes listed above); INTERNAL_FALSE_POSITIVES=0.

## 7. Remediation performed (authorized, bounded)

- Backend `4ed4f81`: 7 adversarial tests (atomicity in real tx,
  cross-user collision, stale-patch, null-patch, unknown-field,
  ledger-safety, new-period calculation).
- Android `acb807c`: 2 minor fixes (dialog identity; NotFound dialog
  close) + 4 proofs (pre-send PATCH/DELETE, dead-generation Ambiguous,
  new-period reload) + Compose identity assertion.
- Android `841b04c`: connected-test defect fix — the delivered
  `detail_editOpensDialog_andSavePropagates` asserted a text that
  existed twice on a real runtime; now asserts the dialog-only Save
  affordance. Verified 180/180 ×2 on the emulator.
- UNRESOLVED_BLOCKERS=0, UNRESOLVED_MAJORS=0.

## 8. Final verdict

```
M14_EXTERNAL_AUDIT_FINAL=BLOCKED_PHYSICAL_RUNTIME
REQ_BE_011_ALIGNMENT=PASS        BACKEND_PATCH_CONTRACT=PASS
BACKEND_DELETE_CONTRACT=PASS     OWNERSHIP=PASS
COLLISION_POLICY=PASS            DELETE_RECREATE=PASS
NO_LEDGER_MUTATION=PASS          PATCH_CONCURRENCY=PASS(documented)
DELETE_CONCURRENCY=PASS(documented)
M13_NO_REPLAY=PASS               SERVER_TRUTH=PASS
SESSION_PRIVACY=PASS             TEST_INTEGRITY=PASS(post-remediation)
RELEASE_SAFETY=PASS              PHYSICAL_RUNTIME=BLOCKED
EMULATOR_RUNTIME=PASS(180/180×2) M0_M13_REGRESSION=PASS
```

The code is audit-clean: every blocker-class and major-class invariant
was independently falsified and survived. The single unmet gate is the
mandatory physical-device evidence — not a code finding.
