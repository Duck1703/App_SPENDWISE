# M9 Argus Review — Ledger Export

```
VERDICT: PASS (0 blocker / 0 major / 2 minor)
SCOPE: product/UX/privacy QA of the M9 diff vs PRODUCT, P3A handoff,
  M8 accepted baseline, and the Human's M9 authorization (export only).
```

## Scope conformance — independently verified

- `git diff` reviewed end-to-end: **no** import-history/pointer store,
  **no** FX consumption, **no** date exposure, **no** mutation, no new
  permissions, no background machinery. `LedgerExportRoute` is a
  `data object` — typed-nav surface unchanged in shape.
- The export artifact is opaque end-to-end: no CSV parse, no
  `occurredOn` surfacing — BE-001 stays backend-gated; the file can
  contain data the UI legitimately doesn't understand.

## UX / IA

- More → "Ledger export" row uses the existing `MoreRow` pattern; sits
  after Rules, above the sign-out divider — does not displace sign-out
  (M7 clipping lesson honored).
- Screen: `SwHeader` (heading semantics) + explanation, `SwDropdownField`
  scope control ("All accounts" default — matches backend's omitted
  param), single `Export CSV` action, phase labels on the button
  ("Preparing export…"/"Saving file…"), banner semantics for
  success/error, 48dp targets, scrollable form.
- Honest UX: cancel of the system picker is silent (not an error);
  success banner appears **only after the destination write
  completes**; save failure is labeled distinctly from export failure;
  no filesystem path is promised to the user.
- Large-font: single-column scrollable form — reachable actions at
  1.3x; no text input → no IME concerns.

## Privacy / security

- Bytes are server-truth transported byte-identical; temp artifact in
  `cacheDir` (app-private, backup-excluded) with delete-on-every-path;
  cross-user exposure impossible (generation guard + teardown delete +
  entry sweep).
- `Content-Disposition` treated as untrusted input; SAF destination is
  user-chosen; zero storage permissions in the merged manifest.

## Financial authority

- Server-generated CSV only — the app writes zero financial content;
  no arithmetic, no currency handling, no local ledger. V1's
  server-authoritative stance fully preserved.

## Findings

| Sev | Item | Disposition |
| --- | --- | --- |
| MINOR | Dropdown stays interactive during download (selection ignored by VM phase gate) | Accepted — VM guards correctness; modal phases block escape |
| MINOR | Export progress is indeterminate (no byte progress) | Accepted — backend gives no progress contract; mirrors import |

## Verdict detail

`CONNECT=153/153 (Pixel 4 XL)`, `JVM=462/462`, release R8 + manifest
clean. No product-integrity issue found.
