package com.spendwise.android.navigation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
}
