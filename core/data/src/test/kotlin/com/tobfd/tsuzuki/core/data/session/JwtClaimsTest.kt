package com.tobfd.tsuzuki.core.data.session

import java.time.Instant
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JwtClaimsTest {

    private fun base64Url(json: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    private fun jwt(payload: String) = "${base64Url("""{"typ":"JWT","alg":"RS256"}""")}.${base64Url(payload)}.signature"

    @Test
    fun aniListToken_yieldsSubjectAndExpiry() {
        val claims = JwtClaims.parse(jwt("""{"aud":"52236","sub":"123456","exp":1790000000.123,"scopes":[]}"""))
        assertEquals(JwtClaims(subject = "123456", expiresAt = Instant.ofEpochSecond(1_790_000_000)), claims)
    }

    @Test
    fun numericSubjectAndIntegerExpiry_areRead() {
        val claims = JwtClaims.parse(jwt("""{"sub":42,"exp":1790000000}"""))
        assertEquals(JwtClaims(subject = "42", expiresAt = Instant.ofEpochSecond(1_790_000_000)), claims)
    }

    @Test
    fun payloadWithUrlSafeCharacters_decodes() {
        // "~~~" encodes to characters that differ between Base64 and Base64URL.
        val claims = JwtClaims.parse(jwt("""{"sub":"~~~","exp":1}"""))
        assertEquals("~~~", claims?.subject)
    }

    @Test
    fun missingClaims_areNull() {
        assertEquals(JwtClaims(subject = null, expiresAt = null), JwtClaims.parse(jwt("""{"aud":"x"}""")))
    }

    @Test
    fun malformedTokens_returnNull() {
        assertNull(JwtClaims.parse(""))
        assertNull(JwtClaims.parse("not-a-jwt"))
        assertNull(JwtClaims.parse("a.b"))
        assertNull(JwtClaims.parse("a.%%%.c"))
        assertNull(JwtClaims.parse("a.${base64Url("not json")}.c"))
        assertNull(JwtClaims.parse("a.${base64Url("[1,2]")}.c"))
    }
}
