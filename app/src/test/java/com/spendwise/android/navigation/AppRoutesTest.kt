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
}
