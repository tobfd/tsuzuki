package com.tobfd.tsuzuki.core.database

import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    private val file: File = ApplicationProvider.getApplicationContext<android.content.Context>()
        .getDatabasePath("migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = file,
        driver = AndroidSQLiteDriver(),
        databaseClass = TsuzukiDatabase::class
    )

    @Test
    fun version1To2_keepsTheListsAndAddsTheWidgetData() = runTest {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO media_lite (id, type, format, status, episodes, chapters, volumes, " +
                    "title_user_preferred, title_romaji, title_english, title_native, cover_url, cover_color, year, " +
                    "average_score, next_airing_episode, is_adult) VALUES (154587, 'ANIME', 'TV', 'RELEASING', 28, " +
                    "NULL, NULL, 'Sousou no Frieren', 'Sousou no Frieren', NULL, NULL, NULL, NULL, 2023, 91, 19, 0)"
            )
            execSQL(
                "INSERT INTO media_list_entry (id, media_id, type, status, score_raw, progress, progress_volumes, " +
                    "repeat, is_private, notes, hidden_from_status_lists, custom_lists, updated_at, sync_run) " +
                    "VALUES (1, 154587, 'ANIME', 'CURRENT', 0, 18, 0, 0, 0, '', 0, '', NULL, 1)"
            )
            close()
        }

        helper.runMigrationsAndValidate(2).close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TsuzukiDatabase::class.java,
            file.path
        ).setDriver(AndroidSQLiteDriver()).build()
        val entry = database.mediaListDao().observeEntries("ANIME").first().single()
        assertEquals(18, entry.entry.progress)
        assertEquals(19, entry.media?.nextAiringEpisode)
        assertNull(entry.media?.nextAiringAt)
        assertTrue(database.friendActivityDao().observeAll().first().isEmpty())
        database.close()
    }
}
