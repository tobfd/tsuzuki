package com.tobfd.tsuzuki.core.database

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.tobfd.tsuzuki.core.database.entity.CustomListEntity
import com.tobfd.tsuzuki.core.database.entity.CustomListNames
import com.tobfd.tsuzuki.core.database.entity.ListSyncEntity
import com.tobfd.tsuzuki.core.database.entity.MediaListEntryEntity
import com.tobfd.tsuzuki.core.database.entity.MediaLiteEntity
import com.tobfd.tsuzuki.core.database.entity.PendingMutationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// SDK 35: Robolectric's SDK 36 setup fails on the JDK 25 test runtime (FileDescriptor reflection).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaListDaoTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        TsuzukiDatabase::class.java
    )
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val dao = database.mediaListDao()
    private val mutations = database.pendingMutationDao()

    @After
    fun close() = database.close()

    private fun media(id: Int) = MediaLiteEntity(
        id = id,
        type = "ANIME",
        format = "TV",
        status = "FINISHED",
        episodes = 28,
        chapters = null,
        volumes = null,
        titleUserPreferred = "Media $id",
        titleRomaji = null,
        titleEnglish = null,
        titleNative = null,
        coverUrl = null,
        coverColor = null,
        year = 2023,
        averageScore = 91,
        nextAiringEpisode = null,
        isAdult = false
    )

    private fun entry(id: Int, mediaId: Int = id * 10, progress: Int = 0, run: Long = 1) = MediaListEntryEntity(
        id = id,
        mediaId = mediaId,
        type = "ANIME",
        status = "CURRENT",
        scoreRaw = 0,
        progress = progress,
        progressVolumes = 0,
        repeat = 0,
        isPrivate = false,
        notes = "",
        hiddenFromStatusLists = false,
        customLists = CustomListNames.encode(listOf("Favs")),
        startedYear = null,
        startedMonth = null,
        startedDay = null,
        completedYear = null,
        completedMonth = null,
        completedDay = null,
        updatedAt = 100,
        syncRun = run
    )

    private suspend fun sync(run: Long, vararg entries: MediaListEntryEntity) = dao.writeSync(
        type = "ANIME",
        run = run,
        media = entries.map { media(it.mediaId) },
        entries = entries.toList(),
        customLists = listOf(CustomListEntity("ANIME", "Favs", 0)),
        sync = ListSyncEntity("ANIME", userId = 1, syncedAt = run)
    )

    private fun mutation(entryId: Int) = PendingMutationEntity(
        entryId = entryId,
        mediaId = entryId * 10,
        kind = "SAVE",
        changes = "{}",
        previous = "{}",
        title = "Media",
        createdAt = 0
    )

    @Test
    fun sync_writesEntriesWithTheirMedia() = runTest {
        sync(run = 1, entry(1), entry(2))

        val entries = dao.observeEntries("ANIME").first()
        assertEquals(listOf(1, 2), entries.map { it.entry.id }.sorted())
        assertEquals("Media 10", entries.first { it.entry.id == 1 }.media?.titleUserPreferred)
        assertEquals(listOf("Favs"), dao.observeCustomLists("ANIME").first().map { it.name })
    }

    @Test
    fun sync_removesEntriesThatAreGoneAndTheirMedia() = runTest {
        sync(run = 1, entry(1), entry(2))
        sync(run = 2, entry(1, run = 2))

        assertEquals(listOf(1), dao.observeEntries("ANIME").first().map { it.entry.id })
        assertNull(dao.observeEntryByMediaId(20).first())
    }

    @Test
    fun sync_keepsLocalChangesThatAreNotSentYet() = runTest {
        sync(run = 1, entry(1, progress = 5), entry(2))
        dao.upsertEntry(entry(1, progress = 6))
        mutations.insert(mutation(entryId = 1))
        // The entry deleted on another device but changed here stays until the change is sent.
        mutations.insert(mutation(entryId = 2))

        sync(run = 2, entry(1, progress = 5, run = 2))

        val entries = dao.observeEntries("ANIME").first().associateBy { it.entry.id }
        assertEquals(6, entries.getValue(1).entry.progress)
        assertEquals(setOf(1, 2), entries.keys)
    }

    @Test
    fun customListNames_roundTrip() {
        val names = listOf("Favs", "Watch with friends", "")
        assertEquals(
            listOf("Favs", "Watch with friends"),
            CustomListNames.decode(CustomListNames.encode(names.dropLast(1)))
        )
        assertEquals(emptyList<String>(), CustomListNames.decode(CustomListNames.encode(emptyList())))
    }

    @Test
    fun mutations_areSentOldestFirstAndFailuresStayApart() = runTest {
        val first = mutations.insert(mutation(entryId = 1))
        val second = mutations.insert(mutation(entryId = 2))
        mutations.markFailed(listOf(second), "Progress too high", null)

        assertEquals(listOf(first), mutations.pending().map { it.id })
        assertEquals(1, mutations.observePendingCount().first())
        assertEquals(listOf("Progress too high"), mutations.observeFailed().first().map { it.failureMessage })

        mutations.dismissFailures(entryId = 2)
        assertEquals(emptyList<PendingMutationEntity>(), mutations.observeFailed().first())
    }

    @Test
    fun clearAllTables_removesEverything() = runTest {
        sync(run = 1, entry(1))
        mutations.insert(mutation(entryId = 1))

        database.clearAllTables()

        assertEquals(0, dao.observeEntries("ANIME").first().size)
        assertEquals(0, mutations.observePendingCount().first())
        assertNull(dao.getSync("ANIME"))
    }
}
