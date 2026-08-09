package com.wovenledger.app.data.sync

import android.util.Log
import com.wovenledger.app.data.repository.ItemRepository
import com.wovenledger.app.data.repository.ItemStockRepository
import com.wovenledger.app.data.repository.PartyRepository
import com.wovenledger.app.data.repository.PaymentRepository
import com.wovenledger.app.data.repository.PlantRepository
import com.wovenledger.app.data.repository.PurchaseBillRepository
import com.wovenledger.app.data.repository.ReceiptRepository
import com.wovenledger.app.data.repository.SalesInvoiceRepository
import com.wovenledger.app.data.repository.StaffRepository
import com.wovenledger.app.data.repository.StaffWorkRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.wheneverBlocking

/**
 * SyncManager's contract is isolation: every entity syncs independently, so one broken
 * endpoint leaves the rest of the app refreshed rather than stale, and the failure is
 * named instead of swallowed. That was a deliberate fix; these tests are what stops it
 * being undone by a future `steps.forEach { it() }`.
 *
 * The repositories are mocked rather than faked because they are final Kotlin classes
 * with DAO and Retrofit collaborators of their own — what matters here is only whether
 * each one was invoked and what happened when it threw.
 */
class SyncManagerTest {

    private val plants: PlantRepository = mock()
    private val stock: ItemStockRepository = mock()
    private val parties: PartyRepository = mock()
    private val items: ItemRepository = mock()
    private val salesInvoices: SalesInvoiceRepository = mock()
    private val purchaseBills: PurchaseBillRepository = mock()
    private val payments: PaymentRepository = mock()
    private val receipts: ReceiptRepository = mock()
    private val staff: StaffRepository = mock()
    private val staffWork: StaffWorkRepository = mock()

    /**
     * SyncManager logs each failure through android.util.Log, which throws
     * "not mocked" under the stubbed android.jar that JVM unit tests compile against.
     * Stubbing the statics here keeps the production code untouched — the alternative
     * is `testOptions.unitTests.isReturnDefaultValues`, which would silently neuter
     * every other Android call in the module as well.
     */
    private lateinit var androidLog: MockedStatic<Log>

    private lateinit var syncManager: SyncManager

    @Before
    fun setUp() {
        androidLog = Mockito.mockStatic(Log::class.java)
        syncManager = SyncManager(
            plantRepository = plants,
            itemStockRepository = stock,
            partyRepository = parties,
            itemRepository = items,
            salesInvoiceRepository = salesInvoices,
            purchaseBillRepository = purchaseBills,
            paymentRepository = payments,
            receiptRepository = receipts,
            staffRepository = staff,
            staffWorkRepository = staffWork,
        )
    }

    @After
    fun tearDown() {
        androidLog.close()
    }

    @Test
    fun `every entity is synced when nothing fails`() = runTest {
        val result = syncManager.sync()

        assertEquals(SyncState.Success, result)
        assertEquals(SyncState.Success, syncManager.state.value)
        verifyAllStepsRan()
    }

    @Test
    fun `a failing step does not stop the ones after it`() = runTest {
        failWith(IllegalStateException("HTTP 500")) { parties.syncFromApi() }

        val result = syncManager.sync()

        assertEquals(SyncState.Failed("parties: HTTP 500"), result)
        // The point of the whole design: items onwards still refreshed.
        verifyAllStepsRan()
    }

    @Test
    fun `the reported failure names the step, so the banner is not just a stack trace`() =
        runTest {
            failWith(IllegalStateException("timeout")) { payments.syncFromApi() }

            val failure = syncManager.sync() as SyncState.Failed

            assertTrue(
                "expected the step name in: ${failure.message}",
                failure.message.startsWith("payments: "),
            )
            assertTrue(
                "expected the cause in: ${failure.message}",
                failure.message.contains("timeout"),
            )
        }

    @Test
    fun `several failures are summarised as the first plus a count of the rest`() = runTest {
        failWith(IllegalStateException("HTTP 500")) { parties.syncFromApi() }
        failWith(IllegalStateException("timeout")) { payments.syncFromApi() }
        failWith(IllegalStateException("HTTP 404")) { staffWork.syncFromApi() }

        val result = syncManager.sync()

        // First in step order, then how many more went wrong — losing the count would
        // make three broken endpoints indistinguishable from one.
        assertEquals(SyncState.Failed("parties: HTTP 500 (+2 more)"), result)
        verifyAllStepsRan()
    }

    @Test
    fun `a failure carrying no message reports its type rather than the word null`() =
        runTest {
            failWith(IllegalStateException()) { items.syncFromApi() }

            val result = syncManager.sync()

            assertEquals(SyncState.Failed("items: IllegalStateException"), result)
        }

    @Test
    fun `the state flow reports failure, so cached data on screen can be labelled stale`() =
        runTest {
            assertEquals(SyncState.Idle, syncManager.state.value)

            failWith(IllegalStateException("no route to host")) { receipts.syncFromApi() }
            syncManager.sync()

            assertEquals(SyncState.Failed("receipts: no route to host"), syncManager.state.value)
        }

    /**
     * Plant 1 is a foreign key target for invoices and bills. The real plants are
     * pulled first, then the placeholder fills the gap if the server has no plant 1 —
     * without it every document sync would fail its foreign key.
     */
    @Test
    fun `the default plant is ensured even when the server has no plant 1`() = runTest {
        syncManager.sync()

        verifyBlocking(plants) { syncFromApi() }
        verifyBlocking(plants) { ensureExists(1L) }
    }

    /**
     * Plants are the first step and everything downstream has a foreign key onto them,
     * so this is the failure most likely to be "fixed" by aborting the whole sync.
     * It must not be: the other endpoints are still reachable and still worth pulling.
     */
    @Test
    fun `a failed plant pull is reported but the rest of the sync still runs`() = runTest {
        failWith(IllegalStateException("HTTP 503")) { plants.syncFromApi() }

        val result = syncManager.sync()

        assertEquals(SyncState.Failed("plants: HTTP 503"), result)
        verifyBlocking(parties) { syncFromApi() }
        verifyBlocking(stock) { syncFromApi() }
    }

    private fun verifyAllStepsRan() {
        verifyBlocking(plants) { syncFromApi() }
        verifyBlocking(plants) { ensureExists(1L) }
        verifyBlocking(parties) { syncFromApi() }
        verifyBlocking(items) { syncFromApi() }
        verifyBlocking(salesInvoices) { syncFromApi() }
        verifyBlocking(purchaseBills) { syncFromApi() }
        verifyBlocking(payments) { syncFromApi() }
        verifyBlocking(receipts) { syncFromApi() }
        verifyBlocking(staff) { syncFromApi() }
        verifyBlocking(staffWork) { syncFromApi() }
        verifyBlocking(stock) { syncFromApi() }
    }
}

/**
 * Makes one sync step blow up the way an unreachable server does.
 *
 * The repositories share no supertype, so the step is passed as the call itself:
 * `failWith(...) { parties.syncFromApi() }`.
 */
private fun failWith(cause: Throwable, step: suspend () -> Unit) {
    wheneverBlocking { step() }.thenThrow(cause)
}
