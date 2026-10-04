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
