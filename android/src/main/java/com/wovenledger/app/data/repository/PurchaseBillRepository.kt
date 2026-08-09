package com.wovenledger.app.data.repository

import com.wovenledger.app.data.dao.PurchaseBillDao
import com.wovenledger.app.data.dao.PurchaseBillLineDao
import com.wovenledger.app.data.entities.PurchaseBill
import com.wovenledger.app.data.entities.PurchaseBillWithLines
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseBillRepository @Inject constructor(
    private val dao: PurchaseBillDao,
    private val lineDao: PurchaseBillLineDao
) {

    suspend fun create(bill: PurchaseBill): Long = dao.insert(bill)

    fun read(id: Long): Flow<PurchaseBill?> = dao.getBill(id)

    suspend fun update(bill: PurchaseBill) = dao.update(bill)

    suspend fun delete(bill: PurchaseBill) = dao.delete(bill)

    fun getAll(): Flow<List<PurchaseBill>> = dao.getAllBills()

    fun getByParty(partyId: Long): Flow<List<PurchaseBill>> = dao.getBillsByParty(partyId)

    fun findForParty(partyId: Long): Flow<List<PurchaseBill>> = dao.getBillsByParty(partyId)

    fun getWithLines(id: Long): Flow<PurchaseBillWithLines?> = combine(
        dao.getBill(id),
        lineDao.getLinesByBill(id)
    ) { bill, lines ->
        if (bill != null) {
            PurchaseBillWithLines(bill, lines)
        } else {
            null
        }
    }

    fun sumTotal(): Flow<Long> = dao.getAllBills().map { bills ->
        bills.sumOf { it.total }
    }

    fun sumTotalForParty(partyId: Long): Flow<Long> = dao.getBillsByParty(partyId).map { bills ->
        bills.sumOf { it.total }
    }

    fun findRecent(): Flow<List<PurchaseBill>> = dao.getAllBills().map { it.take(10) }
}
