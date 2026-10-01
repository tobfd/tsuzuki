package com.tobfd.tsuzuki.feature.notifications.alerts

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tobfd.tsuzuki.core.common.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The one alarm for the next airing episode. */
interface EpisodeAlarmScheduler {
    fun schedule(at: Instant)

    fun cancel()
}

/**
 * An `AlarmManager` alarm, exact when the user allows alarms ("Alarms & reminders"), otherwise as close
 * as Android allows without that permission. Either way it also fires in Doze.
 */
internal class AlarmManagerEpisodeAlarms @Inject constructor(@ApplicationContext private val context: Context) :
    EpisodeAlarmScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, AlertPlanReceiver::class.java).setAction(AlertPlanReceiver.ACTION_EPISODE_AIRED),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    override fun schedule(at: Instant) {
        val time = at.toEpochMilli()
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent())
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent())
        }
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent())
    }
}

/**
 * Makes the plan again: when an episode aired (the alarm), and after things that change it: a reboot or
 * an app update (alarms are gone), the alarm permission changed, or the user switched the app's
 * notifications or a channel on or off.
 */
@AndroidEntryPoint
class AlertPlanReceiver : BroadcastReceiver() {
    @Inject
    lateinit var coordinator: AlertCoordinator

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val pending = goAsync()
        scope.launch {
            try {
                coordinator.replan()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** The episode alarm. */
        const val ACTION_EPISODE_AIRED = "com.tobfd.tsuzuki.action.EPISODE_AIRED"

        private val ACTIONS = setOf(
            ACTION_EPISODE_AIRED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED,
            NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED
        )
    }
}
