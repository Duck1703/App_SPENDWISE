# P4 Argus Architecture Review

ARGUS_REVIEW_MODE = **ISOLATED** (independent reviewer, no drafting context)

## Scope

Independent architecture review of
[../ARCHITECTURE.md](../ARCHITECTURE.md) (P4 blueprint) and
[../P3A-DESIGN-HANDOFF.md](../P3A-DESIGN-HANDOFF.md) against
[../BACKEND-CONTRACT.md](../BACKEND-CONTRACT.md),
[../PRODUCT.md](../PRODUCT.md),
[../ANDROID-BASELINE.md](../ANDROID-BASELINE.md), and governance.

## Findings and dispositions

| ID | Sev | Finding | Disposition |
|---|---|---|---|
| P4-F01 | **MAJOR** | Blanket "any 401 → expire" wrongly covered the login endpoint's own 401 (invalid-credentials → trapped SessionExpired loop) | **FIXED** — §4/§13/§37/§16 now scope expiry to authenticated calls; login/register 401 → inline form errors; logout 401 ignored |
| P4-F02 | **MAJOR** | `@Singleton` repository caches had no session-boundary invalidation → cross-user data exposure | **FIXED** — §13/§19 specify caches keyed to session and invalidated on every session transition |
| P4-F03 | MINOR | "Other forms lose input" contradicted the back-stack preservation mechanism | **FIXED** — §13: mechanism preserves all back-stack forms uniformly; only the txn draft is the product *promise* (harmless superset) |
| P4-F04 | MINOR | No identity-continuity check on resume-login | **FIXED** — username mismatch on resume → `popUpTo(0)` to main root |
| P4-F05 | MINOR | `expiresAt` unpersisted → cold-start flicker SignedIn→expired | **FIXED** — `expiresAt` persisted alongside token; expired → unauth graph directly |
| P4-F06 | MINOR | Product's REFRESHING state unmapped → pull-refresh could flicker to skeleton | **FIXED** — orthogonal `isRefreshing` added to the UiState shape |
| P4-F07 | MINOR | `usesCleartextTraffic=false` vs plain-HTTP dev backend unaddressed | **FIXED** — debug-only networkSecurityConfig for the dev host; release never cleartext |
| P4-F08 | MINOR | ApiException→AppError site stated three different ways | **FIXED** — unified: `toAppError()` at the repository boundary (§7/§14/§16 aligned) |
| P4-F09 | MINOR | Result-vs-throw left open to M0 (mixed-convention risk) | **FIXED** — decided now: throw `AppError`-typed + catch in VM |
| P4-F10 | MINOR | Stale §-cross-reference cluster | **FIXED** — all six dangling/wrong refs corrected |
| P4-F11 | MINOR | SessionExpired screen had no escape path | **FIXED** — offers re-login AND sign-out (clear → auth graph) |
| P4-F12 | MINOR | Register-duplicate indistinguishable-400 copy not carried into error model | **FIXED** — hedged copy specified in §16 |
| P4-F13 | NOTE | Serializer emit direction / "scale-2 end-to-end" overclaim | **ADOPTED** — `JsonUnquotedLiteral` JSON-number emit; "value-preserving" |
| P4-F14 | NOTE | AccountRepository surface + Account-detail data source ambiguous | **ADOPTED** — `GET /{id}` = detail refresh; detail embeds `list(accountId)` |
| P4-F15 | NOTE | Budget currency unspecified for mixed portfolios | **ADOPTED** — explicit selection constrained to portfolio currencies |
| P4-F16 | NOTE | Filter options unenumerated; provenance 4th chip unexplained | **ADOPTED** — type options + provenance values + local-only scoping specified |
| P4-F17 | NOTE | `GET /api/imports/{id}` wrapped w/o V1 consumer | **ADOPTED** — marked "wrapped, unused in V1" |
| P4-F18 | NOTE | OpenDocument MIME list missed common CSV labels | **ADOPTED** — added `vnd.ms-excel`, `x-comma-separated-values` |
| P4-F19 | NOTE | CSV 2-based vs PDF 1-based row numbering unacknowledged | **ADOPTED** — copy notes wire rowNumber shown as-is |
| P4-F20 | NOTE | `ImportChoose(accountId, file)` diagram read as file nav arg | **ADOPTED** — clarified: file held as in-screen state, never a route arg |
| P4-F21 | NOTE | detekt warn-only / no coverage — tradeoff framed as lesson-derived | **ADOPTED** — recorded as accepted tradeoff; test/lint/build are the failing gates |
| P4-F22 | NOTE | Stale offset-correction references remain in PRODUCT.md/ROADMAP.md | **ADOPTED** — ROADMAP M3 corrected; PRODUCT.md refs flagged as superseded-by-Human-override (recorded; approved input not edited) |
| P4-F23 | NOTE | Resume-pop mechanics + dual login paths under-specified | **ADOPTED** — pop-both-entries + `resume` flag on Login specified |

## Verified-correct items (spot confirmations)

- Contract fidelity: auth DTOs, absent `occurredOn`/`description`,
  import multipart `file`+`accountId` query, report DTO, dashboard
  `?month=`, recurring/budget/rule DTOs, `enabled`-ignored,
  ProblemDetail + `fields`-only-on-validation, empty-body 401/404,
  ~1 MB multipart default — all faithful.
- Financial correctness: BigDecimal-only path, direction-by-type model,
  no client balance math, uniform-portfolio dashboard gate provably safe.
- P3A fidelity: task-§5 FAB rule applied (R4 inverse superseded),
  header `+`, one-row filters, blocking import, txn-draft restoration,
  register→login prefill, currency-locked fields.
- Draft-preservation via back-stack-scoped `hiltViewModel()` — sound.
- Token storage: deprecated `EncryptedSharedPreferences` correctly
  rejected; DataStore + Keystore AES/GCM is the current pattern.
- Defensive enums incl. `TRANSFER_*`; retry/idempotency policies match
  contract; streaming upload realistic; M0 genuinely minimal; roadmap
  dependency order sane.
- All Android API claims verified accurate.

## Boundary review

- BACKEND_MODIFIED = **NO** · REFERENCE_MODIFIED = **NO**
- PROTOTYPE_MODIFIED = **NO** · ANDROID_CODE_CREATED = **NO**
- ARCHITECTURE_OVER_ENGINEERED = **NO** (right-sized for V1)
- DECISIONS_MD_CHANGED = **NO** (ADs are PROPOSED in ARCHITECTURE.md only)

## Verdict

**PASS**

- BLOCKER = 0 · MAJOR = 2 · MINOR = 10 · NOTE = 11
- BLOCKERS_RESOLVED = YES · MAJORS_RESOLVED = YES
- All MINORs fixed; all NOTEs adopted or recorded.
