package com.tobfd.tsuzuki.core.model

import java.time.Instant

/** An entry on the viewer's anime or manga list, with the media it belongs to. */
data class MediaListEntry(
    val id: Int,
    val mediaId: Int,
    val status: MediaListStatus,
    /** Score from 0 to 100 whatever the viewer's score format; 0 means not scored. */
    val scoreRaw: Int,
    /** Episodes for anime, chapters for manga. */
    val progress: Int,
    val progressVolumes: Int,
    /** How often it was rewatched or reread. */
    val repeat: Int,
    val isPrivate: Boolean,
    val notes: String,
    val hiddenFromStatusLists: Boolean,
    /** Names of the viewer's custom lists this entry is on. */
    val customLists: Set<String>,
    val startedAt: FuzzyDate?,
    val completedAt: FuzzyDate?,
    val updatedAt: Instant?,
    val media: MediaLite
) {
    val type: MediaType
        get() = media.type
}

/** A new value for a date that may also be removed ([date] null). */
data class DateChange(val date: FuzzyDate?)

/** Changes to a list entry; null fields stay as they are. */
data class EntryChanges(
    val status: MediaListStatus? = null,
    val scoreRaw: Int? = null,
    val progress: Int? = null,
    val progressVolumes: Int? = null,
    val repeat: Int? = null,
    val isPrivate: Boolean? = null,
    val notes: String? = null,
    val hiddenFromStatusLists: Boolean? = null,
    val customLists: Set<String>? = null,
    val startedAt: DateChange? = null,
    val completedAt: DateChange? = null
) {
    fun isEmpty(): Boolean = this == EntryChanges()

    fun applyTo(entry: MediaListEntry): MediaListEntry = entry.copy(
        status = status ?: entry.status,
        scoreRaw = scoreRaw ?: entry.scoreRaw,
        progress = progress ?: entry.progress,
        progressVolumes = progressVolumes ?: entry.progressVolumes,
        repeat = repeat ?: entry.repeat,
        isPrivate = isPrivate ?: entry.isPrivate,
        notes = notes ?: entry.notes,
        hiddenFromStatusLists = hiddenFromStatusLists ?: entry.hiddenFromStatusLists,
        customLists = customLists ?: entry.customLists,
        startedAt = if (startedAt != null) startedAt.date else entry.startedAt,
        completedAt = if (completedAt != null) completedAt.date else entry.completedAt
    )

    /** These changes followed by [later]; where both set a field, [later] wins. */
    fun then(later: EntryChanges): EntryChanges = EntryChanges(
        status = later.status ?: status,
        scoreRaw = later.scoreRaw ?: scoreRaw,
        progress = later.progress ?: progress,
        progressVolumes = later.progressVolumes ?: progressVolumes,
        repeat = later.repeat ?: repeat,
        isPrivate = later.isPrivate ?: isPrivate,
        notes = later.notes ?: notes,
        hiddenFromStatusLists = later.hiddenFromStatusLists ?: hiddenFromStatusLists,
        customLists = later.customLists ?: customLists,
        startedAt = later.startedAt ?: startedAt,
        completedAt = later.completedAt ?: completedAt
    )

    companion object {
        /** The fields that differ from [before] to [after], so only those are sent. */
        fun between(before: MediaListEntry, after: MediaListEntry): EntryChanges = EntryChanges(
            status = after.status.takeIf { it != before.status },
            scoreRaw = after.scoreRaw.takeIf { it != before.scoreRaw },
            progress = after.progress.takeIf { it != before.progress },
            progressVolumes = after.progressVolumes.takeIf { it != before.progressVolumes },
            repeat = after.repeat.takeIf { it != before.repeat },
            isPrivate = after.isPrivate.takeIf { it != before.isPrivate },
            notes = after.notes.takeIf { it != before.notes },
            hiddenFromStatusLists = after.hiddenFromStatusLists.takeIf { it != before.hiddenFromStatusLists },
            customLists = after.customLists.takeIf { it != before.customLists },
            startedAt = DateChange(after.startedAt).takeIf { after.startedAt != before.startedAt },
            completedAt = DateChange(after.completedAt).takeIf { after.completedAt != before.completedAt }
        )
    }
}
