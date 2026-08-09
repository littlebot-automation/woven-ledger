package com.wovenledger.app.ui.screens.documents

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.wovenledger.app.data.entities.Receipt
import com.wovenledger.app.data.repository.PartyRepository
import com.wovenledger.app.data.repository.ReceiptRepository
import com.wovenledger.app.ui.components.AmountRow
import com.wovenledger.app.ui.components.DocumentList
import com.wovenledger.app.ui.components.DocumentSummary
import com.wovenledger.app.ui.components.EmptyState
import com.wovenledger.app.ui.components.MoneyTone
import com.wovenledger.app.ui.components.formatDate
import com.wovenledger.app.ui.navigation.NavigationRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ReceiptsViewModel @Inject constructor(
    receipts: ReceiptRepository,
    parties: PartyRepository,
) : ViewModel() {

    val documents: StateFlow<List<DocumentSummary>> =
        combine(receipts.getAll(), parties.getAll()) { all, allParties ->
            val names = allParties.associate { it.id to it.name }
            all.map {
                DocumentSummary(
                    id = it.id,
                    number = it.no,
                    subtitle = listOfNotNull(formatDate(it.date), names[it.partyId]).joinToString(" · "),
                    amount = it.amount,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun ReceiptsListScreen(navController: NavHostController) {
    val viewModel: ReceiptsViewModel = hiltViewModel()
    val documents by viewModel.documents.collectAsStateWithLifecycle()

    DocumentList(
        documents = documents,
        emptyMessage = "No receipts yet.",
        onOpen = { navController.navigate("${NavigationRoutes.RECEIPT_DETAIL_BASE}/$it") },
        modifier = Modifier.fillMaxSize(),
    )
}

@HiltViewModel
class ReceiptDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    receipts: ReceiptRepository,
    parties: PartyRepository,
) : ViewModel() {

    private val receiptId: Long = savedStateHandle.get<Long>("receiptId") ?: 0L

    val receipt: StateFlow<Receipt?> = receipts.read(receiptId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val partyName: StateFlow<String?> =
        combine(receipts.read(receiptId), parties.getAll()) { receipt, allParties ->
            receipt?.let { current -> allParties.firstOrNull { it.id == current.partyId }?.name }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ReceiptDetailScreen(navController: NavHostController, receiptId: Long) {
    val viewModel: ReceiptDetailViewModel = hiltViewModel()
    val receipt by viewModel.receipt.collectAsStateWithLifecycle()
    val partyName by viewModel.partyName.collectAsStateWithLifecycle()

    val current = receipt
    if (current == null) {
        EmptyState("That receipt isn't in the local copy yet.")
        return
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(current.no, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = listOfNotNull(formatDate(current.date), partyName).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // A receipt is money coming in, so it reads on the receivable side.
                AmountRow("Amount received", current.amount, emphasis = true)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = current.mode.name.lowercase().replace('_', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (current.notes.isNotBlank()) {
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(current.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
