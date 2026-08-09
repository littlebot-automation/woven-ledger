package com.wovenledger.app.pdf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wovenledger.app.data.api.SettingsApiService
import com.wovenledger.app.data.entities.Item
import com.wovenledger.app.data.repository.ItemRepository
import com.wovenledger.app.data.repository.PartyRepository
import com.wovenledger.app.data.repository.PlantRepository
import com.wovenledger.app.data.repository.SalesInvoiceRepository
import com.wovenledger.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Turns a stored sales invoice into a shareable PDF.
 *
 * Everything the page prints is gathered here — the cached document and its lines, the
 * item names and units, the buyer, the plant and the company header — so the renderer
 * only ever sees settled values.
 */
@HiltViewModel
class InvoiceShareViewModel @Inject constructor(
    private val invoices: SalesInvoiceRepository,
    private val parties: PartyRepository,
    private val items: ItemRepository,
    private val plants: PlantRepository,
    private val settingsApi: SettingsApiService,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _sharing = MutableStateFlow(false)
    val sharing: StateFlow<Boolean> = _sharing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun errorShown() {
        _error.value = null
    }

    /**
     * Renders the invoice and opens the share sheet.
     *
     * [context] should be the Activity the user tapped in; it is used for the moment of
     * the call only and never held. Ignores a second tap while the first is still going.
     */
    fun share(context: Context, invoiceId: Long) {
        if (_sharing.value) return

        viewModelScope.launch {
            _sharing.value = true
            _error.value = null

            try {
                val document = withContext(Dispatchers.IO) { buildDocument(invoiceId) }

                if (document == null) {
                    _error.value = "That invoice isn't in the local copy yet — sync and try again."
                    return@launch
                }

                // Rendering and writing are file work; the share sheet is not.
                val file = withContext(Dispatchers.IO) { InvoicePdfSharing.write(context, document) }
                context.startActivity(InvoicePdfSharing.shareIntent(context, file, document))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _error.value = "Could not share the invoice: ${failure.message ?: "unknown error"}"
            } finally {
                _sharing.value = false
            }
        }
    }

    private suspend fun buildDocument(invoiceId: Long): InvoiceDocument? {
        val stored = invoices.getWithLines(invoiceId).first() ?: return null
        val invoice = stored.salesInvoice
        val buyer = parties.read(invoice.partyId).first()
        val itemsById: Map<Long, Item> = items.getAll().first().associateBy { it.id }
        val plant = plants.read(invoice.plantId).first()

        return InvoiceDocument(
            company = company(),
            buyer = InvoiceParty(
                name = buyer?.name ?: "—",
                address = buyer?.address.orEmpty(),
                phone = buyer?.phone.orEmpty(),
                gstin = buyer?.gstin.orEmpty(),
            ),
            number = invoice.no,
            date = invoice.date,
            plantName = plant?.name.orEmpty(),
            lines = stored.lines.map { line ->
                val item = itemsById[line.itemId]

                InvoiceLine(
                    itemName = item?.name ?: "Item #${line.itemId}",
                    hsn = item?.hsn.orEmpty(),
                    qty = line.qty,
                    unit = item?.unit.orEmpty(),
                    rate = line.rate,
                    amount = line.amount,
                )
            },
            subtotal = invoice.subtotal,
            discount = invoice.discount,
            total = invoice.total,
        )
    }

    /**
     * Company header from /api/settings, falling back to the cached settings row.
     *
     * The header is worth a network round trip because the portal is where the company
     * details are edited — but an invoice must still be shareable on a phone with no
     * signal, so a failed call quietly drops to the local copy.
     */
    private suspend fun company(): InvoiceParty {
        val remote = try {
            settingsApi.getSettings()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unreachable: Exception) {
            null
        }

        val local = settings.read().first()

        return InvoiceParty(
            name = remote?.companyName?.ifBlank { null } ?: local?.companyName.orEmpty(),
            address = remote?.address?.ifBlank { null } ?: local?.address.orEmpty(),
            phone = remote?.phone?.ifBlank { null } ?: local?.phone.orEmpty(),
            gstin = remote?.gstin?.ifBlank { null } ?: local?.gstin.orEmpty(),
        )
    }
}
