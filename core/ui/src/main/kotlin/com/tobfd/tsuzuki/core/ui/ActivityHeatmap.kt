package com.tobfd.tsuzuki.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import java.time.temporal.TemporalAdjusters

/** Weeks the heatmap shows (docs/DESIGN.md, Profile). */
const val HEATMAP_WEEKS = 12

/** Primary at these strengths for the 4 intensity levels; level 0 is an empty cell. */
private val LevelAlphas = listOf(0.3f, 0.55f, 0.8f, 1f)

/**
 * The days of the 12-week grid, one list per week (Monday first), oldest week first; days after
 * [today] are null. Each day carries its activity level 0 (none) to 4, relative to the busiest day.
 */
fun heatmapWeeks(days: List<ActivityDay>, today: LocalDate): List<List<Pair<LocalDate, Int>?>> {
    val firstMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(HEATMAP_WEEKS - 1L)
    val amounts = days.filter { !it.date.isBefore(firstMonday) && !it.date.isAfter(today) }
        .associate { it.date to it.amount }
    val busiest = amounts.values.maxOrNull()?.takeIf { it > 0 } ?: 1
    return List(HEATMAP_WEEKS) { week ->
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
 * The activity history heatmap (docs/DESIGN.md, Profile): 12 weeks × 7 days, 12 dp cells in 4
 * strengths of primary, month labels above the week a month starts in. TalkBack reads the total.
 */
@Composable
fun ActivityHeatmap(days: List<ActivityDay>, today: LocalDate, modifier: Modifier = Modifier) {
    val weeks = remember(days, today) { heatmapWeeks(days, today) }
    val locale = LocalConfiguration.current.locales[0]
    val total = remember(days, weeks) {
        val shown = weeks.flatten().mapNotNull { it?.first }.toSet()
        days.filter { it.date in shown }.sumOf { it.amount }
    }
    val description = pluralStringResource(R.plurals.ui_heatmap_description, total, total, HEATMAP_WEEKS)
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.extraSmall)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TsuzukiSizes.heatmapGap)) {
            var lastMonth = -1
            weeks.forEach { week ->
                val month = week.firstNotNullOfOrNull { it?.first }?.monthValue ?: lastMonth
                Box(Modifier.size(TsuzukiSizes.heatmapCell, TsuzukiSizes.heatmapCell + TsuzukiSpacing.small)) {
                    if (month != lastMonth) {
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
                lastMonth = month
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
    val days = (0 until 84 step 2).map { ActivityDay(today.minusDays(it.toLong()), it % 9) }
    TsuzukiPreview {
        ActivityHeatmap(days = days, today = today, modifier = Modifier.padding(TsuzukiSpacing.large))
    }
}
