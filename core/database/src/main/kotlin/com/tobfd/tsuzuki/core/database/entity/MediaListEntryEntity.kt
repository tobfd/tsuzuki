package com.tobfd.tsuzuki.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

/**
 * One entry of the viewer's list. The Lists tab and Home "In Progress" read only from here, so they
 * work offline. Local changes are written here first and then sent through `pending_mutation`.
 */
@Entity(
    tableName = "media_list_entry",
    indices = [Index(value = ["media_id"]), Index(value = ["type", "status"])]
)
data class MediaListEntryEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "media_id") val mediaId: Int,
    /** `ANIME` or `MANGA`, copied from the media so a list can be read without a join. */
    val type: String,
    val status: String,
    /** 0–100 (`score(format: POINT_100)`), whatever the viewer's score format. */
    @ColumnInfo(name = "score_raw") val scoreRaw: Int,
    val progress: Int,
    @ColumnInfo(name = "progress_volumes") val progressVolumes: Int,
    val repeat: Int,
    @ColumnInfo(name = "is_private") val isPrivate: Boolean,
    val notes: String,
    @ColumnInfo(name = "hidden_from_status_lists") val hiddenFromStatusLists: Boolean,
    /** Names of the custom lists the entry is on, see [CustomListNames]. */
    @ColumnInfo(name = "custom_lists") val customLists: String,
    @ColumnInfo(name = "started_year") val startedYear: Int?,
    @ColumnInfo(name = "started_month") val startedMonth: Int?,
    @ColumnInfo(name = "started_day") val startedDay: Int?,
    @ColumnInfo(name = "completed_year") val completedYear: Int?,
    @ColumnInfo(name = "completed_month") val completedMonth: Int?,
    @ColumnInfo(name = "completed_day") val completedDay: Int?,
    /** Epoch seconds, as AniList sends it; local changes set it too. */
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
    /**
     * The sync run that last wrote this row. After a full sync, rows of that type from older runs
     * are gone on AniList and get deleted, unless a local change is still waiting to be sent.
     */
    @ColumnInfo(name = "sync_run") val syncRun: Long
)

/** An entry with its media, as the list screens show it. */
data class EntryWithMedia(
    @Embedded val entry: MediaListEntryEntity,
    @Relation(parentColumns = ["media_id"], entityColumns = ["id"]) val media: MediaLiteEntity?
)

/**
 * Custom list names in one column. The unit separator never appears in a list name typed on
 * AniList, so a plain split restores the set.
 */
object CustomListNames {
    private const val SEPARATOR = '\u001F'

    fun encode(names: Collection<String>): String = names.joinToString(SEPARATOR.toString())

    fun decode(encoded: String): List<String> = if (encoded.isEmpty()) emptyList() else encoded.split(SEPARATOR)
}
