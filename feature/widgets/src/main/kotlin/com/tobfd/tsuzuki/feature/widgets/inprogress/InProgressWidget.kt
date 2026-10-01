package com.tobfd.tsuzuki.feature.widgets.inprogress

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import com.tobfd.tsuzuki.core.designsystem.theme.ShapeTokens
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSizes
import com.tobfd.tsuzuki.core.designsystem.theme.TsuzukiSpacing
import com.tobfd.tsuzuki.core.model.AppearanceSettings
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.feature.widgets.R
import com.tobfd.tsuzuki.feature.widgets.WidgetCover
import com.tobfd.tsuzuki.feature.widgets.WidgetFooter
import com.tobfd.tsuzuki.feature.widgets.WidgetFrame
import com.tobfd.tsuzuki.feature.widgets.WidgetKind
import com.tobfd.tsuzuki.feature.widgets.WidgetLayout
import com.tobfd.tsuzuki.feature.widgets.WidgetMessage
import com.tobfd.tsuzuki.feature.widgets.WidgetPalette
import com.tobfd.tsuzuki.feature.widgets.WidgetPlusOneButton
import com.tobfd.tsuzuki.feature.widgets.WidgetPreviewData
import com.tobfd.tsuzuki.feature.widgets.WidgetSizes
import com.tobfd.tsuzuki.feature.widgets.WidgetText
import com.tobfd.tsuzuki.feature.widgets.WidgetTheme
import com.tobfd.tsuzuki.feature.widgets.isLoggedIn
import com.tobfd.tsuzuki.feature.widgets.loadBitmaps
import com.tobfd.tsuzuki.feature.widgets.openAction
import com.tobfd.tsuzuki.feature.widgets.widgetDependencies
import com.tobfd.tsuzuki.feature.widgets.widgetPalette
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest

/** What the widget draws: the state, the colors and the covers by URL. */
internal data class InProgressRender(
    val state: InProgressState,
    val palette: WidgetPalette,
    val covers: Map<String, Bitmap> = emptyMap()
)

/**
 * "In Progress" (docs/ROADMAP.md, Widgets): Home's In Progress entries from Room, with +1 through
 * the app's mutation queue. It makes no request of its own.
 */
class InProgressWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = WidgetSizes.mode

    override val previewSizeMode = WidgetSizes.mode

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val renders = renders(context)
        val initial = renders.first()
        provideContent {
            val render by renders.collectAsState(initial)
            InProgressContent(render)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val render = InProgressRender(
            state = InProgressState.Ready(WidgetPreviewData.inProgress, queuedChanges = 0),
            palette = widgetPalette(context, AppearanceSettings())
        )
        provideContent { InProgressContent(render) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun renders(context: Context): Flow<InProgressRender> {
        val dependencies = context.widgetDependencies()
        val lists = dependencies.listRepository()
        return combine(
            dependencies.sessionRepository().isLoggedIn,
            dependencies.settingsRepository().appearance,
            lists.observeList(MediaType.ANIME),
            lists.observeList(MediaType.MANGA),
            lists.queuedChangeCount
        ) { loggedIn, appearance, anime, manga, queued ->
            val state = if (loggedIn) {
                InProgressState.Ready(inProgressItems(anime.entries + manga.entries), queued)
            } else {
                InProgressState.LoggedOut
            }
            state to appearance
        }
            .distinctUntilChanged()
            .mapLatest { (state, appearance) ->
                val urls = (state as? InProgressState.Ready)?.items?.map { it.coverUrl }.orEmpty()
                InProgressRender(
                    state = state,
                    palette = widgetPalette(context, appearance),
                    covers = loadBitmaps(context, urls, TsuzukiSizes.widgetCover.width, TsuzukiSizes.widgetCover.height)
                )
            }
    }
}

class InProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = InProgressWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        context.widgetDependencies().widgetUpdater().onWidgetsPlaced(WidgetKind.InProgress)
    }
}

private val EntryIdKey = ActionParameters.Key<Int>("entryId")

/** +1 from the widget: the same `ListRepository.plusOne` as the app (Room first, then the queue). */
internal class PlusOneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryId = parameters[EntryIdKey] ?: return
        context.widgetDependencies().listRepository().plusOne(entryId)
        InProgressWidget().updateAll(context)
    }
}

@Composable
internal fun InProgressContent(render: InProgressRender) {
    val context = LocalContext.current
    val size = LocalSize.current
    val layout = WidgetLayout.of(size)
    val openLists = openAction(context, AppDestination.Tab(AppTab.Lists))
    WidgetTheme(render.palette) {
        WidgetFrame(layout, context.getString(R.string.widget_in_progress_name), titleAction = openLists) {
            when (val state = render.state) {
                InProgressState.LoggedOut -> WidgetMessage(
                    title = context.getString(R.string.widget_log_in),
                    body = context.getString(R.string.widget_log_in_lists).takeIf { layout != WidgetLayout.Row },
                    action = openLists
                )

                is InProgressState.Ready -> when {
                    state.items.isEmpty() -> WidgetMessage(
                        title = context.getString(R.string.widget_in_progress_empty),
                        body = context.getString(R.string.widget_in_progress_empty_body)
                            .takeIf { layout != WidgetLayout.Row },
                        action = openLists
                    )

                    layout == WidgetLayout.Row -> InProgressRow(
                        item = state.items.first(),
                        cover = render.covers[state.items.first().coverUrl],
                        showCover = size.width >= WidgetSizes.rowCoverMinWidth
                    )

                    layout == WidgetLayout.Single -> InProgressSingle(
                        item = state.items.first(),
                        cover = render.covers[state.items.first().coverUrl]
                    )

                    else -> {
                        LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                            items(state.items, itemId = { it.entryId.toLong() }) { item ->
                                Column(modifier = GlanceModifier.padding(bottom = TsuzukiSpacing.small)) {
                                    InProgressRow(item = item, cover = render.covers[item.coverUrl], showCover = true)
                                }
                            }
                        }
                        if (state.queuedChanges > 0) {
                            WidgetFooter(
                                text = context.resources.getQuantityString(
                                    R.plurals.widget_changes_waiting,
                                    state.queuedChanges,
                                    state.queuedChanges
                                ),
                                offline = true
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Cover, title and progress; the row opens the detail page, the button adds one. */
@Composable
private fun InProgressRow(item: InProgressItem, cover: Bitmap?, showCover: Boolean) {
    val context = LocalContext.current
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(openAction(context, AppDestination.Media(item.mediaId, AppTab.Lists)))
                .semantics { contentDescription = rowDescription(context, item) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCover) {
                WidgetCover(cover, item.coverColor, TsuzukiSizes.widgetCover)
                Spacer(GlanceModifier.width(TsuzukiSpacing.medium))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = item.title, style = WidgetText.title, maxLines = if (showCover) 2 else 1)
                Text(text = progressText(context, item), style = WidgetText.meta, maxLines = 1)
            }
        }
        if (item.canPlusOne) {
            Spacer(GlanceModifier.width(TsuzukiSpacing.small))
            PlusOne(item)
        }
    }
}

/** The 2 × 2 layout: one entry with its cover, progress bar and +1. */
@Composable
private fun InProgressSingle(item: InProgressItem, cover: Bitmap?) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .clickable(openAction(context, AppDestination.Media(item.mediaId, AppTab.Lists)))
                .semantics { contentDescription = rowDescription(context, item) }
        ) {
            WidgetCover(cover, item.coverColor, TsuzukiSizes.widgetCoverSmall)
            Spacer(GlanceModifier.width(TsuzukiSpacing.small))
            Text(text = item.title, style = WidgetText.title, maxLines = 3, modifier = GlanceModifier.defaultWeight())
        }
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = progressText(context, item), style = WidgetText.emphasis, maxLines = 1)
                item.total?.takeIf { it > 0 }?.let { total ->
                    Spacer(GlanceModifier.height(TsuzukiSpacing.extraSmall))
                    LinearProgressIndicator(
                        progress = item.progress.toFloat() / total,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(TsuzukiSizes.widgetProgressBar)
                            .cornerRadius(ShapeTokens.extraSmall),
                        color = GlanceTheme.colors.primary,
                        backgroundColor = GlanceTheme.colors.surfaceVariant
                    )
                }
            }
            if (item.canPlusOne) {
                Spacer(GlanceModifier.width(TsuzukiSpacing.small))
                PlusOne(item)
            }
        }
    }
}

@Composable
private fun PlusOne(item: InProgressItem) {
    val context = LocalContext.current
    WidgetPlusOneButton(
        action = actionRunCallback<PlusOneAction>(actionParametersOf(EntryIdKey to item.entryId)),
        contentDescription = context.getString(R.string.widget_plus_one_description, item.title)
    )
}

private fun progressText(context: Context, item: InProgressItem): String = if (item.total != null) {
    context.getString(com.tobfd.tsuzuki.core.ui.R.string.ui_progress, item.progress, item.total)
} else {
    context.getString(com.tobfd.tsuzuki.core.ui.R.string.ui_progress_unknown_total, item.progress)
}

/** "Frieren, episode 18 of 28": TalkBack reads a row as one item. */
private fun rowDescription(context: Context, item: InProgressItem): String {
    val unit = when (item.type) {
        MediaType.ANIME -> R.plurals.widget_progress_episodes
        MediaType.MANGA -> R.plurals.widget_progress_chapters
    }
    val progress = if (item.total != null) {
        context.resources.getQuantityString(unit, item.total, item.progress, item.total)
    } else {
        context.getString(
            when (item.type) {
                MediaType.ANIME -> R.string.widget_progress_episode_unknown_total
                MediaType.MANGA -> R.string.widget_progress_chapter_unknown_total
            },
            item.progress
        )
    }
    return context.getString(R.string.widget_row_description, item.title, progress)
}
