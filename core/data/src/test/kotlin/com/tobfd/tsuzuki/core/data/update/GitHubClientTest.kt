package com.tobfd.tsuzuki.core.data.update

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The AniList access token must never reach GitHub (docs/RELEASING.md, update check). */
class GitHubClientTest {

    @Test
    fun gitHubClient_hasNoInterceptors_soNoAuthCanBeAdded() {
        val client = gitHubClient()

        assertTrue(client.interceptors.isEmpty())
        assertTrue(client.networkInterceptors.isEmpty())
    }

    @Test
    fun releaseRequest_goesUnauthenticatedToGitHubOnly() = runTest {
        val gitHub = FakeGitHub()

        GitHubReleases(gitHub.client, StandardTestDispatcher(testScheduler)).latest()

        val request = gitHub.requests.single()
        assertEquals("https://api.github.com/repos/tobfd/tsuzuki/releases/latest", request.url.toString())
        assertNull(request.header("Authorization"))
        assertTrue(request.headers.names().none { it.equals("Cookie", ignoreCase = true) })
        assertEquals("GET", request.method)
    }
}
