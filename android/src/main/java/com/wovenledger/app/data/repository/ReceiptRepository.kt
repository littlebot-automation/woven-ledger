package com.wovenledger.app.data.repository

import com.wovenledger.app.data.dao.ReceiptDao
import com.wovenledger.app.data.entities.Receipt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptRepository @Inject constructor(private val dao: ReceiptDao) {

    suspend fun create(receipt: Receipt): Long = dao.insert(receipt)

    fun read(id: Long): Flow<Receipt?> = dao.getReceipt(id)

    suspend fun update(receipt: Receipt) = dao.update(receipt)

    suspend fun delete(receipt: Receipt) = dao.delete(receipt)

    fun getAll(): Flow<List<Receipt>> = dao.getAllReceipts()

    fun getByParty(partyId: Long): Flow<List<Receipt>> = dao.getReceiptsByParty(partyId)

    fun findForParty(partyId: Long): Flow<List<Receipt>> = dao.getReceiptsByParty(partyId)

    fun sumAmount(): Flow<Long> = dao.getAllReceipts().map { receipts ->
        receipts.sumOf { it.amount }
    }

    fun sumAmountForParty(partyId: Long): Flow<Long> = dao.getReceiptsByParty(partyId).map { receipts ->
        receipts.sumOf { it.amount }
    }

    fun findRecent(): Flow<List<Receipt>> = dao.getAllReceipts().map { it.take(10) }
}
