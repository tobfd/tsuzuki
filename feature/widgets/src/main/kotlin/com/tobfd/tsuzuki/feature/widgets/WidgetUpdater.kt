package com.tobfd.tsuzuki.feature.widgets

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.tobfd.tsuzuki.core.common.ApplicationScope
import com.tobfd.tsuzuki.core.data.list.ListRepository
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.data.settings.SettingsRepository
import com.tobfd.tsuzuki.core.data.widget.AiringRepository
import com.tobfd.tsuzuki.core.data.widget.FriendActivityRepository
import com.tobfd.tsuzuki.core.model.MediaType
import com.tobfd.tsuzuki.core.model.SessionState
import com.tobfd.tsuzuki.feature.widgets.friends.FriendActivityWidgetReceiver
import com.tobfd.tsuzuki.feature.widgets.friends.friendActivityState
import com.tobfd.tsuzuki.feature.widgets.inprogress.InProgressWidgetReceiver
import com.tobfd.tsuzuki.feature.widgets.inprogress.inProgressItems
import com.tobfd.tsuzuki.feature.widgets.nextepisode.NextEpisodeWidgetReceiver
import com.tobfd.tsuzuki.feature.widgets.nextepisode.upcomingItems
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps placed widgets current while the app process runs. Every change to the lists, the queue,
 * the airing times, the friends' activities, the session or the appearance happens in this process,
 * so watching them here catches all of them; widgets are redrawn only when what they show changed.
 * A login starts the background updates again, so placed widgets fill without waiting.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val listRepository: ListRepository,
    private val airingRepository: AiringRepository,
    private val friendActivityRepository: FriendActivityRepository,
    private val clock: Clock
) {
    private val observed = ConcurrentHashMap.newKeySet<WidgetKind>()

    /** Called once when the app process starts. */
    fun start() {
        scope.launch {
            WidgetKind.entries.filter { it.widget().isPlaced(context) }.forEach(::observe)
            publishPreviews()
        }
    }

    /** The first widget of [kind] was placed. */
    fun onWidgetsPlaced(kind: WidgetKind) {
        observe(kind)
        startBackgroundUpdates(kind)
    }

    /** The last widget of [kind] was removed: its background updates stop. */
    fun onWidgetsRemoved(kind: WidgetKind) {
        when (kind) {
            WidgetKind.InProgress -> Unit
            WidgetKind.NextEpisode -> WidgetWork.cancelNextEpisode(context)
            WidgetKind.FriendActivity -> WidgetWork.cancelFriendActivity(context)
        }
    }

    private fun startBackgroundUpdates(kind: WidgetKind, afterLogin: Boolean = false) {
        when (kind) {
            WidgetKind.InProgress -> Unit

            WidgetKind.NextEpisode -> WidgetWork.scheduleNextEpisodeFetch(
                context,
                delay = Duration.ZERO,
                fromWorker = false
            )

            WidgetKind.FriendActivity -> {
                WidgetWork.scheduleFriendActivity(context)
                if (afterLogin) WidgetWork.refreshFriendActivityNow(context)
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observe(kind: WidgetKind) {
        if (!observed.add(kind)) return
        scope.launch {
            // The first value is what the widget already shows.
            shown(kind).drop(1).debounce(REDRAW_DEBOUNCE_MILLIS).collect {
                val widget = kind.widget()
                if (!widget.isPlaced(context)) return@collect
                widget.updateAll(context)
                if (kind == WidgetKind.NextEpisode) {
                    // The countdowns tick by themselves; at the next airing the row has to change.
                    val now = clock.instant()
                    val soonest = airingRepository.upcoming.first().map { it.airingAt }.filter { it > now }.minOrNull()
                    WidgetWork.scheduleNextEpisodeRedraw(context, soonest, now, fromRedraw = false)
                }
            }
        }
        scope.launch {
            viewerIds().drop(1).collect { viewerId ->
                if (viewerId != null && kind.widget().isPlaced(context)) startBackgroundUpdates(kind, afterLogin = true)
            }
        }
    }

    private fun viewerIds(): Flow<Int?> = sessionRepository.session
        .filterNot { it is SessionState.Loading }
        .map { (it as? SessionState.LoggedIn)?.viewer?.id }
        .distinctUntilChanged()

    /** What a widget of [kind] shows, as a value that changes exactly when it has to be redrawn. */
    private fun shown(kind: WidgetKind): Flow<Any> {
        val common = combine(viewerIds(), settingsRepository.appearance) { viewer, appearance -> viewer to appearance }
        val data: Flow<Any> = when (kind) {
            WidgetKind.InProgress -> combine(
                listRepository.observeList(MediaType.ANIME),
                listRepository.observeList(MediaType.MANGA),
                listRepository.queuedChangeCount
            ) { anime, manga, queued -> inProgressItems(anime.entries + manga.entries) to queued }

            WidgetKind.NextEpisode -> airingRepository.upcoming.map { upcomingItems(it) }

            WidgetKind.FriendActivity -> friendActivityRepository.feed.map { friendActivityState(true, it) }
        }
        return combine(data, common) { shown, viewerAndLook -> shown to viewerAndLook }.distinctUntilChanged()
    }

    /**
     * Android 15+ shows previews drawn by the widgets themselves (`providePreview`) in the widget
     * picker. They are published once per installed version; the system limits how often this works.
     */
    private suspend fun publishPreviews() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val version = "${info.longVersionCode}-${info.lastUpdateTime}"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_PREVIEWS, null) == version) return
        val manager = GlanceAppWidgetManager(context)
        val results = listOf(
            InProgressWidgetReceiver::class,
            NextEpisodeWidgetReceiver::class,
            FriendActivityWidgetReceiver::class
        ).map { runCatching { manager.setWidgetPreviews(it) }.getOrNull() }
        if (results.all { it == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }) {
            prefs.edit().putString(KEY_PREVIEWS, version).apply()
        }
    }

    private companion object {
        const val REDRAW_DEBOUNCE_MILLIS = 500L

        /** Only which app version published its previews; nothing about the user. */
        const val PREFS = "widgets"
        const val KEY_PREVIEWS = "previews_version"
    }
}
