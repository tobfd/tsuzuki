package com.tobfd.tsuzuki.core.ui

import androidx.annotation.StringRes
import com.tobfd.tsuzuki.core.model.MediaStatus

/**
 * Label for the release status of a series. Its words never overlap the list status labels
 * (docs/DESIGN.md, "Status wording").
 */
@StringRes
fun MediaStatus.labelRes(): Int = when (this) {
    MediaStatus.RELEASING -> R.string.ui_media_status_releasing
    MediaStatus.FINISHED -> R.string.ui_media_status_finished
    MediaStatus.NOT_YET_RELEASED -> R.string.ui_media_status_not_yet_released
    MediaStatus.CANCELLED -> R.string.ui_media_status_cancelled
    MediaStatus.HIATUS -> R.string.ui_media_status_hiatus
}
