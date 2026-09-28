package com.tobfd.tsuzuki.core.data.session

import androidx.datastore.preferences.core.emptyPreferences
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.benasher44.uuid.uuid4
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.TestApollo
import com.tobfd.tsuzuki.core.datastore.SessionStore
import com.tobfd.tsuzuki.core.datastore.TokenEncryption
import com.tobfd.tsuzuki.core.model.LogoutReason
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.model.StaffNameLanguage
import com.tobfd.tsuzuki.core.model.TitleLanguage
import com.tobfd.tsuzuki.core.model.Viewer
import com.tobfd.tsuzuki.core.model.ViewerOptions
import com.tobfd.tsuzuki.core.network.ViewerQuery
import com.tobfd.tsuzuki.core.network.auth.AuthEvents
import com.tobfd.tsuzuki.core.network.type.ScoreFormat as GqlScoreFormat
import com.tobfd.tsuzuki.core.network.type.UserStaffNameLanguage
import com.tobfd.tsuzuki.core.network.type.UserTitleLanguage
import com.tobfd.tsuzuki.core.testing.InMemoryDataStore
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultSessionRepositoryTest {

    private val now = Instant.parse("2026-09-28T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val validExpiry = now + Duration.ofDays(300)
    private val validToken = jwt(validExpiry)

    private val apollo = TestApollo()
    private val queue = apollo.queue
    private val apolloClient = apollo.client
    private val authEvents = AuthEvents()
    private val store = SessionStore(InMemoryDataStore(emptyPreferences()), FakeTokenEncryption)

    private val tobfd = Viewer(
        id = 5_424_000,
        name = "tobfd",
        avatarUrl = "https://s4.anilist.co/file/anilistcdn/user/avatar/large/tobfd.png",
        options = ViewerOptions(
            titleLanguage = TitleLanguage.ENGLISH,
            staffNameLanguage = StaffNameLanguage.NATIVE,
            displayAdultContent = false,
            scoreFormat = ScoreFormat.POINT_10_DECIMAL
        )
    )

    @After
    fun tearDown() {
        apolloClient.close()
    }

    private fun TestScope.repository() =
        DefaultSessionRepository(store, apolloClient, clock, authEvents, appScope = backgroundScope)

    // --- logIn ---

    @Test
    fun logIn_withValidToken_storesTheTokenAndViewerAndIsLoggedIn() = runTest {
        enqueueViewer()
        val repository = repository()

        val result = repository.logIn(validToken)

        assertEquals(Result.success(tobfd), result)
        assertEquals(SessionState.LoggedIn(tobfd, validExpiry), repository.session.first())
        assertEquals(validToken, store.accessToken())
        assertEquals(1, apollo.requests)
    }

    @Test
    fun logIn_withExpiredToken_failsWithoutARequest() = runTest {
        val repository = repository()

        val result = repository.logIn(jwt(now - Duration.ofDays(1)))

        assertEquals(AppError.Unauthorized, result.exceptionOrNull())
        assertEquals(0, apollo.requests)
        assertEquals(SessionState.LoggedOut(), repository.session.first())
    }

    @Test
    fun logIn_withMalformedToken_failsWithoutARequest() = runTest {
        val result = repository().logIn("not-a-jwt")

        assertEquals(AppError.Unauthorized, result.exceptionOrNull())
        assertEquals(0, apollo.requests)
    }

    @Test
    fun logIn_whenOffline_discardsTheTokenAndReturnsOffline() = runTest {
        queue.enqueueNetworkError()
        val repository = repository()

        val result = repository.logIn(validToken)

        assertEquals(AppError.Offline, result.exceptionOrNull())
        assertNull(store.accessToken())
        assertEquals(SessionState.LoggedOut(), repository.session.first())
    }

    @Test
    fun logIn_whenAniListRejectsTheToken_returnsUnauthorizedWithoutASessionEndedReason() = runTest {
        queue.enqueue(
            ApolloResponse.Builder(ViewerQuery(), uuid4())
                .exception(ApolloHttpException(401, emptyList(), null, "Unauthorized"))
                .build()
        )
        val repository = repository()

        val result = repository.logIn(validToken)

        assertEquals(AppError.Unauthorized, result.exceptionOrNull())
        assertNull(store.accessToken())
        // A failed login is shown as a login error, not as "AniList ended your session".
        assertEquals(SessionState.LoggedOut(reason = null), repository.session.first())
    }

    @Test
    fun logIn_withGraphQlErrorInAnHttp200Response_returnsTheMappedError() = runTest {
        queue.enqueue(
            ApolloResponse.Builder(ViewerQuery(), uuid4())
                .errors(listOf(Error.Builder("Invalid token").build()))
                .build()
        )

        val result = repository().logIn(validToken)

        assertEquals(AppError.Unauthorized, result.exceptionOrNull())
        assertNull(store.accessToken())
    }

    @Test
    fun logIn_afterGuestMode_leavesGuestMode() = runTest {
        enqueueViewer()
        val repository = repository()
        repository.continueAsGuest()

        repository.logIn(validToken)

        assertEquals(SessionState.LoggedIn(tobfd, validExpiry), repository.session.first())
    }

    // --- session and validate ---

    @Test
    fun storedExpiredToken_isLoggedOutWithExpiredReasonEvenBeforeValidate() = runTest {
        store.saveToken(validToken, now - Duration.ofHours(1))
        store.saveViewer(tobfd, fetchedAt = now - Duration.ofDays(1))

        assertEquals(SessionState.LoggedOut(LogoutReason.Expired), repository().session.first())
    }

    @Test
    fun validate_withExpiredToken_clearsTheSessionWithoutARequest() = runTest {
        store.saveToken(validToken, now - Duration.ofHours(1))
        store.saveViewer(tobfd, fetchedAt = now - Duration.ofDays(1))
        val repository = repository()

        repository.validate()

        assertNull(store.accessToken())
        assertEquals(SessionState.LoggedOut(LogoutReason.Expired), repository.session.first())
        assertEquals(0, apollo.requests)
    }

    @Test
    fun validate_withFreshViewer_makesNoRequest() = runTest {
        store.saveToken(validToken, validExpiry)
        store.saveViewer(tobfd, fetchedAt = now - Duration.ofMinutes(10))
        val repository = repository()

        repository.validate()

        assertEquals(0, apollo.requests)
        assertEquals(SessionState.LoggedIn(tobfd, validExpiry), repository.session.first())
    }

    @Test
    fun validate_withViewerOlderThanAnHour_refreshesIt() = runTest {
        store.saveToken(validToken, validExpiry)
        store.saveViewer(tobfd, fetchedAt = now - Duration.ofHours(2))
        enqueueViewer(name = "tobfd-renamed")
        val repository = repository()

        repository.validate()

        assertEquals(1, apollo.requests)
        assertEquals(
            SessionState.LoggedIn(tobfd.copy(name = "tobfd-renamed"), validExpiry),
            repository.session.first()
        )
    }

    @Test
    fun validate_whenOfflineWithStaleViewer_keepsTheCachedSession() = runTest {
        store.saveToken(validToken, validExpiry)
        store.saveViewer(tobfd, fetchedAt = now - Duration.ofHours(2))
        queue.enqueueNetworkError()
        val repository = repository()

        repository.validate()

        assertEquals(SessionState.LoggedIn(tobfd, validExpiry), repository.session.first())
    }

    @Test
    fun validate_whenLoggedOut_doesNothing() = runTest {
        val repository = repository()

        repository.validate()

        assertEquals(0, apollo.requests)
        assertEquals(SessionState.LoggedOut(), repository.session.first())
    }

    @Test
    fun undecryptableToken_countsAsLoggedOut() = runTest {
        store.saveToken(FakeTokenEncryption.UNDECRYPTABLE, validExpiry)
        store.saveViewer(tobfd, fetchedAt = now)

        assertEquals(SessionState.LoggedOut(), repository().session.first())
    }

    // --- ending a session ---

    @Test
    fun http401DuringASession_endsItWithUnauthorizedReason() = runTest {
        store.saveToken(validToken, validExpiry)
        store.saveViewer(tobfd, fetchedAt = now)
        val repository = repository()
        runCurrent() // let the repository subscribe to AuthEvents

        authEvents.notifyUnauthorized()

        assertEquals(
            SessionState.LoggedOut(LogoutReason.Unauthorized),
            repository.session.first { it is SessionState.LoggedOut }
        )
        assertNull(store.accessToken())
    }

    @Test
    fun logOut_clearsTheSessionAndTheApolloCache() = runTest {
        enqueueViewer()
        val repository = repository()
        repository.logIn(validToken)
        assertNotNull(cachedViewer())

        repository.logOut()

        assertEquals(SessionState.LoggedOut(reason = null), repository.session.first())
        assertNull(store.accessToken())
        assertNull(cachedViewer())
    }

    @Test
    fun continueAsGuest_isGuest() = runTest {
        val repository = repository()

        repository.continueAsGuest()

        assertEquals(SessionState.Guest, repository.session.first())
    }

    // --- helpers ---

    private suspend fun cachedViewer(): ViewerQuery.Viewer? =
        apolloClient.query(ViewerQuery()).fetchPolicy(FetchPolicy.CacheOnly).execute().data?.Viewer

    private fun enqueueViewer(name: String = tobfd.name) {
        val data = ViewerQuery.Data(
            Viewer = ViewerQuery.Viewer(
                __typename = "User",
                id = tobfd.id,
                name = name,
                avatar = ViewerQuery.Avatar(__typename = "UserAvatar", large = tobfd.avatarUrl, medium = null),
                bannerImage = null,
                siteUrl = "https://anilist.co/user/tobfd",
                unreadNotificationCount = 3,
                options = ViewerQuery.Options(
                    __typename = "UserOptions",
                    titleLanguage = UserTitleLanguage.ENGLISH,
                    staffNameLanguage = UserStaffNameLanguage.NATIVE,
                    displayAdultContent = false,
                    profileColor = "blue"
                ),
                mediaListOptions = ViewerQuery.MediaListOptions(
                    __typename = "MediaListOptions",
                    scoreFormat = GqlScoreFormat.POINT_10_DECIMAL,
                    rowOrder = "score",
                    animeList = null,
                    mangaList = null
                )
            )
        )
        queue.enqueue(ApolloResponse.Builder(ViewerQuery(), uuid4()).data(data).build())
    }

    private fun jwt(expiresAt: Instant): String {
        fun encode(json: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
        return "${encode(
            """{"typ":"JWT","alg":"RS256"}"""
        )}.${encode("""{"sub":"5424000","exp":${expiresAt.epochSecond}}""")}.sig"
    }

    /** Reversible stand-in for Tink, which needs the Android Keystore. */
    private object FakeTokenEncryption : TokenEncryption {
        const val UNDECRYPTABLE = "cannot-decrypt"
        private const val PREFIX = "enc:"

        override fun encrypt(plaintext: String): String =
            if (plaintext == UNDECRYPTABLE) plaintext else PREFIX + plaintext

        override fun decrypt(ciphertext: String): String? =
            ciphertext.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)
    }
}
