# M7 ARGUS Review — Hardening (a11y, perf, error polish, release config)

Reviewer role: **ARGUS** (independent QA), per
`agent-system/agents/argus.md`. Fresh read of the M7 diff, the manifest,
the release APK binary, backend sources at `8194daa`, and the
ARCHITECTURE §34/§36/Q4 scope definition. Focus: whether the hardening
claims hold, whether any change introduces a regression, and whether
scope discipline was kept (no feature work inside a hardening
milestone).

## Verdict

```
BLOCKER=0
MAJOR=0
MINOR=0
NOTE=4
M7_ARGUS=PASS
```

## Independent re-verification (not trusting prior reports)

| Claim checked | Finding |
| --- | --- |
| M7 scope is canonical | VERIFIED — ARCHITECTURE §34 row M7 = "a11y pass, perf, error polish, release build config; full quality gates green". ROADMAP "Hardening / UX / Testing". Brief `M7-HARDENING.md` resolves scope without inventing features. All PRODUCT screens S01–S25 already shipped M0–M6 — correctly no new surfaces. |
| R8 serialization fix is real | VERIFIED at the binary — pre-rules DEX contains zero `$$serializer` names; post-rules DEX contains all 28 (20 `data.remote` + `ProblemDetailDto` + 7 typed routes). Retrofit's `serializer(Type)` path is reflective; without the keeps release deserialization would crash. This was a **latent release defect invisible to all compile gates** — the correct class of thing for M7 to catch. |
| Keep-rule scoping | VERIFIED — rules are scoped to `com.spendwise.android.**` (app wire surface) + `kotlinx.serialization.json.**` companions; no broad `-keep class **` bloat. `dontnote` on `AnnotationsKt` is cosmetic-safe. |
| Lazy conversions are behavior-preserving | VERIFIED — `TransactionsScreen`/`AccountDetailScreen`/`ImportScreen` changes alter composition structure only: same rows, same `contentDescription`s, same callbacks, same state models. Keys are unique (`id`, `"fail-"/"row-"` prefixed row numbers). `rowsExpanded` hoisted to composable scope — survives recycling. No VM/state/contract touched. |
| Per-row `SwCard` visual drift | ACCEPTABLE — replaces grouped-card-with-inset-dividers with per-row cards: same `SwCard` language already used by Home carousel items. P3A handoff specifies row metrics + L1 card elevation, not grouped containment. Bounded lists (accounts, planning) keep the grouped treatment deliberately — the split is documented in the brief. |
| `imePadding` correctness | VERIFIED — applied after `verticalScroll`, before content `padding`, on all six form containers; matches the existing `AuthComponents` pattern. Edge-to-edge (`enableEdgeToEdge`) does not auto-apply IME insets — this was a real keyboard-occlusion defect on every non-auth form. |
| `CategoryChoice` semantics | VERIFIED — `selectable(selected, role=RadioButton)` is the canonical single-select pattern; announces selection state that bare `clickable` never exposed. |
| Failure-row tint change | ACCEPTABLE — import failure rows moved `Muted` → `Expense` red inside the new `ImportRowLine`; the section was already labeled as failures; tinting matches the row-status semantics used in the full-list rendering. |
| `versionName` bump | ACCEPTABLE — `0.1.0-m0` → `1.0.0` reflects the complete V1 surface; signing + release versioning remain Human-owned per Q4 (documented in the milestone brief + build comment). |
| Session/privacy posture | VERIFIED unchanged — generation guards, `onVisible` resume wiring (pushed-destination decomposition + `ON_RESUME` observers), backup exclusion of `datastore/`, `usesCleartextTraffic=false`, debug-only Timber. No logging of secrets anywhere. |
| Boundary discipline | VERIFIED — diff confined to `spendwise-android`; backend `8194daa` + reference `5f9622c` untouched; no new dependencies. |
| Gates | VERIFIED — 422/422 JVM (+3 release-keep regression guards), release APK builds minified, androidTest compiles; `adb devices` empty → instrumentation stays compile-verified. |

## Findings — notes only

### NOTE-1 — remaining bounded grouped lists stay eager

Accounts list, budgets, rules, recurring rows still render inside a
single `item`'s `Column`. These lists are user-created and bounded in
practice (accounts ≤ ~20, budgets ≤ 12×categories, rules/recurring
hand-maintained). The three unbounded surfaces (ledger, account
history, import rows) are the ones that were converted. Accepted
asymmetric fix; revisit if any of those lists grow unbounded upstream.

### NOTE-2 — instrumentation coverage remains theoretical

`connectedAndroidTest` has never run (no device, ever). The Compose
suites are compile-verified only; the a11y semantics changes
(`selectable`, lazy item keys) are verified by code review, not
device assertion. Recorded for the human audit; an emulator/device
run remains the residual gap.

### NOTE-3 — `selectable` draws no ripple shape change

`selectable` provides indication via the theme's default (bounded
ripple inside the clipped `RoundedCornerShape`) — visually identical
to the prior `clickable` behavior; no pixel regression.

### NOTE-4 — grouped-card → per-row-card is a deliberate visual change

The one user-visible visual change in M7. Rows gain card borders +
Md gaps; counts/separators are unchanged. Flagged honestly rather than
hidden — design-equivalent within the SwCard language; revertible in
one commit if THE HUMAN prefers the grouped look (at the cost of
re-introducing eager composition for the unbounded ledger).

## Final verdict

```
M7_ARGUS=PASS
All eight changes are hardening-only, verified end-to-end, and
regression-free on every gate that can run locally.
```
