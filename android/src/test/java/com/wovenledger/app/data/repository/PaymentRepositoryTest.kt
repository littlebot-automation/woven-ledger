package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.PaymentDto
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.outbox.Outbox
import com.wovenledger.app.data.dao.PaymentDao
import com.wovenledger.app.data.entities.Payment
import com.wovenledger.app.data.entities.PaymentMode
import com.wovenledger.app.data.entities.PaymentType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

/**
 * A payment voucher points at one side of the business or the other — a party or a
 * staff member — and carries a mode the portal writes as a human label. Both are
 * translated on the way in, and a wrong translation misfiles somebody's money.
 */
class PaymentRepositoryTest {

    private val dao: PaymentDao = mock()
    private val api: WovenLedgerApiService = mock()
    private val outbox: Outbox = mock()
    private val repository = PaymentRepository(dao, api, outbox)

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
    fun `a display label with a space maps onto the enum constant`() = runTest {
        serverReturns(paymentDto(mode = "Bank Transfer"))

        repository.syncFromApi()

        assertEquals(PaymentMode.BANK_TRANSFER, storedPayments().single().mode)
    }

    @Test
    fun `the other modes come across in whatever case the server sends`() = runTest {
        serverReturns(
            paymentDto(id = 1, mode = "cash"),
            paymentDto(id = 2, mode = "UPI"),
            paymentDto(id = 3, mode = "Cheque"),
        )

        repository.syncFromApi()

        assertEquals(
            listOf(PaymentMode.CASH, PaymentMode.UPI, PaymentMode.CHEQUE),
            storedPayments().map { it.mode },
        )
    }

    /**
     * Cash is the fallback. A mode the app does not know must not abort the payments
     * sync — one unrecognised label would otherwise cost the user every payment on the
     * device, and a misfiled mode is far cheaper than a missing voucher.
     */
    @Test
    fun `an absent or unknown mode is recorded as cash rather than failing the sync`() = runTest {
        serverReturns(paymentDto(id = 1, mode = null), paymentDto(id = 2, mode = "Crypto"))

        repository.syncFromApi()

        assertEquals(
            listOf(PaymentMode.CASH, PaymentMode.CASH),
            storedPayments().map { it.mode },
        )
    }

    @Test
    fun `amount, party and notes come across unchanged`() = runTest {
        serverReturns(
            paymentDto(
                id = 11,
                documentNumber = "PAY-2026-0011",
                partyId = 4,
                amount = 91_575L,
                paymentType = "party",
                notes = null,
            )
        )

        repository.syncFromApi()

        val stored = storedPayments().single()
        assertEquals(11L, stored.id)
        assertEquals("PAY-2026-0011", stored.no)
        assertEquals(4L, stored.partyId)
        assertEquals(91_575L, stored.amount)
        assertEquals(PaymentType.PARTY, stored.type)
        assertEquals("", stored.notes)
    }

    /**
     * A wage payment carries a staff member and no party. Neither id may be coerced to
     * 0 to fill the gap: 0 is not a row, and both columns are foreign keys.
     */
    @Test
    fun `a wage payment carries the staff member and no party`() = runTest {
        serverReturns(paymentDto(partyId = null, staffId = 6, paymentType = "staff"))

        repository.syncFromApi()

        val stored = storedPayments().single()
        assertNull(stored.partyId)
        assertEquals(6L, stored.staffId)
        assertEquals(PaymentType.STAFF, stored.type)
    }

    @Test
    fun `payments the server no longer has are removed`() = runTest {
        serverReturns(paymentDto(id = 1), paymentDto(id = 2))

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

    // ---------------------------------------------------------------------- helpers

    private suspend fun serverReturns(vararg payments: PaymentDto) {
        whenever(api.getPayments()).thenReturn(payments.toList())
        whenever(dao.getPayment(any())).thenReturn(flowOf(null))
        wheneverBlocking { dao.insert(any()) }.thenReturn(1L)
    }

    private fun storedPayments(): List<Payment> {
        val captor = argumentCaptor<Payment>()
        verifyBlocking(dao, atLeastOnce()) { insert(captor.capture()) }
        return captor.allValues
    }

    private fun paymentDto(
        id: Int = 1,
        documentNumber: String = "PAY-2026-0001",
        partyId: Int? = 1,
        staffId: Int? = null,
        amount: Long = 10_000L,
        paymentDate: String = "2026-02-14",
        paymentType: String = "party",
        mode: String? = "cash",
        notes: String? = null,
    ) = PaymentDto(
        id = id,
        documentNumber = documentNumber,
        partyId = partyId,
        staffId = staffId,
        amount = amount,
        paymentDate = paymentDate,
        paymentType = paymentType,
        referenceNumber = null,
        mode = mode,
        notes = notes,
        createdAt = "2026-02-14 09:00:00",
        updatedAt = "2026-02-14 09:00:00",
    )
}
