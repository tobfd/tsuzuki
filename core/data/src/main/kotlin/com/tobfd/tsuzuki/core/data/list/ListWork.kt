package com.tobfd.tsuzuki.core.data.list

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tobfd.tsuzuki.core.common.AppError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Background work for the lists, behind an interface so repository tests don't need WorkManager. */
internal interface ListWorkScheduler {
    /** Sends the queue as soon as there is a network, retrying with exponential backoff. */
    fun sendQueuedChanges()

    /** Syncs both lists every 6 hours while logged in. */
    fun schedulePeriodicSync()

    fun cancelAll()
}

private const val TAG = "lists"
private const val SEND_WORK = "lists-send"
private const val SYNC_WORK = "lists-sync"
private const val SYNC_INTERVAL_HOURS = 6L
private const val BACKOFF_SECONDS = 30L

internal class WorkManagerListWorkScheduler @Inject constructor(@ApplicationContext private val context: Context) :
    ListWorkScheduler {

    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun sendQueuedChanges() {
        val request = OneTimeWorkRequestBuilder<ListMutationWorker>()
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()
        // Appending keeps a change made while a run is finishing from waiting for the next trigger.
        WorkManager.getInstance(context).enqueueUniqueWork(SEND_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<ListSyncWorker>(SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(connected)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }
}

/** Sends the queued list changes; see [ListMutationSender]. */
@HiltWorker
internal class ListMutationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sender: ListMutationSender
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (sender.flush()) {
        FlushResult.Done -> Result.success()

        FlushResult.Retry -> Result.retry()

        // The session ends; logout clears the queue.
        FlushResult.Unauthorized -> Result.failure()
    }
}

/** The 6-hourly list sync; sends the queue first. */
@HiltWorker
internal class ListSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ListRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val error = repository.refresh(force = false).exceptionOrNull() ?: return Result.success()
        val temporary = error is AppError.Offline || error is AppError.RateLimited || error is AppError.ApiUnavailable
        return if (temporary) Result.retry() else Result.success()
    }
}
