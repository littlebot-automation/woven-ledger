package com.wovenledger.app.di

import com.wovenledger.app.data.api.LedgerApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * The ledger endpoint rides the Retrofit instance [ApiModule] already builds — same
 * base URL, client and converter — so only the service interface is provided here.
 */
@Module
@InstallIn(SingletonComponent::class)
object LedgerApiModule {

    @Provides
    @Singleton
    fun provideLedgerApiService(retrofit: Retrofit): LedgerApiService =
        retrofit.create(LedgerApiService::class.java)
}
