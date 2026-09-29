package com.tobfd.tsuzuki.core.ui

import androidx.annotation.StringRes
import com.tobfd.tsuzuki.core.model.MediaFormat

/** Label for a media format, as AniList names it ("TV", "Movie", "Light Novel"). */
@StringRes
fun MediaFormat.labelRes(): Int = when (this) {
    MediaFormat.TV -> R.string.ui_format_tv
    MediaFormat.TV_SHORT -> R.string.ui_format_tv_short
    MediaFormat.MOVIE -> R.string.ui_format_movie
    MediaFormat.SPECIAL -> R.string.ui_format_special
    MediaFormat.OVA -> R.string.ui_format_ova
    MediaFormat.ONA -> R.string.ui_format_ona
    MediaFormat.MUSIC -> R.string.ui_format_music
    MediaFormat.MANGA -> R.string.ui_format_manga
    MediaFormat.NOVEL -> R.string.ui_format_novel
    MediaFormat.ONE_SHOT -> R.string.ui_format_one_shot
}
