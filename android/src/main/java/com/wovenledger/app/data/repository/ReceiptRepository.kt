package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.ReceiptDao
import com.wovenledger.app.data.entities.PaymentMode
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

    /** Throws on failure so [com.wovenledger.app.data.sync.SyncManager] can report it. */
    suspend fun syncFromApi() {
        api.getReceipts().forEach { dto ->
            val receipt = Receipt(
                id = dto.id.toLong(),
                no = dto.documentNumber,
                // Room requires a party; the portal always books a receipt against one.
                partyId = dto.partyId?.toLong() ?: return@forEach,
                amount = dto.amount,
                date = java.time.LocalDate.parse(dto.receiptDate),
                // The API sends display labels such as "Bank Transfer".
                mode = dto.mode
                    ?.let { PaymentMode.valueOf(it.uppercase().replace(' ', '_')) }
                    ?: PaymentMode.CASH,
                notes = dto.notes ?: "",
            )

            if (dao.getReceipt(receipt.id).first() == null) {
                dao.insert(receipt)
            } else {
                dao.update(receipt)
            }
        }
    }
}
