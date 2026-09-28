package com.tobfd.tsuzuki.core.data.session

import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The claims the app needs from an AniList access token (a JWT): [subject] is the user id, [expiresAt]
 * the moment the token stops working (about a year after login; it cannot be refreshed).
 * The signature is not checked; AniList does that on every request.
 */
data class JwtClaims(val subject: String?, val expiresAt: Instant?) {
    companion object {
        private const val JWT_PARTS = 3

        /** Returns null if [token] is not a JWT with a JSON payload. */
        fun parse(token: String): JwtClaims? {
            val parts = token.trim().split('.')
            if (parts.size != JWT_PARTS) return null
            val payload = try {
                Base64.getUrlDecoder().decode(parts[1]).decodeToString()
            } catch (e: IllegalArgumentException) {
                return null
            }
            val json = try {
                Json.parseToJsonElement(payload).jsonObject
            } catch (e: IllegalArgumentException) {
                return null
            }
            return JwtClaims(subject = json.string("sub"), expiresAt = json.epochSeconds("exp"))
        }

        private fun JsonObject.string(name: String): String? = try {
            get(name)?.jsonPrimitive?.contentOrNull
        } catch (e: IllegalArgumentException) {
            null
        }

        private fun JsonObject.epochSeconds(name: String): Instant? = try {
            val primitive = get(name)?.jsonPrimitive
            (primitive?.longOrNull ?: primitive?.doubleOrNull?.toLong())?.let(Instant::ofEpochSecond)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
