package com.tobfd.tsuzuki.core.data.update

import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.common.Dispatcher
import com.tobfd.tsuzuki.core.common.TsuzukiDispatchers
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The plain OkHttp client for GitHub. It is built on its own, never from the AniList client, so the
 * AniList auth interceptor (and with it the access token) can't reach GitHub.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubHttpClient

/** The URL of the newest published release of the app (drafts and pre-releases never show up there). */
internal const val LATEST_RELEASE_URL = "https://api.github.com/repos/tobfd/tsuzuki/releases/latest"

/** What the app needs from GitHub's release JSON. */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false
)

/** One unauthenticated request to GitHub's REST API. */
internal class GitHubReleases @Inject constructor(
    @GitHubHttpClient private val client: OkHttpClient,
    @Dispatcher(TsuzukiDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The newest release; null when there is none (404, e.g. before the first release). */
    suspend fun latest(): Result<GitHubRelease?> = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Tsuzuki-Android")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> Result.success(null)
                    !response.isSuccessful -> Result.failure(AppError.Unknown("GitHub answered ${response.code}"))
                    else -> Result.success(json.decodeFromString<GitHubRelease>(response.body.string()))
                }
            }
        } catch (e: IOException) {
            Result.failure(AppError.Offline)
        } catch (e: SerializationException) {
            Result.failure(AppError.Unknown("Unexpected release JSON"))
        } catch (e: IllegalArgumentException) {
            Result.failure(AppError.Unknown("Unexpected release JSON"))
        }
    }
}
