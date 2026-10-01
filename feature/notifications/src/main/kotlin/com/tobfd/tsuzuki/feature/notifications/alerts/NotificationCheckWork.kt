package com.tobfd.tsuzuki.feature.notifications.alerts

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** The periodic check for new AniList notifications. */
interface NotificationCheckScheduler {
    /** Starts the periodic check unless it already runs (an existing schedule is kept). */
    fun ensureScheduled()

    fun cancel()
}

internal class WorkManagerNotificationChecks @Inject constructor(@ApplicationContext private val context: Context) :
    NotificationCheckScheduler {

    override fun ensureScheduled() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<NotificationCheckWorker>(INTERVAL.toMinutes(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }

    private companion object {
        const val NAME = "anilist-notification-check"
        const val TAG = "alerts"

        /** About every half hour: one small request, a second one only when something new arrived. */
        val INTERVAL: Duration = Duration.ofMinutes(30)
    }
}

/**
 * Checks for new AniList notifications (docs/ANILIST_API.md, Android notifications). Failures wait for the
 * next period instead of retrying, so a rate limit or an outage never adds requests.
 */
@HiltWorker
internal class NotificationCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: AlertCoordinator
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        coordinator.checkNotifications()
        return Result.success()
    }
}
