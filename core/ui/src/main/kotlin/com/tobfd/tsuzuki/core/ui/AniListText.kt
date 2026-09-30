package com.tobfd.tsuzuki.core.ui

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
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.model.FuzzyDate
import com.tobfd.tsuzuki.core.model.MediaSeason
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val COLLAPSED_LINES = 4

/** Spoilers in AniList HTML: `<span class='markdown_spoiler'>…</span>`, or raw `~!…!~`. */
private val SpoilerPattern =
    Regex("""<span class=['"]markdown_spoiler['"]>(.*?)</span>|~!(.*?)!~""", RegexOption.DOT_MATCHES_ALL)

/** [html] with spoilers replaced by [placeholder], or unwrapped when [reveal] is set. */
fun withSpoilers(html: String, reveal: Boolean, placeholder: String): String = SpoilerPattern.replace(html) { match ->
    if (reveal) match.groupValues[1].ifEmpty { match.groupValues[2] } else "<i>$placeholder</i>"
}

fun hasSpoilers(html: String): Boolean = SpoilerPattern.containsMatchIn(html)

/**
 * AniList's HTML subset (descriptions, bios; docs/ANILIST_API.md, Text and HTML) with links, spoilers
 * hidden until "Show spoilers", and [collapsedLines] lines until "Read more".
 */
@Composable
fun AniListHtmlText(html: String, modifier: Modifier = Modifier, collapsedLines: Int = COLLAPSED_LINES) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    val placeholder = stringResource(R.string.ui_spoiler)
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
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow }
        )
        Row {
            if (overflows || expanded) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.ui_read_less else R.string.ui_read_more))
                }
            }
            if (hasSpoilers(html)) {
                TextButton(onClick = { revealed = !revealed }) {
                    Text(stringResource(if (revealed) R.string.ui_hide_spoilers else R.string.ui_show_spoilers))
                }
            }
        }
    }
}

fun MediaSeason.labelRes(): Int = when (this) {
    MediaSeason.WINTER -> R.string.ui_season_winter
    MediaSeason.SPRING -> R.string.ui_season_spring
    MediaSeason.SUMMER -> R.string.ui_season_summer
    MediaSeason.FALL -> R.string.ui_season_fall
}

/** AniList's `CharacterRole`: `MAIN`, `SUPPORTING` or `BACKGROUND`. */
@Composable
fun characterRoleLabel(role: String): String = when (role) {
    "MAIN" -> stringResource(R.string.ui_role_main)
    "SUPPORTING" -> stringResource(R.string.ui_role_supporting)
    else -> stringResource(R.string.ui_role_background)
}

/** A full date in the locale's medium style; partial dates as far as they are known. */
@Composable
fun formatDate(date: FuzzyDate): String {
    val locale = LocalConfiguration.current.locales[0]
    date.toLocalDateOrNull()?.let {
        return it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }
    val year = date.year ?: return "?"
    val month = date.month ?: return year.toString()
    return YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("MMM yyyy", locale))
}

@ThemePreviews
@Composable
private fun AniListHtmlTextPreview() {
    TsuzukiPreview {
        AniListHtmlText(
            html = "An elf <i>mage</i> who outlived her party.<br><br>" +
                "<span class='markdown_spoiler'>She meets Himmel again.</span> <a href='https://anilist.co'>AniList</a>"
        )
    }
}
