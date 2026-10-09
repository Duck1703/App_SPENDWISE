package com.spendwise.android.navigation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Typed-route serialization sanity (nav routes round-trip through the
 * NavController's string form without losing args).
 */
class AppRoutesTest {

    @Test
    fun `detail route round-trips its accountId`() {
        val route = AccountDetailRoute(accountId = "acc-123")

        val json = Json.encodeToString(AccountDetailRoute.serializer(), route)
        val restored = Json.decodeFromString(AccountDetailRoute.serializer(), json)

        assertEquals(route, restored)
        assertEquals("acc-123", restored.accountId)
    }

    @Test
    fun `transaction detail route round-trips its transactionId`() {
        val route = TransactionDetailRoute(transactionId = "txn-9")

        val restored = Json.decodeFromString(
            TransactionDetailRoute.serializer(),
            Json.encodeToString(TransactionDetailRoute.serializer(), route),
        )

        assertEquals(route, restored)
        assertEquals("txn-9", restored.transactionId)
    }

    @Test
    fun `import route serializes as a no-arg object`() {
        val json = Json.encodeToString(ImportRoute.serializer(), ImportRoute)

        assertEquals(
            ImportRoute,
            Json.decodeFromString(ImportRoute.serializer(), json),
        )
    }

    @Test
    fun `transaction create route round-trips with and without account prefill`() {
        val bare = TransactionCreateRoute()
        val prefilled = TransactionCreateRoute(accountId = "acc-7")

        assertNull(
            Json.decodeFromString(
                TransactionCreateRoute.serializer(),
                Json.encodeToString(TransactionCreateRoute.serializer(), bare),
            ).accountId,
        )
        assertEquals(
            "acc-7",
            Json.decodeFromString(
                TransactionCreateRoute.serializer(),
                Json.encodeToString(TransactionCreateRoute.serializer(), prefilled),
            ).accountId,
        )
    }

    @Test
    fun `transaction create route round-trips an offset source id`() {
        // M20 (PRODUCT §21): the offsetting-entry mode rides the same
        // typed route — the arg survives the NavController string form.
        val offset = TransactionCreateRoute(offsetTransactionId = "txn-42")

        val restored = Json.decodeFromString(
            TransactionCreateRoute.serializer(),
            Json.encodeToString(TransactionCreateRoute.serializer(), offset),
        )

        assertEquals(offset, restored)
        assertEquals("txn-42", restored.offsetTransactionId)
        assertNull(restored.accountId)

        // no offset arg → the field stays null (normal create)
        assertNull(
            Json.decodeFromString(
                TransactionCreateRoute.serializer(),
                Json.encodeToString(
                    TransactionCreateRoute.serializer(),
                    TransactionCreateRoute(accountId = "acc-1"),
                ),
            ).offsetTransactionId,
        )
    }

    // ── M10 import-history routes ───────────────────────────────────

    @Test
    fun `import history route serializes as a no-arg object`() {
        val json = Json.encodeToString(ImportHistoryRoute.serializer(), ImportHistoryRoute)

        assertEquals(
            ImportHistoryRoute,
            Json.decodeFromString(ImportHistoryRoute.serializer(), json),
        )
    }

    @Test
    fun `import history detail route round-trips its batchId`() {
        val route = ImportHistoryDetailRoute(batchId = "b-99")

        val restored = Json.decodeFromString(
            ImportHistoryDetailRoute.serializer(),
            Json.encodeToString(ImportHistoryDetailRoute.serializer(), route),
        )

        assertEquals(route, restored)
        assertEquals("b-99", restored.batchId)
    }

    // ── M6 planning routes ──────────────────────────────────────────

    @Test
    fun `recurring detail route round-trips its templateId`() {
        val route = RecurringDetailRoute(templateId = "tpl-4")

        val restored = Json.decodeFromString(
            RecurringDetailRoute.serializer(),
            Json.encodeToString(RecurringDetailRoute.serializer(), route),
        )

        assertEquals(route, restored)
        assertEquals("tpl-4", restored.templateId)
    }

    @Test
    fun `budget detail route round-trips its budgetId`() {
        val route = BudgetDetailRoute(budgetId = "b-42")

        val restored = Json.decodeFromString(
            BudgetDetailRoute.serializer(),
            Json.encodeToString(BudgetDetailRoute.serializer(), route),
        )

        assertEquals(route, restored)
        assertEquals("b-42", restored.budgetId)
    }

    @Test
    fun `rule detail route round-trips its ruleId`() {
        val route = RuleDetailRoute(ruleId = "rl-7")

        val restored = Json.decodeFromString(
            RuleDetailRoute.serializer(),
            Json.encodeToString(RuleDetailRoute.serializer(), route),
        )

        assertEquals(route, restored)
        assertEquals("rl-7", restored.ruleId)
    }

    @Test
    fun `planning list routes serialize as no-arg objects`() {
        assertEquals(
            RecurringListRoute,
            Json.decodeFromString(
                RecurringListRoute.serializer(),
                Json.encodeToString(RecurringListRoute.serializer(), RecurringListRoute),
            ),
        )
        assertEquals(
            BudgetsRoute,
            Json.decodeFromString(
                BudgetsRoute.serializer(),
                Json.encodeToString(BudgetsRoute.serializer(), BudgetsRoute),
            ),
        )
        assertEquals(
            RulesRoute,
            Json.decodeFromString(
                RulesRoute.serializer(),
                Json.encodeToString(RulesRoute.serializer(), RulesRoute),
            ),
        )
    }

    @Test
    fun `planning create routes serialize as no-arg objects`() {
        assertEquals(
            RecurringCreateRoute,
            Json.decodeFromString(
                RecurringCreateRoute.serializer(),
                Json.encodeToString(RecurringCreateRoute.serializer(), RecurringCreateRoute),
            ),
        )
        assertEquals(
            BudgetCreateRoute,
            Json.decodeFromString(
                BudgetCreateRoute.serializer(),
                Json.encodeToString(BudgetCreateRoute.serializer(), BudgetCreateRoute),
            ),
        )
        assertEquals(
            RuleCreateRoute,
            Json.decodeFromString(
                RuleCreateRoute.serializer(),
                Json.encodeToString(RuleCreateRoute.serializer(), RuleCreateRoute),
            ),
        )
    }
}
