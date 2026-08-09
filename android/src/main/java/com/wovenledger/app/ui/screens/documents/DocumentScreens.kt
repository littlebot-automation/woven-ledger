package com.wovenledger.app.ui.screens.documents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.wovenledger.app.data.entities.Party
import com.wovenledger.app.data.entities.Payment
import com.wovenledger.app.data.entities.PurchaseBill
import com.wovenledger.app.data.entities.SalesInvoice
import com.wovenledger.app.data.repository.ItemRepository
import com.wovenledger.app.data.repository.PartyRepository
import com.wovenledger.app.data.repository.PaymentRepository
import com.wovenledger.app.data.repository.PurchaseBillRepository
import com.wovenledger.app.data.repository.SalesInvoiceRepository
import com.wovenledger.app.ui.components.AmountRow
import com.wovenledger.app.ui.components.DateRange
import com.wovenledger.app.ui.components.DateRangeFilter
import com.wovenledger.app.ui.components.DocumentList
import com.wovenledger.app.ui.components.DocumentSummary
import com.wovenledger.app.ui.components.EmptyState
import com.wovenledger.app.ui.components.FilterSummary
import com.wovenledger.app.ui.components.MoneyText
import com.wovenledger.app.ui.components.formatMoney
import com.wovenledger.app.ui.components.formatDate
import com.wovenledger.app.ui.navigation.NavigationRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** Party names are shown on every document row, so each list needs the lookup. */
private fun namesById(parties: List<Party>): Map<Long, String> =
    parties.associate { it.id to it.name }

private fun subtitle(date: LocalDate, partyName: String?): String =
    listOfNotNull(formatDate(date), partyName).joinToString(" · ")

// ------------------------------------------------------------------ Sales invoices

@HiltViewModel
class SalesInvoicesViewModel @Inject constructor(
    invoices: SalesInvoiceRepository,
    parties: PartyRepository,
) : ViewModel() {

    private val _range = MutableStateFlow(DateRange())
    val range: StateFlow<DateRange> = _range.asStateFlow()

    fun onRangeChange(value: DateRange) {
        _range.value = value
    }

    val documents: StateFlow<List<DocumentSummary>> =
        combine(invoices.getAll(), parties.getAll(), _range) { all, allParties, window ->
            val names = namesById(allParties)
            all.filter { window.contains(it.date) }
                .map {
                    DocumentSummary(it.id, it.no, subtitle(it.date, names[it.partyId]), it.total)
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What the filter left on screen — the figure worth having when narrowing a period. */
    val total: StateFlow<Long> = documents
        .map { shown -> shown.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
}

@Composable
fun SalesInvoicesListScreen(navController: NavHostController) {
    val viewModel: SalesInvoicesViewModel = hiltViewModel()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        DateRangeFilter(
            range = range,
            onChange = viewModel::onRangeChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (range.isActive) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                FilterSummary(count = documents.size, total = total)
                HorizontalDivider()
            }
        }

        DocumentList(
            documents = documents,
            emptyMessage = if (range.isActive) {
                "No invoices in that period. Widen the dates, or clear the filter."
            } else {
                "No sales invoices yet."
            },
            onOpen = { navController.navigate("${NavigationRoutes.SALES_INVOICE_DETAIL_BASE}/$it") },
            modifier = Modifier.weight(1f),
            onCreate = { navController.navigate(NavigationRoutes.SALES_INVOICE_CREATE) },
            createLabel = "New sales invoice",
        )
    }
}

@HiltViewModel
class SalesInvoiceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    invoices: SalesInvoiceRepository,
    parties: PartyRepository,
    items: ItemRepository,
) : ViewModel() {

    private val invoiceId: Long = savedStateHandle.get<Long>("invoiceId") ?: 0L

    val invoice: StateFlow<SalesInvoice?> = invoices.read(invoiceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val partyName: StateFlow<String?> =
        combine(invoices.read(invoiceId), parties.getAll()) { invoice, allParties ->
            invoice?.let { namesById(allParties)[it.partyId] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Lines now arrive nested in the document payload and are cached alongside it. */
    val lines: StateFlow<List<DocumentLineRow>> =
        combine(invoices.getLines(invoiceId), items.getAll()) { lines, allItems ->
            val names = allItems.associate { it.id to it.name }

            lines.map {
                DocumentLineRow(names[it.itemId] ?: "Item #${it.itemId}", it.qty, it.rate, it.amount)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun SalesInvoiceDetailScreen(navController: NavHostController, invoiceId: Long) {
    val viewModel: SalesInvoiceDetailViewModel = hiltViewModel()
    val invoice by viewModel.invoice.collectAsStateWithLifecycle()
    val partyName by viewModel.partyName.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()

    val current = invoice
    if (current == null) {
        EmptyState("That invoice isn't in the local copy yet.")
        return
    }

    DocumentDetailBody(
        number = current.no,
        date = current.date,
        partyName = partyName,
        subtotal = current.subtotal,
        discount = current.discount,
        total = current.total,
        lines = lines,
        onEdit = {
            navController.navigate("${NavigationRoutes.SALES_INVOICE_EDIT_BASE}/${current.id}")
        },
    )
}

// ------------------------------------------------------------------ Purchase bills

@HiltViewModel
class PurchaseBillsViewModel @Inject constructor(
    bills: PurchaseBillRepository,
    parties: PartyRepository,
) : ViewModel() {

    val documents: StateFlow<List<DocumentSummary>> =
        combine(bills.getAll(), parties.getAll()) { all, allParties ->
            val names = namesById(allParties)
            all.map {
                DocumentSummary(it.id, it.no, subtitle(it.date, names[it.partyId]), it.total)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun PurchaseBillsListScreen(navController: NavHostController) {
    val viewModel: PurchaseBillsViewModel = hiltViewModel()
    val documents by viewModel.documents.collectAsStateWithLifecycle()

    DocumentList(
        documents = documents,
        emptyMessage = "No purchase bills yet.",
        onOpen = { navController.navigate("${NavigationRoutes.PURCHASE_BILL_DETAIL_BASE}/$it") },
        modifier = Modifier.fillMaxSize(),
        onCreate = { navController.navigate(NavigationRoutes.PURCHASE_BILL_CREATE) },
        createLabel = "New purchase bill",
    )
}

@HiltViewModel
class PurchaseBillDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    bills: PurchaseBillRepository,
    parties: PartyRepository,
    items: ItemRepository,
) : ViewModel() {

    private val billId: Long = savedStateHandle.get<Long>("billId") ?: 0L

    val bill: StateFlow<PurchaseBill?> = bills.read(billId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val partyName: StateFlow<String?> =
        combine(bills.read(billId), parties.getAll()) { bill, allParties ->
            bill?.let { namesById(allParties)[it.partyId] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lines: StateFlow<List<DocumentLineRow>> =
        combine(bills.getLines(billId), items.getAll()) { lines, allItems ->
            val names = allItems.associate { it.id to it.name }

            lines.map {
                DocumentLineRow(names[it.itemId] ?: "Item #${it.itemId}", it.qty, it.rate, it.amount)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun PurchaseBillDetailScreen(navController: NavHostController, billId: Long) {
    val viewModel: PurchaseBillDetailViewModel = hiltViewModel()
    val bill by viewModel.bill.collectAsStateWithLifecycle()
    val partyName by viewModel.partyName.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()

    val current = bill
    if (current == null) {
        EmptyState("That bill isn't in the local copy yet.")
        return
    }

    DocumentDetailBody(
        number = current.no,
        date = current.date,
        partyName = partyName,
        subtotal = current.subtotal,
        discount = current.discount,
        total = current.total,
        lines = lines,
        onEdit = {
            navController.navigate("${NavigationRoutes.PURCHASE_BILL_EDIT_BASE}/${current.id}")
        },
    )
}

// ------------------------------------------------------------------ Payments

@HiltViewModel
class PaymentsViewModel @Inject constructor(
    payments: PaymentRepository,
    parties: PartyRepository,
) : ViewModel() {

    val documents: StateFlow<List<DocumentSummary>> =
        combine(payments.getAll(), parties.getAll()) { all, allParties ->
            val names = namesById(allParties)
            all.map {
                DocumentSummary(
                    id = it.id,
                    number = it.no,
                    subtitle = subtitle(it.date, it.partyId?.let(names::get)),
                    amount = it.amount,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun PaymentsListScreen(navController: NavHostController) {
    val viewModel: PaymentsViewModel = hiltViewModel()
    val documents by viewModel.documents.collectAsStateWithLifecycle()

    DocumentList(
        documents = documents,
        emptyMessage = "No payments yet.",
        onOpen = { navController.navigate("${NavigationRoutes.PAYMENT_DETAIL_BASE}/$it") },
        modifier = Modifier.fillMaxSize(),
        onCreate = { navController.navigate(NavigationRoutes.PAYMENT_CREATE) },
        createLabel = "New payment",
    )
}

@HiltViewModel
class PaymentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    payments: PaymentRepository,
    parties: PartyRepository,
) : ViewModel() {

    private val paymentId: Long = savedStateHandle.get<Long>("paymentId") ?: 0L

    val payment: StateFlow<Payment?> = payments.read(paymentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val partyName: StateFlow<String?> =
        combine(payments.read(paymentId), parties.getAll()) { payment, allParties ->
            payment?.partyId?.let { namesById(allParties)[it] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun PaymentDetailScreen(navController: NavHostController, paymentId: Long) {
    val viewModel: PaymentDetailViewModel = hiltViewModel()
    val payment by viewModel.payment.collectAsStateWithLifecycle()
    val partyName by viewModel.partyName.collectAsStateWithLifecycle()

    val current = payment
    if (current == null) {
        EmptyState("That payment isn't in the local copy yet.")
        return
    }

    Column(modifier = Modifier.padding(16.dp)) {
        DocumentHeader(current.no, current.date, partyName)

        EditButton { navController.navigate("${NavigationRoutes.PAYMENT_EDIT_BASE}/${current.id}") }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                AmountRow("Amount", current.amount, emphasis = true)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                LabelledText("Mode", current.mode.name.lowercase().replace('_', ' '))
                LabelledText("Type", current.type.name.lowercase())
                if (current.notes.isNotBlank()) LabelledText("Notes", current.notes)
            }
        }
    }
}

// ------------------------------------------------------------------ Shared

/** One line as the detail screens render it, with its item already named. */
data class DocumentLineRow(
    val itemName: String,
    val qty: Double,
    val rate: Long,
    val amount: Long,
)

@Composable
private fun DocumentDetailBody(
    number: String,
    date: LocalDate,
    partyName: String?,
    subtotal: Long,
    discount: Long,
    total: Long,
    lines: List<DocumentLineRow>,
    onEdit: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        DocumentHeader(number, date, partyName)

        EditButton(onEdit)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                lines.forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.itemName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                // Qty is a count in the item's unit, so it is not money
                                // and is not formatted as such; the rate beside it is.
                                text = "${formatQty(line.qty)} × ${formatMoney(line.rate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MoneyText(line.amount)
                    }
                }

                if (lines.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                AmountRow("Subtotal", subtotal)
                if (discount != 0L) AmountRow("Discount", discount)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                AmountRow("Total", total, emphasis = true)
            }
        }
    }
}

/** Trailing zeros on a count of bags add nothing. */
private fun formatQty(qty: Double): String =
    if (qty == qty.toLong().toDouble()) qty.toLong().toString() else qty.toString()

@Composable
internal fun EditButton(onEdit: () -> Unit) {
    OutlinedButton(onClick = onEdit, modifier = Modifier.padding(top = 12.dp)) {
        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("Edit")
    }
}

@Composable
private fun DocumentHeader(number: String, date: LocalDate, partyName: String?) {
    Column {
        Text(number, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = subtitle(date, partyName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LabelledText(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
