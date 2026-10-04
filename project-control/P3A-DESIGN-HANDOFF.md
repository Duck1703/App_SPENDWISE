# P3A Design Handoff — Human-Accepted UX Constraints

Status: **CANONICAL DESIGN INPUT FOR P4** — `P3A_HUMAN_ACCEPTED_FOR_P4=YES`

This file captures the Human-accepted UX/interaction constraints from the
disposable prototype so Android implementation never has to
reverse-engineer it. It does **not** make the React prototype production
source. `PRODUCT.md` and `BACKEND-CONTRACT.md` remain authoritative for
business behavior. `FINAL_PIXEL_PERFECT_DESIGN=NO` — minor visual polish
is allowed; structure and interaction rules are the contract.

Prototype path (READ ONLY, may contain uncommitted work):
`D:\app_spendwise\spendwise-ui-prototype`

## 1. Design status

- Human-accepted current working tree of `spendwise-ui-prototype`
  (R1–R4 materialization + refinements).
- Direction: **"Polished Premium Personal Finance"** (Calm Financial
  Clarity lineage). Light theme only. Material 3 is interaction
  inspiration, not a copy target.

## 2. Top-level structure (accepted)

Authenticated root: exactly **4 destinations** —

| Destination | Job |
|---|---|
| Home | overview/dashboard; hero + account carousel + recent activity |
| Transactions | browse / filter / inspect |
| Accounts | list + balances + create entry |
| More | Import, Recurring, Budgets, Rules, Sign out |

- Primary action app-wide: **Add transaction**.
- Nested screens hide the bottom bar; import screens hide it too.
- No Transfer entry point, no Profile surface.

## 3. Human-approved interaction rules (P4 contract)

- **Home FAB**: near top → extended "+ Add transaction"; scrolled
  meaningfully → compact "+"; back near top → extended again.
  *(P4 task statement is canonical. The R4 report documented the
  inverse trigger — bottom-extended — as a mid-review correction; the
  task statement supersedes it per source precedence. Architecture
  derives extended/compact from scroll position either way.)*
- **Transactions Add**: small mint-tinted `+` icon button in the header
  (44dp target, `aria-label`/contentDescription "Add transaction").
  NO floating button on Transactions.
- **Filters**: ONE horizontal non-wrapping row — Account / Type /
  Category. Selected values replace labels on the brand-deep "on" chip;
  horizontal scroll allowed at narrow widths (hidden scrollbar + edge
  fade). Type options are All/Income/Expense only. No search, no date
  filter, no month selector.
- **Transaction history**: read-only. NO edit / delete / correct /
  reverse / offset entry anywhere. Detail shows one quiet line:
  "Existing entries can't be edited right now."
  *(Human overrode P3 PD-004 — the offsetting-entry concept is
  officially dropped for V1.)*
- **Transaction date**: none selectable; rows carry no date; create
  shows read-only **"Recorded as today"**.
- **List end**: honest note about unspecified order and missing dates.
- **Transfer**: fully hidden — no entry point, form, or paired-entry
  simulation.
- **Import**: Choose → Processing → Result. Synchronous: blocking
  indeterminate spinner, NO percentage, NO server cancel, no background
  claim. Dedup-safe retry messaging on interruption.
- **Mixed currency**: never silently aggregate; mixed → per-account
  balances + "Combined totals unavailable…" state on Home.
- **Session expiry**: calm full screen, "Your session has expired.
  Sign in again to continue." Re-login returns to prior surface;
  **the Add-transaction draft is restored** (app-level state); other
  forms show "You will return to this screen" without restoration.
- **Register → Login**: username prefilled + confirmation banner; no
  auto sign-in.
- **Sign out**: confirmation; destructive-styled.

## 4. Screen inventory (accepted)

High fidelity (15): Welcome, Login, Register, Session expired, Home
(single/mixed/empty/loading/error variants), Accounts, Create account,
Account detail, Transactions, Transaction detail, Create transaction,
Import choose, Import processing, Import result, More.
Secondary fidelity (9): Recurring list/create/detail, Budgets
list/create/detail, Rules list/create/detail.
Launch (S01) is a state, not a drawn screen.

## 5. Accepted flows (clickable in prototype)

A Welcome→Register→Login→Home · B create account→detail ·
C expense→list→detail · D income→list · E import→result→view
transactions · F create-transaction draft survives session expiry ·
G/H/I Recurring, Budgets, Rules (incl. 409 duplicate handling).

## 6. Import specifics

- Order: account → file type → format truth block → file → processing →
  result.
- Copy: "Import a Spendwise CSV or supported TPBank statement PDF."
  Exact CSV header shown in an expandable block. PDF guidance: original
  TPBank statement only — scans/photos/password-protected/other banks
  unsupported; ANY `.pdf` rejection → generic "This file isn't
  supported" + static guidance (never a guessed cause).
- Result: "Rows in file: N" then three status tiles — Imported /
  Already imported (calm, never error) / Couldn't import (with reason);
  all-duplicates → "Nothing new to import". Row numbering: CSV rows are
  2-based file lines; PDF rows 1-based.
- Interruption copy: retry is safe; already-imported rows are skipped.
- Processing screen: no back/cancel affordance.

## 7. Home specifics

- Hero: deep-teal gradient + motif + scrim. Single-currency portfolio →
  month income + expense figures from the dashboard endpoint.
  *(Prototype labeled it "Recorded totals" because demo data has no
  dates; production may label "This month" since the server computes
  month scope — flagged for Human confirmation in P4 questions.)*
  Mixed currency → "Combined totals unavailable" state, no totals.
  NO cross-account "total balance" anywhere.
- Category breakdown and budget cards are **deliberately omitted** from
  Home (calmer; Human-accepted). P3 listed them — accepted design wins.
- Account card carousel below hero; then recent transactions panel.
- Import quick-card entry also on Home.

## 8. Visual system tokens (translation targets — not final Compose)

**Colors**: ground `#F3F6F4`/`#EAF1EE`, surface `#FFF`, line `#E3E9E6`,
field outline `#758684`, ink `#10282A`, muted `#4A5D5F`, brand-deep
`#0B4F4A`, brand `#0B6E5E`, brand-mid `#0E7C66` (gradient start),
mint `#DFF3EC`/`#C4EADC`, income `#146B37`/`#E0F3E7`, expense
`#A3392A`/`#FDE8E2`, warning `#7A4D00`/`#FFF4DC`, info `#0B5A52`/`#E3F2EF`.
Category tiles (bg/fg): Food `#FDE8E2`/`#B2402C`, Transport
`#E3EDFA`/`#2C5C99`, Housing `#FFF0DB`/`#8A5200`, Entertainment
`#EEE8FB`/`#5A3EA6`, Salary `#E0F3E7`/`#17683B`, Uncategorized
`#EBEFEE`/`#475659`. Account tiles: Cash mint/emerald gradient, Bank blue.

**Type**: Manrope (SIL OFL 1.1, tabular figures `tnum` for money).
Scale: hero money 36/750 −0.02em, titles 26/750 top-level & 21/750
nested, section 17/750, amounts 15.5–16/750, body 16, secondary
13.5–14, captions/chips 12–13/650. Currency code = 0.62em bold muted
suffix, never wraps away from amount.

**Spacing/radius/elevation**: spacing 4/8/12/16/20/24 (16px gutter);
radius 12 fields, 16 tiles/segmented, 20 cards, 24 heroes/dialogs;
buttons+chips pills. Elevation L0 flat / L1 card (1px line + soft
shadow) / L2 hero+CTA+FAB (brand-tinted). No blur/glass.

**Motif**: deep-teal gradient + one decorative layer — original
`hero-landscape.svg` (masked, right side) on Home hero; original
`topo-lines.svg` on mixed-currency/account/budgets heroes + auth
header. One motif per screen. R2 simpler variant = 2 circles + 2 arcs
(Canvas-drawable).

**Components**: 54px buttons (58px primary CTA w/ gradient),
persistent-label fields (52px + error line/icon), amount field w/
locked currency chip, segmented control, Expense/Income type switch,
filter chips (44px), category tiles (44px), transaction rows
(category tile → title/account/provenance chip | signed amount +
Income/Expense word; no date), account cards, budget ring (server
scale-2 %; coral when over), import status tiles, bottom nav (white
sheet, rounded top, mint pill indicator), skeleton loaders, import
pulse, banners, confirmation dialogs.

## 9. Asset strategy + licensing

All assets local; zero runtime remote visual dependencies.

| Asset | License | Android translation |
|---|---|---|
| Lucide icons ×30 | ISC (+Feather MIT) | VectorDrawable imports; `res/drawable` |
| Manrope variable font | SIL OFL 1.1 | bundled font res or downloadable font; `tnum` for money |
| Fluent Emoji 3D ×4 (seedling, coin, card-index-dividers, page-facing-up) | MIT | `drawable-nodpi` WebP; decorative only (`contentDescription=null`) |
| hero-landscape.svg, topo-lines.svg, spendwise-mark.svg | project-owned (original) | VectorDrawable or Canvas; pre-bake mask/radial where VectorDrawable can't express it |

License/notice obligations: copy `THIRD_PARTY_NOTICES.txt` +
per-asset license texts into the Android repo; keep attribution.
Downloaded-but-unused assets (listed in the prototype asset manifest)
must NOT be carried over. No runtime remote fonts/images.

## 10. Accessibility contract (from accepted design)

- Contrast: text ≥4.5:1, large ≥3:1, input outlines ≥3:1 — all token
  pairs already verified in the prototype.
- Direction = sign + word + icon (never color alone).
- Visible focus indication; ≥44dp touch targets.
- Persistent labels — never placeholder-as-label.
- Reduced motion honored (skeleton/pulse stop).
- Icon-only controls always labeled.

## 11. Product-truth constraints (unchangeable by visual iteration)

No dates on rows · no edit/delete/correction · no transfer · no search/
date filter/pagination · hint is never saved · one-shot synchronous
import · TPBank+CSV only · NETWORK_REQUIRED · ~15-min sessions w/
re-sign-in · balances server-authoritative · never silently mix
currencies · username (not email) identity.

## 12. Prototype status note

The prototype remains a **disposable design reference only** —
Vite/React, synthetic data, hash routing. Its `src/` must never be
treated as Android source. This handoff, `PRODUCT.md`, and
`ARCHITECTURE.md` are what M0+ implement against.
