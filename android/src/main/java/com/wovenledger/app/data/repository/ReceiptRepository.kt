package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.NewReceipt
import com.wovenledger.app.data.api.ReceiptDto
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.ReceiptDao
import com.wovenledger.app.data.entities.Receipt
import com.wovenledger.app.data.outbox.Outbox
import com.wovenledger.app.data.outbox.OutboxOperation
import com.wovenledger.app.data.outbox.OutboxTarget
import com.wovenledger.app.data.outbox.attemptOnline
import com.wovenledger.app.data.outbox.nextLocalId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptRepository @Inject constructor(
    private val dao: ReceiptDao,
    private val api: WovenLedgerApiService,
    private val outbox: Outbox,
) {

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

    /**
     * Books the receipt on the server and mirrors it into Room; queues it if the
     * phone cannot reach the server.
     *
     * The receipt number comes back from the server. Nothing here invents one — not
     * even for a queued receipt, which waits without a number rather than claiming
     * `RC-0008` and colliding with whatever the server issues next.
     */
    suspend fun createOnServer(receipt: NewReceipt): SavedDocument<Receipt> {
        val dto = attemptOnline { api.createReceipt(receipt) } ?: return queueCreate(receipt)

        return SavedDocument(store(dto))
    }

    suspend fun updateOnServer(id: Long, receipt: NewReceipt): SavedDocument<Receipt> {
        // A document that has not uploaded has no server id to PUT to: editing it
        // rewrites the write already queued for it instead of asking the server about
        // a record it has never seen.
        if (id < 0) {
            return queueUpdate(id, receipt)
        }

        val dto = attemptOnline { api.updateReceipt(id.toInt(), receipt) }
            ?: return queueUpdate(id, receipt)

        return SavedDocument(store(dto))
    }

    private suspend fun queueCreate(body: NewReceipt): SavedDocument<Receipt> {
        val localId = nextLocalId(dao.minId())
        val echo = echoOf(localId, body)

        dao.insert(echo)
        outbox.enqueue(OutboxTarget.RECEIPT, OutboxOperation.CREATE, localId, body)

        return SavedDocument(echo, queued = true)
    }

    private suspend fun queueUpdate(id: Long, body: NewReceipt): SavedDocument<Receipt> {
        val stored = dao.getReceipt(id).first()
        val edited = echoOf(id, body).copy(no = stored?.no.orEmpty())

        dao.insert(edited)
        outbox.enqueue(OutboxTarget.RECEIPT, OutboxOperation.UPDATE, id, body)

        return SavedDocument(edited, queued = true)
    }

    /**
     * Removes a locally written receipt that is never going to be uploaded.
     *
     * Only the outbox calls this, when the user abandons a queued write.
     */
    suspend fun deleteLocal(localId: Long) = dao.deleteById(localId)

    /** Sends a queued create, then swaps the local echo for what the server stored. */
    suspend fun uploadCreate(localId: Long, body: NewReceipt): List<String> {
        val dto = api.createReceipt(body)

        dao.deleteById(localId)
        store(dto)

        return emptyList()
    }

    suspend fun uploadUpdate(id: Long, body: NewReceipt): List<String> {
        store(api.updateReceipt(id.toInt(), body))

        return emptyList()
    }

    /** Throws on failure so [com.wovenledger.app.data.sync.SyncManager] can report it. */
    suspend fun syncFromApi() {
        val dtos = api.getReceipts()

        dtos.forEach { dto ->
            val receipt = toEntity(dto) ?: return@forEach

            if (dao.getReceipt(receipt.id).first() == null) {
                dao.insert(receipt)
            } else {
                dao.update(receipt)
            }
        }

        // Upserting alone left receipts deleted on the portal on the phone for good.
        // Queued receipts are kept: the server has never seen them, so their absence
        // from its list is not a deletion.
        val keep = dtos.map { it.id.toLong() } + dao.pendingIds()
        if (keep.isEmpty()) dao.deleteSynced() else dao.deleteMissing(keep)
    }

    /** What a queued receipt looks like in the app while it waits — no number yet. */
    private fun echoOf(id: Long, body: NewReceipt) = Receipt(
        id = id,
        no = "",
        partyId = body.partyId.toLong(),
        amount = body.amount,
        date = LocalDate.parse(body.receiptDate),
        mode = paymentModeOf(body.mode),
        notes = body.notes.orEmpty(),
    )

    private suspend fun store(dto: ReceiptDto): Receipt =
        // The server rejects a receipt without a party, so a saved one always has one.
        checkNotNull(toEntity(dto)) { "The server returned a receipt with no party." }
            .also { dao.insert(it) }

    /** Null when the row has no party — Room requires one and the portal always books one. */
    private fun toEntity(dto: ReceiptDto): Receipt? {
        val partyId = dto.partyId?.toLong() ?: return null

        return Receipt(
            id = dto.id.toLong(),
            no = dto.documentNumber,
            partyId = partyId,
            amount = dto.amount,
            date = java.time.LocalDate.parse(dto.receiptDate),
            mode = paymentModeOf(dto.mode),
            notes = dto.notes ?: "",
        )
    }
}
