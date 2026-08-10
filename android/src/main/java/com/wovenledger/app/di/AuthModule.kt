package com.wovenledger.app.di

import com.wovenledger.app.data.api.AuthApiService
import com.wovenledger.app.data.auth.AuthTokens
import com.wovenledger.app.data.auth.RoomTokenStorage
import com.wovenledger.app.data.auth.SessionManager
import com.wovenledger.app.data.auth.TokenStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Wires the session's two halves together.
 *
 * [SessionManager] is asked for by name where the app needs to sign in or out, and by
 * its [AuthTokens] face where only the interceptor's narrow view is wanted — same
 * singleton either way, which matters: two instances would mean the interceptor
 * reading a token the UI had already thrown away.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindTokenStorage(storage: RoomTokenStorage): TokenStorage

    @Binds
    abstract fun bindAuthTokens(session: SessionManager): AuthTokens
}

/** Rides the Retrofit instance [ApiModule] already builds, as the labour module does. */
@Module
@InstallIn(SingletonComponent::class)
object AuthApiModule {

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService =
        retrofit.create(AuthApiService::class.java)
}
