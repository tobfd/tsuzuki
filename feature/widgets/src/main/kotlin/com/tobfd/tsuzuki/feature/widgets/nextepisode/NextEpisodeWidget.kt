package com.tobfd.tsuzuki.feature.widgets.nextepisode

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.tobfd.tsuzuki.core.common.AppDestination
import com.tobfd.tsuzuki.core.common.AppTab
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiTypography
import com.tobfd.tsuzuki.core.model.AppearanceSettings
import com.tobfd.tsuzuki.feature.widgets.R
import com.tobfd.tsuzuki.feature.widgets.RefreshProblem
import com.tobfd.tsuzuki.feature.widgets.WidgetCover
import com.tobfd.tsuzuki.feature.widgets.WidgetFooter
import com.tobfd.tsuzuki.feature.widgets.WidgetFrame
import com.tobfd.tsuzuki.feature.widgets.WidgetKind
import com.tobfd.tsuzuki.feature.widgets.WidgetLayout
import com.tobfd.tsuzuki.feature.widgets.WidgetMessage
import com.tobfd.tsuzuki.feature.widgets.WidgetPalette
import com.tobfd.tsuzuki.feature.widgets.WidgetPreviewData
import com.tobfd.tsuzuki.feature.widgets.WidgetSizes
import com.tobfd.tsuzuki.feature.widgets.WidgetText
import com.tobfd.tsuzuki.feature.widgets.WidgetTheme
import com.tobfd.tsuzuki.feature.widgets.currentRefreshProblem
import com.tobfd.tsuzuki.feature.widgets.isLoggedIn
import com.tobfd.tsuzuki.feature.widgets.loadBitmaps
import com.tobfd.tsuzuki.feature.widgets.openAction
import com.tobfd.tsuzuki.feature.widgets.widgetDependencies
import com.tobfd.tsuzuki.feature.widgets.widgetPalette
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest

internal data class NextEpisodeRender(
    val state: NextEpisodeState,
    val palette: WidgetPalette,
    val covers: Map<String, Bitmap> = emptyMap(),
    /** Previews are pictures: they get fixed countdown text instead of a ticking chronometer. */
    val preview: Boolean = false
)

/**
 * "Next episode" (docs/ROADMAP.md, Widgets): the next episodes of the anime the viewer is watching,
 * from Room. [NextEpisodeWorker] refreshes them at most once an hour and soon after an episode airs.
 */
class NextEpisodeWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = WidgetSizes.mode

    override val previewSizeMode = WidgetSizes.mode

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val renders = renders(context)
        val initial = renders.first()
        provideContent {
            val render by renders.collectAsState(initial)
            NextEpisodeContent(render, problem = currentRefreshProblem, now = Instant.now())
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val now = Instant.now()
        val render = NextEpisodeRender(
            state = NextEpisodeState.Ready(WidgetPreviewData.upcoming(now)),
            palette = widgetPalette(context, AppearanceSettings()),
            preview = true
        )
        provideContent { NextEpisodeContent(render, problem = null, now = now) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun renders(context: Context): Flow<NextEpisodeRender> {
        val dependencies = context.widgetDependencies()
        return combine(
            dependencies.sessionRepository().isLoggedIn,
            dependencies.settingsRepository().appearance,
            dependencies.airingRepository().upcoming
        ) { loggedIn, appearance, upcoming ->
            val state = if (loggedIn) NextEpisodeState.Ready(upcomingItems(upcoming)) else NextEpisodeState.LoggedOut
            state to appearance
        }
            .distinctUntilChanged()
            .mapLatest { (state, appearance) ->
                val urls = (state as? NextEpisodeState.Ready)?.items?.map { it.coverUrl }.orEmpty()
                NextEpisodeRender(
                    state = state,
                    palette = widgetPalette(context, appearance),
                    covers = loadBitmaps(context, urls, TsuzukiSizes.widgetCover.width, TsuzukiSizes.widgetCover.height)
                )
            }
    }
}

class NextEpisodeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextEpisodeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.widgetDependencies().widgetUpdater().onWidgetsPlaced(WidgetKind.NextEpisode)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.widgetDependencies().widgetUpdater().onWidgetsRemoved(WidgetKind.NextEpisode)
    }
}

@Composable
internal fun NextEpisodeContent(render: NextEpisodeRender, problem: RefreshProblem?, now: Instant) {
    val context = LocalContext.current
    val size = LocalSize.current
    val layout = WidgetLayout.of(size)
    val openLists = openAction(context, AppDestination.Tab(AppTab.Lists))
    WidgetTheme(render.palette) {
        WidgetFrame(layout, context.getString(R.string.widget_next_episode_name), titleAction = openLists) {
            when (val state = render.state) {
                NextEpisodeState.LoggedOut -> WidgetMessage(
                    title = context.getString(R.string.widget_log_in),
                    body = context.getString(R.string.widget_log_in_lists).takeIf { layout != WidgetLayout.Row },
                    action = openLists
                )

                is NextEpisodeState.Ready -> when {
                    state.items.isEmpty() -> WidgetMessage(
                        title = context.getString(R.string.widget_next_episode_empty),
                        body = context.getString(R.string.widget_next_episode_empty_body)
                            .takeIf { layout != WidgetLayout.Row },
                        action = openLists
                    )

                    layout == WidgetLayout.Row -> UpcomingRow(
                        item = state.items.first(),
                        render = render,
                        now = now,
                        showCover = size.width >= WidgetSizes.rowCoverMinWidth
                    )

                    layout == WidgetLayout.Single -> UpcomingSingle(state.items.first(), render, now)

                    else -> {
                        LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                            items(state.items, itemId = { it.mediaId.toLong() }) { item ->
                                Column(modifier = GlanceModifier.padding(bottom = TsuzukiSpacing.small)) {
                                    UpcomingRow(item = item, render = render, now = now, showCover = true)
                                }
                            }
                        }
                        problem?.let { ProblemFooter(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(item: UpcomingItem, render: NextEpisodeRender, now: Instant, showCover: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(openAction(context, AppDestination.Media(item.mediaId, AppTab.Lists)))
            .semantics { contentDescription = rowDescription(context, item, now) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCover) {
            WidgetCover(render.covers[item.coverUrl], item.coverColor, TsuzukiSizes.widgetCover)
            Spacer(GlanceModifier.width(TsuzukiSpacing.medium))
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(text = item.title, style = WidgetText.title, maxLines = if (showCover) 2 else 1)
            Text(text = episodeLine(context, item, now), style = WidgetText.meta, maxLines = 1)
        }
        Spacer(GlanceModifier.width(TsuzukiSpacing.small))
        CountdownText(item, render, now, large = false)
    }
}

/** The 2 × 2 layout: the soonest episode with a large countdown. */
@Composable
private fun UpcomingSingle(item: UpcomingItem, render: NextEpisodeRender, now: Instant) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(openAction(context, AppDestination.Media(item.mediaId, AppTab.Lists)))
            .semantics { contentDescription = rowDescription(context, item, now) }
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            WidgetCover(render.covers[item.coverUrl], item.coverColor, TsuzukiSizes.widgetCoverSmall)
            Spacer(GlanceModifier.width(TsuzukiSpacing.small))
            Text(text = item.title, style = WidgetText.title, maxLines = 3, modifier = GlanceModifier.defaultWeight())
        }
        CountdownText(item, render, now, large = true)
        Text(text = episodeLine(context, item, now), style = WidgetText.meta, maxLines = 1)
    }
}

@Composable
private fun CountdownText(item: UpcomingItem, render: NextEpisodeRender, now: Instant, large: Boolean) {
    val context = LocalContext.current
    val style = if (large) {
        WidgetText.emphasis.copy(fontSize = TsuzukiTypography.headlineSmall.fontSize)
    } else {
        WidgetText.emphasis
    }
    when (val countdown = Countdown.of(item.airingAt, now)) {
        is Countdown.Live -> if (render.preview) {
            Text(text = clockText(countdown.remaining), style = style, maxLines = 1)
        } else {
            LiveCountdown(
                remaining = countdown.remaining,
                palette = render.palette,
                layout = if (large) R.layout.widget_countdown_large else R.layout.widget_countdown
            )
        }

        is Countdown.Days -> Text(
            text = context.resources.getQuantityString(
                R.plurals.widget_in_days,
                countdown.days.toInt(),
                countdown.days.toInt()
            ),
            style = style,
            maxLines = 1
        )

        Countdown.Aired -> Text(text = context.getString(R.string.widget_aired), style = style, maxLines = 1)
    }
}

/** A `Chronometer` counting down, so the widget ticks without being redrawn. */
@Composable
private fun LiveCountdown(remaining: Duration, palette: WidgetPalette, @LayoutRes layout: Int) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, layout).apply {
        setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime() + remaining.toMillis(), null, true)
        setChronometerCountDown(R.id.widget_countdown, true)
        setColorInt(
            R.id.widget_countdown,
            "setTextColor",
            palette.light.primary.toArgb(),
            palette.dark.primary.toArgb()
        )
    }
    AndroidRemoteViews(views)
}

@Composable
private fun ProblemFooter(problem: RefreshProblem) {
    val context = LocalContext.current
    WidgetFooter(
        text = context.getString(
            when (problem) {
                RefreshProblem.Offline -> R.string.widget_next_episode_offline
                RefreshProblem.Failed -> R.string.widget_next_episode_failed
            }
        ),
        offline = problem == RefreshProblem.Offline
    )
}

/** "Episode 6 · Fri 17:30", plus "2 behind" when aired episodes are still unwatched. */
private fun episodeLine(context: Context, item: UpcomingItem, now: Instant): String {
    val parts = mutableListOf(
        context.getString(R.string.widget_episode, item.episode),
        airingTime(context, item.airingAt, now)
    )
    if (item.behind > 0) parts += context.resources.getQuantityString(R.plurals.widget_behind, item.behind, item.behind)
    return parts.joinToString(context.getString(R.string.widget_separator))
}

/** Weekday and time within a week ("Fri 17:30"), else the date ("9 Oct, 17:30"), in the app language. */
private fun airingTime(context: Context, airingAt: Instant, now: Instant): String {
    val locale: Locale = context.resources.configuration.locales[0]
    val skeleton = if (Duration.between(now, airingAt) < Duration.ofDays(6)) "EEEjmm" else "dMMMjmm"
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return DateTimeFormatter.ofPattern(pattern, locale).format(airingAt.atZone(ZoneId.systemDefault()))
}

/** "5:07:12" for a fixed countdown in previews. */
private fun clockText(remaining: Duration): String =
    "%d:%02d:%02d".format(remaining.toHours(), remaining.toMinutesPart(), remaining.toSecondsPart())

private fun rowDescription(context: Context, item: UpcomingItem, now: Instant): String =
    context.getString(R.string.widget_next_episode_description, item.title, episodeLine(context, item, now))
