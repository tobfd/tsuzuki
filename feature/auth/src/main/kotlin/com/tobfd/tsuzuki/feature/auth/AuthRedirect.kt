package com.tobfd.tsuzuki.feature.auth

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

private const val REDIRECT_PREFIX = "tsuzuki://auth"

/** Builds the AniList login page URL for the implicit grant (docs/ANILIST_API.md, "Login"). */
fun authorizeUrl(clientId: String): String =
    "https://anilist.co/api/v2/oauth/authorize?client_id=$clientId&response_type=token"

/** What AniList sent back to `tsuzuki://auth`. */
sealed interface AuthRedirect {
    /** Approved: the token is in the URL fragment (`#access_token=...`). */
    data class Success(val accessToken: String) : AuthRedirect

    /** Denied or failed on AniList's side, e.g. `?error=access_denied`. */
    data class Denied(val error: String) : AuthRedirect

    /** Not a login redirect, or one without token and error. */
    data object Invalid : AuthRedirect
}

/** True for `tsuzuki://auth` with nothing but a path, query or fragment after it. */
fun isAuthRedirect(uri: String): Boolean {
    if (!uri.startsWith(REDIRECT_PREFIX, ignoreCase = true)) return false
    val rest = uri.substring(REDIRECT_PREFIX.length)
    return rest.isEmpty() || rest.first() in "/?#"
}

/**
 * Parses the redirect. AniList puts `access_token`, `token_type` and `expires_in` in the fragment;
 * errors can come in the query or the fragment.
 */
fun parseAuthRedirect(uri: String): AuthRedirect {
    if (!isAuthRedirect(uri)) return AuthRedirect.Invalid
    val fragment = uri.substringAfter('#', missingDelimiterValue = "")
    val query = uri.substringBefore('#').substringAfter('?', missingDelimiterValue = "")
    val params = parameters(query) + parameters(fragment)
    params["access_token"]?.takeIf { it.isNotBlank() }?.let { return AuthRedirect.Success(it) }
    params["error"]?.takeIf { it.isNotBlank() }?.let { return AuthRedirect.Denied(it) }
    return AuthRedirect.Invalid
}

private fun parameters(part: String): Map<String, String> = part.split('&')
    .mapNotNull { pair ->
        val separator = pair.indexOf('=')
        if (separator <= 0) return@mapNotNull null
        decode(pair.substring(0, separator)) to decode(pair.substring(separator + 1))
    }
    .toMap()

private fun decode(value: String): String = try {
    // The Charset overload needs API 33; minSdk is 31.
    URLDecoder.decode(value, "UTF-8")
} catch (e: UnsupportedEncodingException) {
    value
} catch (e: IllegalArgumentException) {
    value
}

/**
 * Hands `tsuzuki://auth` redirects from `MainActivity` to the login screen. Buffered, so a redirect
 * that arrives while the login screen is being recreated is not lost.
 */
@Singleton
class AuthRedirects @Inject constructor() {
    private val channel = Channel<String>(Channel.BUFFERED)

    val redirects: Flow<String> = channel.receiveAsFlow()

    /** Returns false if [uri] is not a login redirect. */
    fun dispatch(uri: String): Boolean {
        if (!isAuthRedirect(uri)) return false
        channel.trySend(uri)
        return true
    }
}
