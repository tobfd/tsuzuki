package com.tobfd.tsuzuki.core.data.di

import com.tobfd.tsuzuki.core.data.list.ConnectivityNetworkMonitor
import com.tobfd.tsuzuki.core.data.list.DatabaseUserDataCleaner
import com.tobfd.tsuzuki.core.data.list.DefaultListRepository
import com.tobfd.tsuzuki.core.data.list.ListRepository
import com.tobfd.tsuzuki.core.data.list.ListWorkScheduler
import com.tobfd.tsuzuki.core.data.list.NetworkMonitor
import com.tobfd.tsuzuki.core.data.list.UserDataCleaner
import com.tobfd.tsuzuki.core.data.list.WorkManagerListWorkScheduler
import com.tobfd.tsuzuki.core.data.notifications.DefaultNotificationsRepository
import com.tobfd.tsuzuki.core.data.notifications.NotificationsRepository
import com.tobfd.tsuzuki.core.data.session.DefaultSessionRepository
import com.tobfd.tsuzuki.core.data.session.SessionRepository
import com.tobfd.tsuzuki.core.data.session.SessionTokenProvider
import com.tobfd.tsuzuki.core.network.auth.AccessTokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun sessionRepository(impl: DefaultSessionRepository): SessionRepository

    @Binds
    fun accessTokenProvider(impl: SessionTokenProvider): AccessTokenProvider

    @Binds
    fun notificationsRepository(impl: DefaultNotificationsRepository): NotificationsRepository

    @Binds
    fun listRepository(impl: DefaultListRepository): ListRepository

    @Binds
    fun listWorkScheduler(impl: WorkManagerListWorkScheduler): ListWorkScheduler

    @Binds
    fun userDataCleaner(impl: DatabaseUserDataCleaner): UserDataCleaner

    @Binds
    fun networkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor
}
