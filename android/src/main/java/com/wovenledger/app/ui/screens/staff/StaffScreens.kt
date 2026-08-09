package com.wovenledger.app.ui.screens.staff

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.wovenledger.app.data.entities.Staff
import com.wovenledger.app.data.entities.StaffWageType
import com.wovenledger.app.data.entities.StaffWork
import com.wovenledger.app.data.repository.StaffRepository
import com.wovenledger.app.data.repository.StaffWorkRepository
import com.wovenledger.app.ui.components.DocumentNumberText
import com.wovenledger.app.ui.components.EmptyState
import com.wovenledger.app.ui.components.MoneyTone
import com.wovenledger.app.ui.components.MoneyText
import com.wovenledger.app.ui.components.AmountRow
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

/** How a wage type reads on screen. */
internal fun StaffWageType.label(): String = when (this) {
    StaffWageType.DAILY -> "Daily rate"
    StaffWageType.PIECE -> "Piece rate"
}

// ------------------------------------------------------------------ List

/** A staff member alongside what they are currently owed. */
data class StaffRow(
    val staff: Staff,
    val unpaid: Long,
)

@HiltViewModel
class StaffListViewModel @Inject constructor(
    staff: StaffRepository,
    staffWork: StaffWorkRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val rows: StateFlow<List<StaffRow>> = combine(
        staff.getAll(),
        staffWork.getUnpaidWork(),
        _query,
    ) { members, unpaid, query ->
        val owed = unpaid.groupBy { it.staffId }
            .mapValues { (_, entries) -> entries.sumOf { it.getAmount() } }

        members.asSequence()
            .filter { member ->
                query.isBlank() ||
                    member.name.contains(query, ignoreCase = true) ||
                    member.role.contains(query, ignoreCase = true)
            }
            .map { member -> StaffRow(member, owed[member.id] ?: 0L) }
            .sortedBy { it.staff.name }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }
}

@Composable
fun StaffListScreen(navController: NavHostController) {
    val viewModel: StaffListViewModel = hiltViewModel()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search name or role") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        if (rows.isEmpty()) {
            EmptyState("No staff match. Pull the latest from the server, or clear the search.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.staff.id }) { row ->
                    StaffCard(row) {
                        navController.navigate("${NavigationRoutes.STAFF_DETAIL_BASE}/${row.staff.id}")
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffCard(row: StaffRow, onClick: () -> Unit) {
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
                Text(row.staff.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = buildString {
                        append(row.staff.wageType.label())
                        if (row.staff.role.isNotBlank()) append(" · ${row.staff.role}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyText(row.staff.getEffectiveRate())
            }

            // Wages are money we owe, so an outstanding figure reads as a payable.
            val tone = if (row.unpaid > 0) MoneyTone.Pay else MoneyTone.Neutral

            Column(horizontalAlignment = Alignment.End) {
                MoneyText(row.unpaid, tone = tone)
                Text(
                    text = if (row.unpaid > 0) "unpaid" else "settled",
                    style = MaterialTheme.typography.labelSmall,
                    color = moneyToneColor(tone),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Detail

data class StaffDetail(
    val staff: Staff? = null,
    val work: List<StaffWork> = emptyList(),
) {
    val unpaidTotal: Long get() = work.filter { !it.paid }.sumOf { it.getAmount() }
    val paidTotal: Long get() = work.filter { it.paid }.sumOf { it.getAmount() }
    val earnedTotal: Long get() = work.sumOf { it.getAmount() }
}

@HiltViewModel
class StaffDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    staff: StaffRepository,
    staffWork: StaffWorkRepository,
) : ViewModel() {

    private val staffId: Long = savedStateHandle.get<Long>("staffId") ?: 0L

    val detail: StateFlow<StaffDetail> = combine(
        staff.read(staffId),
        staffWork.getByStaff(staffId),
    ) { member, work ->
        StaffDetail(member, work)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StaffDetail())
}

@Composable
fun StaffDetailScreen(navController: NavHostController, staffId: Long) {
    val viewModel: StaffDetailViewModel = hiltViewModel()
    val detail by viewModel.detail.collectAsStateWithLifecycle()

    val staff = detail.staff
    if (staff == null) {
        EmptyState("That staff member isn't in the local copy yet.")
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(staff.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = buildString {
                    append(staff.wageType.label())
                    if (staff.role.isNotBlank()) append(" · ${staff.role}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AmountRow("Owed", detail.unpaidTotal, emphasis = true)
                    AmountRow("Settled to date", detail.paidTotal)
                    AmountRow("Earned in total", detail.earnedTotal)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AmountRow(staff.wageType.label(), staff.getEffectiveRate())
                    if (staff.phone.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Phone",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(staff.phone, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        item {
            Column {
                Text(
                    text = "Work history (${detail.work.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                HorizontalDivider()
            }
        }

        if (detail.work.isEmpty()) {
            item {
                Text(
                    text = "No work recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(detail.work, key = { it.id }) { entry ->
                WorkEntryCard(entry, subtitle = entry.workType)
            }
        }
    }
}

// ------------------------------------------------------------------ Shared row

/**
 * One day's work. Used by both the staff detail history and the work list, which
 * differ only in whether the subtitle names the staff member or the work type.
 */
@Composable
internal fun WorkEntryCard(entry: StaffWork, subtitle: String) {
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
                Text(formatDate(entry.date), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "$subtitle · ${formatQty(entry.qty)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.paid && entry.paymentVoucherId != null) {
                    DocumentNumberText("Voucher #${entry.paymentVoucherId}")
                }
            }

            // Unpaid work is money still owed; settled work is history, so it stays neutral.
            val tone = if (entry.paid) MoneyTone.Neutral else MoneyTone.Pay

            Column(horizontalAlignment = Alignment.End) {
                MoneyText(entry.getAmount(), tone = tone)
                Text(
                    text = if (entry.paid) "paid" else "unpaid",
                    style = MaterialTheme.typography.labelSmall,
                    color = moneyToneColor(tone),
                )
            }
        }
    }
}

/** Qty is a count of days or pieces — trailing zeros only add noise. */
internal fun formatQty(qty: Double): String =
    if (qty % 1.0 == 0.0) qty.toLong().toString() else qty.toString()
