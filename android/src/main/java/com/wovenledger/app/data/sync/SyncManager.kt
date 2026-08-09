package com.wovenledger.app.data.sync

import android.util.Log
import com.wovenledger.app.data.entities.Plant
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What the last sync attempt did, so the UI can say so instead of showing a blank list. */
sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data object Success : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * Pulls the server's records into Room.
 *
 * Sync failures are surfaced through [state] rather than swallowed: an unreachable
 * server and an empty database look identical on screen otherwise, which makes the
 * app appear broken in exactly the case where it should say "couldn't reach the
 * server".
 */
@Singleton
class SyncManager @Inject constructor(
    private val plantRepository: PlantRepository,
    private val itemStockRepository: ItemStockRepository,
    private val partyRepository: PartyRepository,
    private val itemRepository: ItemRepository,
    private val salesInvoiceRepository: SalesInvoiceRepository,
    private val purchaseBillRepository: PurchaseBillRepository,
    private val paymentRepository: PaymentRepository,
    private val receiptRepository: ReceiptRepository,
    private val staffRepository: StaffRepository,
    private val staffWorkRepository: StaffWorkRepository
) {
    private companion object {
        const val TAG = "WovenLedgerSync"
        const val DEFAULT_PLANT_ID = 1L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Fire-and-forget sync for callers without a scope of their own, such as Activity startup. */
    fun syncAllData() {
        scope.launch { sync() }
    }

    /**
     * Pulls the real plants, then guarantees plant 1 exists regardless.
     *
     * Document sync assigns every invoice and bill to plant 1, so if the server has no
     * plant with that id the documents would fail their foreign key. The placeholder is
     * a floor, not a substitute — a synced plant carries its real name.
     */
    private suspend fun syncPlants() {
        plantRepository.syncFromApi()
        plantRepository.ensureExists(DEFAULT_PLANT_ID)
    }

    /**
     * Runs every entity sync, each independently: one failing endpoint must not stop
     * the others from refreshing, or a single broken route would leave the whole app
     * stale. Reports failure if any of them failed, naming the first one.
     */
    suspend fun sync(): SyncState {
        _state.value = SyncState.Syncing

        // Order is a foreign-key constraint, not a preference: plants and items must
        // land before the rows that point at them, and stock points at both.
        val steps = listOf<Pair<String, suspend () -> Unit>>(
            // Masters first, then the records that point at them.
            "plants" to ::syncPlants,
            "parties" to partyRepository::syncFromApi,
            "items" to itemRepository::syncFromApi,
            // Staff precedes payments: a wage voucher carries a staff_id, so the staff
            // row has to exist or the payment insert fails its foreign key.
            "staff" to staffRepository::syncFromApi,
            "sales invoices" to salesInvoiceRepository::syncFromApi,
            "purchase bills" to purchaseBillRepository::syncFromApi,
            "payments" to paymentRepository::syncFromApi,
            "receipts" to receiptRepository::syncFromApi,
            "staff work" to staffWorkRepository::syncFromApi,
            // Stock points at both items and plants.
            "stock" to itemStockRepository::syncFromApi,
        )

        val failures = steps.mapNotNull { (name, run) ->
            runCatching { run() }
                .exceptionOrNull()
                ?.let { cause ->
                    // Log with the throwable: the banner only has room for a summary, and
                    // a constraint violation is unreadable without its stack trace.
                    Log.w(TAG, "Sync step '$name' failed", cause)
                    name to (cause.message ?: cause::class.simpleName ?: "failed")
                }
        }

        val result = if (failures.isEmpty()) {
            SyncState.Success
        } else {
            val (name, message) = failures.first()
            SyncState.Failed(
                if (failures.size == 1) "$name: $message" else "$name: $message (+${failures.size - 1} more)"
            )
        }

        _state.value = result

        return result
    }
}
