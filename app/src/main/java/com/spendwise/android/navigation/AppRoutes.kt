package com.spendwise.android.navigation

import kotlinx.serialization.Serializable

/**
 * Typed navigation routes (AD-004, §13).
 *
 * Auth destinations live in the unauthenticated graph; the main shell and
 * the session-expired/re-authentication destinations live in the
 * authenticated graph. Session-expired is PUSHED on top of the preserved
 * main stack — never a second parallel destination.
 */
sealed interface AppRoute

// ── Auth graph ────────────────────────────────────────────────────────────
@Serializable
data object WelcomeRoute : AppRoute

/**
 * @param prefill username to seed the form (register success, expired
 *   session resume)
 * @param accountCreated show the post-register confirmation banner
 * @param resume pushed over the preserved main stack during re-auth —
 *   suppresses the "create account" cross-link
 */
@Serializable
data class LoginRoute(
    val prefill: String? = null,
    val accountCreated: Boolean = false,
    val resume: Boolean = false,
) : AppRoute

@Serializable
data object RegisterRoute : AppRoute

// ── Authenticated graph ───────────────────────────────────────────────────
/** Wrapper hosting the four-destination tab shell (keeps tab stack intact). */
@Serializable
data object MainShellRoute : AppRoute

@Serializable
data object SessionExpiredRoute : AppRoute

// Main shell — four top-level destinations (P3 product structure)
@Serializable
data object HomeRoute : AppRoute

@Serializable
data object TransactionsRoute : AppRoute

@Serializable
data object AccountsRoute : AppRoute

@Serializable
data object MoreRoute : AppRoute

/**
 * Full-screen account flows pushed OVER the preserved shell (outer graph):
 * the shell — and its tab state — stays beneath, and an in-flight create
 * form survives a session-expired overlay for same-user resume (§13).
 */
@Serializable
data object AccountCreateRoute : AppRoute

@Serializable
data class AccountDetailRoute(val accountId: String) : AppRoute

/** M19: rename + archive/unarchive (REQ-BE-006). */
@Serializable
data class AccountEditRoute(val accountId: String) : AppRoute

/**
 * Full-screen transaction flows pushed over the preserved shell — same
 * contract as the account routes: the shell stays beneath, the expiry
 * overlay can cover them, and an unsent create draft survives a
 * same-user resume (AD-011 — the transaction draft is the product's
 * explicitly promised restoration).
 *
 * @param accountId optional prefill when launched from an account's
 *   detail screen — the user may still change it.
 */
@Serializable
data class TransactionCreateRoute(val accountId: String? = null) : AppRoute

/**
 * Read-only detail derived from already-fetched list data — the backend
 * has no `GET /api/transactions/{id}`.
 */
@Serializable
data class TransactionDetailRoute(val transactionId: String) : AppRoute

/**
 * Create transfer (M12/REQ-BE-003) — pushed over the preserved shell;
 * same session-expired/draft-preservation contract as the other
 * full-screen create flows.
 *
 * @param fromAccountId optional source-account prefill when launched
 *   from an account's detail screen.
 */
@Serializable
data class TransferCreateRoute(val fromAccountId: String? = null) : AppRoute

/**
 * Statement import (M5, PRODUCT §23–25) — pushed over the preserved
 * shell so the synchronous processing state survives a session-expired
 * overlay, and `Done` returns to the origin tab.
 */
@Serializable
data object ImportRoute : AppRoute

/**
 * Import history (M10, REQ-BE-004) — pushed over the preserved shell
 * from the More tab; detail takes the batch id.
 */
@Serializable
data object ImportHistoryRoute : AppRoute

@Serializable
data class ImportHistoryDetailRoute(val batchId: String) : AppRoute

/**
 * Planning surfaces (M6, PRODUCT §30 S15–S23) — pushed over the
 * preserved shell from the More tab; details take their id argument.
 */
@Serializable
data object RecurringListRoute : AppRoute

@Serializable
data object RecurringCreateRoute : AppRoute

@Serializable
data class RecurringDetailRoute(val templateId: String) : AppRoute

@Serializable
data class RecurringEditRoute(val templateId: String) : AppRoute

@Serializable
data object BudgetsRoute : AppRoute

@Serializable
data object BudgetCreateRoute : AppRoute

@Serializable
data class BudgetDetailRoute(val budgetId: String) : AppRoute

@Serializable
data object RulesRoute : AppRoute

@Serializable
data object RuleCreateRoute : AppRoute

@Serializable
data class RuleDetailRoute(val ruleId: String) : AppRoute

/**
 * Ledger export (M9) — pushed over the preserved shell from the More
 * tab; the server-generated CSV artifact is opaque to the UI.
 */
@Serializable
data object LedgerExportRoute : AppRoute
