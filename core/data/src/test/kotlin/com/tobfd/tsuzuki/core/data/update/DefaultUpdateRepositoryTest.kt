package com.tobfd.tsuzuki.core.data.update

import androidx.datastore.preferences.core.emptyPreferences
import com.tobfd.tsuzuki.core.common.AppBuildInfo
import com.tobfd.tsuzuki.core.data.MutableClock
import com.tobfd.tsuzuki.core.datastore.UpdateStore
import com.tobfd.tsuzuki.core.model.AppUpdate
import com.tobfd.tsuzuki.core.testing.InMemoryDataStore
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultUpdateRepositoryTest {

    private val gitHub = FakeGitHub()
    private val store = UpdateStore(InMemoryDataStore(emptyPreferences()))
    private val clock = MutableClock(Instant.parse("2026-10-01T12:00:00Z"))

    private fun TestScope.repository(version: String = "1.0.0", debug: Boolean = false, enabled: Boolean = true) =
        DefaultUpdateRepository(
            GitHubReleases(gitHub.client, StandardTestDispatcher(testScheduler)),
            store,
            AppBuildInfo(versionName = version, isDebug = debug, updateCheckEnabled = enabled),
            clock
        )

    @Test
    fun newerRelease_isAnUpdate_withItsPage() = runTest {
        gitHub.body = FakeGitHub.release("v1.1.0")

        val update = repository().checkNow().getOrThrow()

        assertEquals(AppUpdate("1.1.0", "https://github.com/tobfd/tsuzuki/releases/tag/v1.1.0"), update)
    }

    @Test
    fun sameOrOlderRelease_isUpToDate() = runTest {
        gitHub.body = FakeGitHub.release("v1.0.0")
        assertNull(repository().checkNow().getOrThrow())

        gitHub.body = FakeGitHub.release("v0.9.9")
        assertNull(repository().checkNow().getOrThrow())
    }

    @Test
    fun versionsCompareAsNumbers() = runTest {
        gitHub.body = FakeGitHub.release("v1.10.0")
        assertEquals("1.10.0", repository(version = "1.9.0").checkNow().getOrThrow()?.version)
    }

    @Test
    fun draftsPrereleasesAndBrokenTags_areIgnored() = runTest {
        listOf(
            FakeGitHub.release("v2.0.0", draft = true),
            FakeGitHub.release("v2.0.0", prerelease = true),
            FakeGitHub.release("v2.0.0-beta1"),
            FakeGitHub.release("latest")
        ).forEach { body ->
            gitHub.body = body
            assertNull(body, repository().checkNow().getOrThrow())
        }
    }

    @Test
    fun noReleaseYet_404_isUpToDate() = runTest {
        gitHub.code = 404
        gitHub.body = """{"message":"Not Found"}"""

        assertNull(repository().checkNow().getOrThrow())
    }

    @Test
    fun serverErrorsAndBrokenJson_areFailures_notCrashes() = runTest {
        gitHub.code = 500
        assertTrue(repository().checkNow().isFailure)

        gitHub.code = 200
        gitHub.body = "<html>"
        assertTrue(repository().checkNow().isFailure)
    }

    @Test
    fun foundUpdate_showsOnHome_untilDismissed() = runTest {
        gitHub.body = FakeGitHub.release("v1.1.0")
        val repository = repository()
        repository.checkNow()

        val update = repository.availableUpdate.first()
        assertEquals("1.1.0", update?.version)

        repository.dismiss(update!!)
        assertNull(repository.availableUpdate.first())
    }

    @Test
    fun aNewerRelease_afterADismissedOne_showsAgain() = runTest {
        val repository = repository()
        gitHub.body = FakeGitHub.release("v1.1.0")
        repository.checkNow()
        repository.dismiss(repository.availableUpdate.first()!!)

        gitHub.body = FakeGitHub.release("v1.2.0")
        repository.checkNow()

        assertEquals("1.2.0", repository.availableUpdate.first()?.version)
    }

    @Test
    fun installingTheUpdate_hidesTheHint() = runTest {
        gitHub.body = FakeGitHub.release("v1.1.0")
        repository(version = "1.0.0").checkNow()

        assertNull(repository(version = "1.1.0").availableUpdate.first())
    }

    @Test
    fun startCheck_atMostOnceADay() = runTest {
        val repository = repository()
        repository.checkOnStart()
        clock.advanceBy(Duration.ofHours(23))
        repository.checkOnStart()
        assertEquals(1, gitHub.requests.size)

        clock.advanceBy(Duration.ofHours(2))
        repository.checkOnStart()
        assertEquals(2, gitHub.requests.size)
    }

    @Test
    fun startCheck_failureIsSilent() = runTest {
        gitHub.code = 503
        repository().checkOnStart()
        assertEquals(1, gitHub.requests.size)
    }

    @Test
    fun startCheck_skippedInDebugBuilds_andWithAutoCheckOff() = runTest {
        repository(debug = true).checkOnStart()

        val repository = repository()
        repository.setAutoCheck(false)
        repository.checkOnStart()

        assertTrue(gitHub.requests.isEmpty())
        assertFalse(repository.autoCheck.first())
    }

    @Test
    fun disabledByTheBuild_neverAsksAndShowsNothing() = runTest {
        gitHub.body = FakeGitHub.release("v9.0.0")
        val repository = repository(enabled = false)

        repository.checkOnStart()
        assertNull(repository.checkNow().getOrThrow())

        assertTrue(gitHub.requests.isEmpty())
        assertNull(repository.availableUpdate.first())
        assertFalse(repository.isEnabled)
    }
}
