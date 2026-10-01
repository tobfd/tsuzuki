package com.tobfd.tsuzuki.core.data.update

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * A fake transport behind the app's own GitHub client: it records every request and answers with
 * [code] and [body] instead of going to the network.
 */
internal class FakeGitHub {
    val requests = mutableListOf<Request>()
    var code = 200
    var body = release("v1.0.0")

    /** The app's GitHub client with this fake added last, so it sees the request as it would go out. */
    val client: OkHttpClient = gitHubClient().newBuilder()
        .addInterceptor(
            Interceptor { chain ->
                requests += chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("fake")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
        )
        .build()

    companion object {
        fun release(tag: String, draft: Boolean = false, prerelease: Boolean = false) = """
            {
              "tag_name": "$tag",
              "html_url": "https://github.com/tobfd/tsuzuki/releases/tag/$tag",
              "draft": $draft,
              "prerelease": $prerelease,
              "assets": [],
              "body": "Notes"
            }
        """.trimIndent()
    }
}
