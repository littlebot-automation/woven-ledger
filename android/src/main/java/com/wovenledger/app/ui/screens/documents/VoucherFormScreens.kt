package com.wovenledger.app.ui.screens.documents

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
import com.wovenledger.app.data.api.NewPayment
import com.wovenledger.app.data.api.NewReceipt
import com.wovenledger.app.data.entities.Party
import com.wovenledger.app.data.entities.PaymentMode
import com.wovenledger.app.data.entities.PaymentType
import com.wovenledger.app.data.entities.Staff
import com.wovenledger.app.data.repository.PartyRepository
import com.wovenledger.app.data.repository.PaymentRepository
import com.wovenledger.app.data.repository.ReceiptRepository
import com.wovenledger.app.data.repository.StaffRepository
import com.wovenledger.app.data.repository.toWire
import com.wovenledger.app.ui.components.DateField
import com.wovenledger.app.ui.components.FormScaffold
import com.wovenledger.app.ui.components.MoneyField
import com.wovenledger.app.ui.components.PickerField
import com.wovenledger.app.ui.components.QueuedBanner
import com.wovenledger.app.ui.components.failureMessage
import com.wovenledger.app.ui.components.fieldErrorsOf
import com.wovenledger.app.ui.components.paiseToTyped
import com.wovenledger.app.ui.components.rupeesToPaise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Receipts and payments: money in and money out.
 *
 * Neither moves stock, so unlike the document forms they have no lines and nothing
 * to warn about — but both take a document number, which only the server issues.
 */

/** Every mode the server's Receipt::MODES and Payment::MODES allow, in portal order. */
private val MODES = listOf(
    PaymentMode.CASH to "Cash",
    PaymentMode.BANK_TRANSFER to "Bank transfer",
    PaymentMode.UPI to "UPI",
    PaymentMode.CHEQUE to "Cheque",
)

// ------------------------------------------------------------------ Receipt

data class ReceiptForm(
    val date: LocalDate = LocalDate.now(),
    val partyId: Long? = null,
    val amount: String = "",
    val mode: PaymentMode = PaymentMode.CASH,
    val notes: String = "",
    val submitting: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val message: String? = null,
    val saved: Boolean = false,
    /** Saved on this phone only, waiting for a connection — and so without a number. */
    val queued: Boolean = false,
)

@HiltViewModel
class ReceiptFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val receipts: ReceiptRepository,
    parties: PartyRepository,
    private val gson: Gson,
) : ViewModel() {

    private val receiptId: Long? = savedStateHandle.get<Long>("receiptId")

    val editing: Boolean = receiptId != null

    private val _form = MutableStateFlow(ReceiptForm())
    val form: StateFlow<ReceiptForm> = _form.asStateFlow()

    /**
     * Only parties the server knows about: a receipt against a party that has not
     * uploaded yet would carry a negative local id the server cannot resolve.
     */
    val parties: StateFlow<List<Party>> = parties.getPostable()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        receiptId?.let { id ->
            viewModelScope.launch {
                receipts.read(id).first()?.let { receipt ->
                    _form.update {
                        it.copy(
                            date = receipt.date,
                            partyId = receipt.partyId,
                            amount = paiseToTyped(receipt.amount),
                            mode = receipt.mode,
                            notes = receipt.notes,
                        )
                    }
                }
            }
        }
    }

    fun onDateChange(value: LocalDate) = _form.update { it.copy(date = value) }

    fun onPartyChange(value: Long) = _form.update { it.copy(partyId = value, errors = it.errors - "party_id") }

    fun onAmountChange(value: String) = _form.update { it.copy(amount = value, errors = it.errors - "amount") }

    fun onModeChange(value: PaymentMode) = _form.update { it.copy(mode = value, errors = it.errors - "mode") }

    fun onNotesChange(value: String) = _form.update { it.copy(notes = value) }

    fun submit() {
        val current = _form.value
        val local = buildMap {
            if (current.partyId == null) put("party_id", "Choose a party")

            val paise = rupeesToPaise(current.amount)
            when {
                paise == null -> put("amount", "Enter an amount in rupees")
                paise <= 0 -> put("amount", "Enter an amount greater than zero")
            }
        }

        if (local.isNotEmpty()) {
            _form.update { it.copy(errors = local, message = null) }

            return
        }

        val body = NewReceipt(
            partyId = current.partyId!!.toInt(),
            amount = rupeesToPaise(current.amount)!!,
            receiptDate = current.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            mode = current.mode.toWire(),
            notes = current.notes.trim().ifBlank { null },
        )

        viewModelScope.launch {
            _form.update { it.copy(submitting = true, errors = emptyMap(), message = null) }

            runCatching {
                if (receiptId == null) {
                    receipts.createOnServer(body)
                } else {
                    receipts.updateOnServer(receiptId, body)
                }
            }.onSuccess { saved ->
                _form.update { it.copy(submitting = false, saved = true, queued = saved.queued) }
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
}

@Composable
fun ReceiptFormScreen(navController: NavHostController) {
    val viewModel: ReceiptFormViewModel = hiltViewModel()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val parties by viewModel.parties.collectAsStateWithLifecycle()

    LaunchedEffect(form.saved, form.queued) {
        if (form.saved && !form.queued) {
            navController.popBackStack()
        }
    }

    FormScaffold(
        submitting = form.submitting,
        saveLabel = if (viewModel.editing) "Save receipt" else "Record receipt",
        message = form.message,
        onSave = viewModel::submit,
        banner = {
            QueuedBanner(queued = form.queued, onDismiss = { navController.popBackStack() })
        },
    ) {
        DateField(
            date = form.date,
            error = form.errors["receipt_date"],
            onChange = viewModel::onDateChange,
        )

        PickerField(
            label = "Received from",
            selected = parties.firstOrNull { it.id == form.partyId }?.name,
            placeholder = "Choose a party",
            options = parties.map { it.id to it.name },
            error = form.errors["party_id"],
            onSelect = viewModel::onPartyChange,
        )

        MoneyField(
            label = "Amount (₹)",
            value = form.amount,
            error = form.errors["amount"],
            onChange = viewModel::onAmountChange,
        )

        PickerField(
            label = "Mode",
            selected = MODES.firstOrNull { it.first == form.mode }?.second,
            placeholder = "Cash",
            options = MODES,
            error = form.errors["mode"],
            onSelect = viewModel::onModeChange,
        )

        OutlinedTextField(
            value = form.notes,
            onValueChange = viewModel::onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notes") },
            minLines = 2,
        )
    }
}

// ------------------------------------------------------------------ Payment

data class PaymentForm(
    val date: LocalDate = LocalDate.now(),
    val type: PaymentType = PaymentType.PARTY,
    val partyId: Long? = null,
    val staffId: Long? = null,
    val amount: String = "",
    val mode: PaymentMode = PaymentMode.CASH,
    val notes: String = "",
    val submitting: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val message: String? = null,
    val saved: Boolean = false,
    /** Saved on this phone only, waiting for a connection — and so without a number. */
    val queued: Boolean = false,
)

private val PAYMENT_TYPES = listOf(
    PaymentType.PARTY to "To a party",
    PaymentType.STAFF to "To staff (wages)",
)

@HiltViewModel
class PaymentFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val payments: PaymentRepository,
    parties: PartyRepository,
    staff: StaffRepository,
    private val gson: Gson,
) : ViewModel() {

    private val paymentId: Long? = savedStateHandle.get<Long>("paymentId")

    val editing: Boolean = paymentId != null

    private val _form = MutableStateFlow(PaymentForm())
    val form: StateFlow<PaymentForm> = _form.asStateFlow()

    /**
     * Only parties the server knows about: a receipt against a party that has not
     * uploaded yet would carry a negative local id the server cannot resolve.
     */
    val parties: StateFlow<List<Party>> = parties.getPostable()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val staff: StateFlow<List<Staff>> = staff.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        paymentId?.let { id ->
            viewModelScope.launch {
                payments.read(id).first()?.let { payment ->
                    _form.update {
                        it.copy(
                            date = payment.date,
                            type = payment.type,
                            partyId = payment.partyId,
                            staffId = payment.staffId,
                            amount = paiseToTyped(payment.amount),
                            mode = payment.mode,
                            notes = payment.notes,
                        )
                    }
                }
            }
        }
    }

    fun onDateChange(value: LocalDate) = _form.update { it.copy(date = value) }

    /** Switching sides clears the other one: a voucher pays a party or staff, never both. */
    fun onTypeChange(value: PaymentType) = _form.update {
        it.copy(
            type = value,
            partyId = if (value == PaymentType.PARTY) it.partyId else null,
            staffId = if (value == PaymentType.STAFF) it.staffId else null,
            errors = it.errors - "payment_type" - "party_id" - "staff_id",
        )
    }

    fun onPartyChange(value: Long) = _form.update { it.copy(partyId = value, errors = it.errors - "party_id") }

    fun onStaffChange(value: Long) = _form.update { it.copy(staffId = value, errors = it.errors - "staff_id") }

    fun onAmountChange(value: String) = _form.update { it.copy(amount = value, errors = it.errors - "amount") }

    fun onModeChange(value: PaymentMode) = _form.update { it.copy(mode = value, errors = it.errors - "mode") }

    fun onNotesChange(value: String) = _form.update { it.copy(notes = value) }

    fun submit() {
        val current = _form.value
        val local = buildMap {
            if (current.type == PaymentType.PARTY && current.partyId == null) {
                put("party_id", "Choose a party")
            }
            if (current.type == PaymentType.STAFF && current.staffId == null) {
                put("staff_id", "Choose a staff member")
            }

            val paise = rupeesToPaise(current.amount)
            when {
                paise == null -> put("amount", "Enter an amount in rupees")
                paise <= 0 -> put("amount", "Enter an amount greater than zero")
            }
        }

        if (local.isNotEmpty()) {
            _form.update { it.copy(errors = local, message = null) }

            return
        }

        val body = NewPayment(
            partyId = current.partyId?.toInt().takeIf { current.type == PaymentType.PARTY },
            staffId = current.staffId?.toInt().takeIf { current.type == PaymentType.STAFF },
            amount = rupeesToPaise(current.amount)!!,
            paymentDate = current.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            paymentType = current.type.name.lowercase(),
            mode = current.mode.toWire(),
            notes = current.notes.trim().ifBlank { null },
        )

        viewModelScope.launch {
            _form.update { it.copy(submitting = true, errors = emptyMap(), message = null) }

            runCatching {
                if (paymentId == null) {
                    payments.createOnServer(body)
                } else {
                    payments.updateOnServer(paymentId, body)
                }
            }.onSuccess { saved ->
                _form.update { it.copy(submitting = false, saved = true, queued = saved.queued) }
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
}

@Composable
fun PaymentFormScreen(navController: NavHostController) {
    val viewModel: PaymentFormViewModel = hiltViewModel()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val parties by viewModel.parties.collectAsStateWithLifecycle()
    val staff by viewModel.staff.collectAsStateWithLifecycle()

    LaunchedEffect(form.saved, form.queued) {
        if (form.saved && !form.queued) {
            navController.popBackStack()
        }
    }

    FormScaffold(
        submitting = form.submitting,
        saveLabel = if (viewModel.editing) "Save payment" else "Record payment",
        message = form.message,
        onSave = viewModel::submit,
        banner = {
            QueuedBanner(queued = form.queued, onDismiss = { navController.popBackStack() })
        },
    ) {
        DateField(
            date = form.date,
            error = form.errors["payment_date"],
            onChange = viewModel::onDateChange,
        )

        PickerField(
            label = "Paid to",
            selected = PAYMENT_TYPES.firstOrNull { it.first == form.type }?.second,
            placeholder = "To a party",
            options = PAYMENT_TYPES,
            error = form.errors["payment_type"],
            onSelect = viewModel::onTypeChange,
        )

        // Only the side the type selects is shown; the other is not merely disabled,
        // because a voucher that carried both would be ambiguous in the ledger.
        if (form.type == PaymentType.PARTY) {
            PickerField(
                label = "Party",
                selected = parties.firstOrNull { it.id == form.partyId }?.name,
                placeholder = "Choose a party",
                options = parties.map { it.id to it.name },
                error = form.errors["party_id"],
                onSelect = viewModel::onPartyChange,
            )
        } else {
            PickerField(
                label = "Staff member",
                selected = staff.firstOrNull { it.id == form.staffId }?.name,
                placeholder = "Choose a staff member",
                options = staff.map { it.id to it.name },
                error = form.errors["staff_id"],
                onSelect = viewModel::onStaffChange,
            )
        }

        MoneyField(
            label = "Amount (₹)",
            value = form.amount,
            error = form.errors["amount"],
            onChange = viewModel::onAmountChange,
        )

        PickerField(
            label = "Mode",
            selected = MODES.firstOrNull { it.first == form.mode }?.second,
            placeholder = "Cash",
            options = MODES,
            error = form.errors["mode"],
            onSelect = viewModel::onModeChange,
        )

        OutlinedTextField(
            value = form.notes,
            onValueChange = viewModel::onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notes") },
            minLines = 2,
        )
    }
}
