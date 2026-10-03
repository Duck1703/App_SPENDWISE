# PRODUCT.md — Spendwise Android

Status: PRINCIPLES ONLY — detailed product blueprint pending.

## Product intent

Spendwise Android is intended to become a real mobile personal-finance client that
consumes the Spendwise Spring Boot backend (`D:\spendwise`).

It is NOT:

- a demo wrapper for a Java Spring course;
- a direct copy or rename of `kotlin-accelerator-ai`;
- an Android-side bank-PDF parser;
- backed by an independently invented backend.

## Currently approved product principles

- The app consumes the **real Spendwise backend** over REST/HTTP; the backend
  remains the source of truth for server-side financial behavior and domain writes.
- Mobile-first UX: the app is designed for Android interaction patterns, not a
  port of a server or desktop flow.
- The Android app does **not** parse bank PDFs locally. Android selects and
  uploads CSV/PDF files; the **backend owns parsing, deduplication, and domain
  writes**.
- Likely future capability areas (directional only, not committed V1 scope):
  accounts, transactions, income/expense entry, transfers, CSV/PDF import, and a
  basic dashboard.

## What is intentionally NOT decided yet

- Detailed V1 feature set and screen inventory.
- Offline behavior and local persistence policy.
- Authentication/session UX details.
- Any architecture or technology choices.

These will be established by:

1. P1 — senior Android reference architecture audit
   ([ANDROID-BASELINE.md](ANDROID-BASELINE.md));
2. P2 — backend contract audit ([BACKEND-CONTRACT.md](BACKEND-CONTRACT.md));
3. P3 — explicit product blueprint (a future controlled document).

## DETAILED PRODUCT BLUEPRINT

**PENDING** — to be produced at phase P3.
