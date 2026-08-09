package com.wovenledger.app.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.wovenledger.app.data.entities.BalanceType
import com.wovenledger.app.data.entities.Party
import com.wovenledger.app.data.entities.SalesInvoice
import com.wovenledger.app.data.repository.PartyRepository
import com.wovenledger.app.data.repository.PaymentRepository
import com.wovenledger.app.data.repository.PurchaseBillRepository
import com.wovenledger.app.data.repository.SalesInvoiceRepository
import com.wovenledger.app.data.sync.SyncManager
import com.wovenledger.app.data.sync.SyncState
import com.wovenledger.app.ui.components.MoneyTone
import com.wovenledger.app.ui.components.MoneyText
import com.wovenledger.app.ui.components.SyncBanner
import com.wovenledger.app.ui.components.formatDate
import com.wovenledger.app.ui.navigation.NavigationRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardData(
    val totalSales: Long = 0,
    val totalPurchases: Long = 0,
    val cashOut: Long = 0,
    val receivables: Long = 0,
    val payables: Long = 0,
    val topOutstanding: List<Party> = emptyList(),
    val recentInvoices: List<SalesInvoice> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    salesInvoices: SalesInvoiceRepository,
    purchaseBills: PurchaseBillRepository,
    payments: PaymentRepository,
    parties: PartyRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    val syncState: StateFlow<SyncState> = syncManager.state

    val data: StateFlow<DashboardData> = combine(
        salesInvoices.sumTotal(),
        purchaseBills.sumTotal(),
        payments.sumAmount(),
        parties.getAll(),
        salesInvoices.findRecent(),
    ) { sales, purchases, paid, allParties, recent ->
        // A party's balance direction lives in openingBalanceType, so receivables and
        // payables are two partitions of the same list rather than a sign test.
        val receivable = allParties
            .filter { it.openingBalanceType == BalanceType.TO_RECEIVE }
            .sumOf { it.openingBalance }
        val payable = allParties
            .filter { it.openingBalanceType == BalanceType.TO_PAY }
            .sumOf { it.openingBalance }

        DashboardData(
            totalSales = sales,
            totalPurchases = purchases,
            cashOut = paid,
            receivables = receivable,
            payables = payable,
            topOutstanding = allParties
                .filter { it.openingBalance > 0 }
                .sortedByDescending { it.openingBalance }
                .take(5),
            recentInvoices = recent.take(5),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardData())

    fun retrySync() {
        viewModelScope.launch { syncManager.sync() }
    }
}

@Composable
fun DashboardScreen(navController: NavHostController) {
    val viewModel: DashboardViewModel = hiltViewModel()
    val data by viewModel.data.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        (syncState as? SyncState.Failed)?.let { failure ->
            item {
                SyncBanner(
                    // Name what actually failed; "couldn't reach the server" is wrong
                    // when the server answered and it was the save that broke.
                    message = "Showing saved data — sync failed (${failure.message}).",
                    onRetry = viewModel::retrySync,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Total sales", data.totalSales, Modifier.weight(1f))
                StatTile("Total purchases", data.totalPurchases, Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Receivables", data.receivables, Modifier.weight(1f), MoneyTone.Receive)
                StatTile("Payables", data.payables, Modifier.weight(1f), MoneyTone.Pay)
            }
        }

        item {
            StatTile("Cash out (payments)", data.cashOut, Modifier.fillMaxWidth())
        }

        item { SectionHeading("Top outstanding") }

        if (data.topOutstanding.isEmpty()) {
            item { QuietRow("Nothing outstanding.") }
        } else {
            items(data.topOutstanding, key = { "party-${it.id}" }) { party ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController.navigate("${NavigationRoutes.PARTY_DETAIL_BASE}/${party.id}")
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
                        Text(party.name, style = MaterialTheme.typography.bodyLarge)
                        MoneyText(
                            paise = party.openingBalance,
                            tone = if (party.openingBalanceType == BalanceType.TO_RECEIVE) {
                                MoneyTone.Receive
                            } else {
                                MoneyTone.Pay
                            },
                        )
                    }
                }
            }
        }

        item { SectionHeading("Recent invoices") }

        if (data.recentInvoices.isEmpty()) {
            item { QuietRow("No invoices yet.") }
        } else {
            items(data.recentInvoices, key = { "invoice-${it.id}" }) { invoice ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController.navigate("${NavigationRoutes.SALES_INVOICE_DETAIL_BASE}/${invoice.id}")
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
                        Column {
                            Text(invoice.no, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                formatDate(invoice.date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MoneyText(invoice.total)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    paise: Long,
    modifier: Modifier = Modifier,
    tone: MoneyTone = MoneyTone.Neutral,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(paise, emphasis = true, tone = tone)
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        HorizontalDivider()
    }
}

@Composable
private fun QuietRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}
