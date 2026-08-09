package com.wovenledger.app.ui.screens.parties

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.google.gson.Gson
import com.wovenledger.app.data.api.NewParty
import com.wovenledger.app.data.entities.BalanceType
import com.wovenledger.app.data.entities.PartyType
import com.wovenledger.app.data.repository.PartyRepository
import com.wovenledger.app.ui.components.FormScaffold
import com.wovenledger.app.ui.components.MoneyField
import com.wovenledger.app.ui.components.PickerField
import com.wovenledger.app.ui.components.failureMessage
import com.wovenledger.app.ui.components.fieldErrorsOf
import com.wovenledger.app.ui.components.paiseToTyped
import com.wovenledger.app.ui.components.rupeesToPaise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The party form, used for both a new party and an existing one.
 *
 * Opening balance is the only figure here that is editable — the running ledger
 * balance the lists show is derived by the portal from every document on the
 * party's account, and is never written back.
 */
data class PartyForm(
    val name: String = "",
    val type: PartyType = PartyType.CUSTOMER,
    val phone: String = "",
    val gstin: String = "",
    val address: String = "",
    val openingBalance: String = "",
    val openingType: BalanceType = BalanceType.TO_RECEIVE,
    val submitting: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val message: String? = null,
    val saved: Boolean = false,
)

/** The wire values for Party::TYPES, paired with what the form shows. */
private val PARTY_TYPES = listOf(
    PartyType.CUSTOMER to "Customer",
    PartyType.SUPPLIER to "Supplier",
    PartyType.BOTH to "Both",
)

private val BALANCE_TYPES = listOf(
    BalanceType.TO_RECEIVE to "To receive (they owe us)",
    BalanceType.TO_PAY to "To pay (we owe them)",
)

@HiltViewModel
class PartyFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val parties: PartyRepository,
    private val gson: Gson,
) : ViewModel() {

    /** Null on the create route, which has no id in its path. */
    private val partyId: Long? = savedStateHandle.get<Long>("partyId")

    val editing: Boolean = partyId != null

    private val _form = MutableStateFlow(PartyForm())
    val form: StateFlow<PartyForm> = _form.asStateFlow()

    init {
        // An edit starts from what the phone already synced, so the form opens filled
        // rather than blank — a blank edit form would silently erase whatever it did
        // not show.
        partyId?.let { id ->
            viewModelScope.launch {
                parties.read(id).first()?.let { party ->
                    _form.update {
                        it.copy(
                            name = party.name,
                            type = party.type,
                            phone = party.phone,
                            gstin = party.gstin.orEmpty(),
                            address = party.address,
                            openingBalance = paiseToTyped(party.openingBalance),
                            openingType = party.openingBalanceType,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _form.update { it.copy(name = value, errors = it.errors - "name") }

    fun onTypeChange(value: PartyType) = _form.update { it.copy(type = value, errors = it.errors - "type") }

    fun onPhoneChange(value: String) = _form.update { it.copy(phone = value, errors = it.errors - "phone") }

    fun onGstinChange(value: String) = _form.update { it.copy(gstin = value) }

    fun onAddressChange(value: String) = _form.update { it.copy(address = value) }

    fun onOpeningBalanceChange(value: String) =
        _form.update { it.copy(openingBalance = value, errors = it.errors - "opening_balance") }

    fun onOpeningTypeChange(value: BalanceType) = _form.update { it.copy(openingType = value) }

    /** Posts and waits: there is no queue, so a failure means nothing was saved. */
    fun submit() {
        val current = _form.value
        val local = validate(current)

        if (local.isNotEmpty()) {
            _form.update { it.copy(errors = local, message = null) }

            return
        }

        val body = NewParty(
            name = current.name.trim(),
            type = current.type.name.lowercase(),
            phone = current.phone.trim().ifBlank { null },
            gstNumber = current.gstin.trim().ifBlank { null },
            address = current.address.trim().ifBlank { null },
            openingBalance = rupeesToPaise(current.openingBalance) ?: 0L,
            openingBalanceType = if (current.openingType == BalanceType.TO_PAY) "to_pay" else "to_receive",
        )

        viewModelScope.launch {
            _form.update { it.copy(submitting = true, errors = emptyMap(), message = null) }

            runCatching {
                if (partyId == null) {
                    parties.createOnServer(body)
                } else {
                    parties.updateOnServer(partyId, body)
                }
            }.onSuccess {
                _form.update { it.copy(submitting = false, saved = true) }
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

    private fun validate(form: PartyForm): Map<String, String> = buildMap {
        if (form.name.isBlank()) put("name", "Party name is required")

        if (form.openingBalance.isNotBlank() && rupeesToPaise(form.openingBalance) == null) {
            put("opening_balance", "Enter an amount in rupees")
        }
    }
}

@Composable
fun PartyFormScreen(navController: NavHostController) {
    val viewModel: PartyFormViewModel = hiltViewModel()
    val form by viewModel.form.collectAsStateWithLifecycle()

    // The list behind this screen observes Room, and the save already wrote there,
    // so returning is all that is left to do.
    LaunchedEffect(form.saved) {
        if (form.saved) {
            navController.popBackStack()
        }
    }

    FormScaffold(
        submitting = form.submitting,
        saveLabel = if (viewModel.editing) "Save changes" else "Add party",
        message = form.message,
        onSave = viewModel::submit,
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = viewModel::onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") },
            singleLine = true,
            isError = form.errors.containsKey("name"),
            supportingText = { form.errors["name"]?.let { Text(it) } },
        )

        PickerField(
            label = "Type",
            selected = PARTY_TYPES.firstOrNull { it.first == form.type }?.second,
            placeholder = "Customer",
            options = PARTY_TYPES,
            error = form.errors["type"],
            onSelect = viewModel::onTypeChange,
        )

        OutlinedTextField(
            value = form.phone,
            onValueChange = viewModel::onPhoneChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Phone") },
            singleLine = true,
            isError = form.errors.containsKey("phone"),
            supportingText = { form.errors["phone"]?.let { Text(it) } },
        )

        OutlinedTextField(
            value = form.gstin,
            onValueChange = viewModel::onGstinChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("GSTIN") },
            singleLine = true,
            isError = form.errors.containsKey("gst_number"),
            supportingText = { Text(form.errors["gst_number"] ?: "Optional") },
        )

        OutlinedTextField(
            value = form.address,
            onValueChange = viewModel::onAddressChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Address") },
            minLines = 2,
            isError = form.errors.containsKey("address"),
            supportingText = { form.errors["address"]?.let { Text(it) } },
        )

        MoneyField(
            label = "Opening balance (₹)",
            value = form.openingBalance,
            error = form.errors["opening_balance"],
            onChange = viewModel::onOpeningBalanceChange,
            hint = "What they owed, or were owed, before the ledger starts",
        )

        PickerField(
            label = "Opening balance direction",
            selected = BALANCE_TYPES.firstOrNull { it.first == form.openingType }?.second,
            placeholder = "To receive (they owe us)",
            options = BALANCE_TYPES,
            error = form.errors["opening_balance_type"],
            onSelect = viewModel::onOpeningTypeChange,
        )
    }
}
