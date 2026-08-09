package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.PurchaseBillDao
import com.wovenledger.app.data.dao.PurchaseBillLineDao
import com.wovenledger.app.data.entities.PurchaseBill
import com.wovenledger.app.data.entities.PurchaseBillWithLines
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseBillRepository @Inject constructor(
    private val dao: PurchaseBillDao,
    private val lineDao: PurchaseBillLineDao,
    private val api: WovenLedgerApiService
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

    /** Throws on failure so [com.wovenledger.app.data.sync.SyncManager] can report it. */
    suspend fun syncFromApi() {
        val apiBills = api.getPurchaseBills()
        apiBills.forEach { dto ->
            val bill = PurchaseBill(
                id = dto.id.toLong(),
                no = dto.documentNumber,
                partyId = dto.partyId.toLong(),
                plantId = 1L, // Default plant
                date = java.time.LocalDate.parse(dto.billDate),
                subtotal = dto.totalAmount - dto.gstAmount,
                discount = dto.discountAmount,
                total = dto.netAmount
            )
            val existing = dao.getBill(bill.id).first()
            if (existing == null) {
                dao.insert(bill)
            } else {
                dao.update(bill)
            }
        }
    }
}
