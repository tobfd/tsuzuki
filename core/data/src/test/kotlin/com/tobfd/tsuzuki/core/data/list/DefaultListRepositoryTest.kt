package com.tobfd.tsuzuki.core.data.list

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.apollographql.apollo.api.Optional
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.MutableClock
import com.tobfd.tsuzuki.core.data.TestApollo
import com.tobfd.tsuzuki.core.database.TsuzukiDatabase
import com.tobfd.tsuzuki.core.model.EntryChanges
import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.core.network.DeleteMediaListEntryMutation
import com.tobfd.tsuzuki.core.network.MediaListCollectionQuery
import com.tobfd.tsuzuki.core.network.SaveMediaListEntryMutation
import com.tobfd.tsuzuki.core.network.type.MediaType as NetworkMediaType
import com.tobfd.tsuzuki.core.testing.FakeSessionRepository
import com.tobfd.tsuzuki.core.testing.SampleData
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A scheduler that only counts, so tests decide when the queue is sent. */
internal class FakeListWorkScheduler : ListWorkScheduler {
    var sendRequests = 0
    var periodicSyncScheduled = false
    var cancelled = false

    override fun sendQueuedChanges() {
        sendRequests++
    }

    override fun schedulePeriodicSync() {
        periodicSyncScheduled = true
    }

    override fun cancelAll() {
        cancelled = true
    }
}

// SDK 35: Robolectric's SDK 36 setup fails on the JDK 25 test runtime (FileDescriptor reflection).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultListRepositoryTest {

    private val apollo = TestApollo()
    private val clock = MutableClock(Instant.parse("2026-09-29T10:00:00Z"))
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        TsuzukiDatabase::class.java
    )
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val scheduler = FakeListWorkScheduler()
    private val session = FakeSessionRepository(SessionState.LoggedIn(SampleData.viewer, SampleData.tokenExpiry))
    private val sender = ListMutationSender(apollo.client, database.pendingMutationDao(), database.mediaListDao())
    private val repository = DefaultListRepository(
        apolloClient = apollo.client,
        database = database,
        listDao = database.mediaListDao(),
        mutationDao = database.pendingMutationDao(),
        sender = sender,
        scheduler = scheduler,
        sessionRepository = session,
        clock = clock
    )

    private val anyCollection = MediaListCollectionQuery(1, NetworkMediaType.ANIME)
    private val anySave = SaveMediaListEntryMutation()

    @After
    fun tearDown() {
        apollo.client.close()
        database.close()
    }

    /** Syncs [anime] (one chunk) and an empty manga list. */
    private suspend fun syncAnime(vararg anime: String) {
        apollo.enqueueJson(anyCollection, collectionJson(false, Triple("Watching", false, anime.toList())))
        apollo.enqueueJson(anyCollection, collectionJson(false))
        repository.refresh(force = true).getOrThrow()
    }

    private suspend fun entry(id: Int) = repository.observeList(MediaType.ANIME).first().entries.first { it.id == id }

    // --- sync ---

    @Test
    fun refresh_readsEveryChunkAndKeepsEachEntryOnce() = runTest {
        val favs = mapOf("Favs" to true, "Later" to false)
        apollo.enqueueJson(
            anyCollection,
            collectionJson(
                true,
                Triple("Watching", false, listOf(entryJson(1, customLists = favs))),
                Triple("Favs", true, listOf(entryJson(1, customLists = favs)))
            )
        )
        apollo.enqueueJson(
            anyCollection,
            collectionJson(false, Triple("Planning", false, listOf(entryJson(2, status = "PLANNING"))))
        )
        apollo.enqueueJson(
            anyCollection,
            collectionJson(false, Triple("Reading", false, listOf(entryJson(3, type = "MANGA"))))
        )

        repository.refresh(force = true).getOrThrow()

        val anime = repository.observeList(MediaType.ANIME).first()
        assertEquals(listOf(1, 2), anime.entries.map { it.id }.sorted())
        assertEquals(listOf("Favs", "Later"), anime.customLists)
        assertEquals(setOf("Favs"), anime.entries.first { it.id == 1 }.customLists)
        assertEquals(listOf(3), repository.observeList(MediaType.MANGA).first().entries.map { it.id })
        assertEquals(3, apollo.requests)
    }

    @Test
    fun refresh_storesTheRawScoreAndMedia() = runTest {
        syncAnime(entryJson(1, title = "Sousou no Frieren", progress = 18, scoreRaw = 85))

        val frieren = entry(1)
        assertEquals(85, frieren.scoreRaw)
        assertEquals(18, frieren.progress)
        assertEquals("Sousou no Frieren", frieren.media.title.userPreferred)
        assertEquals(28, frieren.media.total)
        assertEquals(FuzzyDate(2026, 1, 5), frieren.startedAt)
        assertNull(frieren.completedAt)
    }

    @Test
    fun refresh_withinFifteenMinutes_isSkippedUnlessForced() = runTest {
        syncAnime(entryJson(1))
        clock.advanceBy(Duration.ofMinutes(14))

        repository.refresh(force = false).getOrThrow()
        assertEquals(2, apollo.requests)

        clock.advanceBy(Duration.ofMinutes(1))
        apollo.enqueueJson(anyCollection, collectionJson(false, Triple("Watching", false, listOf(entryJson(1)))))
        apollo.enqueueJson(anyCollection, collectionJson(false))
        repository.refresh(force = false).getOrThrow()
        assertEquals(4, apollo.requests)
    }

    @Test
    fun refresh_offline_failsAndKeepsTheList() = runTest {
        syncAnime(entryJson(1))
        apollo.enqueueOffline(anyCollection)

        val result = repository.refresh(force = true)

        assertEquals(AppError.Offline, result.exceptionOrNull())
        assertEquals(listOf(1), repository.observeList(MediaType.ANIME).first().entries.map { it.id })
    }

    @Test
    fun refresh_forGuests_doesNothing() = runTest {
        session.sessionState.value = SessionState.Guest
        assertTrue(repository.refresh(force = true).isSuccess)
        assertEquals(0, apollo.requests)
    }

    // --- local changes ---

    @Test
    fun plusOne_changesRoomAtOnceAndQueuesIt() = runTest {
        syncAnime(entryJson(1, progress = 18))

        val outcome = repository.plusOne(1)!!

        assertFalse(outcome.completed)
        assertEquals(18, outcome.before.progress)
        assertEquals(19, entry(1).progress)
        assertEquals(1, repository.queuedChangeCount.first())
        assertEquals(1, scheduler.sendRequests)
    }

    @Test
    fun plusOne_reachingTheTotal_completesTheEntry() = runTest {
        syncAnime(entryJson(1, progress = 27))

        val outcome = repository.plusOne(1)!!

        assertTrue(outcome.completed)
        assertEquals(MediaListStatus.COMPLETED, entry(1).status)
        assertEquals(FuzzyDate(2026, 9, 29), entry(1).completedAt)
    }

    @Test
    fun plusOne_onAPlannedEntry_doesNothing() = runTest {
        syncAnime(entryJson(1, status = "PLANNING"))
        assertNull(repository.plusOne(1))
        assertEquals(0, repository.queuedChangeCount.first())
    }

    @Test
    fun start_setsWatchingFromZeroToday() = runTest {
        syncAnime(entryJson(1, status = "PLANNING", progress = 3))

        repository.start(1)

        val started = entry(1)
        assertEquals(MediaListStatus.CURRENT, started.status)
        assertEquals(0, started.progress)
        assertEquals(FuzzyDate(2026, 9, 29), started.startedAt)
    }

    @Test
    fun restore_bringsTheEntryBack() = runTest {
        syncAnime(entryJson(1, progress = 27))
        val outcome = repository.plusOne(1)!!

        repository.restore(outcome.before)

        val restored = entry(1)
        assertEquals(27, restored.progress)
        assertEquals(MediaListStatus.CURRENT, restored.status)
        assertNull(restored.completedAt)
    }

    @Test
    fun update_withoutChanges_queuesNothing() = runTest {
        syncAnime(entryJson(1))
        assertNull(repository.update(1, EntryChanges()))
        assertNull(repository.update(1, EntryChanges(progress = 0)))
    }

    @Test
    fun delete_removesTheEntryAndQueuesIt() = runTest {
        syncAnime(entryJson(1), entryJson(2))

        repository.delete(1)

        assertEquals(listOf(2), repository.observeList(MediaType.ANIME).first().entries.map { it.id })
        assertEquals(1, repository.queuedChangeCount.first())
    }

    @Test
    fun localChanges_surviveASyncThatHappensBeforeTheyAreSent() = runTest {
        syncAnime(entryJson(1, progress = 18))
        repository.plusOne(1)
        // The send fails offline; the sync that follows still sees the old progress on AniList.
        apollo.enqueueOffline(anySave)

        syncAnime(entryJson(1, progress = 18))

        assertEquals(19, entry(1).progress)
        assertEquals(1, repository.queuedChangeCount.first())
    }

    // --- sending ---

    @Test
    fun severalPlusOnes_goOutAsOneRequestWithTheLastValue() = runTest {
        syncAnime(entryJson(1, progress = 18))
        repeat(3) { repository.plusOne(1) }
        apollo.enqueueJson(anySave, saveResponseJson(entryJson(1, progress = 21)))

        assertEquals(FlushResult.Done, sender.flush())

        val sent = apollo.operations.filterIsInstance<SaveMediaListEntryMutation>().single()
        assertEquals(Optional.present(1), sent.id)
        assertEquals(Optional.present(21), sent.progress)
        assertEquals(Optional.Absent, sent.status)
        assertEquals(0, repository.queuedChangeCount.first())
    }

    @Test
    fun plusOneUndoneBeforeSending_sendsNothing() = runTest {
        syncAnime(entryJson(1, progress = 27))
        val outcome = repository.plusOne(1)!!
        repository.restore(outcome.before)
        val requestsBefore = apollo.requests

        assertEquals(FlushResult.Done, sender.flush())

        assertEquals(requestsBefore, apollo.requests)
        assertEquals(0, repository.queuedChangeCount.first())
    }

    @Test
    fun offline_keepsTheQueueForARetry() = runTest {
        syncAnime(entryJson(1, progress = 18))
        val changeId = repository.update(1, EntryChanges(progress = 20))!!
        apollo.enqueueOffline(anySave)

        assertEquals(FlushResult.Retry, sender.flush())

        assertEquals(ChangeState.Queued, repository.observeChange(changeId).first())
        assertEquals(20, entry(1).progress)
    }

    @Test
    fun sentChange_isReportedAsSent() = runTest {
        syncAnime(entryJson(1, progress = 18))
        val changeId = repository.update(1, EntryChanges(progress = 20))!!
        apollo.enqueueJson(anySave, saveResponseJson(entryJson(1, progress = 20)))

        sender.flush()

        assertEquals(ChangeState.Sent, repository.observeChange(changeId).first())
    }

    @Test
    fun rejectedChange_isRolledBackAndExplained() = runTest {
        syncAnime(entryJson(1, title = "Sousou no Frieren", progress = 18))
        val changeId = repository.update(1, EntryChanges(progress = 40))!!
        repository.update(1, EntryChanges(scoreRaw = 90))
        apollo.enqueueJson(anySave, validationErrorJson("progress", "The progress may not be greater than 28."))

        assertEquals(FlushResult.Done, sender.flush())

        val rolledBack = entry(1)
        assertEquals(18, rolledBack.progress)
        assertEquals(0, rolledBack.scoreRaw)
        val rejected = repository.rejectedChanges.first().single()
        assertEquals("Sousou no Frieren", rejected.title)
        assertEquals(RejectReason.Validation, rejected.reason)
        assertEquals(mapOf("progress" to listOf("The progress may not be greater than 28.")), rejected.fields)
        assertTrue(repository.observeChange(changeId).first() is ChangeState.Rejected)
        assertEquals(0, repository.queuedChangeCount.first())

        repository.dismissRejection(1)
        assertEquals(emptyList<RejectedChange>(), repository.rejectedChanges.first())
    }

    @Test
    fun changeToAnEntryRemovedElsewhere_dropsItHere() = runTest {
        syncAnime(entryJson(1), entryJson(2))
        repository.update(1, EntryChanges(progress = 3))
        apollo.enqueueJson(anySave, NOT_FOUND_JSON)

        sender.flush()

        assertEquals(listOf(2), repository.observeList(MediaType.ANIME).first().entries.map { it.id })
        assertEquals(RejectReason.NotFound, repository.rejectedChanges.first().single().reason)
    }

    @Test
    fun unexpectedErrors_areRetriedThenGivenUp() = runTest {
        syncAnime(entryJson(1, progress = 18))
        repository.update(1, EntryChanges(progress = 20))

        repeat(MAX_ATTEMPTS - 1) {
            apollo.enqueueJson(anySave, SERVER_ERROR_JSON)
            assertEquals(FlushResult.Retry, sender.flush())
        }
        apollo.enqueueJson(anySave, SERVER_ERROR_JSON)
        assertEquals(FlushResult.Done, sender.flush())

        assertEquals(18, entry(1).progress)
        assertEquals(RejectReason.Other, repository.rejectedChanges.first().single().reason)
    }

    @Test
    fun delete_isSentAndAlreadyGoneCountsAsDone() = runTest {
        syncAnime(entryJson(1), entryJson(2))
        repository.delete(1)
        repository.delete(2)
        apollo.enqueueJson(DeleteMediaListEntryMutation(0), DELETE_RESPONSE_JSON)
        apollo.enqueueJson(DeleteMediaListEntryMutation(0), NOT_FOUND_JSON)

        assertEquals(FlushResult.Done, sender.flush())

        assertEquals(listOf(1, 2), apollo.operations.filterIsInstance<DeleteMediaListEntryMutation>().map { it.id })
        assertEquals(0, repository.queuedChangeCount.first())
        assertEquals(emptyList<RejectedChange>(), repository.rejectedChanges.first())
    }

    @Test
    fun changesBeforeADelete_areNotSent() = runTest {
        syncAnime(entryJson(1, progress = 3))
        repository.plusOne(1)
        repository.delete(1)
        apollo.enqueueJson(DeleteMediaListEntryMutation(0), DELETE_RESPONSE_JSON)

        sender.flush()

        assertEquals(
            emptyList<SaveMediaListEntryMutation>(),
            apollo.operations.filterIsInstance<SaveMediaListEntryMutation>()
        )
    }

    @Test
    fun scheduleBackgroundSync_schedulesThePeriodicWork() {
        repository.scheduleBackgroundSync()
        assertTrue(scheduler.periodicSyncScheduled)
    }
}
