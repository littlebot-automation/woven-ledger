package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.NewPurchaseBill
import com.wovenledger.app.data.api.PurchaseBillDto
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.PurchaseBillDao
import com.wovenledger.app.data.dao.PurchaseBillLineDao
import com.wovenledger.app.data.entities.PurchaseBill
import com.wovenledger.app.data.entities.PurchaseBillLine
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
    private val api: WovenLedgerApiService,
    private val plants: PlantRepository
) {

    suspend fun create(bill: PurchaseBill): Long = dao.insert(bill)

    fun read(id: Long): Flow<PurchaseBill?> = dao.getBill(id)

    suspend fun update(bill: PurchaseBill) = dao.update(bill)

    suspend fun delete(bill: PurchaseBill) = dao.delete(bill)

    fun getAll(): Flow<List<PurchaseBill>> = dao.getAllBills()

    fun getByParty(partyId: Long): Flow<List<PurchaseBill>> = dao.getBillsByParty(partyId)

    fun findForParty(partyId: Long): Flow<List<PurchaseBill>> = dao.getBillsByParty(partyId)

    fun getLines(billId: Long): Flow<List<PurchaseBillLine>> = lineDao.getLinesByBill(billId)

    /** Purchase lines for one item, for its movement history. */
    fun getLinesForItem(itemId: Long): Flow<List<PurchaseBillLine>> = lineDao.getLinesByItem(itemId)

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

    /**
     * Books the bill on the server and mirrors what came back into Room.
     *
     * A purchase adds stock, so it can never fall short; the warnings list is
     * carried anyway so both document forms read one response shape.
     */
    suspend fun createOnServer(bill: NewPurchaseBill): SavedDocument<PurchaseBill> =
        store(api.createPurchaseBill(bill))

    suspend fun updateOnServer(id: Long, bill: NewPurchaseBill): SavedDocument<PurchaseBill> =
        store(api.updatePurchaseBill(id.toInt(), bill))

    /** Throws on failure so [com.wovenledger.app.data.sync.SyncManager] can report it. */
    suspend fun syncFromApi() {
        val dtos = api.getPurchaseBills()

        dtos.forEach { dto ->
            val bill = toEntity(dto)
            plants.ensureExists(bill.plantId)

            if (dao.getBill(bill.id).first() == null) {
                dao.insert(bill)
            } else {
                dao.update(bill)
            }

            storeLines(dto)
        }

        // Upserting alone left bills deleted on the portal on the phone for good.
        val ids = dtos.map { it.id.toLong() }
        if (ids.isEmpty()) dao.deleteAll() else dao.deleteMissing(ids)
    }

    private suspend fun store(dto: PurchaseBillDto): SavedDocument<PurchaseBill> {
        val bill = toEntity(dto)
        plants.ensureExists(bill.plantId)
        dao.insert(bill)
        storeLines(dto)

        return SavedDocument(bill, dto.warnings)
    }

    private suspend fun storeLines(dto: PurchaseBillDto) {
        lineDao.deleteForBill(dto.id.toLong())

        dto.lines.forEach { line ->
            lineDao.insert(
                PurchaseBillLine(
                    id = line.id.toLong(),
                    billId = dto.id.toLong(),
                    itemId = line.itemId.toLong(),
                    qty = line.qty,
                    rate = line.rate,
                    amount = line.amount
                )
            )
        }
    }

    private fun toEntity(dto: PurchaseBillDto) = PurchaseBill(
        id = dto.id.toLong(),
        no = dto.documentNumber,
        partyId = dto.partyId.toLong(),
        plantId = dto.plantId?.toLong() ?: DEFAULT_PLANT_ID,
        date = java.time.LocalDate.parse(dto.billDate),
        subtotal = dto.totalAmount - dto.gstAmount,
        discount = dto.discountAmount,
        total = dto.netAmount
    )

    private companion object {
        const val DEFAULT_PLANT_ID = 1L
    }
}
