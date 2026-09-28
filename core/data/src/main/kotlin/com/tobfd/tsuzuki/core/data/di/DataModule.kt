package com.tobfd.tsuzuki.core.data.di

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
}
