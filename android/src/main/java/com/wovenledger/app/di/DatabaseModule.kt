package com.wovenledger.app.di

import android.content.Context
import androidx.room.Room
import com.wovenledger.app.data.database.WovenLedgerDatabase
import com.wovenledger.app.data.dao.*
import com.wovenledger.app.data.auth.AuthTokenDao
import com.wovenledger.app.data.outbox.OutboxDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(
        @ApplicationContext context: Context
    ): WovenLedgerDatabase {
        return WovenLedgerDatabase.getDatabase(context)
    }

    @Singleton
    @Provides
    fun providePlantDao(database: WovenLedgerDatabase): PlantDao = database.plantDao()

    @Singleton
    @Provides
    fun provideSettingsDao(database: WovenLedgerDatabase): SettingsDao = database.settingsDao()

    @Singleton
    @Provides
    fun providePartyDao(database: WovenLedgerDatabase): PartyDao = database.partyDao()

    @Singleton
    @Provides
    fun provideItemDao(database: WovenLedgerDatabase): ItemDao = database.itemDao()

    @Singleton
    @Provides
    fun provideItemStockDao(database: WovenLedgerDatabase): ItemStockDao = database.itemStockDao()

    @Singleton
    @Provides
    fun provideStaffDao(database: WovenLedgerDatabase): StaffDao = database.staffDao()

    @Singleton
    @Provides
    fun provideStaffWorkDao(database: WovenLedgerDatabase): StaffWorkDao = database.staffWorkDao()

    @Singleton
    @Provides
    fun provideSalesInvoiceDao(database: WovenLedgerDatabase): SalesInvoiceDao =
        database.salesInvoiceDao()

    @Singleton
    @Provides
    fun provideSalesInvoiceLineDao(database: WovenLedgerDatabase): SalesInvoiceLineDao =
        database.salesInvoiceLineDao()

    @Singleton
    @Provides
    fun providePurchaseBillDao(database: WovenLedgerDatabase): PurchaseBillDao =
        database.purchaseBillDao()

    @Singleton
    @Provides
    fun providePurchaseBillLineDao(database: WovenLedgerDatabase): PurchaseBillLineDao =
        database.purchaseBillLineDao()

    @Singleton
    @Provides
    fun provideReceiptDao(database: WovenLedgerDatabase): ReceiptDao = database.receiptDao()

    @Singleton
    @Provides
    fun providePaymentDao(database: WovenLedgerDatabase): PaymentDao = database.paymentDao()

    @Singleton
    @Provides
    fun provideOutboxDao(database: WovenLedgerDatabase): OutboxDao = database.outboxDao()

    @Singleton
    @Provides
    fun provideAuthTokenDao(database: WovenLedgerDatabase): AuthTokenDao = database.authTokenDao()
}
