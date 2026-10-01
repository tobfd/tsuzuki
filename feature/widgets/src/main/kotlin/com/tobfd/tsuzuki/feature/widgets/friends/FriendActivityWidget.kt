package com.tobfd.tsuzuki.feature.widgets.friends

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.Text
import com.tobfd.tsuzuki.core.common.AppDestination
import com.tobfd.tsuzuki.core.common.AppTab
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.Activity
import com.tobfd.tsuzuki.core.model.AppearanceSettings
import com.tobfd.tsuzuki.core.ui.listUpdateSummary
import com.tobfd.tsuzuki.core.ui.plainText
import com.tobfd.tsuzuki.feature.widgets.R
import com.tobfd.tsuzuki.feature.widgets.RefreshProblem
import com.tobfd.tsuzuki.feature.widgets.WidgetAvatar
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest

internal data class FriendActivityRender(
    val state: FriendActivityState,
    val palette: WidgetPalette,
    val avatars: Map<String, Bitmap> = emptyMap(),
    val covers: Map<String, Bitmap> = emptyMap()
)

/**
 * "Friends' activity" (docs/ROADMAP.md, Widgets): the newest activities of the people the viewer
 * follows. [FriendActivityWorker] fetches them every few hours; in between the widget shows the last
 * result from Room.
 */
class FriendActivityWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = WidgetSizes.mode

    override val previewSizeMode = WidgetSizes.mode

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val renders = renders(context)
        val initial = renders.first()
        provideContent {
            val render by renders.collectAsState(initial)
            FriendActivityContent(render, problem = currentRefreshProblem)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val render = FriendActivityRender(
            state = FriendActivityState.Ready(WidgetPreviewData.friendActivity(Instant.now()), fetchedAt = null),
            palette = widgetPalette(context, AppearanceSettings())
        )
        provideContent { FriendActivityContent(render, problem = null) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun renders(context: Context): Flow<FriendActivityRender> {
        val dependencies = context.widgetDependencies()
        return combine(
            dependencies.sessionRepository().isLoggedIn,
            dependencies.settingsRepository().appearance,
            dependencies.friendActivityRepository().feed
        ) { loggedIn, appearance, feed -> friendActivityState(loggedIn, feed) to appearance }
            .distinctUntilChanged()
            .mapLatest { (state, appearance) ->
                val activities = (state as? FriendActivityState.Ready)?.activities.orEmpty()
                FriendActivityRender(
                    state = state,
                    palette = widgetPalette(context, appearance),
                    avatars = loadBitmaps(
                        context,
                        activities.map { it.user.avatarUrl },
                        TsuzukiSizes.widgetAvatar,
                        TsuzukiSizes.widgetAvatar
                    ),
                    covers = loadBitmaps(
                        context,
                        activities.map { it.coverUrl },
                        TsuzukiSizes.widgetCoverSmall.width,
                        TsuzukiSizes.widgetCoverSmall.height
                    )
                )
            }
    }
}

class FriendActivityWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FriendActivityWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.widgetDependencies().widgetUpdater().onWidgetsPlaced(WidgetKind.FriendActivity)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.widgetDependencies().widgetUpdater().onWidgetsRemoved(WidgetKind.FriendActivity)
    }
}

@Composable
internal fun FriendActivityContent(render: FriendActivityRender, problem: RefreshProblem?) {
    val context = LocalContext.current
    val size = LocalSize.current
    val layout = WidgetLayout.of(size)
    val openHome = openAction(context, AppDestination.Tab(AppTab.Home))
    WidgetTheme(render.palette) {
        WidgetFrame(layout, context.getString(R.string.widget_friends_name), titleAction = openHome) {
            when (val state = render.state) {
                FriendActivityState.LoggedOut -> WidgetMessage(
                    title = context.getString(R.string.widget_log_in),
                    body = context.getString(R.string.widget_log_in_friends).takeIf { layout != WidgetLayout.Row },
                    action = openHome
                )

                is FriendActivityState.Ready -> when {
                    state.activities.isEmpty() -> WidgetMessage(
                        title = context.getString(
                            when {
                                problem == RefreshProblem.Offline -> R.string.widget_friends_offline_title
                                state.fetchedAt == null -> R.string.widget_loading
                                else -> R.string.widget_friends_empty
                            }
                        ),
                        body = context.getString(R.string.widget_friends_empty_body)
                            .takeIf { layout != WidgetLayout.Row && state.fetchedAt != null },
                        action = openHome
                    )

                    layout == WidgetLayout.Row -> ActivityRow(
                        activity = state.activities.first(),
                        render = render,
                        maxLines = 1,
                        showCover = false
                    )

                    layout == WidgetLayout.Single -> ActivityRow(
                        activity = state.activities.first(),
                        render = render,
                        maxLines = 4,
                        showCover = false
                    )

                    else -> {
                        LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                            items(state.activities, itemId = { it.id.toLong() }) { activity ->
                                Column(modifier = GlanceModifier.padding(bottom = TsuzukiSpacing.small)) {
                                    ActivityRow(
                                        activity = activity,
                                        render = render,
                                        maxLines = 2,
                                        showCover = size.width >= WidgetSizes.rowCoverMinWidth
                                    )
                                }
                            }
                        }
                        state.fetchedAt?.let { fetchedAt -> UpdatedFooter(fetchedAt, problem) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(activity: Activity, render: FriendActivityRender, maxLines: Int, showCover: Boolean) {
    val context = LocalContext.current
    val text = summary(context, activity)
    val destination = when (activity) {
        is Activity.ListUpdate -> AppDestination.Media(activity.media.id, AppTab.Home)
        is Activity.Text -> AppDestination.User(activity.user.id, activity.user.name)
    }
    val time = activityTime(context, activity.createdAt)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(openAction(context, destination))
            .semantics { contentDescription = context.getString(R.string.widget_activity_description, text, time) },
        verticalAlignment = Alignment.Top
    ) {
        WidgetAvatar(activity.user.avatarUrl?.let { render.avatars[it] })
        Spacer(GlanceModifier.width(TsuzukiSpacing.small))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(text = text, style = WidgetText.body, maxLines = maxLines)
            Spacer(GlanceModifier.height(TsuzukiSpacing.extraExtraSmall))
            Text(text = time, style = WidgetText.meta, maxLines = 1)
        }
        if (showCover && activity is Activity.ListUpdate) {
            Spacer(GlanceModifier.width(TsuzukiSpacing.small))
            WidgetCover(
                bitmap = activity.media.coverUrl?.let { render.covers[it] },
                coverColor = activity.media.coverColor,
                size = TsuzukiSizes.widgetCoverSmall
            )
        }
    }
}

@Composable
private fun UpdatedFooter(fetchedAt: Instant, problem: RefreshProblem?) {
    val context = LocalContext.current
    val time = activityTime(context, fetchedAt)
    WidgetFooter(
        text = context.getString(
            when (problem) {
                null -> R.string.widget_updated
                RefreshProblem.Offline -> R.string.widget_updated_offline
                RefreshProblem.Failed -> R.string.widget_updated_failed
            },
            time
        ),
        offline = problem == RefreshProblem.Offline
    )
}

/** Plain text: Glance can't mix styles in one text. */
private fun summary(context: Context, activity: Activity): String = when (activity) {
    is Activity.ListUpdate -> listUpdateSummary(context.resources, activity)

    is Activity.Text -> context.getString(
        R.string.widget_text_activity,
        activity.user.name,
        plainText(activity.html, context.getString(com.tobfd.tsuzuki.core.ui.R.string.ui_spoiler))
    )
}

/**
 * Clock time today ("14:02"), the weekday this week ("Mon 14:02"), else the date ("28 Sep"). Unlike
 * "3 hours ago" this doesn't go stale between the widget's updates.
 */
private fun activityTime(context: Context, instant: Instant): String {
    val locale: Locale = context.resources.configuration.locales[0]
    val zone = ZoneId.systemDefault()
    val date = instant.atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(date, LocalDate.now(zone))
    val skeleton = when {
        days <= 0 -> "jmm"
        days < 6 -> "EEEjmm"
        else -> "dMMM"
    }
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        .format(instant.atZone(zone))
}
