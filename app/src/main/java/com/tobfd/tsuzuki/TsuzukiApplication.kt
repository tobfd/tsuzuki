package com.tobfd.tsuzuki

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tobfd.tsuzuki.feature.notifications.alerts.AlertCoordinator
import com.tobfd.tsuzuki.feature.notifications.alerts.createAlertChannels
import com.tobfd.tsuzuki.feature.widgets.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TsuzukiApplication :
    Application(),
    Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var widgetUpdater: WidgetUpdater

    @Inject
    lateinit var alertCoordinator: AlertCoordinator

    override fun onCreate() {
        super.onCreate()
        // Placed home-screen widgets follow every change the app makes while it runs.
        widgetUpdater.start()
        // Android notifications: channels for the system settings, alarms and checks follow the session.
        createAlertChannels(this)
        alertCoordinator.start()
    }

    /** WorkManager starts on demand with Hilt's factory (the default initializer is removed in the manifest). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
