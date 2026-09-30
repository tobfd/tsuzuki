package com.tobfd.tsuzuki.core.network.debug

import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

/** Bytes of a response body looked at for GraphQL errors. */
private const val PEEK_BYTES = 64L * 1024

/** Characters of variables or errors put into one log line. */
private const val MAX_LOG_CHARS = 600

private val OperationName = Regex(""""operationName"\s*:\s*"([^"]+)"""")
private val Variables = Regex(""""variables"\s*:\s*(\{.*?\})\s*(,\s*"|})""", RegexOption.DOT_MATCHES_ALL)
private val Errors = Regex(""""errors"\s*:\s*(\[.*])""", RegexOption.DOT_MATCHES_ALL)

/**
 * Debug builds only: logs each AniList request's operation name and variables, the HTTP status, the
 * result size and any GraphQL `errors` (they can come with HTTP 200). Headers are never read, so the
 * access token never reaches the log; the request body holds no token.
 */
class RequestLogInterceptor(private val log: (String) -> Unit) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body?.let { requestBody ->
            Buffer().also { requestBody.writeTo(it) }.readUtf8()
        }.orEmpty()
        log(describeRequest(body))
        val response = chain.proceed(request)
        log(describeResponse(response.code, response.peekBody(PEEK_BYTES).string()))
        return response
    }
}

/** "→ SearchMedia {\"page\":1,…}" from a GraphQL request body. */
fun describeRequest(body: String): String {
    val name = OperationName.find(body)?.groupValues?.get(1) ?: "?"
    val variables = Variables.find(body)?.groupValues?.get(1)?.take(MAX_LOG_CHARS) ?: "{}"
    return "→ $name $variables"
}

/** "← 200, 1234 chars" plus the GraphQL errors, if there are any. */
fun describeResponse(code: Int, body: String): String {
    val errors = Errors.find(body)?.groupValues?.get(1)?.take(MAX_LOG_CHARS)
    return "← $code, ${body.length} chars" + (errors?.let { ", errors: $it" } ?: "")
}
