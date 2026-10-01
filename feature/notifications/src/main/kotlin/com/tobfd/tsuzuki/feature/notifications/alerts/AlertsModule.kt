package com.tobfd.tsuzuki.feature.notifications.alerts

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface AlertsModule {
    @Binds
    fun gate(impl: SystemAlertGate): AlertGate

    @Binds
    fun alarms(impl: AlarmManagerEpisodeAlarms): EpisodeAlarmScheduler

    @Binds
    fun checks(impl: WorkManagerNotificationChecks): NotificationCheckScheduler

    @Binds
    fun poster(impl: SystemAlertPoster): AlertPoster
}
