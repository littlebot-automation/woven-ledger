package com.wovenledger.app.data.repository

import com.google.gson.Gson
import com.wovenledger.app.data.api.NewDocumentLine
import com.wovenledger.app.data.api.NewSalesInvoice
import com.wovenledger.app.data.api.SalesInvoiceDto
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.SalesInvoiceDao
import com.wovenledger.app.data.dao.SalesInvoiceLineDao
import com.wovenledger.app.data.entities.SalesInvoice
import com.wovenledger.app.data.entities.SalesInvoiceLine
import com.wovenledger.app.data.outbox.FakeOutboxDao
import com.wovenledger.app.data.outbox.Outbox
import com.wovenledger.app.data.outbox.OutboxOperation
import com.wovenledger.app.data.outbox.OutboxStatus
import com.wovenledger.app.data.outbox.OutboxTarget
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException

/**
 * What a save does when the server cannot be reached — and, just as importantly, what
 * it does not do when the server can be reached and says no.
 *
 * The two failures look alike from the outside and must not be treated alike. A lost
 * connection is temporary and the same request will succeed later, so it is queued. A
 * 422 is a decision about the payload: queueing it would retry a doomed request
 * forever while telling the user their work was saved.
 */
class OfflineWriteTest {

    private val dao: SalesInvoiceDao = mock()
    private val lineDao: SalesInvoiceLineDao = mock()
    private val api: WovenLedgerApiService = mock()
    private val plants: PlantRepository = mock()

    private val outboxDao = FakeOutboxDao()
    private val outbox = Outbox(outboxDao, Gson())

    private val repository = SalesInvoiceRepository(dao, lineDao, api, plants, outbox)

    @Before
    fun emptyDatabase() {
        wheneverBlocking { dao.minId() }.thenReturn(null)
        wheneverBlocking { lineDao.minId() }.thenReturn(null)
        wheneverBlocking { dao.pendingIds() }.thenReturn(emptyList())
        whenever(dao.getInvoice(any())).thenReturn(flowOf(null))
    }

    // ----------------------------------------------------------- queue, or don't

    @Test
    fun `a save with no connection is queued rather than lost`() = runTest {
        serverIsUnreachable(UnknownHostException("invoice.littlebotautomation.com"))

        val saved = repository.createOnServer(invoiceBody())

        assertTrue("the caller must know it was not sent", saved.queued)

        val queued = outboxDao.rows.single()
        assertEquals(OutboxTarget.SALES_INVOICE, queued.target)
        assertEquals(OutboxOperation.CREATE, queued.operation)
        assertEquals(OutboxStatus.PENDING, queued.status)
        assertEquals(saved.document.id, queued.recordId)
        assertTrue(
            "the payload must be the request body, ready to send unchanged",
            queued.payload.contains("\"invoice_date\":\"2026-08-09\""),
        )
    }

    @Test
    fun `a timeout is a connectivity failure and is queued too`() = runTest {
        serverIsUnreachable(java.net.SocketTimeoutException("timeout"))

        assertTrue(repository.createOnServer(invoiceBody()).queued)
        assertEquals(1, outboxDao.rows.size)
    }

    /**
     * The most important half of the rule. A 422 says the payload is wrong; it would
     * be wrong in exactly the same way on every retry, so it must reach the form now
     * rather than becoming a queue row that fails silently forever.
     */
    @Test
    fun `a 422 is not queued — it is thrown so the form can show it`() = runTest {
        wheneverBlocking { api.createSalesInvoice(any()) }.thenAnswer { throw httpError(422) }

        assertThrows(HttpException::class.java) {
            runBlockingCreate()
        }

        assertTrue("a rejected write must not be queued", outboxDao.rows.isEmpty())
        // Nor may it leave a local copy behind: nothing was saved, anywhere.
        verifyBlocking(dao, never()) { insert(any()) }
    }

    @Test
    fun `a 500 is not queued either — the server answered, and the answer is not ours to retry`() =
        runTest {
            wheneverBlocking { api.createSalesInvoice(any()) }.thenAnswer { throw httpError(500) }

            assertThrows(HttpException::class.java) { runBlockingCreate() }

            assertTrue(outboxDao.rows.isEmpty())
        }

    @Test
    fun `a save that reaches the server is not queued at all`() = runTest {
        wheneverBlocking { api.createSalesInvoice(any()) }.thenReturn(invoiceDto())

        val saved = repository.createOnServer(invoiceBody())

        assertEquals(false, saved.queued)
        assertEquals("SI-0015", saved.document.no)
        assertTrue(outboxDao.rows.isEmpty())
    }

    // ------------------------------------------------------------------ local echo

    /**
     * A queued document has to be visible immediately. One that vanished from the list
     * until the signal came back would be typed in again, and then the shop would have
     * two of it.
     */
    @Test
    fun `a queued invoice is written locally under a negative id, with no number`() = runTest {
        serverIsUnreachable()

        repository.createOnServer(invoiceBody())

        val stored = insertedInvoice()
        assertEquals(-1L, stored.id)
        assertEquals("", stored.no)
        assertEquals(1L, stored.partyId)
    }

    @Test
    fun `local ids keep counting downwards, so two queued invoices never collide`() = runTest {
        serverIsUnreachable()
        wheneverBlocking { dao.minId() }.thenReturn(-4L)

        repository.createOnServer(invoiceBody())

        assertEquals(-5L, insertedInvoice().id)
    }

    @Test
    fun `a queued invoice carries its lines and this phone's arithmetic`() = runTest {
        serverIsUnreachable()

        repository.createOnServer(
            invoiceBody(
                lines = listOf(NewDocumentLine(itemId = 7, qty = 3.0, rate = 12_500L)),
                discount = 5_000L,
            )
        )

        val stored = insertedInvoice()
        assertEquals(37_500L, stored.subtotal)
        assertEquals(32_500L, stored.total)

        val line = argumentCaptor<SalesInvoiceLine>()
        verifyBlocking(lineDao) { insert(line.capture()) }
        assertEquals(-1L, line.firstValue.id)
        assertEquals(-1L, line.firstValue.invoiceId)
        assertEquals(37_500L, line.firstValue.amount)
    }

    // ---------------------------------------------------------------- coalescing

    /**
     * Editing something that has not uploaded yet rewrites the queued payload instead
     * of queueing behind it. The server has seen neither version, so sending both
     * would upload a document and then immediately correct it.
     */
    @Test
    fun `editing a queued invoice replaces the write already waiting for it`() = runTest {
        serverIsUnreachable()

        val first = repository.createOnServer(invoiceBody())
        whenever(dao.getInvoice(first.document.id)).thenReturn(flowOf(first.document))
        repository.updateOnServer(first.document.id, invoiceBody(discount = 9_900L))

        val queued = outboxDao.rows.single()
        assertEquals(OutboxOperation.CREATE, queued.operation)
        assertTrue(queued.payload.contains("\"discount_amount\":9900"))
    }

    /**
     * The signal coming back does not make a queued document PUT-able. It has no
     * server id, so `PUT /api/sales-invoices/-1` is the one request that must never be
     * made — it would 404, be marked a refusal, and lose the document.
     */
    @Test
    fun `editing a document that has not uploaded never asks the server about it`() = runTest {
        serverIsUnreachable()
        val queued = repository.createOnServer(invoiceBody()).document
        whenever(dao.getInvoice(queued.id)).thenReturn(flowOf(queued))

        // The connection is back, but this document still only exists here.
        wheneverBlocking { api.updateSalesInvoice(any(), any()) }.thenReturn(invoiceDto())

        val edited = repository.updateOnServer(queued.id, invoiceBody(discount = 100L))

        assertTrue(edited.queued)
        verifyBlocking(api, never()) { updateSalesInvoice(any(), any()) }
        assertEquals(OutboxOperation.CREATE, outboxDao.rows.single().operation)
    }

    // ------------------------------------------------------------------- pruning

    /**
     * The one that matters most.
     *
     * Sync prunes rows the server did not send. A queued invoice is missing from that
     * list for the one reason that must never mean "deleted": the server has not been
     * told it exists. Pruning it would destroy a sale nobody else has a record of, and
     * would do it silently.
     */
    @Test
    fun `a sync keeps queued invoices when it prunes what the server no longer has`() =
        runTest {
            whenever(api.getSalesInvoices()).thenReturn(listOf(invoiceDto(id = 1)))
            wheneverBlocking { dao.pendingIds() }.thenReturn(listOf(-1L, -2L))
            wheneverBlocking { dao.insert(any()) }.thenReturn(1L)

            repository.syncFromApi()

            verifyBlocking(dao) { deleteMissing(listOf(1L, -1L, -2L)) }
        }

    /**
     * The nastiest version of the same case: the server has no invoices at all, which
     * the pruning used to answer by clearing the table outright.
     */
    @Test
    fun `an empty server list still does not take the queued invoices with it`() = runTest {
        whenever(api.getSalesInvoices()).thenReturn(emptyList())
        wheneverBlocking { dao.pendingIds() }.thenReturn(listOf(-1L))

        repository.syncFromApi()

        verifyBlocking(dao) { deleteMissing(listOf(-1L)) }
        verifyBlocking(dao, never()) { deleteSynced() }
    }

    // --------------------------------------------------------------------- helpers

    private fun serverIsUnreachable(cause: Throwable = IOException("no route to host")) {
        wheneverBlocking { api.createSalesInvoice(any()) }.thenAnswer { throw cause }
        wheneverBlocking { api.updateSalesInvoice(any(), any()) }.thenAnswer { throw cause }
    }

    /** `assertThrows` needs a blocking call; the repository's is a suspending one. */
    private fun runBlockingCreate() = kotlinx.coroutines.runBlocking {
        repository.createOnServer(invoiceBody())
    }

    private fun insertedInvoice(): SalesInvoice {
        val captor = argumentCaptor<SalesInvoice>()
        verifyBlocking(dao) { insert(captor.capture()) }

        return captor.firstValue
    }

    private fun invoiceBody(
        lines: List<NewDocumentLine> = listOf(NewDocumentLine(itemId = 7, qty = 1.0, rate = 1_000L)),
        discount: Long = 0L,
    ) = NewSalesInvoice(
        partyId = 1,
        plantId = 1,
        invoiceDate = "2026-08-09",
        discountAmount = discount,
        notes = null,
        lines = lines,
    )

    private fun invoiceDto(id: Int = 15) = SalesInvoiceDto(
        id = id,
        documentNumber = "SI-0015",
        partyId = 1,
        plantId = 1,
        invoiceDate = "2026-08-09",
        dueDate = null,
        totalAmount = 1_000L,
        discountAmount = 0L,
        gstAmount = 0L,
        netAmount = 1_000L,
        status = "issued",
        notes = null,
        createdAt = "2026-08-09 10:00:00",
        updatedAt = "2026-08-09 10:00:00",
    )

    private fun httpError(code: Int) = HttpException(
        Response.error<Any>(
            code,
            """{"errors":{"lines":"At least one line required"}}"""
                .toResponseBody("application/json".toMediaType()),
        )
    )
}
