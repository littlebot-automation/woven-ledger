package com.wovenledger.app.ui.screens.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.wovenledger.app.data.entities.BalanceType
import com.wovenledger.app.data.repository.PartyRepository
import com.wovenledger.app.data.repository.PurchaseBillRepository
import com.wovenledger.app.data.repository.SalesInvoiceRepository
import com.wovenledger.app.data.repository.StaffRepository
import com.wovenledger.app.data.repository.StaffWorkRepository
import androidx.compose.material3.Surface
import com.wovenledger.app.ui.components.DateRange
import com.wovenledger.app.ui.components.DateRangeFilter
import com.wovenledger.app.ui.components.EmptyState
import com.wovenledger.app.ui.components.MoneyTone
import com.wovenledger.app.ui.components.MoneyText
import com.wovenledger.app.ui.components.formatDate
import com.wovenledger.app.ui.components.formatMoney
import com.wovenledger.app.ui.screens.stock.StockReportBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One line of any report: a label, some context, and a figure. */
data class ReportRow(
    val title: String,
    val subtitle: String,
    val amount: Long,
    val tone: MoneyTone = MoneyTone.Neutral,
)

data class Report(
    val rows: List<ReportRow> = emptyList(),
    val total: Long = 0,
    val totalLabel: String = "Total",
)

data class ReportsData(
    val sales: Report = Report(),
    val purchases: Report = Report(),
    val receivables: Report = Report(),
    val payables: Report = Report(),
    val staffPayments: Report = Report(),
    val staffPaid: Long = 0,
    val staffUnpaid: Long = 0,
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    salesInvoices: SalesInvoiceRepository,
    purchaseBills: PurchaseBillRepository,
    parties: PartyRepository,
    staff: StaffRepository,
    staffWork: StaffWorkRepository,
) : ViewModel() {

    private val _range = MutableStateFlow(DateRange())
    val range: StateFlow<DateRange> = _range.asStateFlow()

    fun onRangeChange(value: DateRange) {
        _range.value = value
    }

    val data: StateFlow<ReportsData> = combine(
        salesInvoices.getAll(),
        purchaseBills.getAll(),
        parties.getAll(),
        staff.getAll(),
        staffWork.getAll(),
        _range,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val invoicesAll = values[0] as List<com.wovenledger.app.data.entities.SalesInvoice>
        @Suppress("UNCHECKED_CAST")
        val billsAll = values[1] as List<com.wovenledger.app.data.entities.PurchaseBill>
        @Suppress("UNCHECKED_CAST")
        val allParties = values[2] as List<com.wovenledger.app.data.entities.Party>
        @Suppress("UNCHECKED_CAST")
        val allStaff = values[3] as List<com.wovenledger.app.data.entities.Staff>
        @Suppress("UNCHECKED_CAST")
        val workAll = values[4] as List<com.wovenledger.app.data.entities.StaffWork>
        val window = values[5] as DateRange

        // Only dated records can be windowed. Party balances and stock are positions as
        // they stand today, not activity over a period, so they ignore the filter.
        val invoices = invoicesAll.filter { window.contains(it.date) }
        val bills = billsAll.filter { window.contains(it.date) }
        val work = workAll.filter { window.contains(it.date) }

        val partyNames = allParties.associate { it.id to it.name }

        val sales = Report(
            rows = invoices.map {
                ReportRow(it.no, "${formatDate(it.date)} · ${partyNames[it.partyId] ?: "—"}", it.total)
            },
            total = invoices.sumOf { it.total },
            totalLabel = "Total sales",
        )

        val purchases = Report(
            rows = bills.map {
                ReportRow(it.no, "${formatDate(it.date)} · ${partyNames[it.partyId] ?: "—"}", it.total)
            },
            total = bills.sumOf { it.total },
            totalLabel = "Total purchases",
        )

        // docs/SPEC.md §6 puts anything above a rounding threshold on these two reports,
        // so a party sitting at exactly zero appears on neither.
        // The running balance is signed by the portal: positive is owed to us.
        val owing = allParties.filter { it.ledgerBalance > 0 }
        val owed = allParties.filter { it.ledgerBalance < 0 }

        val receivables = Report(
            rows = owing.sortedByDescending { it.ledgerBalance }.map {
                ReportRow(it.name, it.type.name.lowercase(), it.ledgerBalance, MoneyTone.Receive)
            },
            total = owing.sumOf { it.ledgerBalance },
            totalLabel = "Total receivable",
        )

        val payables = Report(
            rows = owed.sortedBy { it.ledgerBalance }.map {
                ReportRow(it.name, it.type.name.lowercase(), -it.ledgerBalance, MoneyTone.Pay)
            },
            total = owed.sumOf { -it.ledgerBalance },
            totalLabel = "Total payable",
        )

        val byStaff = work.groupBy { it.staffId }
        val staffNames = allStaff.associate { it.id to it.name }

        val staffRows = byStaff.map { (staffId, entries) ->
            val unpaid = entries.filter { !it.paid }.sumOf { it.getAmount() }
            val paid = entries.filter { it.paid }.sumOf { it.getAmount() }

            ReportRow(
                title = staffNames[staffId] ?: "Staff #$staffId",
                subtitle = "paid ${formatMoney(paid)} · unpaid ${formatMoney(unpaid)}",
                amount = unpaid,
                tone = if (unpaid > 0) MoneyTone.Pay else MoneyTone.Neutral,
            )
        }.sortedByDescending { it.amount }

        ReportsData(
            sales = sales,
            purchases = purchases,
            receivables = receivables,
            payables = payables,
            staffPayments = Report(staffRows, staffRows.sumOf { it.amount }, "Total unpaid wages"),
            staffPaid = work.filter { it.paid }.sumOf { it.getAmount() },
            staffUnpaid = work.filter { !it.paid }.sumOf { it.getAmount() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsData())
}

private enum class Tab(val label: String, val dated: Boolean) {
    Sales("Sales", dated = true),
    Purchases("Purchases", dated = true),
    Stock("Stock", dated = false),
    Receivables("Receivables", dated = false),
    Payables("Payables", dated = false),
    StaffPayments("Staff wages", dated = true),
}

@Composable
fun ReportsScreen(navController: NavHostController) {
    val viewModel: ReportsViewModel = hiltViewModel()
    val data by viewModel.data.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.Sales) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Tab.entries.forEach { entry ->
                FilterChip(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    label = { Text(entry.label) },
                )
            }
        }

        // Receivables, payables and stock are positions as they stand now, not activity
        // over a period, so a date window would be meaningless on them.
        if (tab.dated) {
            DateRangeFilter(
                range = range,
                onChange = viewModel::onRangeChange,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        when (tab) {
            // Stock owns its own fetch and list; the other tabs render from Room.
            Tab.Stock -> StockReportBody()
            Tab.Sales -> ReportBody(data.sales)
            Tab.Purchases -> ReportBody(data.purchases)
            Tab.Receivables -> ReportBody(data.receivables)
            Tab.Payables -> ReportBody(data.payables)
            Tab.StaffPayments -> ReportBody(
                report = data.staffPayments,
                header = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TotalTile("Paid", data.staffPaid, MoneyTone.Neutral, Modifier.weight(1f))
                        TotalTile("Unpaid", data.staffUnpaid, MoneyTone.Pay, Modifier.weight(1f))
                    }
                },
            )
        }
    }
}

@Composable
private fun ReportBody(report: Report, header: (@Composable () -> Unit)? = null) {
    if (report.rows.isEmpty() && header == null) {
        EmptyState("Nothing matches. Widen the dates, or clear the filter.")
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (header != null) {
                item { header() }
            }

            items(report.rows, key = { it.title }) { row ->
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        Text(row.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = row.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MoneyText(row.amount, tone = row.tone)
                }
            }
            }
        }

        // The total sits under the list rather than above it: after narrowing the
        // dates, the figure you came for is the last thing you read, not the first.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = report.totalLabel,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = if (report.rows.size == 1) "1 entry" else "${report.rows.size} entries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MoneyText(report.total, emphasis = true)
            }
        }
    }
}

@Composable
private fun TotalTile(label: String, paise: Long, tone: MoneyTone, modifier: Modifier = Modifier) {
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
