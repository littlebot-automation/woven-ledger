package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.NewPurchaseBill
import com.wovenledger.app.data.api.PurchaseBillDto
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.PurchaseBillDao
import com.wovenledger.app.data.dao.PurchaseBillLineDao
import com.wovenledger.app.data.entities.PurchaseBill
import com.wovenledger.app.data.entities.PurchaseBillLine
import com.wovenledger.app.data.entities.PurchaseBillWithLines
import com.wovenledger.app.data.entities.amountOf
import com.wovenledger.app.data.outbox.Outbox
import com.wovenledger.app.data.outbox.OutboxOperation
import com.wovenledger.app.data.outbox.OutboxTarget
import com.wovenledger.app.data.outbox.attemptOnline
import com.wovenledger.app.data.outbox.nextLocalId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseBillRepository @Inject constructor(
    private val dao: PurchaseBillDao,
    private val lineDao: PurchaseBillLineDao,
    private val api: WovenLedgerApiService,
    private val plants: PlantRepository,
    private val outbox: Outbox
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
    suspend fun createOnServer(bill: NewPurchaseBill): SavedDocument<PurchaseBill> {
        val dto = attemptOnline { api.createPurchaseBill(bill) } ?: return queueCreate(bill)

        return store(dto)
    }

    suspend fun updateOnServer(id: Long, bill: NewPurchaseBill): SavedDocument<PurchaseBill> {
        // A document that has not uploaded has no server id to PUT to: editing it
        // rewrites the write already queued for it instead of asking the server about
        // a record it has never seen.
        if (id < 0) {
            return queueUpdate(id, bill)
        }

        val dto = attemptOnline { api.updatePurchaseBill(id.toInt(), bill) }
            ?: return queueUpdate(id, bill)

        return store(dto)
    }

    /**
     * Writes the bill locally under a negative id and queues it. It has no number
     * until it uploads: `PB-0011` is the server's to issue.
     */
    private suspend fun queueCreate(body: NewPurchaseBill): SavedDocument<PurchaseBill> {
        val localId = nextLocalId(dao.minId())
        val echo = echoOf(localId, body)

        dao.insert(echo)
        storeQueuedLines(localId, body)
        outbox.enqueue(OutboxTarget.PURCHASE_BILL, OutboxOperation.CREATE, localId, body)

        return SavedDocument(echo, queued = true)
    }

    private suspend fun queueUpdate(id: Long, body: NewPurchaseBill): SavedDocument<PurchaseBill> {
        val stored = dao.getBill(id).first()
        val edited = echoOf(id, body).copy(no = stored?.no.orEmpty())

        dao.insert(edited)
        storeQueuedLines(id, body)
        outbox.enqueue(OutboxTarget.PURCHASE_BILL, OutboxOperation.UPDATE, id, body)

        return SavedDocument(edited, queued = true)
    }

    /**
     * Removes a locally written bill that is never going to be uploaded.
     *
     * Only the outbox calls this, when the user abandons a queued write.
     */
    suspend fun deleteLocal(localId: Long) = dao.deleteById(localId)

    /** Sends a queued create, then swaps the local echo for what the server stored. */
    suspend fun uploadCreate(localId: Long, body: NewPurchaseBill): List<String> {
        val dto = api.createPurchaseBill(body)

        dao.deleteById(localId)

        return store(dto).warnings
    }

    suspend fun uploadUpdate(id: Long, body: NewPurchaseBill): List<String> =
        store(api.updatePurchaseBill(id.toInt(), body)).warnings

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
        // Queued bills are kept: absent from the server's list means "never sent",
        // not "deleted", and pruning them would throw away unsent work.
        val keep = dtos.map { it.id.toLong() } + dao.pendingIds()
        if (keep.isEmpty()) dao.deleteSynced() else dao.deleteMissing(keep)
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

    /**
     * What a queued bill looks like in the app while it waits — this phone's
     * arithmetic, no number, and no claim about stock.
     */
    private fun echoOf(id: Long, body: NewPurchaseBill): PurchaseBill {
        val subtotal = body.lines.sumOf { amountOf(it.qty, it.rate ?: 0L) }

        return PurchaseBill(
            id = id,
            no = "",
            partyId = body.partyId.toLong(),
            plantId = body.plantId?.toLong() ?: DEFAULT_PLANT_ID,
            date = LocalDate.parse(body.billDate),
            subtotal = subtotal,
            discount = body.discountAmount,
            total = maxOf(0L, subtotal - body.discountAmount),
        )
    }

    private suspend fun storeQueuedLines(billId: Long, body: NewPurchaseBill) {
        lineDao.deleteForBill(billId)

        var lineId = nextLocalId(lineDao.minId())
        body.lines.forEach { line ->
            lineDao.insert(
                PurchaseBillLine(
                    id = lineId,
                    billId = billId,
                    itemId = line.itemId.toLong(),
                    qty = line.qty,
                    rate = line.rate ?: 0L,
                )
            )
            lineId -= 1
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
