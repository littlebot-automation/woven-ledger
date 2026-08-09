package com.wovenledger.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.wovenledger.app.data.api.SettingsApiService
import com.wovenledger.app.data.api.SettingsDto
import com.wovenledger.app.data.entities.Settings
import com.wovenledger.app.data.repository.SettingsRepository
import com.wovenledger.app.ui.components.ErrorState
import com.wovenledger.app.ui.components.LoadingState
import com.wovenledger.app.ui.components.MoneyText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Content(val settings: SettingsDto) : SettingsUiState
    data class Failed(val message: String) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val api: SettingsApiService,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = SettingsUiState.Loading

            runCatching { api.getSettings() }
                .onSuccess { dto ->
                    _state.value = SettingsUiState.Content(dto)
                    cacheLocally(dto)
                }
                .onFailure {
                    _state.value = SettingsUiState.Failed(
                        it.message ?: it::class.simpleName ?: "Couldn't load settings"
                    )
                }
        }
    }

    /**
     * Room has no columns for the prefixes, so only the rest is cached. The screen still
     * reads the API — this keeps the local row current for anything else that wants it.
     */
    private suspend fun cacheLocally(dto: SettingsDto) {
        settings.create(
            Settings(
                id = 1,
                companyName = dto.companyName.orEmpty(),
                address = dto.address.orEmpty(),
                phone = dto.phone.orEmpty(),
                gstin = dto.gstin.orEmpty(),
                defaultWage = dto.defaultWage ?: 0,
                currentPlantId = dto.currentPlantId ?: 1,
                nextInvoiceNo = dto.nextInvoiceNo ?: 1,
                nextPurchaseNo = dto.nextPurchaseNo ?: 1,
                nextReceiptNo = dto.nextReceiptNo ?: 1,
                nextPaymentNo = dto.nextPaymentNo ?: 1,
            )
        )
    }
}

@Composable
fun SettingsScreen(navController: NavHostController) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is SettingsUiState.Loading -> LoadingState()
        is SettingsUiState.Failed -> ErrorState(
            message = "Couldn't load settings (${current.message}).",
            onRetry = viewModel::load,
        )

        is SettingsUiState.Content -> SettingsBody(current.settings)
    }
}

@Composable
private fun SettingsBody(settings: SettingsDto) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsCard("Company") {
                LabelledValue("Name", settings.companyName)
                LabelledValue("GSTIN", settings.gstin, mono = true)
                LabelledValue("Phone", settings.phone)
                LabelledValue("Address", settings.address)
            }
        }

        item {
            SettingsCard("Document numbering") {
                NumberingRow("Sales invoice", settings.invoicePrefix, settings.nextInvoiceNo)
                NumberingRow("Purchase bill", settings.purchasePrefix, settings.nextPurchaseNo)
                NumberingRow("Receipt", settings.receiptPrefix, settings.nextReceiptNo)
                NumberingRow("Payment", settings.paymentPrefix, settings.nextPaymentNo)
            }
        }

        item {
            SettingsCard("Operations") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Default wage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MoneyText(settings.defaultWage ?: 0)
                }
                LabelledValue("Current plant", settings.currentPlantId?.toString())
            }
        }

        item {
            Text(
                text = "Settings are managed in the portal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            content()
        }
    }
}

/** The next number a document will take, shown the way it will actually be printed. */
@Composable
private fun NumberingRow(label: String, prefix: String?, next: Long?) {
    val preview = if (prefix != null && next != null) {
        prefix + next.toString().padStart(4, '0')
    } else {
        null
    }

    LabelledValue(label, preview, mono = true)
}

@Composable
private fun LabelledValue(label: String, value: String?, mono: Boolean = false) {
    if (value.isNullOrBlank()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
