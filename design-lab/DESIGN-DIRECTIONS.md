# SpendWise Design Directions

Design Exploration Studio — three visual identities for a premium Vietnamese
personal finance app. This is a browser prototype only; it does not replace
the native Android application.

---

## Concept A — Modern Private Banking

**Subtitle:** Editorial · Warm · High-trust

**Positioning:** An expensive financial product designed for adults. The
interface borrows from private banking and editorial print — restrained,
spacious, and confident without decoration.

**Visual language**

- **Surfaces:** Warm ivory ground (`#F5F1EA`) with white cards and deep
  charcoal (`#1A1714`) as the primary "ink" for heroes and primary actions.
- **Accent:** Rich burgundy (`#7A1E2B`), supported by a muted gold
  (`#9A7B4F`) for secondary emphasis.
- **Typography:** Fraunces (editorial serif) for headings and money
  displays, Inter for body and labels. Serif numerals give the balance a
  banknote-like quality.
- **Composition:** A dark "month overview" hero card states the net cash
  flow first, then income/expense in a symmetrical split. Accounts appear
  as compact cards (bank = light, cash = charcoal). Category spending is a
  clean horizontal bar list.
- **Motion:** Quiet fade-up entrances and scale feedback only.

**Trust cues:** Direction is always sign + icon + word (never color alone),
every amount carries the VND label, and the detail screen repeats the
honesty line "existing entries can't be edited."

**Best for:** A trust-first, premium personal banking audience.

---

## Concept B — Next-Generation Fintech

**Subtitle:** Bold · Dark · Expressive

**Positioning:** A confident consumer fintech product in the same league as
modern neobank apps — but with a unique identity: deep midnight surfaces
and an electric lime accent that no legacy bank would dare.

**Visual language**

- **Surfaces:** Layered dark blues (`#0B0F1A` ground, `#151B2E` surface,
  `#222B48` cards) with subtle radial glows behind key numbers.
- **Accent:** Electric lime (`#C6F432`) for primary actions and the active
  tab, blue (`#4D7CFF`) and violet (`#8B5CF6`) for category coding.
- **Typography:** Space Grotesk throughout — geometric, technical, bold
  weights for balances; lime-colored total balance as the visual anchor.
- **Composition:** A glowing total-balance hero with a live net-flow pill,
  a three-up quick-action row (Ghi / Nhập / Phân tích), income and expense
  cards side by side, then accounts and a color-coded category breakdown.
- **Motion:** Glow accents, active-pill highlights, scale-press feedback.

**Trust cues:** Same financial correctness rules as A and B — signed
amounts with direction words, provenance chips (Manual/Imported/Recurring/
Transfer), and un-editable entry notice.

**Best for:** A distinctive, commercially competitive consumer brand.

---

## Concept C — Sculpted Minimalism

**Subtitle:** Clean · Data-first · Precise

**Positioning:** A design-system product. Near-monochrome with a single
emerald accent; every element earns its place. Maximum scanability with
zero noise.

**Visual language**

- **Surfaces:** Near-white ground (`#FAFAFA`), hairline dividers, no
  cards on the main flow — lists sit directly on the canvas.
- **Accent:** Single emerald (`#00875A`) for income and primary
  indicators; a restrained coral (`#CC3300`) for expenses only.
- **Typography:** Inter at display weights (800) with tight tracking for
  the huge net-flow figure; small uppercase letter-spaced labels create a
  strict hierarchy. Money is always tabular.
- **Composition:** A typographic hero (no box) states net flow; income and
  expense share one bordered split row; accounts are plain rows; category
  bars show percentages; transactions are simple divided rows.
- **Motion:** Underline transitions, minimal fades.

**Trust cues:** Identical financial honesty rules; text-based filters with
an accent underline instead of pill chrome.

**Best for:** Users who prioritize clarity and speed above all.

---

## Recommendation

**Concept B (Next-Generation Fintech)** is the strongest candidate for
commercialization: it is the most distinctive and memorable, the dark
identity reads as "modern money app" rather than "bank utility," the lime
accent gives SpendWise an ownable brand color, and the bold numeric display
is excellent for VND amounts (which are long and need strong hierarchy).
Concepts A and C remain strong alternates for a trust-led or
clarity-led audience.

## Financial truth rules applied in all concepts

- Amount and currency always displayed together (VND / ₫).
- Direction = sign + icon + word, never color alone.
- No cross-currency totals; all demo data is single-currency VND.
- Provenance chips distinguish Manual / Imported / Recurring / Transfer.
- No edit/delete affordances — the detail screen states entries cannot be
  changed.
- All data is synthetic Vietnamese demo data, clearly not real user data.
