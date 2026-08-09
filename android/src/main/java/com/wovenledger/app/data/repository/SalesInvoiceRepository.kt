package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.SalesInvoiceDao
import com.wovenledger.app.data.dao.SalesInvoiceLineDao
import com.wovenledger.app.data.entities.SalesInvoice
import com.wovenledger.app.data.entities.SalesInvoiceWithLines
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalesInvoiceRepository @Inject constructor(
    private val dao: SalesInvoiceDao,
    private val lineDao: SalesInvoiceLineDao,
    private val api: WovenLedgerApiService
) {

    suspend fun create(invoice: SalesInvoice): Long = dao.insert(invoice)

    fun read(id: Long): Flow<SalesInvoice?> = dao.getInvoice(id)

    suspend fun update(invoice: SalesInvoice) = dao.update(invoice)

    suspend fun delete(invoice: SalesInvoice) = dao.delete(invoice)

    fun getAll(): Flow<List<SalesInvoice>> = dao.getAllInvoices()

    fun getByParty(partyId: Long): Flow<List<SalesInvoice>> = dao.getInvoicesByParty(partyId)

    fun findForParty(partyId: Long): Flow<List<SalesInvoice>> = dao.getInvoicesByParty(partyId)

    fun getWithLines(id: Long): Flow<SalesInvoiceWithLines?> = combine(
        dao.getInvoice(id),
        lineDao.getLinesByInvoice(id)
    ) { invoice, lines ->
        if (invoice != null) {
            SalesInvoiceWithLines(invoice, lines)
        } else {
            null
        }
    }

    fun sumTotal(): Flow<Long> = dao.getAllInvoices().map { invoices ->
        invoices.sumOf { it.total }
    }

    fun sumTotalForParty(partyId: Long): Flow<Long> = dao.getInvoicesByParty(partyId).map { invoices ->
        invoices.sumOf { it.total }
    }

    fun findRecent(): Flow<List<SalesInvoice>> = dao.getAllInvoices().map { it.take(10) }

    /** Throws on failure so [com.wovenledger.app.data.sync.SyncManager] can report it. */
    suspend fun syncFromApi() {
        val apiInvoices = api.getSalesInvoices()
        apiInvoices.forEach { dto ->
            val invoice = SalesInvoice(
                id = dto.id.toLong(),
                no = dto.documentNumber,
                partyId = dto.partyId.toLong(),
                plantId = 1L, // Default plant
                date = java.time.LocalDate.parse(dto.invoiceDate),
                subtotal = dto.totalAmount - dto.gstAmount,
                discount = dto.discountAmount,
                total = dto.netAmount
            )
            val existing = dao.getInvoice(invoice.id).first()
            if (existing == null) {
                dao.insert(invoice)
            } else {
                dao.update(invoice)
            }
        }
    }
}
