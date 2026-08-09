package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.NewSalesInvoice
import com.wovenledger.app.data.api.SalesInvoiceDto
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.SalesInvoiceDao
import com.wovenledger.app.data.dao.SalesInvoiceLineDao
import com.wovenledger.app.data.entities.SalesInvoice
import com.wovenledger.app.data.entities.SalesInvoiceLine
import com.wovenledger.app.data.entities.SalesInvoiceWithLines
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A saved document and whatever the server warned about while saving it. */
data class SavedDocument<T>(val document: T, val warnings: List<String>)

@Singleton
class SalesInvoiceRepository @Inject constructor(
    private val dao: SalesInvoiceDao,
    private val lineDao: SalesInvoiceLineDao,
    private val api: WovenLedgerApiService,
    private val plants: PlantRepository
) {

    suspend fun create(invoice: SalesInvoice): Long = dao.insert(invoice)

    fun read(id: Long): Flow<SalesInvoice?> = dao.getInvoice(id)

    suspend fun update(invoice: SalesInvoice) = dao.update(invoice)

    suspend fun delete(invoice: SalesInvoice) = dao.delete(invoice)

    fun getAll(): Flow<List<SalesInvoice>> = dao.getAllInvoices()

    fun getByParty(partyId: Long): Flow<List<SalesInvoice>> = dao.getInvoicesByParty(partyId)

    fun findForParty(partyId: Long): Flow<List<SalesInvoice>> = dao.getInvoicesByParty(partyId)

    fun getLines(invoiceId: Long): Flow<List<SalesInvoiceLine>> = lineDao.getLinesByInvoice(invoiceId)

    /** Sales lines for one item, for its movement history. */
    fun getLinesForItem(itemId: Long): Flow<List<SalesInvoiceLine>> = lineDao.getLinesByItem(itemId)

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

    /**
     * Raises the invoice on the server and mirrors what came back into Room.
     *
     * The document number and the totals are the server's answer, never a local
     * guess, which is what makes online-only writes worth the wait. Stock
     * shortfalls ride along as warnings beside a successful save.
     */
    suspend fun createOnServer(invoice: NewSalesInvoice): SavedDocument<SalesInvoice> =
        store(api.createSalesInvoice(invoice))

    suspend fun updateOnServer(id: Long, invoice: NewSalesInvoice): SavedDocument<SalesInvoice> =
        store(api.updateSalesInvoice(id.toInt(), invoice))

    /** Throws on failure so [com.wovenledger.app.data.sync.SyncManager] can report it. */
    suspend fun syncFromApi() {
        val dtos = api.getSalesInvoices()

        dtos.forEach { dto ->
            val invoice = toEntity(dto)
            plants.ensureExists(invoice.plantId)

            if (dao.getInvoice(invoice.id).first() == null) {
                dao.insert(invoice)
            } else {
                dao.update(invoice)
            }

            storeLines(dto)
        }

        // Upserting alone left invoices deleted on the portal on the phone for good.
        val ids = dtos.map { it.id.toLong() }
        if (ids.isEmpty()) dao.deleteAll() else dao.deleteMissing(ids)
    }

    private suspend fun store(dto: SalesInvoiceDto): SavedDocument<SalesInvoice> {
        val invoice = toEntity(dto)
        plants.ensureExists(invoice.plantId)
        dao.insert(invoice)
        storeLines(dto)

        return SavedDocument(invoice, dto.warnings)
    }

    /**
     * Lines are replaced, never merged: the server sends the document as it now
     * reads, and a line it dropped must not survive here.
     */
    private suspend fun storeLines(dto: SalesInvoiceDto) {
        lineDao.deleteForInvoice(dto.id.toLong())

        dto.lines.forEach { line ->
            lineDao.insert(
                SalesInvoiceLine(
                    id = line.id.toLong(),
                    invoiceId = dto.id.toLong(),
                    itemId = line.itemId.toLong(),
                    qty = line.qty,
                    rate = line.rate,
                    amount = line.amount
                )
            )
        }
    }

    private fun toEntity(dto: SalesInvoiceDto) = SalesInvoice(
        id = dto.id.toLong(),
        no = dto.documentNumber,
        partyId = dto.partyId.toLong(),
        plantId = dto.plantId?.toLong() ?: DEFAULT_PLANT_ID,
        date = java.time.LocalDate.parse(dto.invoiceDate),
        subtotal = dto.totalAmount - dto.gstAmount,
        discount = dto.discountAmount,
        total = dto.netAmount
    )

    private companion object {
        const val DEFAULT_PLANT_ID = 1L
    }
}
