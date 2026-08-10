package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.NewSalesInvoice
import com.wovenledger.app.data.api.SalesInvoiceDto
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.SalesInvoiceDao
import com.wovenledger.app.data.dao.SalesInvoiceLineDao
import com.wovenledger.app.data.entities.SalesInvoice
import com.wovenledger.app.data.entities.SalesInvoiceLine
import com.wovenledger.app.data.entities.amountOf
import com.wovenledger.app.data.entities.SalesInvoiceWithLines
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

/**
 * A saved document and whatever the server warned about while saving it.
 *
 * [queued] means it is saved on this phone only and is waiting for a connection. Such
 * a document has no number and no warnings yet — both are the server's answers, and it
 * has not been asked.
 */
data class SavedDocument<T>(
    val document: T,
    val warnings: List<String> = emptyList(),
    val queued: Boolean = false,
)

@Singleton
class SalesInvoiceRepository @Inject constructor(
    private val dao: SalesInvoiceDao,
    private val lineDao: SalesInvoiceLineDao,
    private val api: WovenLedgerApiService,
    private val plants: PlantRepository,
    private val outbox: Outbox
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
     * Raises the invoice on the server and mirrors what came back into Room; queues it
     * if the phone cannot reach the server.
     *
     * The server is always asked first. Its answer carries the things only it can
     * decide — the document number, the totals it computed, and any stock shortfall —
     * and an immediate rejection is worth far more than a queued write that fails an
     * hour later. Only a connectivity failure falls through to the queue: a 422 is a
     * bad payload that would be rejected identically tomorrow, so it is rethrown.
     */
    suspend fun createOnServer(invoice: NewSalesInvoice): SavedDocument<SalesInvoice> {
        val dto = attemptOnline { api.createSalesInvoice(invoice) } ?: return queueCreate(invoice)

        return store(dto)
    }

    suspend fun updateOnServer(id: Long, invoice: NewSalesInvoice): SavedDocument<SalesInvoice> {
        // A document that has not uploaded has no server id to PUT to: editing it
        // rewrites the write already queued for it instead of asking the server about
        // a record it has never seen.
        if (id < 0) {
            return queueUpdate(id, invoice)
        }

        val dto = attemptOnline { api.updateSalesInvoice(id.toInt(), invoice) }
            ?: return queueUpdate(id, invoice)

        return store(dto)
    }

    /**
     * Writes the invoice locally under a negative id and queues it.
     *
     * The echo matters as much as the queue row: an invoice that vanished from the
     * list until the signal came back would be typed in again, and then the shop would
     * have two. It carries no number — `SI-0015` comes from a counter in Settings that
     * only the server may consume — so the screens show "Pending upload" where the
     * number goes.
     */
    private suspend fun queueCreate(body: NewSalesInvoice): SavedDocument<SalesInvoice> {
        val localId = nextLocalId(dao.minId())
        val echo = echoOf(localId, body)

        dao.insert(echo)
        storeQueuedLines(localId, body)
        outbox.enqueue(OutboxTarget.SALES_INVOICE, OutboxOperation.CREATE, localId, body)

        return SavedDocument(echo, queued = true)
    }

    /**
     * Applies an edit locally and queues the PUT.
     *
     * The row keeps its server id and its number: this document exists on the server
     * already and only its contents are in doubt.
     */
    private suspend fun queueUpdate(id: Long, body: NewSalesInvoice): SavedDocument<SalesInvoice> {
        val stored = dao.getInvoice(id).first()
        val edited = echoOf(id, body).copy(no = stored?.no.orEmpty())

        dao.insert(edited)
        storeQueuedLines(id, body)
        outbox.enqueue(OutboxTarget.SALES_INVOICE, OutboxOperation.UPDATE, id, body)

        return SavedDocument(edited, queued = true)
    }

    /**
     * Removes a locally written invoice that is never going to be uploaded.
     *
     * Only the outbox calls this, when the user abandons a queued write.
     */
    suspend fun deleteLocal(localId: Long) = dao.deleteById(localId)

    /** Sends a queued create, then swaps the local echo for what the server stored. */
    suspend fun uploadCreate(localId: Long, body: NewSalesInvoice): List<String> {
        val dto = api.createSalesInvoice(body)

        // Delete first: the echo and the real row are the same document, and its lines
        // go with it through the foreign key.
        dao.deleteById(localId)

        return store(dto).warnings
    }

    suspend fun uploadUpdate(id: Long, body: NewSalesInvoice): List<String> =
        store(api.updateSalesInvoice(id.toInt(), body)).warnings

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
        //
        // Queued invoices are kept explicitly: the server has never been told they
        // exist, so they are absent from this list for the one reason that must not
        // mean "deleted". Pruning them would destroy a sale nobody has a copy of.
        val keep = dtos.map { it.id.toLong() } + dao.pendingIds()
        if (keep.isEmpty()) dao.deleteSynced() else dao.deleteMissing(keep)
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

    /**
     * What a queued invoice looks like in the app while it waits.
     *
     * Everything here is derived from what was typed, and every figure the server owns
     * is left empty rather than guessed: no number, and totals that are only this
     * phone's arithmetic until the server confirms them. A line with no rate counts as
     * zero, which is exactly what the form showed while it was being filled in.
     */
    private fun echoOf(id: Long, body: NewSalesInvoice): SalesInvoice {
        val subtotal = body.lines.sumOf { amountOf(it.qty, it.rate ?: 0L) }

        return SalesInvoice(
            id = id,
            no = "",
            partyId = body.partyId.toLong(),
            plantId = body.plantId?.toLong() ?: DEFAULT_PLANT_ID,
            date = LocalDate.parse(body.invoiceDate),
            subtotal = subtotal,
            discount = body.discountAmount,
            total = maxOf(0L, subtotal - body.discountAmount),
        )
    }

    /** Lines of a queued document, numbered negatively like the document itself. */
    private suspend fun storeQueuedLines(invoiceId: Long, body: NewSalesInvoice) {
        lineDao.deleteForInvoice(invoiceId)

        var lineId = nextLocalId(lineDao.minId())
        body.lines.forEach { line ->
            lineDao.insert(
                SalesInvoiceLine(
                    id = lineId,
                    invoiceId = invoiceId,
                    itemId = line.itemId.toLong(),
                    qty = line.qty,
                    rate = line.rate ?: 0L,
                )
            )
            lineId -= 1
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
