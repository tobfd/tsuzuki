package com.tobfd.tsuzuki.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import com.tobfd.tsuzuki.core.designsystem.preview.ThemePreviews
import com.tobfd.tsuzuki.core.designsystem.preview.TsuzukiPreview
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.ActivityDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** At most a year of weeks (docs/DESIGN.md, Profile); the current week counts as one. */
const val HEATMAP_MAX_WEEKS = 53

/**
 * At least this many weeks. AniList's `activityHistory` keeps only about half a year (185 days as of
 * 2026-10-01) and only days with activity, so the grid starts at the oldest day it sends.
 */
const val HEATMAP_MIN_WEEKS = 12

/** Primary at these strengths for the 4 intensity levels; level 0 is an empty cell. */
private val LevelAlphas = listOf(0.3f, 0.55f, 0.8f, 1f)

/**
 * How many weeks the grid shows: from the week of the oldest active day AniList sent up to this week,
 * between [HEATMAP_MIN_WEEKS] and [HEATMAP_MAX_WEEKS].
 */
fun heatmapWeekCount(days: List<ActivityDay>, today: LocalDate): Int {
    val oldest = days.filter { it.amount > 0 && !it.date.isAfter(today) }.minOfOrNull { it.date }
        ?: return HEATMAP_MIN_WEEKS
    val weeks = ChronoUnit.WEEKS.between(oldest.mondayOfWeek(), today.mondayOfWeek()) + 1
    return weeks.toInt().coerceIn(HEATMAP_MIN_WEEKS, HEATMAP_MAX_WEEKS)
}

private fun LocalDate.mondayOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * The days of the grid, one list per week (Monday first), oldest week first; days after [today] are
 * null. Each day carries its activity level 0 (none) to 4, relative to the busiest day shown.
 */
fun heatmapWeeks(
    days: List<ActivityDay>,
    today: LocalDate,
    weekCount: Int = heatmapWeekCount(days, today)
): List<List<Pair<LocalDate, Int>?>> {
    val firstMonday = today.mondayOfWeek().minusWeeks(weekCount - 1L)
    val amounts = days.filter { !it.date.isBefore(firstMonday) && !it.date.isAfter(today) }
        .associate { it.date to it.amount }
    val busiest = amounts.values.maxOrNull()?.takeIf { it > 0 } ?: 1
    return List(weekCount) { week ->
        List(DayOfWeek.entries.size) { day ->
            val date = firstMonday.plusWeeks(week.toLong()).plusDays(day.toLong())
            if (date.isAfter(today)) {
                null
            } else {
                val amount = amounts[date] ?: 0
                val level = if (amount <= 0) 0 else ((amount * LevelAlphas.size + busiest - 1) / busiest).coerceIn(1, 4)
                date to level
            }
        }
    }
}

/**
 * The weeks that get a month label: the first week of each month, unless the next label follows within
 * [MIN_LABEL_GAP_WEEKS] weeks (a month cut short at the start of the grid), where the two would overlap.
 */
internal fun monthLabelWeeks(weeks: List<List<Pair<LocalDate, Int>?>>): Set<Int> {
    val starts = mutableListOf<Int>()
    var lastMonth = -1
    weeks.forEachIndexed { index, week ->
        val month = week.firstNotNullOfOrNull { it?.first }?.monthValue ?: return@forEachIndexed
        if (month != lastMonth) starts += index
        lastMonth = month
    }
    return starts.filterIndexed { i, week -> starts.getOrNull(i + 1)?.let { it - week >= MIN_LABEL_GAP_WEEKS } ?: true }
        .toSet()
}

/** A short month name is about two cells wide. */
private const val MIN_LABEL_GAP_WEEKS = 3

/**
 * The activity history heatmap (docs/DESIGN.md, Profile): up to a year of weeks × 7 days, 12 dp cells
 * in 4 strengths of primary, month labels above the week a month starts in. Wider than the screen it
 * scrolls sideways and starts at today. TalkBack reads the total.
 */
@Composable
fun ActivityHeatmap(days: List<ActivityDay>, today: LocalDate, modifier: Modifier = Modifier) {
    val weeks = remember(days, today) { heatmapWeeks(days, today) }
    val locale = LocalConfiguration.current.locales[0]
    val total = remember(days, weeks) {
        val shown = weeks.flatten().mapNotNull { it?.first }.toSet()
        days.filter { it.date in shown }.sumOf { it.amount }
    }
    val description = pluralStringResource(R.plurals.ui_heatmap_description, total, total, weeks.size)
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clearAndSetSemantics { contentDescription = description }
            // Reversed, so it starts scrolled to the newest week.
            .horizontalScroll(rememberScrollState(), reverseScrolling = true),
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)
    ) {
        val labels = remember(weeks) { monthLabelWeeks(weeks) }
        Row(horizontalArrangement = Arrangement.spacedBy(TsuzukiSizes.heatmapGap)) {
            weeks.forEachIndexed { index, week ->
                Box(Modifier.size(TsuzukiSizes.heatmapCell, TsuzukiSizes.heatmapCell + TsuzukiSpacing.small)) {
                    if (index in labels) {
                        val date = week.firstNotNullOf { it?.first }
                        Text(
                            text = date.month.getDisplayName(TextStyle.SHORT, locale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TsuzukiSizes.heatmapGap)) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSizes.heatmapGap)) {
                    week.forEach { day ->
                        val color = when {
                            day == null -> Color.Transparent
                            day.second == 0 -> empty
                            else -> primary.copy(alpha = LevelAlphas[day.second - 1])
                        }
                        Box(
                            Modifier
                                .size(TsuzukiSizes.heatmapCell)
                                .background(color, MaterialTheme.shapes.extraSmall)
                        )
                    }
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun ActivityHeatmapPreview() {
    val today = LocalDate.of(2026, 9, 30)
    val days = (0 until 185 step 2).map { ActivityDay(today.minusDays(it.toLong()), it % 9) }
    TsuzukiPreview {
        ActivityHeatmap(days = days, today = today, modifier = Modifier.padding(TsuzukiSpacing.large))
    }
}
