package com.spendwise.android.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class JwtClaimsTest {

    private fun token(payload: String): String {
        val header = b64("""{"alg":"HS256","typ":"JWT"}""")
        return "$header.${b64(payload)}.signature"
    }

    private fun b64(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())

    @Test
    fun `subject is read from a well-formed jwt`() {
        val jwt = token("""{"sub":"9b0b7f0e-1111-4222-8333-444455556666","jti":"x"}""")
        assertEquals("9b0b7f0e-1111-4222-8333-444455556666", JwtClaims.subjectOf(jwt))
    }

    @Test
    fun `malformed tokens return null`() {
        assertNull(JwtClaims.subjectOf("not-a-jwt"))
        assertNull(JwtClaims.subjectOf("a.b"))
        assertNull(JwtClaims.subjectOf("a.b.c.d"))
    }

    @Test
    fun `missing or non-json payload returns null`() {
        assertNull(JwtClaims.subjectOf(token("not json")))
        assertNull(JwtClaims.subjectOf(token("""{"jti":"x"}""")))
    }
}
