package com.tobfd.tsuzuki.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.tobfd.tsuzuki.core.designsystem.theme.StatusColor
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTheme
import com.tobfd.tsuzuki.core.model.MediaListStatus
import com.tobfd.tsuzuki.core.model.MediaType

/** The status color set for this list status. */
@Composable
@ReadOnlyComposable
fun MediaListStatus.statusColor(): StatusColor {
    val colors = TsuzukiTheme.statusColors
    return when (this) {
        MediaListStatus.CURRENT -> colors.current
        MediaListStatus.PLANNING -> colors.planning
        MediaListStatus.COMPLETED -> colors.completed
        MediaListStatus.PAUSED -> colors.paused
        MediaListStatus.DROPPED -> colors.dropped
        MediaListStatus.REPEATING -> colors.repeating
    }
}

/** Label for this status; current and repeating read differently for anime and manga. */
@StringRes
fun MediaListStatus.labelRes(type: MediaType): Int = when (this) {
    MediaListStatus.CURRENT -> when (type) {
        MediaType.ANIME -> R.string.ui_status_watching
        MediaType.MANGA -> R.string.ui_status_reading
    }

    MediaListStatus.PLANNING -> R.string.ui_status_planning

    MediaListStatus.COMPLETED -> R.string.ui_status_completed

    MediaListStatus.PAUSED -> R.string.ui_status_paused

    MediaListStatus.DROPPED -> R.string.ui_status_dropped

    MediaListStatus.REPEATING -> when (type) {
        MediaType.ANIME -> R.string.ui_status_rewatching
        MediaType.MANGA -> R.string.ui_status_rereading
    }
}
