package com.wovenledger.app.ui.screens.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.wovenledger.app.data.api.LedgerApiService
import com.wovenledger.app.data.api.LedgerEntryDto
import com.wovenledger.app.data.api.PartyLedgerDto
import com.wovenledger.app.ui.components.DocumentNumberText
import com.wovenledger.app.ui.components.EmptyState
import com.wovenledger.app.ui.components.ErrorState
import com.wovenledger.app.ui.components.LoadingState
import com.wovenledger.app.ui.components.MoneyTone
import com.wovenledger.app.ui.components.MoneyText
import com.wovenledger.app.ui.components.formatDate
import com.wovenledger.app.ui.components.formatMoney
import com.wovenledger.app.ui.components.moneyToneColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.absoluteValue

/**
 * The party ledger — spec §4.4.
 *
 * Deliberately server-only: the ledger is derived from every document touching a party,
 * so a Room copy would go stale the moment any one of them changed. The screen reads the
 * API on each visit and says so plainly when it cannot.
 */
sealed interface PartyLedgerUiState {
    data object Loading : PartyLedgerUiState
    data class Content(val ledger: PartyLedgerDto) : PartyLedgerUiState
    data class Failed(val message: String) : PartyLedgerUiState
}

@HiltViewModel
class PartyLedgerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: LedgerApiService,
) : ViewModel() {

    /**
     * The nav argument. Read tolerantly because a route may declare it as a Long or leave
     * it as the raw path string — a mismatch here would otherwise be a cast crash rather
     * than a screen that simply reports it could not load.
     */
    private val partyId: Long = when (val raw = savedStateHandle.get<Any?>("partyId")) {
        is Long -> raw
        is Int -> raw.toLong()
        is String -> raw.toLongOrNull() ?: 0L
        else -> 0L
    }

    private val _state = MutableStateFlow<PartyLedgerUiState>(PartyLedgerUiState.Loading)
    val state: StateFlow<PartyLedgerUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = PartyLedgerUiState.Loading

            runCatching { api.getPartyLedger(partyId) }
                .onSuccess { _state.value = PartyLedgerUiState.Content(it) }
                .onFailure {
                    _state.value = PartyLedgerUiState.Failed(
                        it.message ?: it::class.simpleName ?: "Couldn't load the ledger"
                    )
                }
        }
    }
}

@Composable
fun PartyLedgerScreen(navController: NavHostController, partyId: Long) {
    val viewModel: PartyLedgerViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is PartyLedgerUiState.Loading -> LoadingState()
        is PartyLedgerUiState.Failed -> ErrorState(
            message = "Couldn't load the ledger (${current.message}).",
            onRetry = viewModel::load,
        )

        is PartyLedgerUiState.Content -> LedgerBody(current.ledger)
    }
}

@Composable
private fun LedgerBody(ledger: PartyLedgerDto) {
    Column(modifier = Modifier.fillMaxSize()) {
        ClosingBalanceCard(
            partyName = ledger.partyName,
            closingBalance = ledger.closingBalance,
            modifier = Modifier.padding(16.dp),
        )

        if (ledger.entries.isEmpty()) {
            EmptyState(
                message = "No invoices, bills, receipts or payments for this party yet.",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The server orders these oldest first and the running balance only reads
                // correctly in that order, so the list is rendered exactly as it arrives —
                // hence the positional key.
                itemsIndexed(ledger.entries, key = { index, _ -> index }) { _, entry ->
                    LedgerEntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun ClosingBalanceCard(partyName: String, closingBalance: Long, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(partyName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Closing balance",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = directionLabel(closingBalance),
                    style = MaterialTheme.typography.bodyMedium,
                    color = moneyToneColor(toneOf(closingBalance)),
                )
                // Unsigned: the direction is the caption's job, so nothing reads as a
                // minus sign whose meaning depends on which side of the account you are on.
                MoneyText(
                    paise = closingBalance.absoluteValue,
                    emphasis = true,
                    tone = toneOf(closingBalance),
                )
            }
        }
    }
}

@Composable
private fun LedgerEntryRow(entry: LedgerEntryDto) {
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
                Text(displayDate(entry.date), style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.type,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // "-" is what the opening balance carries: it has no document to name.
                    if (entry.reference.isNotBlank() && entry.reference != "-") {
                        DocumentNumberText(entry.reference)
                    }
                }
            }

            Column(
                modifier = Modifier.padding(start = 12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Dr / Cr rather than a bare sign: this is a running account, and a
                    // trader reads "Dr 9,920" as "it went onto their account" instantly.
                    Text(
                        text = if (entry.amount < 0L) "Cr" else "Dr",
                        style = MaterialTheme.typography.labelSmall,
                        color = moneyToneColor(toneOf(entry.amount)),
                    )
                    MoneyText(paise = entry.amount.absoluteValue, tone = toneOf(entry.amount))
                }
                Text(
                    text = "${balanceCaption(entry.balance)} ${formattedBalance(entry.balance)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = moneyToneColor(toneOf(entry.balance)),
                )
            }
        }
    }
}

/**
 * Positive means they owe us, negative means we owe them — the app's single most
 * important convention, carried unchanged from the server.
 */
private fun toneOf(paise: Long): MoneyTone = when {
    paise > 0L -> MoneyTone.Receive
    paise < 0L -> MoneyTone.Pay
    else -> MoneyTone.Neutral
}

private fun directionLabel(paise: Long): String = when {
    paise > 0L -> "They owe us"
    paise < 0L -> "We owe them"
    else -> "Settled"
}

private fun balanceCaption(paise: Long): String = when {
    paise > 0L -> "Bal Dr"
    paise < 0L -> "Bal Cr"
    else -> "Bal"
}

/** Never hand-formats currency: [formatMoney] owns that, here as in every screen. */
private fun formattedBalance(paise: Long): String = formatMoney(paise.absoluteValue)

/** The wire carries 'yyyy-MM-dd'; anything unparseable is shown as it arrived. */
private fun displayDate(raw: String): String =
    runCatching { formatDate(LocalDate.parse(raw)) }.getOrDefault(raw)
