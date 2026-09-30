package com.tobfd.tsuzuki.core.data.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.TestApollo
import com.tobfd.tsuzuki.core.data.list.enqueueJson
import com.tobfd.tsuzuki.core.data.list.enqueueOffline
import com.tobfd.tsuzuki.core.data.requestVariables
import com.tobfd.tsuzuki.core.database.TsuzukiDatabase
import com.tobfd.tsuzuki.core.database.entity.MediaListEntryEntity
import com.tobfd.tsuzuki.core.database.entity.MediaLiteEntity
import com.tobfd.tsuzuki.core.datastore.SessionStore
import com.tobfd.tsuzuki.core.datastore.SettingsStore
import com.tobfd.tsuzuki.core.datastore.TokenEncryption
import com.tobfd.tsuzuki.core.model.AppColors
import com.tobfd.tsuzuki.core.model.AppThemeMode
import com.tobfd.tsuzuki.core.model.AppearanceSettings
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.model.TitleLanguage
import com.tobfd.tsuzuki.core.network.UpdateUserOptionsMutation
import com.tobfd.tsuzuki.core.testing.InMemoryDataStore
import com.tobfd.tsuzuki.core.testing.SampleData
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private object PlainTokenEncryption : TokenEncryption {
    override fun encrypt(plaintext: String): String = plaintext

    override fun decrypt(ciphertext: String): String = ciphertext
}

private fun updateJson(titleLanguage: String, adult: Boolean, scoreFormat: String) = """
    {"data":{"UpdateUser":{"__typename":"User","id":5424000,
     "options":{"__typename":"UserOptions","titleLanguage":"$titleLanguage","displayAdultContent":$adult},
     "mediaListOptions":{"__typename":"MediaListOptions","scoreFormat":"$scoreFormat"}}}}
""".trimIndent()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultSettingsRepositoryTest {

    private val apollo = TestApollo()
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        TsuzukiDatabase::class.java
    )
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val sessionStore = SessionStore(InMemoryDataStore(emptyPreferences()), PlainTokenEncryption)
    private val settingsStore = SettingsStore(InMemoryDataStore(emptyPreferences()))
    private val repository =
        DefaultSettingsRepository(settingsStore, sessionStore, apollo.client, database.mediaListDao())

    @Before
    fun setUp() = runTest {
        // SampleData.viewer reads titles in romaji and scores out of 10 with decimals.
        sessionStore.saveViewer(SampleData.viewer, Instant.parse("2026-10-01T10:00:00Z"))
    }

    @After
    fun tearDown() {
        apollo.client.close()
        database.close()
    }

    @Test
    fun appearance_defaultsToMaterialYouAndIsStored() = runTest {
        assertEquals(AppearanceSettings(), repository.appearance.first())

        repository.setColors(AppColors.AniListBlue)
        repository.setThemeMode(AppThemeMode.Dark)
        repository.setPureBlack(true)

        assertEquals(
            AppearanceSettings(AppColors.AniListBlue, AppThemeMode.Dark, pureBlack = true),
            repository.appearance.first()
        )
    }

    @Test
    fun titleLanguage_isSavedOnAniListCachedAndAppliedToTheOfflineLists() = runTest {
        insertFrieren()
        apollo.enqueueJson(UpdateUserOptionsMutation(), updateJson("ENGLISH", false, "POINT_10_DECIMAL"))

        val result = repository.updateAniListOptions(AniListOptionsChange(titleLanguage = TitleLanguage.ENGLISH))

        assertEquals(TitleLanguage.ENGLISH, result.getOrThrow().titleLanguage)
        val variables = apollo.operations.single().requestVariables()
        assertEquals("""{"titleLanguage":"ENGLISH"}""", variables)
        assertEquals(TitleLanguage.ENGLISH, sessionStore.session.first().viewer?.options?.titleLanguage)
        val media = database.mediaListDao().observeEntries("ANIME").first().single().media
        assertEquals("Frieren: Beyond Journey's End", media?.titleUserPreferred)
    }

    @Test
    fun scoreFormatAndAdultContent_areSavedTogetherWithTheirAniListValues() = runTest {
        apollo.enqueueJson(UpdateUserOptionsMutation(), updateJson("ROMAJI", true, "POINT_5"))

        val options = repository.updateAniListOptions(
            AniListOptionsChange(scoreFormat = ScoreFormat.POINT_5, displayAdultContent = true)
        ).getOrThrow()

        assertEquals(ScoreFormat.POINT_5, options.scoreFormat)
        assertTrue(options.displayAdultContent)
        assertEquals(options, sessionStore.session.first().viewer?.options)
    }

    @Test
    fun offline_changesNothing() = runTest {
        apollo.enqueueOffline(UpdateUserOptionsMutation())

        val result = repository.updateAniListOptions(AniListOptionsChange(scoreFormat = ScoreFormat.POINT_5))

        assertEquals(AppError.Offline, result.exceptionOrNull())
        assertEquals(SampleData.viewer.options, sessionStore.session.first().viewer?.options)
    }

    @Test
    fun loggedOut_failsWithoutARequest() = runTest {
        sessionStore.clear()

        val result = repository.updateAniListOptions(AniListOptionsChange(displayAdultContent = true))

        assertEquals(AppError.Unauthorized, result.exceptionOrNull())
        assertFalse(apollo.requests > 0)
    }

    private suspend fun insertFrieren() {
        val dao = database.mediaListDao()
        dao.upsertMedia(
            listOf(
                MediaLiteEntity(
                    id = 154587,
                    type = "ANIME",
                    format = "TV",
                    status = "FINISHED",
                    episodes = 28,
                    chapters = null,
                    volumes = null,
                    titleUserPreferred = "Sousou no Frieren",
                    titleRomaji = "Sousou no Frieren",
                    titleEnglish = "Frieren: Beyond Journey's End",
                    titleNative = "葬送のフリーレン",
                    coverUrl = null,
                    coverColor = null,
                    year = 2023,
                    averageScore = 91,
                    nextAiringEpisode = null,
                    isAdult = false
                )
            )
        )
        dao.upsertEntry(
            MediaListEntryEntity(
                id = 1, mediaId = 154587, type = "ANIME", status = "CURRENT", scoreRaw = 0, progress = 18,
                progressVolumes = 0, repeat = 0, isPrivate = false, notes = "", hiddenFromStatusLists = false,
                customLists = "", startedYear = null, startedMonth = null, startedDay = null, completedYear = null,
                completedMonth = null, completedDay = null, updatedAt = null, syncRun = 1
            )
        )
    }
}
