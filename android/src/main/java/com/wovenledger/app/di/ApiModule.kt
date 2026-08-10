package com.wovenledger.app.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.wovenledger.app.BuildConfig
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.auth.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    /**
     * The live portal in a release build, and the machine running the app in a debug
     * one — 10.0.2.2 is the emulator's route to the host's loopback, so a local Symfony
     * on :8000 is reachable without republishing anything.
     */
    private val BASE_URL = BuildConfig.API_BASE_URL

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
        .create()

    @Provides
    @Singleton
    fun provideHttpClient(auth: AuthInterceptor): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // BODY on a release build would write the sign-in password and the whole
            // JWT into logcat, where any other app with log access could read them.
            // Debug builds still get full bodies, minus the bearer header.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }

            redactHeader("Authorization")
        }

        return OkHttpClient.Builder()
            // Auth first: the logger then sees the request as it will be sent, and
            // redacts the header it just gained.
            .addInterceptor(auth)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(gson: Gson, httpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun provideWovenLedgerApiService(retrofit: Retrofit): WovenLedgerApiService =
        retrofit.create(WovenLedgerApiService::class.java)
}
