package com.wovenledger.app.ui.screens.parties

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.wovenledger.app.data.entities.BalanceType
import com.wovenledger.app.data.entities.Party
import com.wovenledger.app.data.entities.PartyType
import com.wovenledger.app.data.entities.Payment
import com.wovenledger.app.data.entities.PurchaseBill
import com.wovenledger.app.data.entities.SalesInvoice
import com.wovenledger.app.data.repository.PartyRepository
import com.wovenledger.app.data.repository.PaymentRepository
import com.wovenledger.app.data.repository.PurchaseBillRepository
import com.wovenledger.app.data.repository.SalesInvoiceRepository
import com.wovenledger.app.ui.components.DocumentNumberText
import com.wovenledger.app.ui.components.EmptyState
import com.wovenledger.app.ui.components.PendingBadge
import com.wovenledger.app.ui.components.MoneyTone
import com.wovenledger.app.ui.components.MoneyText
import com.wovenledger.app.ui.components.formatDate
import com.wovenledger.app.ui.components.moneyToneColor
import com.wovenledger.app.ui.navigation.NavigationRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// ------------------------------------------------------------------ List

@HiltViewModel
class PartiesListViewModel @Inject constructor(
    parties: PartyRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _filter = MutableStateFlow<PartyType?>(null)
    val filter: StateFlow<PartyType?> = _filter

    val results: StateFlow<List<Party>> = combine(
        parties.getAll(),
        _query,
        _filter,
    ) { all, query, type ->
        all.asSequence()
            .filter { party ->
                type == null || party.type == type || party.type == PartyType.BOTH
            }
            .filter { party ->
                query.isBlank() ||
                    party.name.contains(query, ignoreCase = true) ||
                    party.phone.contains(query, ignoreCase = true) ||
                    party.gstin?.contains(query, ignoreCase = true) == true
            }
            .sortedBy { it.name }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onFilterChange(value: PartyType?) {
        _filter.value = if (_filter.value == value) null else value
    }
}

@Composable
fun PartiesListScreen(navController: NavHostController) {
    val viewModel: PartiesListViewModel = hiltViewModel()
    val parties by viewModel.results.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search name, phone or GSTIN") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = filter == PartyType.CUSTOMER,
                onClick = { viewModel.onFilterChange(PartyType.CUSTOMER) },
                label = { Text("Customers") },
            )
            FilterChip(
                selected = filter == PartyType.SUPPLIER,
                onClick = { viewModel.onFilterChange(PartyType.SUPPLIER) },
                label = { Text("Suppliers") },
            )
        }

        if (parties.isEmpty()) {
            EmptyState("No parties match. Pull the latest from the server, or clear the filter.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(parties, key = { it.id }) { party ->
                    PartyRow(party) {
                        navController.navigate("${NavigationRoutes.PARTY_DETAIL_BASE}/${party.id}")
                    }
                }
            }
        }
    }

        // Invoicing a customer who is not on file yet should not mean a trip to the
        // portal, so a party can be added from here.
        FloatingActionButton(
            onClick = { navController.navigate(NavigationRoutes.PARTY_CREATE) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New party")
        }
    }
}

@Composable
private fun PartyRow(party: Party, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                Text(party.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = buildString {
                        append(party.type.name.lowercase().replaceFirstChar { it.uppercase() })
                        if (party.phone.isNotBlank()) append(" · ${party.phone}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // A party added with no signal has only a local id, so the document
                // forms cannot use it yet. Saying so here explains why it is missing
                // from the pickers.
                if (party.id < 0) {
                    PendingBadge(modifier = Modifier.padding(top = 4.dp))
                }
            }
            // The running balance, not the opening one: what they owe today.
            val receivable = party.ledgerBalance >= 0
            val tone = if (receivable) MoneyTone.Receive else MoneyTone.Pay

            Column(horizontalAlignment = Alignment.End) {
                MoneyText(kotlin.math.abs(party.ledgerBalance), tone = tone)
                Text(
                    text = if (receivable) "to receive" else "to pay",
                    style = MaterialTheme.typography.labelSmall,
                    color = moneyToneColor(tone),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Detail

data class PartyDetail(
    val party: Party? = null,
    val invoices: List<SalesInvoice> = emptyList(),
    val bills: List<PurchaseBill> = emptyList(),
    val payments: List<Payment> = emptyList(),
)

@HiltViewModel
class PartyDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    parties: PartyRepository,
    salesInvoices: SalesInvoiceRepository,
    purchaseBills: PurchaseBillRepository,
    payments: PaymentRepository,
) : ViewModel() {

    private val partyId: Long = savedStateHandle.get<Long>("partyId") ?: 0L

    val detail: StateFlow<PartyDetail> = combine(
        parties.read(partyId),
        salesInvoices.findForParty(partyId),
        purchaseBills.findForParty(partyId),
        payments.findForParty(partyId),
    ) { party, invoices, bills, partyPayments ->
        PartyDetail(party, invoices, bills, partyPayments)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PartyDetail())
}

@Composable
fun PartyDetailScreen(navController: NavHostController, partyId: Long) {
    val viewModel: PartyDetailViewModel = hiltViewModel()
    val detail by viewModel.detail.collectAsStateWithLifecycle()

    val party = detail.party
    if (party == null) {
        EmptyState("That party isn't in the local copy yet.")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(party.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = party.type.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { navController.navigate("${NavigationRoutes.PARTY_EDIT_BASE}/${party.id}") },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Edit")
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val receivable = party.ledgerBalance >= 0
                    val tone = if (receivable) MoneyTone.Receive else MoneyTone.Pay

                    DetailLine("Balance", null) {
                        MoneyText(kotlin.math.abs(party.ledgerBalance), emphasis = true, tone = tone)
                    }
                    DetailLine("Direction", if (receivable) "They owe us" else "We owe them")
                    if (party.openingBalance != 0L) {
                        DetailLine("Opening", null) {
                            MoneyText(
                                party.openingBalance,
                                tone = if (party.openingBalanceType == BalanceType.TO_RECEIVE) {
                                    MoneyTone.Receive
                                } else {
                                    MoneyTone.Pay
                                },
                            )
                        }
                    }
                    if (party.phone.isNotBlank()) DetailLine("Phone", party.phone)
                    if (!party.gstin.isNullOrBlank()) DetailLine("GSTIN", party.gstin)
                    if (party.address.isNotBlank()) DetailLine("Address", party.address)
                }
            }
        }

        documentSection(
            title = "Sales invoices",
            rows = detail.invoices.map { DocumentRow(it.id, it.no, it.total, it.date) },
            route = NavigationRoutes.SALES_INVOICE_DETAIL_BASE,
            navController = navController,
        )

        documentSection(
            title = "Purchase bills",
            rows = detail.bills.map { DocumentRow(it.id, it.no, it.total, it.date) },
            route = NavigationRoutes.PURCHASE_BILL_DETAIL_BASE,
            navController = navController,
        )

        documentSection(
            title = "Payments",
            rows = detail.payments.map { DocumentRow(it.id, it.no, it.amount, it.date) },
            route = NavigationRoutes.PAYMENT_DETAIL_BASE,
            navController = navController,
        )
    }
}

private data class DocumentRow(
    val id: Long,
    val number: String,
    val amount: Long,
    val date: java.time.LocalDate,
)

/**
 * The three document lists on this screen differ only in their title and where a tap
 * goes, so they share one renderer rather than three near-identical blocks.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.documentSection(
    title: String,
    rows: List<DocumentRow>,
    route: String,
    navController: NavHostController,
) {
    item {
        Column {
            Text(
                text = "$title (${rows.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            HorizontalDivider()
        }
    }

    if (rows.isEmpty()) {
        item {
            Text(
                text = "None yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        return
    }

    items(rows, key = { "$route-${it.id}" }) { row ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate("$route/${row.id}") },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    DocumentNumberText(row.number)
                    Text(
                        formatDate(row.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MoneyText(row.amount)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String?, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailing != null) {
            trailing()
        } else {
            Text(
                text = value.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
