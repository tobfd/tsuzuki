package com.tobfd.tsuzuki.feature.widgets.inprogress

import com.tobfd.tsuzuki.core.model.MediaListEntry
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.feature.widgets.WIDGET_MAX_ITEMS

/** One entry of the "In Progress" widget. */
internal data class InProgressItem(
    val entryId: Int,
    val mediaId: Int,
    val type: MediaType,
    val title: String,
    val progress: Int,
    /** Episodes or chapters; null while unknown. */
    val total: Int?,
    val coverUrl: String?,
    val coverColor: String?
) {
    /** Like the app: no +1 once the total is reached. */
    val canPlusOne: Boolean get() = total == null || progress < total

    /**
     * The next +1 would complete the entry. The widget can't offer Undo, so that +1 opens the app's list
     * editor instead (decided 2026-10-01).
     */
    val plusOneCompletes: Boolean get() = total != null && progress + 1 >= total
}

internal sealed interface InProgressState {
    /** Guests and logged-out users: the widget asks to log in. */
    data object LoggedOut : InProgressState

    /** [queuedChanges] are list changes not sent yet (offline), shown under the list. */
    data class Ready(val items: List<InProgressItem>, val queuedChanges: Int) : InProgressState
}

/**
 * Home's "In Progress" (docs/ROADMAP.md, M5): anime and manga being watched, read or repeated, the
 * most recently changed first.
 */
internal fun inProgressItems(entries: List<MediaListEntry>): List<InProgressItem> = entries
    .filter { it.status == MediaListStatus.CURRENT || it.status == MediaListStatus.REPEATING }
    .sortedByDescending { it.updatedAt }
    .take(WIDGET_MAX_ITEMS)
    .map {
        InProgressItem(
            entryId = it.id,
            mediaId = it.mediaId,
            type = it.type,
            title = it.media.title.userPreferred,
            progress = it.progress,
            total = it.media.total,
            coverUrl = it.media.coverUrl,
            coverColor = it.media.coverColor
        )
    }
