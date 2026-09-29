package com.tobfd.tsuzuki.feature.media

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.MediaRanking
import com.tobfd.tsuzuki.core.model.RankingType
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val DESCRIPTION_LINES = 4

/** Spoilers in AniList HTML: `<span class='markdown_spoiler'>…</span>`, or raw `~!…!~`. */
private val SpoilerPattern =
    Regex("""<span class=['"]markdown_spoiler['"]>(.*?)</span>|~!(.*?)!~""", RegexOption.DOT_MATCHES_ALL)

/** [html] with spoilers replaced by [placeholder], or unwrapped when [reveal] is set. */
internal fun withSpoilers(html: String, reveal: Boolean, placeholder: String): String =
    SpoilerPattern.replace(html) { match ->
        if (reveal) match.groupValues[1].ifEmpty { match.groupValues[2] } else "<i>$placeholder</i>"
    }

internal fun hasSpoilers(html: String): Boolean = SpoilerPattern.containsMatchIn(html)

/** The description: AniList's HTML with links, spoilers hidden, 4 lines until "Read more". */
@Composable
internal fun Description(html: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    val placeholder = stringResource(R.string.media_spoiler)
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(html, revealed, linkColor) {
        AnnotatedString.fromHtml(
            withSpoilers(html, revealed, placeholder),
            linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor))
        )
    }
    var overflows by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow }
        )
        Row {
            if (overflows || expanded) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.media_read_less else R.string.media_read_more))
                }
            }
            if (hasSpoilers(html)) {
                TextButton(onClick = { revealed = !revealed }) {
                    Text(stringResource(if (revealed) R.string.media_hide_spoilers else R.string.media_show_spoilers))
                }
            }
        }
    }
}

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

@Composable
internal fun seasonLabel(season: String): String? = when (season) {
    "WINTER" -> stringResource(R.string.media_season_winter)
    "SPRING" -> stringResource(R.string.media_season_spring)
    "SUMMER" -> stringResource(R.string.media_season_summer)
    "FALL" -> stringResource(R.string.media_season_fall)
    else -> null
}

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

@Composable
internal fun characterRoleLabel(role: String): String = when (role) {
    "MAIN" -> stringResource(R.string.media_role_main)
    "SUPPORTING" -> stringResource(R.string.media_role_supporting)
    else -> stringResource(R.string.media_role_background)
}

/** A full date in the locale's medium style; partial dates as far as they are known. */
@Composable
internal fun formatDate(date: FuzzyDate): String {
    val locale = LocalConfiguration.current.locales[0]
    date.toLocalDateOrNull()?.let {
        return it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
    val year = date.year ?: return "?"
    val month = date.month ?: return year.toString()
    return YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("MMM yyyy", locale))
}
