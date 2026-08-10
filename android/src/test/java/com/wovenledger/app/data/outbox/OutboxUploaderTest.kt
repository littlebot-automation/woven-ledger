package com.wovenledger.app.data.outbox

import android.util.Log
import com.google.gson.Gson
import com.wovenledger.app.data.api.NewParty
import com.wovenledger.app.data.api.NewPayment
import com.wovenledger.app.data.api.NewPurchaseBill
import com.wovenledger.app.data.api.NewReceipt
import com.wovenledger.app.data.api.NewSalesInvoice
import com.wovenledger.app.data.api.NewStaffWork
import com.wovenledger.app.data.repository.PartyRepository
import com.wovenledger.app.data.repository.PaymentRepository
import com.wovenledger.app.data.repository.PurchaseBillRepository
import com.wovenledger.app.data.repository.ReceiptRepository
import com.wovenledger.app.data.repository.SalesInvoiceRepository
import com.wovenledger.app.data.repository.StaffWorkRepository
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException

/**
 * What the drain guarantees.
 *
 * Order and stopping are the two properties worth pinning down. Writes go out in the
 * order they were made, one at a time, and the first connectivity failure ends the
 * run — a drain that carried on would hammer an unreachable server once per queued
 * row, and a drain that skipped ahead could send an edit before the create it edits.
 */
class OutboxUploaderTest {

    private val dao = FakeOutboxDao()
    private val gson = Gson()

    private val parties: PartyRepository = mock()
    private val salesInvoices: SalesInvoiceRepository = mock()
    private val purchaseBills: PurchaseBillRepository = mock()
    private val receipts: ReceiptRepository = mock()
    private val payments: PaymentRepository = mock()
    private val staffWork: StaffWorkRepository = mock()

    private val uploader = OutboxUploader(
        dao = dao,
        gson = gson,
        parties = parties,
        salesInvoices = salesInvoices,
        purchaseBills = purchaseBills,
        receipts = receipts,
        payments = payments,
        staffWork = staffWork,
    )

    /** The uploader logs through android.util.Log, which throws under the stub jar. */
    private lateinit var androidLog: MockedStatic<Log>

    @Before
    fun setUp() {
        androidLog = Mockito.mockStatic(Log::class.java)

        // A successful upload with nothing to report answers with no warnings. An
        // unstubbed Mockito suspend function answers with null, which would look like
        // a failure rather than a quiet success.
        wheneverBlocking { parties.uploadCreate(any(), any()) }.thenReturn(emptyList())
        wheneverBlocking { parties.uploadUpdate(any(), any()) }.thenReturn(emptyList())
        wheneverBlocking { salesInvoices.uploadCreate(any(), any()) }.thenReturn(emptyList())
        wheneverBlocking { salesInvoices.uploadUpdate(any(), any()) }.thenReturn(emptyList())
        wheneverBlocking { purchaseBills.uploadCreate(any(), any()) }.thenReturn(emptyList())
        wheneverBlocking { purchaseBills.uploadUpdate(any(), any()) }.thenReturn(emptyList())
        wheneverBlocking { receipts.uploadCreate(any(), any()) }.thenReturn(emptyList())
        wheneverBlocking { receipts.uploadUpdate(any(), any()) }.thenReturn(emptyList())
        wheneverBlocking { payments.uploadCreate(any(), any()) }.thenReturn(emptyList())
        wheneverBlocking { payments.uploadUpdate(any(), any()) }.thenReturn(emptyList())
        wheneverBlocking { staffWork.uploadCreate(any(), any()) }.thenReturn(emptyList())
        wheneverBlocking { staffWork.uploadUpdate(any(), any()) }.thenReturn(emptyList())
    }

    @After
    fun tearDown() {
        androidLog.close()
    }

    // ----------------------------------------------------------------------- order

    @Test
    fun `queued writes are sent oldest first, whatever order the rows were stored in`() =
        runTest {
            // Deliberately inserted newest-first: the queue is ordered by when the
            // work was done, not by how the rows happen to sit in the table.
            queue(receipt(), createdAt = 300)
            queue(party(), createdAt = 100)
            queue(invoice(), createdAt = 200)

            uploader.drain()

            val order = inOrder(parties, salesInvoices, receipts)
            order.verifyBlocking(parties) { uploadCreate(any(), any()) }
            order.verifyBlocking(salesInvoices) { uploadCreate(any(), any()) }
            order.verifyBlocking(receipts) { uploadCreate(any(), any()) }
        }

    @Test
    fun `an emptied queue reports what it sent`() = runTest {
        queue(party(), createdAt = 1)
        queue(invoice(), createdAt = 2)

        assertEquals(DrainResult.Done(uploaded = 2), uploader.drain())
        assertTrue("the queue should be empty: ${dao.rows}", dao.rows.isEmpty())
    }

    // -------------------------------------------------------------- stopping short

    @Test
    fun `the drain stops at the first connectivity failure`() = runTest {
        queue(party(), createdAt = 100)
        queue(invoice(), createdAt = 200)
        queue(receipt(), createdAt = 300)

        failWith(UnknownHostException("invoice.littlebotautomation.com")) {
            salesInvoices.uploadCreate(any(), any())
        }

        val result = uploader.drain()

        // The party went, the invoice could not, and nothing behind it was tried:
        // sending the receipt now would put the queue out of order and would fail
        // in exactly the same way.
        assertTrue("expected Offline, got $result", result is DrainResult.Offline)
        assertEquals(1, (result as DrainResult.Offline).uploaded)
        verifyBlocking(receipts, never()) { uploadCreate(any(), any()) }
    }

    @Test
    fun `a postponed write records the attempt and waits before the next one`() = runTest {
        queue(invoice(), createdAt = 100)
        failWith(IOException("timeout")) { salesInvoices.uploadCreate(any(), any()) }

        val result = uploader.drain() as DrainResult.Offline

        val row = dao.rows.single()
        assertEquals(1, row.attempts)
        assertEquals("timeout", row.lastError)
        assertEquals(OutboxStatus.PENDING, row.status)
        assertTrue("expected a backoff, got ${result.retryInMillis}", result.retryInMillis > 0)
        assertTrue(
            "expected the row to be held back until the backoff has passed",
            row.nextAttemptAt > System.currentTimeMillis(),
        )
    }

    @Test
    fun `a write that has failed too many times is given up on and shown to the user`() =
        runTest {
            queue(invoice(), createdAt = 100, attempts = 9)
            failWith(IOException("still no signal")) { salesInvoices.uploadCreate(any(), any()) }

            uploader.drain()

            // Retrying forever would hide a write that is going nowhere behind a
            // spinner nobody is watching.
            assertEquals(OutboxStatus.FAILED, dao.rows.single().status)
        }

    // ------------------------------------------------------------------- refusals

    @Test
    fun `a refusal is not retried, and does not hold up the rest of the queue`() = runTest {
        queue(invoice(), createdAt = 100)
        queue(receipt(), createdAt = 200)

        failWith(httpError(422)) { salesInvoices.uploadCreate(any(), any()) }

        val result = uploader.drain()

        assertEquals(DrainResult.Done(uploaded = 1), result)
        assertEquals(OutboxStatus.FAILED, dao.rows.single().status)
        // The receipt behind it is unrelated and still goes.
        verifyBlocking(receipts) { uploadCreate(any(), any()) }
    }

    // --------------------------------------------------------------- expired token

    /**
     * A 401 is not the queued write's fault, and treating it as one is expensive.
     *
     * HttpException is a RuntimeException, so without its own branch a 401 would fall
     * into the refusal arm: every remaining row marked FAILED, one doomed request each,
     * and no bulk reset to undo it — the user's only way back would be tapping Retry
     * once per lost write.
     */
    @Test
    fun `an expired token stops the drain and leaves the queue exactly as it was`() = runTest {
        queue(party(), createdAt = 100)
        queue(invoice(), createdAt = 200)
        queue(receipt(), createdAt = 300)

        failWith(httpError(401)) { salesInvoices.uploadCreate(any(), any()) }

        val result = uploader.drain()

        assertEquals(DrainResult.Unauthorized(uploaded = 1), result)

        // Nothing behind the invoice was tried: it would fail identically, and order
        // has to hold.
        verifyBlocking(receipts, never()) { uploadCreate(any(), any()) }

        val waiting = dao.rows.sortedBy { it.createdAt }
        assertEquals(2, waiting.size)
        waiting.forEach { row ->
            assertEquals(OutboxStatus.PENDING, row.status)
            // No attempt is counted, so a spell of expired tokens cannot walk a write
            // up to MAX_ATTEMPTS and give up on it.
            assertEquals(0, row.attempts)
            assertEquals(0L, row.nextAttemptAt)
        }
    }

    @Test
    fun `a 401 is the only status treated as an expired token`() = runTest {
        queue(invoice(), createdAt = 100)
        failWith(httpError(403)) { salesInvoices.uploadCreate(any(), any()) }

        // A 403 is the server refusing this particular write, not the session; signing
        // in again would change nothing about it.
        assertEquals(DrainResult.Done(uploaded = 0), uploader.drain())
        assertEquals(OutboxStatus.FAILED, dao.rows.single().status)
    }

    @Test
    fun `a failed write can be retried by hand`() = runTest {
        queue(invoice(), createdAt = 100)
        failWith(httpError(422)) { salesInvoices.uploadCreate(any(), any()) }
        uploader.drain()

        uploader.retry(dao.rows.single().id)

        val row = dao.rows.single()
        assertEquals(OutboxStatus.PENDING, row.status)
        assertEquals(0, row.attempts)
        assertNull(row.lastError)
    }

    // ------------------------------------------------------------------- warnings

    /**
     * The stock check happens on the server, so a queued sale is only checked when it
     * uploads — long after the form that would have shown the answer has gone. The row
     * is kept rather than deleted so the warning has somewhere to live until it is read.
     */
    @Test
    fun `warnings from a queued document survive the upload that produced them`() = runTest {
        queue(invoice(), createdAt = 100)
        wheneverBlocking { salesInvoices.uploadCreate(any(), any()) }
            .thenReturn(listOf("Cotton yarn is short by 40 kg"))

        uploader.drain()

        val row = dao.rows.single()
        assertEquals(OutboxStatus.UPLOADED, row.status)
        assertTrue(
            "expected the warning to be kept, got ${row.warnings}",
            row.warnings.orEmpty().contains("short by 40 kg"),
        )
    }

    @Test
    fun `an upload with nothing to say leaves no trace`() = runTest {
        queue(invoice(), createdAt = 100)

        uploader.drain()

        assertTrue(dao.rows.isEmpty())
    }

    // ------------------------------------------------------------------ discarding

    @Test
    fun `abandoning a queued create removes the local copy as well as the queue row`() =
        runTest {
            queue(invoice(), createdAt = 100, recordId = -3L)

            uploader.discard(dao.rows.single().id)

            // Leaving the echo behind would show a document in the lists forever that
            // nothing is going to upload.
            verifyBlocking(salesInvoices) { deleteLocal(-3L) }
            assertTrue(dao.rows.isEmpty())
        }

    // -------------------------------------------------------------------- dispatch

    @Test
    fun `each target is sent to its own endpoint, as a create or an update`() = runTest {
        queue(party(), createdAt = 1, operation = OutboxOperation.UPDATE, recordId = 7)
        queue(bill(), createdAt = 2)
        queue(payment(), createdAt = 3)
        queue(work(), createdAt = 4)

        uploader.drain()

        verifyBlocking(parties) { uploadUpdate(eq(7L), any()) }
        verifyBlocking(purchaseBills) { uploadCreate(any(), any()) }
        verifyBlocking(payments) { uploadCreate(any(), any()) }
        verifyBlocking(staffWork) { uploadCreate(any(), any()) }
    }

    // --------------------------------------------------------------------- helpers

    private suspend fun queue(
        target: Pair<OutboxTarget, Any>,
        createdAt: Long,
        recordId: Long = -1L,
        attempts: Int = 0,
        operation: OutboxOperation = OutboxOperation.CREATE,
    ) {
        dao.insert(
            OutboxEntry(
                target = target.first,
                operation = operation,
                payload = gson.toJson(target.second),
                recordId = recordId,
                createdAt = createdAt,
                attempts = attempts,
            )
        )
    }

    private fun party() = OutboxTarget.PARTY to NewParty(
        name = "Rameshwar Textiles",
        type = "customer",
        phone = null,
        gstNumber = null,
        address = null,
        openingBalance = 0,
        openingBalanceType = "to_receive",
    )

    private fun invoice() = OutboxTarget.SALES_INVOICE to NewSalesInvoice(
        partyId = 1,
        plantId = 1,
        invoiceDate = "2026-08-09",
        discountAmount = 0,
        notes = null,
        lines = emptyList(),
    )

    private fun bill() = OutboxTarget.PURCHASE_BILL to NewPurchaseBill(
        partyId = 1,
        plantId = 1,
        billDate = "2026-08-09",
        discountAmount = 0,
        notes = null,
        lines = emptyList(),
    )

    private fun receipt() = OutboxTarget.RECEIPT to NewReceipt(
        partyId = 1,
        amount = 10_000,
        receiptDate = "2026-08-09",
        mode = "Cash",
        notes = null,
    )

    private fun payment() = OutboxTarget.PAYMENT to NewPayment(
        partyId = 1,
        staffId = null,
        amount = 10_000,
        paymentDate = "2026-08-09",
        paymentType = "party",
        mode = "Cash",
        notes = null,
    )

    private fun work() = OutboxTarget.STAFF_WORK to NewStaffWork(
        date = "2026-08-09",
        staffId = 1,
        plantId = 1,
        workType = "Stitching",
        qty = 8.0,
        rate = null,
    )

    private fun httpError(code: Int) = HttpException(
        Response.error<Any>(code, """{"errors":{"qty":"Qty is required"}}""".toResponseBody("application/json".toMediaType()))
    )
}

/**
 * Makes one upload fail the way an unreachable server, or a refusing one, does.
 *
 * `thenAnswer` rather than `thenThrow`: a Kotlin suspend function declares no checked
 * exceptions, so Mockito rejects an IOException stubbed the direct way — even though
 * that is exactly what OkHttp throws through one.
 */
private fun failWith(cause: Throwable, call: suspend () -> Any) {
    wheneverBlocking { call() }.thenAnswer { throw cause }
}
