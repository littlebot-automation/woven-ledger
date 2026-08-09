package com.wovenledger.app.di

import com.wovenledger.app.data.api.SettingsApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/** Rides the Retrofit instance [ApiModule] already builds, as the labour module does. */
@Module
@InstallIn(SingletonComponent::class)
object SettingsApiModule {

    @Provides
    @Singleton
    fun provideSettingsApiService(retrofit: Retrofit): SettingsApiService =
        retrofit.create(SettingsApiService::class.java)
}
