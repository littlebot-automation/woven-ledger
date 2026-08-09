package com.wovenledger.app.di

import com.wovenledger.app.data.api.StockApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * The plant and stock endpoints ride the Retrofit instance [ApiModule] already builds —
 * same base URL, client and converter — so only the service interface is provided here.
 */
@Module
@InstallIn(SingletonComponent::class)
object StockApiModule {

    @Provides
    @Singleton
    fun provideStockApiService(retrofit: Retrofit): StockApiService =
        retrofit.create(StockApiService::class.java)
}
