package com.wovenledger.app.data.repository

import com.wovenledger.app.data.dao.SettingsDao
import com.wovenledger.app.data.entities.Settings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(private val dao: SettingsDao) {

    suspend fun create(settings: Settings) = dao.insert(settings)

    fun read(): Flow<Settings?> = dao.getSettings()

    suspend fun update(settings: Settings) = dao.update(settings)

    fun getSettings(): Flow<Settings?> = dao.getSettings()

    suspend fun incrementInvoiceNumber(settings: Settings) {
        val updated = settings.copy(nextInvoiceNo = settings.nextInvoiceNo + 1)
        dao.update(updated)
    }

    suspend fun incrementPurchaseNumber(settings: Settings) {
        val updated = settings.copy(nextPurchaseNo = settings.nextPurchaseNo + 1)
        dao.update(updated)
    }

    suspend fun incrementReceiptNumber(settings: Settings) {
        val updated = settings.copy(nextReceiptNo = settings.nextReceiptNo + 1)
        dao.update(updated)
    }

    suspend fun incrementPaymentNumber(settings: Settings) {
        val updated = settings.copy(nextPaymentNo = settings.nextPaymentNo + 1)
        dao.update(updated)
    }
}
