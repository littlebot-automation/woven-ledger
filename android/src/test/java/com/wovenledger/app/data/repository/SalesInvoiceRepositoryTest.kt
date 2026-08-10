package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.DocumentLineDto
import com.wovenledger.app.data.api.SalesInvoiceDto
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.outbox.Outbox
import com.wovenledger.app.data.dao.SalesInvoiceDao
import com.wovenledger.app.data.dao.SalesInvoiceLineDao
import com.wovenledger.app.data.entities.SalesInvoice
import com.wovenledger.app.data.entities.SalesInvoiceLine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import java.time.LocalDate

/**
 * The invoice sync rearranges the server's money columns rather than copying them:
 * the API sends a GST-inclusive total and a separate GST figure, while Room stores the
 * pre-tax subtotal. The two are easy to swap and impossible to spot on screen without
 * a calculator, so the arithmetic is pinned down here.
 */
class SalesInvoiceRepositoryTest {

    private val dao: SalesInvoiceDao = mock()
    private val lineDao: SalesInvoiceLineDao = mock()
    private val api: WovenLedgerApiService = mock()
    private val outbox: Outbox = mock()
    private val plants: PlantRepository = mock()
    private val repository = SalesInvoiceRepository(dao, lineDao, api, plants, outbox)

    /**
     * Room answers "no queued rows" with an empty list; an unstubbed Mockito suspend
     * function answers with null. The pruning tests that matter — the ones proving a
     * queued row survives a sync — stub this themselves.
     */
    @Before
    fun noQueuedRowsByDefault() {
        wheneverBlocking { dao.pendingIds() }.thenReturn(emptyList())
    }


    @Test
    fun `the stored subtotal is the server total minus its GST`() = runTest {
        serverReturns(
            invoiceDto(totalAmount = 118_000L, gstAmount = 18_000L, netAmount = 118_000L)
        )

        repository.syncFromApi()

        assertEquals(100_000L, insertedInvoice().subtotal)
    }

    @Test
    fun `the stored total is the net payable, not the gross`() = runTest {
        serverReturns(
            invoiceDto(
                totalAmount = 118_000L,
                gstAmount = 18_000L,
                discountAmount = 5_000L,
                netAmount = 113_000L,
            )
        )

        repository.syncFromApi()

        val stored = insertedInvoice()
        assertEquals(113_000L, stored.total)
        assertEquals(5_000L, stored.discount)
    }

    @Test
    fun `identity and date come across unchanged`() = runTest {
        serverReturns(
            invoiceDto(
                id = 42,
                documentNumber = "INV-2026-0042",
                partyId = 9,
                invoiceDate = "2026-03-17",
            )
        )

        repository.syncFromApi()

        val stored = insertedInvoice()
        assertEquals(42L, stored.id)
        assertEquals("INV-2026-0042", stored.no)
        assertEquals(9L, stored.partyId)
        assertEquals(LocalDate.of(2026, 3, 17), stored.date)
    }

    @Test
    fun `an invoice keeps the plant the server put it at`() = runTest {
        serverReturns(invoiceDto(plantId = 3))

        repository.syncFromApi()

        assertEquals(3L, insertedInvoice().plantId)
    }

    /**
     * An invoice with no plant still has to satisfy the plant foreign key, so it falls
     * to plant 1 — and that row is ensured before the insert, not hoped for.
     */
    @Test
    fun `an invoice with no plant falls back to plant 1, which is ensured first`() = runTest {
        serverReturns(invoiceDto(plantId = null))

        repository.syncFromApi()

        assertEquals(1L, insertedInvoice().plantId)

        val order = inOrder(plants, dao)
        order.verifyBlocking(plants) { ensureExists(1L) }
        order.verifyBlocking(dao) { insert(any()) }
    }

    @Test
    fun `an invoice already in the database is updated, not inserted again`() = runTest {
        whenever(api.getSalesInvoices()).thenReturn(listOf(invoiceDto(id = 3, netAmount = 9_900L)))
        whenever(dao.getInvoice(3L)).thenReturn(flowOf(invoice(id = 3L)))

        repository.syncFromApi()

        val captor = argumentCaptor<SalesInvoice>()
        verifyBlocking(dao) { update(captor.capture()) }
        assertEquals(9_900L, captor.firstValue.total)
        verifyBlocking(dao, never()) { insert(any()) }
    }

    // ----------------------------------------------------------------------- lines

    /**
     * Lines are replaced, not merged: the server sends the document as it now reads,
     * so a line it dropped must be gone here too. Merging would leave a deleted line
     * on the invoice and overstate its total on the detail screen.
     */
    @Test
    fun `the lines on an invoice are replaced, not merged`() = runTest {
        serverReturns(
            invoiceDto(
                id = 8,
                lines = listOf(
                    lineDto(id = 100, itemId = 5, qty = 2.0, rate = 12_500L, amount = 25_000L)
                ),
            )
        )

        repository.syncFromApi()

        val order = inOrder(lineDao)
        order.verifyBlocking(lineDao) { deleteForInvoice(8L) }

        val captor = argumentCaptor<SalesInvoiceLine>()
        order.verifyBlocking(lineDao) { insert(captor.capture()) }
        val line = captor.firstValue
        assertEquals(100L, line.id)
        assertEquals(8L, line.invoiceId)
        assertEquals(5L, line.itemId)
        assertEquals(2.0, line.qty, 0.0)
        assertEquals(12_500L, line.rate)
        // The server's amount is taken as sent rather than recomputed here.
        assertEquals(25_000L, line.amount)
    }

    @Test
    fun `an invoice whose lines the server dropped is left with none`() = runTest {
        serverReturns(invoiceDto(id = 8, lines = emptyList()))

        repository.syncFromApi()

        verifyBlocking(lineDao) { deleteForInvoice(8L) }
        verifyBlocking(lineDao, never()) { insert(any()) }
    }

    // --------------------------------------------------------------------- pruning

    @Test
    fun `invoices the server no longer has are removed`() = runTest {
        serverReturns(invoiceDto(id = 1), invoiceDto(id = 2))

        repository.syncFromApi()

        verifyBlocking(dao) { deleteMissing(listOf(1L, 2L)) }
    }

    /** `NOT IN ()` is not valid SQLite, so "the server has none" clears the table. */
    @Test
    fun `an empty server list clears the table rather than keeping everything`() = runTest {
        serverReturns()

        repository.syncFromApi()

        verifyBlocking(dao) { deleteSynced() }
        verifyBlocking(dao, never()) { deleteMissing(any()) }
    }

    // ------------------------------------------------------------------- read side

    @Test
    fun `the dashboard total is the sum of every invoice`() = runTest {
        whenever(dao.getAllInvoices()).thenReturn(
            flowOf(
                listOf(
                    invoice(id = 1, total = 118_000L),
                    invoice(id = 2, total = 91_575L),
                    invoice(id = 3, total = 0L),
                )
            )
        )

        assertEquals(209_575L, repository.sumTotal().first())
    }

    /**
     * A missing invoice must yield null rather than an empty shell: the detail screen
     * distinguishes "not found" from "found, no lines".
     */
    @Test
    fun `an invoice with no row yields null rather than an empty document`() = runTest {
        whenever(dao.getInvoice(5L)).thenReturn(flowOf(null))
        whenever(lineDao.getLinesByInvoice(5L)).thenReturn(flowOf(listOf(line(invoiceId = 5L))))

        assertNull(repository.getWithLines(5L).first())
    }

    @Test
    fun `an invoice is returned with its lines attached`() = runTest {
        whenever(dao.getInvoice(5L)).thenReturn(flowOf(invoice(id = 5L)))
        whenever(lineDao.getLinesByInvoice(5L)).thenReturn(
            flowOf(listOf(line(invoiceId = 5L, qty = 2.0, rate = 12_500L)))
        )

        val document = repository.getWithLines(5L).first()

        assertEquals(5L, document?.salesInvoice?.id)
        assertEquals(listOf(25_000L), document?.lines?.map { it.amount })
    }

    // ---------------------------------------------------------------------- helpers

    private suspend fun serverReturns(vararg invoices: SalesInvoiceDto) {
        whenever(api.getSalesInvoices()).thenReturn(invoices.toList())
        whenever(dao.getInvoice(any())).thenReturn(flowOf(null))
        wheneverBlocking { dao.insert(any()) }.thenReturn(1L)
        wheneverBlocking { lineDao.insert(any()) }.thenReturn(1L)
    }

    private fun insertedInvoice(): SalesInvoice {
        val captor = argumentCaptor<SalesInvoice>()
        verifyBlocking(dao) { insert(captor.capture()) }
        return captor.firstValue
    }

    private fun invoiceDto(
        id: Int = 1,
        documentNumber: String = "INV-2026-0001",
        partyId: Int = 1,
        plantId: Int? = 1,
        invoiceDate: String = "2026-01-31",
        totalAmount: Long = 0L,
        discountAmount: Long = 0L,
        gstAmount: Long = 0L,
        netAmount: Long = 0L,
        lines: List<DocumentLineDto> = emptyList(),
    ) = SalesInvoiceDto(
        id = id,
        documentNumber = documentNumber,
        partyId = partyId,
        plantId = plantId,
        invoiceDate = invoiceDate,
        dueDate = null,
        totalAmount = totalAmount,
        discountAmount = discountAmount,
        gstAmount = gstAmount,
        netAmount = netAmount,
        status = "issued",
        notes = null,
        lines = lines,
        createdAt = "2026-01-31 10:00:00",
        updatedAt = "2026-01-31 10:00:00",
    )

    private fun lineDto(
        id: Int,
        itemId: Int,
        qty: Double,
        rate: Long,
        amount: Long,
    ) = DocumentLineDto(id = id, itemId = itemId, qty = qty, rate = rate, amount = amount)

    private fun invoice(id: Long = 1L, total: Long = 0L) = SalesInvoice(
        id = id,
        no = "INV-2026-000$id",
        date = LocalDate.of(2026, 1, 31),
        partyId = 1L,
        plantId = 1L,
        total = total,
    )

    private fun line(invoiceId: Long, qty: Double = 1.0, rate: Long = 0L) = SalesInvoiceLine(
        id = 1L,
        invoiceId = invoiceId,
        itemId = 1L,
        qty = qty,
        rate = rate,
    )
}
