# M5 ARGUS Review — CSV / TPBank PDF Import

Reviewer role: **ARGUS** (independent QA), per
`agent-system/agents/argus.md`. Independent of FLUX/SENTINEL — findings
from a fresh read of the diff, the backend sources at `8194daa`, the
P3A spec, and the session/financial invariants. Focus: blockers,
majors, minors, notes across UI/UX, accessibility, error honesty,
navigation, backend assumptions, privacy, financial correctness.

## Verdict

```
BLOCKER=0
MAJOR=0
MINOR=2 (both fixed in-review)
NOTE=2
M5_ARGUS=PASS
```

## Independent re-verification (not trusting prior reports)

| Claim checked | Finding |
| --- | --- |
| `POST /api/imports` multipart contract | VERIFIED — `ImportController` at `8194daa`: `@RequestParam UUID accountId` (query), `MultipartFile file` (part name `file`), synchronous, 201 + `Location` + `ImportBatchSummaryResponse`. The client matches field-for-field. |
| Ownership/404 | VERIFIED — the controller looks the account up in the caller's scope before importing; foreign id → 404 → `ImportError.AccountUnavailable` honest copy. |
| Structural-CSV 500 drift | VERIFIED HONEST — the client never assumes 400; 5xx restores the form with the outcome-ambiguous banner. The drift is documented, not masked. |
| No `totalCount` | VERIFIED — `ImportBatch.totalRows` derives from `rows.size`; nothing invents the field. |
| `GET /api/imports/{id}` | VERIFIED — exists but correctly **unused** (the POST body is the report; no batch list exists). |
| Multipart caps | VERIFIED — no `spring.servlet.multipart` config at `8194daa` → Boot defaults 1 MB file / 10 MB request; the client's `1_048_576L` gate matches the real file cap and oversize → 500 honestly. |
| No auto-retry | VERIFIED — `retryOnConnectionFailure(false)` in `NetworkModule` (pre-existing); the VM's `isUploading` guard kills double-taps (gated test proves one POST); 401 flows through `Session.Expired` with zero replay. |
| Extension dispatch | VERIFIED — server lower-cases the filename extension; the multipart `filename=` is the picker's display name preserved verbatim (`createFormData("file", displayName, …)`); the client gate is lower-cased too (`Statement.CSV` accepted). |
| CSV guidance copy | VERIFIED — the truth block states the exact header + no-spaces/no-BOM + `yyyy-MM-dd` + sign convention + currency-match rule. PDF copy is TPBank-only, no OCR/scans/password/foreign banks — verbatim honesty. |
| Session/cross-user | VERIFIED — generation captured pre-call, checked pre-publish; dead-gen → `SessionEndedException` (tested); 401 → `Session.Expired` via the real interceptor path; same-user resume reloads accounts only, never replays; different-user sign-in destroys the flow's VM state with the graph entry. |
| Financial correctness | VERIFIED — no `BigDecimal`, `Float`, or `Double` appears in the import feature; counts are server fields; the only mutations are cache invalidation + the existing ledger signal. |
| P3A fidelity | VERIFIED — More primary row + Home secondary quick-card; choose → file → format-truth → processing → result; processing is modal/indeterminate/no-back; result is counts-first + calm duplicates + failure reasons + rows-on-demand; "Done" returns to origin. |
| Boundary discipline | VERIFIED — `git diff` confined to the product repo; no backend/reference/prototype edits; no M6 surfaces; no new dependencies (OkHttp `MultipartBody` ships with Retrofit). |

## Findings

### MINOR-1 — `toPart` failure mapped to `NetworkUnavailable` (fixed in-review)

A locally-unreadable picked file threw `IOException` inside the
network `try` → claimed "No connection" and, worse, rendered the
outcome-ambiguous banner for a POST that never left the device.
**Fix**: `toPart` is wrapped separately →
`StatementUnreadableException` → `ImportError.Generic`, never
ambiguous; repo + VM tests cover it (`unreadable picked file … never
reaches the wire`, `an unreadable file surfaces generic`).

### MINOR-2 — More sign-out button under the 48dp touch floor (fixed in-review)

The pre-existing `OutlinedButton` inherited M3's 40dp default — the
same class of finding as M4 MINOR-2. Now `heightIn(min =
SwSpacing.TouchTargetMin)`.

### NOTE-1 — ambiguous notice is dropped on same-user resume rebuild

When expiry mid-upload arms `expiredMidLoad` and the same user
resumes, `onVisible()` rebuilds a clean `Form` — the "check your
transactions" hint disappears. Acceptable: account balances refetch
through normal visible-lifecycle loads and imported rows are
reachable in Transactions immediately. Re-upload remains safe by
server dedup; the product contract (reload or safely restore) is met.
Not a correctness defect.

### NOTE-2 — SAF accepts `application/octet-stream`, so foreign types reach the extension gate

An `.xls`/`.exe` pick lands at the honest `UnsupportedFile` state
rather than being filtered out by the picker. Deliberate — provider
MIME tagging is unreliable; the extension gate is the source of
truth and the message is accurate.

## Verdict

`M5_ARGUS=PASS` — `BLOCKER=0`, `MAJOR=0`; both minors remediated and
re-verified in-review. The slice is honest about ambiguous outcomes,
never retries a non-idempotent POST, streams files without parsing,
and stays inside the backend contract with no invented surface.
