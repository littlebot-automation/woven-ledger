package com.wovenledger.app.ui.screens.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.wovenledger.app.data.api.StockRowDto
import com.wovenledger.app.data.entities.Item
import com.wovenledger.app.data.repository.ItemRepository
import com.wovenledger.app.data.repository.ItemStockRepository
import com.wovenledger.app.data.repository.PartyRepository
import com.wovenledger.app.data.repository.PurchaseBillRepository
import com.wovenledger.app.data.repository.SalesInvoiceRepository
import com.wovenledger.app.pdf.formatQty
import com.wovenledger.app.ui.components.DocumentNumberText
import com.wovenledger.app.ui.components.EmptyState
import com.wovenledger.app.ui.components.MoneyText
import com.wovenledger.app.ui.components.formatDate
import com.wovenledger.app.ui.components.formatMoney
import com.wovenledger.app.ui.navigation.NavigationRoutes
import com.wovenledger.app.ui.theme.WovenDanger
import com.wovenledger.app.ui.theme.WovenSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ItemsListViewModel @Inject constructor(
    items: ItemRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val results: StateFlow<List<Item>> = combine(items.getAll(), _query) { all, query ->
        all.filter {
            query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.hsn.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }
}

@Composable
fun ItemsListScreen(navController: NavHostController) {
    val viewModel: ItemsListViewModel = hiltViewModel()
    val items by viewModel.results.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search items or HSN") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        if (items.isEmpty()) {
            EmptyState("No items yet.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate("${NavigationRoutes.ITEM_DETAIL_BASE}/${item.id}")
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "${item.type.readable()} · per ${item.unit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MoneyText(item.defaultRate)
                        }
                    }
                }
            }
        }
    }
}

/** One movement of an item: bought in, or sold out. */
data class ItemMovement(
    val date: LocalDate,
    val documentNumber: String,
    val partyName: String?,
    val qty: Double,
    val rate: Long,
    val amount: Long,
    val inward: Boolean,
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    items: ItemRepository,
    invoices: SalesInvoiceRepository,
    bills: PurchaseBillRepository,
    parties: PartyRepository,
    private val stock: ItemStockRepository,
) : ViewModel() {

    private val itemId: Long = savedStateHandle.get<Long>("itemId") ?: 0L

    val item: StateFlow<Item?> = items.read(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _stockRow = MutableStateFlow<StockRowDto?>(null)
    val stockRow: StateFlow<StockRowDto?> = _stockRow.asStateFlow()

    init {
        viewModelScope.launch {
            // Plant names and the low-stock verdict live server-side; Room holds only
            // bare quantities, so the breakdown is fetched rather than derived.
            _stockRow.value = runCatching { stock.fetchReportFor(itemId) }.getOrNull()
        }
    }

    /**
     * Everything that moved this item, newest first.
     *
     * Purchases and sales are one history rather than two lists: what an item cost
     * last time and what it sold for are read together, not separately.
     */
    val movements: StateFlow<List<ItemMovement>> = combine(
        invoices.getLinesForItem(itemId),
        invoices.getAll(),
        bills.getLinesForItem(itemId),
        bills.getAll(),
        parties.getAll(),
    ) { saleLines, allInvoices, billLines, allBills, allParties ->
        val names = allParties.associate { it.id to it.name }
        val invoiceById = allInvoices.associateBy { it.id }
        val billById = allBills.associateBy { it.id }

        val sold = saleLines.mapNotNull { line ->
            invoiceById[line.invoiceId]?.let { invoice ->
                ItemMovement(
                    date = invoice.date,
                    documentNumber = invoice.no,
                    partyName = names[invoice.partyId],
                    qty = line.qty,
                    rate = line.rate,
                    amount = line.amount,
                    inward = false,
                )
            }
        }

        val bought = billLines.mapNotNull { line ->
            billById[line.billId]?.let { bill ->
                ItemMovement(
                    date = bill.date,
                    documentNumber = bill.no,
                    partyName = names[bill.partyId],
                    qty = line.qty,
                    rate = line.rate,
                    amount = line.amount,
                    inward = true,
                )
            }
        }

        (sold + bought).sortedWith(
            compareByDescending<ItemMovement> { it.date }.thenByDescending { it.documentNumber }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun ItemDetailScreen(navController: NavHostController, itemId: Long) {
    val viewModel: ItemDetailViewModel = hiltViewModel()
    val item by viewModel.item.collectAsStateWithLifecycle()
    val stockRow by viewModel.stockRow.collectAsStateWithLifecycle()
    val movements by viewModel.movements.collectAsStateWithLifecycle()

    val current = item
    if (current == null) {
        EmptyState("That item isn't in the local copy yet.")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(current.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = current.type.readable(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    LabelledRow("Default rate") { MoneyText(current.defaultRate, emphasis = true) }
                    LabelledRow("Unit") { Text(current.unit) }
                    if (current.hsn.isNotBlank()) {
                        LabelledRow("HSN") { Text(current.hsn) }
                    }
                }
            }
        }

        stockRow?.let { row ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (row.isLow) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LabelledRow("In stock") {
                            Text(
                                text = "${formatQty(row.totalQty)} ${row.unit}",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        if (row.isLow) {
                            Text(
                                text = "Low on stock",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        row.byPlant.forEach { plant ->
                            LabelledRow(plant.plantName) {
                                Text(
                                    text = "${formatQty(plant.qty)} ${row.unit}",
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "History",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            HorizontalDivider()
        }

        if (movements.isEmpty()) {
            item {
                Text(
                    text = "This item hasn't been bought or sold yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else {
            items(movements, key = { "${it.documentNumber}-${it.date}" }) { movement ->
                MovementCard(movement, current.unit)
            }
        }
    }
}

@Composable
private fun MovementCard(movement: ItemMovement, unit: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (movement.inward) {
                                Icons.Filled.ArrowDownward
                            } else {
                                Icons.Filled.ArrowUpward
                            },
                            contentDescription = if (movement.inward) "Bought" else "Sold",
                            tint = if (movement.inward) WovenSuccess else WovenDanger,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        DocumentNumberText(movement.documentNumber)
                    }
                    Text(
                        text = listOfNotNull(formatDate(movement.date), movement.partyName)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        // The sign says which way it moved; the icon repeats it in
                        // colour, so neither carries the meaning alone.
                        text = "${if (movement.inward) "+" else "−"}${formatQty(movement.qty)} $unit",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (movement.inward) WovenSuccess else WovenDanger,
                    )
                    Text(
                        text = "@ ${formatMoney(movement.rate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (movement.inward) "Purchase value" else "Sale value",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyText(movement.amount)
            }
        }
    }
}

@Composable
private fun LabelledRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        value()
    }
}

private fun com.wovenledger.app.data.entities.ItemType.readable(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
