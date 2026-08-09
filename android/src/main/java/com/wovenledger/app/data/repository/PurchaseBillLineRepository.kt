package com.wovenledger.app.data.repository

import com.wovenledger.app.data.dao.PurchaseBillLineDao
import com.wovenledger.app.data.entities.PurchaseBillLine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseBillLineRepository @Inject constructor(private val dao: PurchaseBillLineDao) {

    suspend fun create(line: PurchaseBillLine): Long = dao.insert(line)

    fun getByBill(billId: Long): Flow<List<PurchaseBillLine>> = dao.getLinesByBill(billId)

    suspend fun delete(line: PurchaseBillLine) = dao.delete(line)

    suspend fun deleteByBill(billId: Long) {
        dao.getLinesByBill(billId).collect { lines ->
            lines.forEach { line -> dao.delete(line) }
        }
    }

    fun hasEntriesFor(billId: Long): Flow<List<PurchaseBillLine>> = dao.getLinesByBill(billId)
}
