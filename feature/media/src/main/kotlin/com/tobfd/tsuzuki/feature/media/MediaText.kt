package com.tobfd.tsuzuki.feature.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tobfd.tsuzuki.core.model.MediaRanking
import com.tobfd.tsuzuki.core.model.RankingType

/** "#1 highest rated all time", "#3 most popular 2023". */
@Composable
internal fun rankingText(ranking: MediaRanking): String = when {
    ranking.type == RankingType.Rated && ranking.allTime -> stringResource(
        R.string.media_rank_rated_all_time,
        ranking.rank
    )

    ranking.type == RankingType.Rated -> stringResource(R.string.media_rank_rated_year, ranking.rank, ranking.year ?: 0)

    ranking.allTime -> stringResource(R.string.media_rank_popular_all_time, ranking.rank)

    else -> stringResource(R.string.media_rank_popular_year, ranking.rank, ranking.year ?: 0)
}

/** AniList's `MediaRelation`, e.g. SEQUEL → "Sequel". */
@Composable
internal fun relationLabel(type: String): String = stringResource(
    when (type) {
        "ADAPTATION" -> R.string.media_relation_adaptation
        "PREQUEL" -> R.string.media_relation_prequel
        "SEQUEL" -> R.string.media_relation_sequel
        "PARENT" -> R.string.media_relation_parent
        "SIDE_STORY" -> R.string.media_relation_side_story
        "CHARACTER" -> R.string.media_relation_character
        "SUMMARY" -> R.string.media_relation_summary
        "ALTERNATIVE" -> R.string.media_relation_alternative
        "SPIN_OFF" -> R.string.media_relation_spin_off
        "SOURCE" -> R.string.media_relation_source
        "COMPILATION" -> R.string.media_relation_compilation
        "CONTAINS" -> R.string.media_relation_contains
        else -> R.string.media_relation_other
    }
)

/** AniList's `MediaSource`; unknown values are shown as "Light novel" style words. */
@Composable
internal fun sourceLabel(source: String): String = when (source) {
    "ORIGINAL" -> stringResource(R.string.media_source_original)
    "MANGA" -> stringResource(R.string.media_source_manga)
    "LIGHT_NOVEL" -> stringResource(R.string.media_source_light_novel)
    "NOVEL" -> stringResource(R.string.media_source_novel)
    "WEB_NOVEL" -> stringResource(R.string.media_source_web_novel)
    "VISUAL_NOVEL" -> stringResource(R.string.media_source_visual_novel)
    "VIDEO_GAME" -> stringResource(R.string.media_source_video_game)
    "ANIME" -> stringResource(R.string.media_source_anime)
    else -> source.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
