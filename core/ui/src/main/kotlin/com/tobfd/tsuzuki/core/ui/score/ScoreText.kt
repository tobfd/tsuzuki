package com.tobfd.tsuzuki.core.ui.score

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.tobfd.tsuzuki.core.designsystem.icon.TsuzukiIcons
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.ScoreFormat
import com.tobfd.tsuzuki.core.ui.R

private const val SMILEY_SCALE = 1.25f
private val DefaultSmileySize = 20.dp

/**
 * A list score in the viewer's [format]: "85", "8.5", "8", "★★★★☆" or a smiley; "–" when not
 * scored. TalkBack reads it as e.g. "8.5 out of 10".
 */
@Composable
fun ScoreText(
    score: Double,
    format: ScoreFormat,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    val locale = LocalConfiguration.current.locales[0]
    val display = remember(score, format, locale) { scoreDisplay(score, format, locale) }
    val description = display.description()
    val semanticsModifier = modifier.clearAndSetSemantics { contentDescription = description }

    when (display) {
        is ScoreDisplay.Smiley -> {
            val iconSize = with(LocalDensity.current) {
                if (style.fontSize.isSpecified) (style.fontSize * SMILEY_SCALE).toDp() else DefaultSmileySize
            }
            Icon(
                painter = painterResource(display.sentiment.icon()),
                contentDescription = null,
                modifier = semanticsModifier.size(iconSize),
                tint = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
            )
        }

        else -> Text(
            text = display.text(),
            modifier = semanticsModifier,
            style = style,
            color = color
        )
    }
}

@Composable
private fun ScoreDisplay.text(): String = when (this) {
    ScoreDisplay.None -> stringResource(R.string.ui_score_none)

    is ScoreDisplay.Number -> text

    is ScoreDisplay.Stars ->
        stringResource(R.string.ui_score_star_filled).repeat(filled) +
            stringResource(R.string.ui_score_star_empty).repeat(ScoreDisplay.MAX_STARS - filled)

    is ScoreDisplay.Smiley -> ""
}

@Composable
private fun ScoreDisplay.description(): String = when (this) {
    ScoreDisplay.None -> stringResource(R.string.ui_score_none_description)

    is ScoreDisplay.Number -> stringResource(R.string.ui_score_out_of, text, max)

    is ScoreDisplay.Stars -> pluralStringResource(R.plurals.ui_score_stars, filled, filled, ScoreDisplay.MAX_STARS)

    is ScoreDisplay.Smiley -> stringResource(
        when (sentiment) {
            ScoreDisplay.Sentiment.Disliked -> R.string.ui_score_disliked
            ScoreDisplay.Sentiment.Neutral -> R.string.ui_score_neutral
            ScoreDisplay.Sentiment.Liked -> R.string.ui_score_liked
        }
    )
}

private fun ScoreDisplay.Sentiment.icon(): Int = when (this) {
    ScoreDisplay.Sentiment.Disliked -> TsuzukiIcons.SentimentDissatisfied
    ScoreDisplay.Sentiment.Neutral -> TsuzukiIcons.SentimentNeutral
    ScoreDisplay.Sentiment.Liked -> TsuzukiIcons.SentimentSatisfied
}

@ThemePreviews
@Composable
private fun ScoreTextPreview() {
    TsuzukiPreview {
        Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)) {
            ScoreText(score = 85.0, format = ScoreFormat.POINT_100)
            ScoreText(score = 8.5, format = ScoreFormat.POINT_10_DECIMAL)
            ScoreText(score = 8.0, format = ScoreFormat.POINT_10)
            ScoreText(score = 4.0, format = ScoreFormat.POINT_5)
            ScoreText(score = 3.0, format = ScoreFormat.POINT_3)
            ScoreText(score = 0.0, format = ScoreFormat.POINT_10_DECIMAL)
        }
    }
}
