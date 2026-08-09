package com.wovenledger.app.di

import com.wovenledger.app.data.api.LabourApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * The labour endpoints ride the Retrofit instance [ApiModule] already builds — same
 * base URL, client and converter — so only the service interface is provided here.
 */
@Module
@InstallIn(SingletonComponent::class)
object LabourApiModule {

    @Provides
    @Singleton
    fun provideLabourApiService(retrofit: Retrofit): LabourApiService =
        retrofit.create(LabourApiService::class.java)
}
