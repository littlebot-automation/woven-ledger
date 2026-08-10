package com.wovenledger.app.data.repository

import com.google.gson.Gson
import com.wovenledger.app.data.api.LabourApiService
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.PartyDao
import com.wovenledger.app.data.dao.PaymentDao
import com.wovenledger.app.data.dao.PurchaseBillDao
import com.wovenledger.app.data.dao.PurchaseBillLineDao
import com.wovenledger.app.data.dao.ReceiptDao
import com.wovenledger.app.data.dao.StaffDao
import com.wovenledger.app.data.dao.StaffWorkDao
import com.wovenledger.app.data.outbox.FakeOutboxDao
import com.wovenledger.app.data.outbox.Outbox
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

/**
 * Pruning must never delete work that has not been uploaded.
 *
 * Sync removes rows the server did not send, which is right for anything the portal
 * deleted and catastrophic for anything the phone has not sent yet: a queued row is
 * missing from the server's list because the server has never heard of it. This is the
 * failure that loses a shopkeeper's morning without any error appearing anywhere, so
 * every repository that can queue a write is checked here, not just the one that was
 * convenient to test.
 *
 * The worst case is the one below — the server returning nothing at all, which the
 * pruning used to answer by clearing the table outright.
 */
class PruningKeepsQueuedRowsTest {

    private val outbox = Outbox(FakeOutboxDao(), Gson())

    @Test
    fun `a queued party survives a sync that finds nothing on the server`() = runTest {
        val dao: PartyDao = mock()
        val api: WovenLedgerApiService = mock()
        whenever(api.getParties()).thenReturn(emptyList())
        wheneverBlocking { dao.pendingIds() }.thenReturn(listOf(-1L))

        PartyRepository(dao, api, outbox).syncFromApi()

        verifyBlocking(dao) { deleteMissing(listOf(-1L)) }
        verifyBlocking(dao, never()) { deleteSynced() }
    }

    @Test
    fun `a queued purchase bill survives a sync that finds nothing on the server`() = runTest {
        val dao: PurchaseBillDao = mock()
        val lineDao: PurchaseBillLineDao = mock()
        val api: WovenLedgerApiService = mock()
        whenever(api.getPurchaseBills()).thenReturn(emptyList())
        wheneverBlocking { dao.pendingIds() }.thenReturn(listOf(-1L, -2L))

        PurchaseBillRepository(dao, lineDao, api, mock(), outbox).syncFromApi()

        verifyBlocking(dao) { deleteMissing(listOf(-1L, -2L)) }
        verifyBlocking(dao, never()) { deleteSynced() }
    }

    @Test
    fun `a queued receipt survives a sync that finds nothing on the server`() = runTest {
        val dao: ReceiptDao = mock()
        val api: WovenLedgerApiService = mock()
        whenever(api.getReceipts()).thenReturn(emptyList())
        wheneverBlocking { dao.pendingIds() }.thenReturn(listOf(-1L))

        ReceiptRepository(dao, api, outbox).syncFromApi()

        verifyBlocking(dao) { deleteMissing(listOf(-1L)) }
        verifyBlocking(dao, never()) { deleteSynced() }
    }

    @Test
    fun `a queued payment survives a sync that finds nothing on the server`() = runTest {
        val dao: PaymentDao = mock()
        val api: WovenLedgerApiService = mock()
        whenever(api.getPayments()).thenReturn(emptyList())
        wheneverBlocking { dao.pendingIds() }.thenReturn(listOf(-1L))

        PaymentRepository(dao, api, outbox).syncFromApi()

        verifyBlocking(dao) { deleteMissing(listOf(-1L)) }
        verifyBlocking(dao, never()) { deleteSynced() }
    }

    @Test
    fun `a queued work entry survives a sync that finds nothing on the server`() = runTest {
        val dao: StaffWorkDao = mock()
        val staffDao: StaffDao = mock()
        val api: LabourApiService = mock()
        whenever(api.getStaffWork()).thenReturn(emptyList())
        wheneverBlocking { dao.pendingIds() }.thenReturn(listOf(-1L))

        StaffWorkRepository(dao, staffDao, api, mock(), outbox).syncFromApi()

        verifyBlocking(dao) { deleteMissing(listOf(-1L)) }
        verifyBlocking(dao, never()) { deleteSynced() }
    }

    /** With nothing queued, pruning still clears out what the server no longer has. */
    @Test
    fun `an empty server list clears the table when there is nothing waiting`() = runTest {
        val dao: PartyDao = mock()
        val api: WovenLedgerApiService = mock()
        whenever(api.getParties()).thenReturn(emptyList())
        wheneverBlocking { dao.pendingIds() }.thenReturn(emptyList())

        PartyRepository(dao, api, outbox).syncFromApi()

        verifyBlocking(dao) { deleteSynced() }
        verifyBlocking(dao, never()) { deleteMissing(any()) }
    }
}
