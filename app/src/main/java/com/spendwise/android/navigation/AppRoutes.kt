package com.spendwise.android.navigation

import kotlinx.serialization.Serializable

/**
 * Typed navigation routes (AD-004). M0 wires the auth entry graph and the
 * four top-level destinations; feature routes (transaction detail, import,
 * planning, …) are declared with their milestones, never guessed.
 */
sealed interface AppRoute

// Auth graph
@Serializable
data object WelcomeRoute : AppRoute

@Serializable
data object LoginRoute : AppRoute

@Serializable
data object RegisterRoute : AppRoute

// Main graph — four top-level destinations (P3 product structure)
@Serializable
data object HomeRoute : AppRoute

@Serializable
data object TransactionsRoute : AppRoute

@Serializable
data object AccountsRoute : AppRoute

@Serializable
data object MoreRoute : AppRoute
