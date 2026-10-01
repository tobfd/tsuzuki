package com.tobfd.tsuzuki.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.tobfd.tsuzuki.core.database.entity.AiringUpdate
import com.tobfd.tsuzuki.core.database.entity.CustomListEntity
import com.tobfd.tsuzuki.core.database.entity.EntryWithMedia
import com.tobfd.tsuzuki.core.database.entity.ListSyncEntity
import com.tobfd.tsuzuki.core.database.entity.MediaListEntryEntity
import com.tobfd.tsuzuki.core.database.entity.MediaLiteEntity
import kotlinx.coroutines.flow.Flow

/** The viewer's lists: entries, their media, custom list names and sync times. */
@Dao
abstract class MediaListDao {

    @Transaction
    @Query("SELECT * FROM media_list_entry WHERE type = :type")
    abstract fun observeEntries(type: String): Flow<List<EntryWithMedia>>

    @Transaction
    @Query("SELECT * FROM media_list_entry WHERE media_id = :mediaId ORDER BY updated_at DESC LIMIT 1")
    abstract fun observeEntryByMediaId(mediaId: Int): Flow<EntryWithMedia?>

    @Transaction
    @Query("SELECT * FROM media_list_entry WHERE id = :id")
    abstract suspend fun getEntry(id: Int): EntryWithMedia?

    @Query("SELECT * FROM custom_list WHERE type = :type ORDER BY position")
    abstract fun observeCustomLists(type: String): Flow<List<CustomListEntity>>

    @Upsert
    abstract suspend fun upsertMedia(media: List<MediaLiteEntity>)

    @Upsert
    abstract suspend fun upsertEntries(entries: List<MediaListEntryEntity>)

    @Upsert
    abstract suspend fun upsertEntry(entry: MediaListEntryEntity)

    @Query("DELETE FROM media_list_entry WHERE id = :id")
    abstract suspend fun deleteEntry(id: Int)

    @Query("SELECT DISTINCT entry_id FROM pending_mutation")
    abstract suspend fun entryIdsWithPendingChanges(): List<Int>

    /** Rows of [type] that the sync run [run] didn't see, except those with changes still to send. */
    @Query(
        "DELETE FROM media_list_entry WHERE type = :type AND sync_run != :run " +
            "AND id NOT IN (SELECT entry_id FROM pending_mutation)"
    )
    abstract suspend fun deleteEntriesMissingFromSync(type: String, run: Long)

    /**
     * Picks the shown title of every media again after the viewer changed their title language
     * (`ROMAJI`, `ENGLISH` or `NATIVE`), falling back to romaji like AniList's `userPreferred`.
     */
    @Query(
        """
        UPDATE media_lite SET title_user_preferred = COALESCE(
            CASE :language WHEN 'ENGLISH' THEN title_english WHEN 'NATIVE' THEN title_native ELSE NULL END,
            title_romaji,
            title_user_preferred
        )
        """
    )
    abstract suspend fun applyTitleLanguage(language: String)

    /**
     * Anime the viewer is watching (or rewatching) that are still airing or not released yet, soonest
     * next episode first: the ids the widgets' airing refresh asks AniList about.
     */
    @Query(
        """
        SELECT DISTINCT m.id FROM media_lite m JOIN media_list_entry e ON e.media_id = m.id
        WHERE e.type = 'ANIME' AND e.status IN ('CURRENT', 'REPEATING')
            AND m.status IN ('RELEASING', 'NOT_YET_RELEASED')
        ORDER BY m.next_airing_at IS NULL, m.next_airing_at
        LIMIT :limit
        """
    )
    abstract suspend fun airingAnimeIds(limit: Int): List<Int>

    @Query(
        "UPDATE media_lite SET next_airing_episode = :episode, next_airing_at = :airingAt, " +
            "status = COALESCE(:status, status), episodes = COALESCE(:episodes, episodes) WHERE id = :id"
    )
    abstract suspend fun updateAiring(id: Int, episode: Int?, airingAt: Long?, status: String?, episodes: Int?)

    /** Writes the next episodes from one airing refresh in a single transaction. */
    @Transaction
    open suspend fun updateAiring(updates: List<AiringUpdate>) {
        updates.forEach { updateAiring(it.mediaId, it.episode, it.airingAt, it.status, it.episodes) }
    }

    /** Media rows no entry refers to any more (no data beyond what the app needs). */
    @Query("DELETE FROM media_lite WHERE id NOT IN (SELECT media_id FROM media_list_entry)")
    abstract suspend fun deleteUnusedMedia()

    @Query("DELETE FROM custom_list WHERE type = :type")
    abstract suspend fun deleteCustomLists(type: String)

    @Insert
    abstract suspend fun insertCustomLists(lists: List<CustomListEntity>)

    @Query("SELECT * FROM list_sync WHERE type = :type")
    abstract suspend fun getSync(type: String): ListSyncEntity?

    @Upsert
    abstract suspend fun upsertSync(sync: ListSyncEntity)

    /**
     * Writes one full sync of [type] in a single transaction. Entries with local changes still
     * waiting to be sent keep their local state, so a sync never undoes them.
     */
    @Transaction
    open suspend fun writeSync(
        type: String,
        run: Long,
        media: List<MediaLiteEntity>,
        entries: List<MediaListEntryEntity>,
        customLists: List<CustomListEntity>,
        sync: ListSyncEntity
    ) {
        val pending = entryIdsWithPendingChanges().toSet()
        upsertMedia(media)
        upsertEntries(entries.filterNot { it.id in pending })
        deleteEntriesMissingFromSync(type, run)
        deleteUnusedMedia()
        deleteCustomLists(type)
        insertCustomLists(customLists)
        upsertSync(sync)
    }
}
