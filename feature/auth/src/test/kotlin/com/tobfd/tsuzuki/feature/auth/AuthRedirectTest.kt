package com.tobfd.tsuzuki.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRedirectTest {

    private val token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln-_"

    @Test
    fun tokenInFragment_isSuccess() {
        val redirect = parseAuthRedirect("tsuzuki://auth#access_token=$token&token_type=Bearer&expires_in=31536000")
        assertEquals(AuthRedirect.Success(token), redirect)
    }

    @Test
    fun tokenAsOnlyFragmentParameter_isSuccess() {
        assertEquals(AuthRedirect.Success(token), parseAuthRedirect("tsuzuki://auth#access_token=$token"))
    }

    @Test
    fun trailingSlashBeforeFragment_isAccepted() {
        assertEquals(AuthRedirect.Success(token), parseAuthRedirect("tsuzuki://auth/#access_token=$token"))
    }

    @Test
    fun errorInQuery_isDenied() {
        val redirect = parseAuthRedirect("tsuzuki://auth?error=access_denied&error_description=The+user+denied")
        assertEquals(AuthRedirect.Denied("access_denied"), redirect)
    }

    @Test
    fun errorInFragment_isDenied() {
        assertEquals(AuthRedirect.Denied("access_denied"), parseAuthRedirect("tsuzuki://auth#error=access_denied"))
    }

    @Test
    fun percentEncodedValues_areDecoded() {
        assertEquals(AuthRedirect.Denied("server error"), parseAuthRedirect("tsuzuki://auth#error=server%20error"))
    }

    @Test
    fun redirectWithoutTokenOrError_isInvalid() {
        assertEquals(AuthRedirect.Invalid, parseAuthRedirect("tsuzuki://auth"))
        assertEquals(AuthRedirect.Invalid, parseAuthRedirect("tsuzuki://auth#access_token=&token_type=Bearer"))
        assertEquals(AuthRedirect.Invalid, parseAuthRedirect("tsuzuki://auth#garbage"))
    }

    @Test
    fun otherUris_areNotAuthRedirects() {
        assertFalse(isAuthRedirect("https://anilist.co/api/v2/oauth/authorize"))
        assertFalse(isAuthRedirect("tsuzuki://authorize#access_token=$token"))
        assertFalse(isAuthRedirect("other://auth#access_token=$token"))
        assertEquals(AuthRedirect.Invalid, parseAuthRedirect("tsuzuki://authx#access_token=$token"))
    }

    @Test
    fun schemeAndHost_matchCaseInsensitively() {
        assertTrue(isAuthRedirect("TSUZUKI://AUTH#access_token=$token"))
    }

    @Test
    fun authorizeUrl_usesImplicitGrantWithClientId() {
        assertEquals(
            "https://anilist.co/api/v2/oauth/authorize?client_id=12345&response_type=token",
            authorizeUrl("12345")
        )
    }

    @Test
    fun dispatch_acceptsOnlyAuthRedirects() {
        val redirects = AuthRedirects()
        assertTrue(redirects.dispatch("tsuzuki://auth#access_token=$token"))
        assertFalse(redirects.dispatch("https://example.com"))
    }
}
