package com.wovenledger.app.ui.screens.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.google.gson.Gson
import com.wovenledger.app.data.api.NewDocumentLine
import com.wovenledger.app.data.api.NewPurchaseBill
import com.wovenledger.app.data.api.NewSalesInvoice
import com.wovenledger.app.data.entities.Item
import com.wovenledger.app.data.entities.Party
import com.wovenledger.app.data.entities.PartyType
import com.wovenledger.app.data.entities.Plant
import com.wovenledger.app.data.repository.ItemRepository
import com.wovenledger.app.data.repository.PartyRepository
import com.wovenledger.app.data.repository.PlantRepository
import com.wovenledger.app.data.repository.PurchaseBillRepository
import com.wovenledger.app.data.repository.SalesInvoiceRepository
import com.wovenledger.app.ui.components.AmountRow
import com.wovenledger.app.ui.components.DateField
import com.wovenledger.app.ui.components.FormScaffold
import com.wovenledger.app.ui.components.MoneyField
import com.wovenledger.app.ui.components.PickerField
import com.wovenledger.app.ui.components.WarningBanner
import com.wovenledger.app.ui.components.failureMessage
import com.wovenledger.app.ui.components.fieldErrorsOf
import com.wovenledger.app.ui.components.paiseToTyped
import com.wovenledger.app.ui.components.rupeesToPaise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * One line as it is being typed. Qty and rate stay text so a half-entered "12." is
 * not forced through a number on every keystroke; they convert once, on save.
 */
data class LineForm(
    val itemId: Long? = null,
    val qty: String = "",
    val rate: String = "",
)

data class DocumentForm(
    val date: LocalDate = LocalDate.now(),
    val partyId: Long? = null,
    val plantId: Long? = null,
    val discount: String = "",
    val notes: String = "",
    val lines: List<LineForm> = listOf(LineForm()),
    val submitting: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val message: String? = null,
    val saved: Boolean = false,
    /** Soft stock shortfalls the server reported beside a successful save. */
    val warnings: List<String> = emptyList(),
)

/** What an edit form is opened with, read out of Room. */
data class DocumentSnapshot(
    val date: LocalDate,
    val partyId: Long,
    val plantId: Long,
    val discount: Long,
    val lines: List<LineForm>,
)

/**
 * Everything a sales invoice form and a purchase bill form do identically — which
 * is everything except which parties are offered and which endpoint is posted to.
 * The two documents are mirror images in the domain, and duplicating this would be
 * two copies of the same line-item arithmetic to keep in step.
 */
abstract class DocumentFormViewModel(
    private val gson: Gson,
    parties: PartyRepository,
    items: ItemRepository,
    plants: PlantRepository,
    private val documentId: Long?,
) : ViewModel() {

    val editing: Boolean = documentId != null

    private val _form = MutableStateFlow(DocumentForm())
    val form: StateFlow<DocumentForm> = _form.asStateFlow()

    /** Customers buy and suppliers sell, so each form offers only its own side. */
    abstract val partyChoices: StateFlow<List<Party>>

    protected val allParties: StateFlow<List<Party>> = parties.getAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val items: StateFlow<List<Item>> = items.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val plants: StateFlow<List<Plant>> = plants.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * A live preview of what the server will compute. Nothing here is ever sent:
     * the stored totals are the server's, derived from the lines it accepted.
     */
    val subtotal: StateFlow<Long> = _form
        .map { form -> form.lines.sumOf { lineAmount(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val total: StateFlow<Long> = _form
        .map { form ->
            val net = form.lines.sumOf { lineAmount(it) } - (rupeesToPaise(form.discount) ?: 0L)
            maxOf(0L, net)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** Loads the stored document into the form, so an edit starts from what exists. */
    protected fun prefill() {
        val id = documentId ?: return

        viewModelScope.launch {
            load(id)?.let { stored ->
                _form.update {
                    it.copy(
                        date = stored.date,
                        partyId = stored.partyId,
                        plantId = stored.plantId,
                        discount = if (stored.discount == 0L) "" else paiseToTyped(stored.discount),
                        lines = stored.lines.ifEmpty { listOf(LineForm()) },
                    )
                }
            }
        }
    }

    protected abstract suspend fun load(id: Long): DocumentSnapshot?

    /** @return the warnings the server sent back with a successful save */
    protected abstract suspend fun persist(
        id: Long?,
        partyId: Int,
        plantId: Int?,
        date: String,
        discountPaise: Long,
        notes: String?,
        lines: List<NewDocumentLine>,
    ): List<String>

    fun onDateChange(value: LocalDate) = _form.update { it.copy(date = value) }

    fun onPartyChange(value: Long) = _form.update { it.copy(partyId = value, errors = it.errors - "party_id") }

    fun onPlantChange(value: Long) = _form.update { it.copy(plantId = value, errors = it.errors - "plant_id") }

    fun onDiscountChange(value: String) =
        _form.update { it.copy(discount = value, errors = it.errors - "discount_amount") }

    fun onNotesChange(value: String) = _form.update { it.copy(notes = value) }

    fun addLine() = _form.update { it.copy(lines = it.lines + LineForm(), errors = it.errors - "lines") }

    /** A document always keeps one line: an empty list has nothing to type into. */
    fun removeLine(index: Int) = _form.update { form ->
        val remaining = form.lines.filterIndexed { i, _ -> i != index }

        form.copy(lines = remaining.ifEmpty { listOf(LineForm()) }, errors = form.errors - "lines")
    }

    /** Picking an item prefills its rate, exactly as the portal's form does. */
    fun onLineItemChange(index: Int, itemId: Long) = updateLine(index) { line ->
        val defaultRate = items.value.firstOrNull { it.id == itemId }?.defaultRate

        line.copy(
            itemId = itemId,
            rate = if (line.rate.isBlank() && defaultRate != null) paiseToTyped(defaultRate) else line.rate,
        )
    }

    fun onLineQtyChange(index: Int, value: String) = updateLine(index) { it.copy(qty = value) }

    fun onLineRateChange(index: Int, value: String) = updateLine(index) { it.copy(rate = value) }

    /**
     * Posts and waits. Writes are online-only, so a failure here means the document
     * does not exist anywhere — there is no queue that will finish the job later.
     */
    fun submit() {
        val current = _form.value
        val local = validate(current)

        if (local.isNotEmpty()) {
            _form.update { it.copy(errors = local, message = null) }

            return
        }

        val lines = current.lines
            .filter { it.itemId != null && (it.qty.toDoubleOrNull() ?: 0.0) > 0 }
            .map {
                NewDocumentLine(
                    itemId = it.itemId!!.toInt(),
                    qty = it.qty.toDouble(),
                    rate = rupeesToPaise(it.rate),
                )
            }

        viewModelScope.launch {
            _form.update { it.copy(submitting = true, errors = emptyMap(), message = null) }

            runCatching {
                persist(
                    id = documentId,
                    partyId = current.partyId!!.toInt(),
                    plantId = current.plantId?.toInt(),
                    date = current.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    discountPaise = rupeesToPaise(current.discount) ?: 0L,
                    notes = current.notes.trim().ifBlank { null },
                    lines = lines,
                )
            }.onSuccess { warnings ->
                _form.update { it.copy(submitting = false, saved = true, warnings = warnings) }
            }.onFailure { cause ->
                val fields = fieldErrorsOf(cause, gson)
                _form.update {
                    it.copy(
                        submitting = false,
                        errors = fields,
                        message = if (fields.isEmpty()) failureMessage(cause) else null,
                    )
                }
            }
        }
    }

    /**
     * Local checks only, and deliberately not the stock check: running short is a
     * warning the server returns beside a successful save, never a reason to stop.
     */
    private fun validate(form: DocumentForm): Map<String, String> = buildMap {
        if (form.partyId == null) put("party_id", "Choose a party")
        if (form.plantId == null) put("plant_id", "Choose a plant")

        if (form.discount.isNotBlank() && rupeesToPaise(form.discount) == null) {
            put("discount_amount", "Enter an amount in rupees")
        }

        val usable = form.lines.filter { it.itemId != null && (it.qty.toDoubleOrNull() ?: 0.0) > 0 }
        when {
            usable.isEmpty() -> put("lines", "Add at least one line with an item and a quantity")
            usable.any { it.rate.isNotBlank() && rupeesToPaise(it.rate) == null } ->
                put("lines", "Every rate must be an amount in rupees")
        }
    }

    private fun updateLine(index: Int, change: (LineForm) -> LineForm) = _form.update { form ->
        form.copy(
            lines = form.lines.mapIndexed { i, line -> if (i == index) change(line) else line },
            errors = form.errors - "lines",
        )
    }

    private fun lineAmount(line: LineForm): Long {
        val qty = line.qty.toDoubleOrNull() ?: return 0L
        val rate = rupeesToPaise(line.rate) ?: return 0L

        return (qty * rate).toLong()
    }

    /** Room stores the document and its lines separately; the form wants them together. */
    protected fun snapshotOf(
        date: LocalDate,
        partyId: Long,
        plantId: Long,
        discount: Long,
        lines: List<Triple<Long, Double, Long>>,
    ) = DocumentSnapshot(
        date = date,
        partyId = partyId,
        plantId = plantId,
        discount = discount,
        lines = lines.map { (itemId, qty, rate) ->
            LineForm(itemId = itemId, qty = trimZeros(qty), rate = paiseToTyped(rate))
        },
    )

    /** 1200.0 reads better as "1200" in a field the user is about to retype. */
    private fun trimZeros(qty: Double): String =
        if (qty == qty.toLong().toDouble()) qty.toLong().toString() else qty.toString()
}

// ------------------------------------------------------------------ Sales invoice

@HiltViewModel
class SalesInvoiceFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val invoices: SalesInvoiceRepository,
    parties: PartyRepository,
    items: ItemRepository,
    plants: PlantRepository,
    gson: Gson,
) : DocumentFormViewModel(gson, parties, items, plants, savedStateHandle.get<Long>("invoiceId")) {

    override val partyChoices: StateFlow<List<Party>> = allParties
        .map { all -> all.filter { it.type != PartyType.SUPPLIER } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        prefill()
    }

    override suspend fun load(id: Long): DocumentSnapshot? {
        val invoice = invoices.read(id).first() ?: return null
        val lines = invoices.getLines(id).first()

        return snapshotOf(
            date = invoice.date,
            partyId = invoice.partyId,
            plantId = invoice.plantId,
            discount = invoice.discount,
            lines = lines.map { Triple(it.itemId, it.qty, it.rate) },
        )
    }

    override suspend fun persist(
        id: Long?,
        partyId: Int,
        plantId: Int?,
        date: String,
        discountPaise: Long,
        notes: String?,
        lines: List<NewDocumentLine>,
    ): List<String> {
        val body = NewSalesInvoice(
            partyId = partyId,
            plantId = plantId,
            invoiceDate = date,
            discountAmount = discountPaise,
            notes = notes,
            lines = lines,
        )

        return if (id == null) {
            invoices.createOnServer(body).warnings
        } else {
            invoices.updateOnServer(id, body).warnings
        }
    }
}

@Composable
fun SalesInvoiceFormScreen(navController: NavHostController) {
    val viewModel: SalesInvoiceFormViewModel = hiltViewModel()

    DocumentFormBody(
        navController = navController,
        viewModel = viewModel,
        partyLabel = "Buyer",
        saveLabel = if (viewModel.editing) "Save invoice" else "Raise invoice",
    )
}

// ------------------------------------------------------------------ Purchase bill

@HiltViewModel
class PurchaseBillFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bills: PurchaseBillRepository,
    parties: PartyRepository,
    items: ItemRepository,
    plants: PlantRepository,
    gson: Gson,
) : DocumentFormViewModel(gson, parties, items, plants, savedStateHandle.get<Long>("billId")) {

    override val partyChoices: StateFlow<List<Party>> = allParties
        .map { all -> all.filter { it.type != PartyType.CUSTOMER } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        prefill()
    }

    override suspend fun load(id: Long): DocumentSnapshot? {
        val bill = bills.read(id).first() ?: return null
        val lines = bills.getLines(id).first()

        return snapshotOf(
            date = bill.date,
            partyId = bill.partyId,
            plantId = bill.plantId,
            discount = bill.discount,
            lines = lines.map { Triple(it.itemId, it.qty, it.rate) },
        )
    }

    override suspend fun persist(
        id: Long?,
        partyId: Int,
        plantId: Int?,
        date: String,
        discountPaise: Long,
        notes: String?,
        lines: List<NewDocumentLine>,
    ): List<String> {
        val body = NewPurchaseBill(
            partyId = partyId,
            plantId = plantId,
            billDate = date,
            discountAmount = discountPaise,
            notes = notes,
            lines = lines,
        )

        return if (id == null) {
            bills.createOnServer(body).warnings
        } else {
            bills.updateOnServer(id, body).warnings
        }
    }
}

@Composable
fun PurchaseBillFormScreen(navController: NavHostController) {
    val viewModel: PurchaseBillFormViewModel = hiltViewModel()

    DocumentFormBody(
        navController = navController,
        viewModel = viewModel,
        partyLabel = "Supplier",
        saveLabel = if (viewModel.editing) "Save bill" else "Book bill",
    )
}

// ------------------------------------------------------------------ Shared body

@Composable
private fun DocumentFormBody(
    navController: NavHostController,
    viewModel: DocumentFormViewModel,
    partyLabel: String,
    saveLabel: String,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val parties by viewModel.partyChoices.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val plants by viewModel.plants.collectAsStateWithLifecycle()
    val subtotal by viewModel.subtotal.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()

    // A clean save returns straight to the list. One that carried warnings stays put
    // until they have been read — the document is saved either way.
    LaunchedEffect(form.saved, form.warnings) {
        if (form.saved && form.warnings.isEmpty()) {
            navController.popBackStack()
        }
    }

    // The plant is required, and picking it by hand every time would be tedious when
    // there is only one to pick.
    LaunchedEffect(plants) {
        if (form.plantId == null && plants.size == 1) {
            viewModel.onPlantChange(plants.first().id)
        }
    }

    FormScaffold(
        submitting = form.submitting,
        saveLabel = saveLabel,
        message = form.message,
        onSave = viewModel::submit,
        banner = {
            WarningBanner(
                warnings = form.warnings,
                onDismiss = { navController.popBackStack() },
            )
        },
    ) {
        DateField(
            date = form.date,
            error = form.errors["invoice_date"] ?: form.errors["bill_date"],
            onChange = viewModel::onDateChange,
        )

        PickerField(
            label = partyLabel,
            selected = parties.firstOrNull { it.id == form.partyId }?.name,
            placeholder = "Choose a $partyLabel".lowercase().replaceFirstChar { it.uppercase() },
            options = parties.map { it.id to it.name },
            error = form.errors["party_id"],
            onSelect = viewModel::onPartyChange,
        )

        PickerField(
            label = "Plant",
            selected = plants.firstOrNull { it.id == form.plantId }?.name,
            placeholder = "Choose a plant",
            options = plants.map { it.id to it.name },
            error = form.errors["plant_id"],
            onSelect = viewModel::onPlantChange,
        )

        Text(
            text = "Items",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        form.errors["lines"]?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        form.lines.forEachIndexed { index, line ->
            LineCard(
                line = line,
                items = items,
                onItemChange = { viewModel.onLineItemChange(index, it) },
                onQtyChange = { viewModel.onLineQtyChange(index, it) },
                onRateChange = { viewModel.onLineRateChange(index, it) },
                onRemove = { viewModel.removeLine(index) },
            )
        }

        OutlinedButton(onClick = viewModel::addLine, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Add line")
        }

        MoneyField(
            label = "Discount (₹)",
            value = form.discount,
            error = form.errors["discount_amount"],
            onChange = viewModel::onDiscountChange,
            hint = "Leave blank for none",
        )

        OutlinedTextField(
            value = form.notes,
            onValueChange = viewModel::onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notes") },
            minLines = 2,
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Derived on screen, never sent: the server totals what it stores.
                AmountRow("Subtotal", subtotal)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                AmountRow("Total", total, emphasis = true)
            }
        }
    }
}

@Composable
private fun LineCard(
    line: LineForm,
    items: List<Item>,
    onItemChange: (Long) -> Unit,
    onQtyChange: (String) -> Unit,
    onRateChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val item = items.firstOrNull { it.id == line.itemId }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PickerField(
                    label = "Item",
                    selected = item?.name,
                    placeholder = "Choose an item",
                    options = items.map { it.id to it.name },
                    error = null,
                    onSelect = onItemChange,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove line")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.qty,
                    onValueChange = onQtyChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Qty") },
                    singleLine = true,
                    // The unit is the item's own, and is a count rather than money.
                    supportingText = { item?.let { Text(it.unit) } },
                )
                MoneyField(
                    label = "Rate (₹)",
                    value = line.rate,
                    error = null,
                    onChange = onRateChange,
                    modifier = Modifier.weight(1f),
                )
            }

            AmountRow("Line amount", lineAmountOf(line))
        }
    }
}

private fun lineAmountOf(line: LineForm): Long {
    val qty = line.qty.toDoubleOrNull() ?: return 0L
    val rate = rupeesToPaise(line.rate) ?: return 0L

    return (qty * rate).toLong()
}
