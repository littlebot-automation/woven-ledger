package com.wovenledger.app.data.sync

import android.util.Log
import com.wovenledger.app.data.entities.Plant
import com.wovenledger.app.data.repository.ItemRepository
import com.wovenledger.app.data.repository.PartyRepository
import com.wovenledger.app.data.repository.PaymentRepository
import com.wovenledger.app.data.repository.PlantRepository
import com.wovenledger.app.data.repository.PurchaseBillRepository
import com.wovenledger.app.data.repository.SalesInvoiceRepository
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
    private val partyRepository: PartyRepository,
    private val itemRepository: ItemRepository,
    private val salesInvoiceRepository: SalesInvoiceRepository,
    private val purchaseBillRepository: PurchaseBillRepository,
    private val paymentRepository: PaymentRepository
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
     * Sales invoices and purchase bills carry a foreign key to a plant, and the API does
     * not expose plants yet, so documents would fail to insert with nothing to point at.
     * This holds a single placeholder open until /api/plants exists; the document sync
     * already assigns every document to plant 1.
     */
    private suspend fun ensureDefaultPlant() {
        if (plantRepository.read(DEFAULT_PLANT_ID).first() == null) {
            plantRepository.create(
                Plant(id = DEFAULT_PLANT_ID, name = "Main plant", address = "")
            )
        }
    }

    /**
     * Runs every entity sync, each independently: one failing endpoint must not stop
     * the others from refreshing, or a single broken route would leave the whole app
     * stale. Reports failure if any of them failed, naming the first one.
     */
    suspend fun sync(): SyncState {
        _state.value = SyncState.Syncing

        val steps = listOf<Pair<String, suspend () -> Unit>>(
            "plants" to ::ensureDefaultPlant,
            "parties" to partyRepository::syncFromApi,
            "items" to itemRepository::syncFromApi,
            "sales invoices" to salesInvoiceRepository::syncFromApi,
            "purchase bills" to purchaseBillRepository::syncFromApi,
            "payments" to paymentRepository::syncFromApi,
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
