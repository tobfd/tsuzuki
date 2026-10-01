package com.tobfd.tsuzuki.feature.widgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tobfd.tsuzuki.core.common.AppError
import com.tobfd.tsuzuki.core.data.widget.AiringRepository
import com.tobfd.tsuzuki.core.data.widget.FriendActivityRepository
import com.tobfd.tsuzuki.feature.widgets.friends.FriendActivityWidget
import com.tobfd.tsuzuki.feature.widgets.inprogress.InProgressWidget
import com.tobfd.tsuzuki.feature.widgets.nextepisode.NextEpisodeWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** The three widgets. */
enum class WidgetKind {
    InProgress,
    NextEpisode,
    FriendActivity;

    internal fun widget(): GlanceAppWidget = when (this) {
        InProgress -> InProgressWidget()
        NextEpisode -> NextEpisodeWidget()
        FriendActivity -> FriendActivityWidget()
    }
}

/**
 * The widgets' background work (docs/ANILIST_API.md, Widgets). Only these requests exist, and only
 * while the matching widget is on a home screen:
 * - "Next episode": one `NextEpisodes` request, at most once an hour, soon after the next episode
 *   airs and at least every 12 hours; between those a request-free redraw when an episode airs.
 * - "Friends' activity": one `ActivityFeed` request every 3 hours, backed off on errors.
 * "In Progress" reads Room only and never schedules anything.
 */
internal object WidgetWork {
    private const val TAG = "widgets"
    private const val FRIENDS_PERIODIC = "widget-friends"
    private const val FRIENDS_NOW = "widget-friends-now"
    private const val NEXT_FETCH = "widget-next-episode-fetch"
    private const val NEXT_REDRAW = "widget-next-episode-redraw"
    const val KEY_FETCH = "fetch"

    private val FRIENDS_INTERVAL: Duration = Duration.ofHours(3)
    private val FRIENDS_BACKOFF: Duration = Duration.ofMinutes(15)
    private val NEXT_BACKOFF: Duration = Duration.ofHours(1)
    val NEXT_MIN_INTERVAL: Duration = Duration.ofHours(1)
    val NEXT_MAX_INTERVAL: Duration = Duration.ofHours(12)

    /** AniList moves `nextAiringEpisode` on once the episode is out; a little later is safe. */
    val AFTER_AIRING: Duration = Duration.ofMinutes(2)

    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun scheduleFriendActivity(context: Context) {
        val request = PeriodicWorkRequestBuilder<FriendActivityWorker>(FRIENDS_INTERVAL.toMinutes(), TimeUnit.MINUTES)
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, FRIENDS_BACKOFF.toMinutes(), TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(FRIENDS_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One extra update right after a login, so a placed widget doesn't wait for the next period. */
    fun refreshFriendActivityNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<FriendActivityWorker>()
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, FRIENDS_BACKOFF.toMinutes(), TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(FRIENDS_NOW, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelFriendActivity(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(FRIENDS_PERIODIC)
            cancelUniqueWork(FRIENDS_NOW)
        }
    }

    /**
     * The next `NextEpisodes` request in [delay]. From the worker itself it is appended to the
     * running request; from outside ([fromWorker] false) a scheduled one is kept, so placing a
     * second widget or logging in again never adds requests.
     */
    fun scheduleNextEpisodeFetch(context: Context, delay: Duration, fromWorker: Boolean) {
        val request = OneTimeWorkRequestBuilder<NextEpisodeWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, NEXT_BACKOFF.toMinutes(), TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_FETCH to true))
            .addTag(TAG)
            .build()
        val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(NEXT_FETCH, policy, request)
    }

    /**
     * A redraw without a request at [at], when an episode airs; null cancels it. It replaces the
     * planned one, unless it comes from that redraw itself ([fromRedraw]), which it follows.
     */
    fun scheduleNextEpisodeRedraw(context: Context, at: Instant?, now: Instant, fromRedraw: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (at == null) {
            if (!fromRedraw) workManager.cancelUniqueWork(NEXT_REDRAW)
            return
        }
        val request = OneTimeWorkRequestBuilder<NextEpisodeWorker>()
            .setInitialDelay(Duration.between(now, at).coerceAtLeast(Duration.ZERO).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_FETCH to false))
            .addTag(TAG)
            .build()
        val policy = if (fromRedraw) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        workManager.enqueueUniqueWork(NEXT_REDRAW, policy, request)
    }

    fun cancelNextEpisode(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(NEXT_FETCH)
            cancelUniqueWork(NEXT_REDRAW)
        }
    }

    /**
     * When the next `NextEpisodes` request goes out after one at [now]: shortly after the soonest
     * episode airs, but never sooner than an hour and never later than 12 hours.
     */
    fun nextFetchDelay(soonestAiring: Instant?, now: Instant): Duration {
        val untilAired = soonestAiring?.let { Duration.between(now, it).plus(AFTER_AIRING) } ?: NEXT_MAX_INTERVAL
        return untilAired.coerceIn(NEXT_MIN_INTERVAL, NEXT_MAX_INTERVAL)
    }
}

/** An error worth trying again later with backoff; the rest waits for the next planned update. */
private fun Throwable.isTemporary(): Boolean =
    this is AppError.Offline || this is AppError.RateLimited || this is AppError.ApiUnavailable

/** Fetches the followed users' activities for the "Friends' activity" widget. */
@HiltWorker
internal class FriendActivityWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: FriendActivityRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val widget = FriendActivityWidget()
        if (!widget.isPlaced(applicationContext)) return Result.success()
        val error = repository.refresh().exceptionOrNull()
        // Logged out: the widget shows the log-in state, nothing to report.
        widget.setRefreshProblem(
            applicationContext,
            error?.takeUnless {
                it is AppError.Unauthorized
            }?.let(RefreshProblem::of)
        )
        return if (error?.isTemporary() == true) Result.retry() else Result.success()
    }
}

/**
 * The "Next episode" widget's work: with [WidgetWork.KEY_FETCH] one `NextEpisodes` request, else only
 * a redraw because an episode aired. Either way it plans the next redraw, and a fetch plans the next
 * fetch.
 */
@HiltWorker
internal class NextEpisodeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: AiringRepository,
    private val clock: Clock
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val widget = NextEpisodeWidget()
        if (!widget.isPlaced(applicationContext)) return Result.success()
        val fetch = inputData.getBoolean(WidgetWork.KEY_FETCH, false)
        if (fetch) {
            val error = repository.refresh().exceptionOrNull()
            widget.setRefreshProblem(
                applicationContext,
                error?.takeUnless { it is AppError.Unauthorized }?.let(RefreshProblem::of)
            )
            when {
                // A login starts the chain again (WidgetUpdater).
                error is AppError.Unauthorized -> return Result.success()

                error?.isTemporary() == true -> return Result.retry()
            }
            val now = clock.instant()
            WidgetWork.scheduleNextEpisodeFetch(
                applicationContext,
                WidgetWork.nextFetchDelay(soonestAiring(now), now),
                fromWorker = true
            )
        } else {
            widget.updateAll(applicationContext)
        }
        val now = clock.instant()
        WidgetWork.scheduleNextEpisodeRedraw(applicationContext, soonestAiring(now), now, fromRedraw = !fetch)
        return Result.success()
    }

    private suspend fun soonestAiring(now: Instant): Instant? =
        repository.upcoming.first().map { it.airingAt }.filter { it > now }.minOrNull()
}
