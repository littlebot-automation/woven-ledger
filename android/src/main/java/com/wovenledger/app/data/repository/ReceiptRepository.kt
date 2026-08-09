package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.NewReceipt
import com.wovenledger.app.data.api.ReceiptDto
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.ReceiptDao
import com.wovenledger.app.data.entities.Receipt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptRepository @Inject constructor(
    private val dao: ReceiptDao,
    private val api: WovenLedgerApiService,
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
     * Books the receipt on the server and mirrors it into Room.
     *
     * The receipt number comes back from the server; nothing here invents one.
     */
    suspend fun createOnServer(receipt: NewReceipt): Receipt = store(api.createReceipt(receipt))

    suspend fun updateOnServer(id: Long, receipt: NewReceipt): Receipt =
        store(api.updateReceipt(id.toInt(), receipt))

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
        val ids = dtos.map { it.id.toLong() }
        if (ids.isEmpty()) dao.deleteAll() else dao.deleteMissing(ids)
    }

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
