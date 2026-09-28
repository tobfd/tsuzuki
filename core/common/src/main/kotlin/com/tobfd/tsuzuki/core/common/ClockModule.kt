package com.tobfd.tsuzuki.core.common

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    /** Wall clock for expiry checks; tests pass a fixed `Clock`. */
    @Provides
    fun clock(): Clock = Clock.systemUTC()
}
