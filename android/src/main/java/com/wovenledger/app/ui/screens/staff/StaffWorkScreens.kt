package com.wovenledger.app.ui.screens.staff

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.google.gson.Gson
import com.wovenledger.app.data.api.NewStaffWork
import com.wovenledger.app.data.entities.Plant
import com.wovenledger.app.data.entities.Staff
import com.wovenledger.app.data.entities.StaffWork
import com.wovenledger.app.data.repository.PlantRepository
import com.wovenledger.app.data.repository.StaffRepository
import com.wovenledger.app.data.repository.StaffWorkRepository
import com.wovenledger.app.ui.components.AmountRow
import com.wovenledger.app.ui.components.DateField
import com.wovenledger.app.ui.components.EmptyState
import com.wovenledger.app.ui.components.FormScaffold
import com.wovenledger.app.ui.components.MoneyField
import com.wovenledger.app.ui.components.PickerField
import com.wovenledger.app.ui.components.QueuedBanner
import com.wovenledger.app.ui.components.failureMessage
import com.wovenledger.app.ui.components.fieldErrorsOf
import com.wovenledger.app.ui.components.paiseToTyped
import com.wovenledger.app.ui.components.rupeesToPaise
import com.wovenledger.app.ui.navigation.NavigationRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// ------------------------------------------------------------------ List

/** A work entry with the staff member's name resolved for display. */
data class StaffWorkRow(
    val work: StaffWork,
    val staffName: String,
)

@HiltViewModel
class StaffWorkListViewModel @Inject constructor(
    staffWork: StaffWorkRepository,
    staff: StaffRepository,
) : ViewModel() {

    private val _unpaidOnly = MutableStateFlow(false)
    val unpaidOnly: StateFlow<Boolean> = _unpaidOnly.asStateFlow()

    val rows: StateFlow<List<StaffWorkRow>> = combine(
        staffWork.getAll(),
        staff.getAll(),
        _unpaidOnly,
    ) { entries, members, unpaidOnly ->
        val names = members.associate { it.id to it.name }

        entries
            .filter { !unpaidOnly || !it.paid }
            .map { entry -> StaffWorkRow(entry, names[entry.staffId] ?: "Staff #${entry.staffId}") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Total of whatever is on screen — with the filter on, that is what we owe. */
    val total: StateFlow<Long> = rows
        .map { shown -> shown.sumOf { it.work.getAmount() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun onUnpaidOnlyChange(value: Boolean) {
        _unpaidOnly.value = value
    }
}

@Composable
fun StaffWorkListScreen(navController: NavHostController) {
    val viewModel: StaffWorkListViewModel = hiltViewModel()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val unpaidOnly by viewModel.unpaidOnly.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = unpaidOnly,
                    onClick = { viewModel.onUnpaidOnlyChange(!unpaidOnly) },
                    label = { Text("Unpaid only") },
                )
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                AmountRow(
                    label = if (unpaidOnly) "Outstanding wages" else "All recorded work",
                    paise = total,
                    emphasis = true,
                )
                HorizontalDivider()
            }

            if (rows.isEmpty()) {
                EmptyState(
                    if (unpaidOnly) {
                        "Nothing outstanding. Every entry has been settled."
                    } else {
                        "No work recorded yet. Pull the latest from the server, or add an entry."
                    }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.work.id }) { row ->
                        WorkEntryCard(
                            entry = row.work,
                            subtitle = "${row.staffName} · ${row.work.workType}",
                            // Settled entries are frozen — their amount is already inside
                            // a wage voucher — so only unpaid ones open for correction.
                            onClick = if (row.work.paid) {
                                null
                            } else {
                                {
                                    navController.navigate(
                                        "${NavigationRoutes.STAFF_WORK_EDIT_BASE}/${row.work.id}"
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { navController.navigate(NavigationRoutes.STAFF_WORK_CREATE) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Record work")
        }
    }
}

// ------------------------------------------------------------------ Form

/**
 * The work-entry form, mirroring the portal's, for both a new entry and a
 * correction to an existing one.
 *
 * Rate is held as typed rupees rather than paise so a half-typed "12." does not have
 * to round-trip through a number; it converts at the boundary.
 */
data class StaffWorkForm(
    val date: LocalDate = LocalDate.now(),
    val staffId: Long? = null,
    val plantId: Long? = null,
    val workType: String = "",
    val qty: String = "",
    val rate: String = "",
    val submitting: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val message: String? = null,
    val saved: Boolean = false,
    /** Saved on this phone only, waiting for a connection. */
    val queued: Boolean = false,
)

@HiltViewModel
class StaffWorkFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val staffWork: StaffWorkRepository,
    private val gson: Gson,
    staffRepository: StaffRepository,
    plantRepository: PlantRepository,
) : ViewModel() {

    /** Null on the create route, which carries no id. */
    private val workId: Long? = savedStateHandle.get<Long>("workId")

    val editing: Boolean = workId != null

    private val _form = MutableStateFlow(StaffWorkForm())
    val form: StateFlow<StaffWorkForm> = _form.asStateFlow()

    val staff: StateFlow<List<Staff>> = staffRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val plants: StateFlow<List<Plant>> = plantRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Live preview only. The server computes the stored amount, so nothing here is
     * ever sent.
     */
    val amount: StateFlow<Long> = combine(_form, staffRepository.getAll()) { form, members ->
        val qty = form.qty.toDoubleOrNull() ?: 0.0
        val rate = rupeesToPaise(form.rate)
            ?: members.firstOrNull { it.id == form.staffId }?.getEffectiveRate()
            ?: 0L

        (qty * rate).toLong()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    init {
        workId?.let { id ->
            viewModelScope.launch {
                staffWork.read(id).first()?.let { entry ->
                    _form.update {
                        it.copy(
                            date = entry.date,
                            staffId = entry.staffId,
                            plantId = entry.plantId,
                            workType = entry.workType,
                            qty = formatQty(entry.qty),
                            rate = paiseToTyped(entry.rate),
                        )
                    }
                }
            }
        }
    }

    fun onDateChange(value: LocalDate) = _form.update { it.copy(date = value, errors = it.errors - "date") }

    fun onStaffChange(value: Long) = _form.update { it.copy(staffId = value, errors = it.errors - "staff_id") }

    fun onPlantChange(value: Long?) = _form.update { it.copy(plantId = value, errors = it.errors - "plant_id") }

    fun onWorkTypeChange(value: String) = _form.update { it.copy(workType = value, errors = it.errors - "work_type") }

    fun onQtyChange(value: String) = _form.update { it.copy(qty = value, errors = it.errors - "qty") }

    fun onRateChange(value: String) = _form.update { it.copy(rate = value, errors = it.errors - "rate") }

    /**
     * Waits for the server, and writes the entry down here if there is no connection.
     *
     * The server's own field errors still surface on a 422 — including its refusal to
     * touch an entry a wage voucher has already settled — because a refusal is a
     * decision, not a connection problem, and queueing it would retry it forever.
     */
    fun submit() {
        val current = _form.value
        val local = validate(current)

        if (local.isNotEmpty()) {
            _form.update { it.copy(errors = local, message = null) }

            return
        }

        val body = NewStaffWork(
            date = current.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
            staffId = current.staffId!!.toInt(),
            plantId = current.plantId?.toInt(),
            workType = current.workType.trim(),
            qty = current.qty.toDouble(),
            rate = rupeesToPaise(current.rate),
        )

        viewModelScope.launch {
            _form.update { it.copy(submitting = true, errors = emptyMap(), message = null) }

            runCatching {
                if (workId == null) {
                    staffWork.createOnServer(body)
                } else {
                    staffWork.updateOnServer(workId, body)
                }
            }.onSuccess { saved ->
                _form.update { it.copy(submitting = false, saved = true, queued = saved.queued) }
            }.onFailure { cause ->
                val fields = fieldErrorsOf(cause, gson)
                _form.update {
                    it.copy(
                        submitting = false,
                        errors = fields,
                        message = if (fields.isEmpty()) failureMessage(cause) else fields["paid"],
                    )
                }
            }
        }
    }

    private fun validate(form: StaffWorkForm): Map<String, String> = buildMap {
        if (form.staffId == null) put("staff_id", "Pick a staff member")
        if (form.workType.isBlank()) put("work_type", "Work type is required")

        val qty = form.qty.toDoubleOrNull()
        when {
            form.qty.isBlank() || qty == null -> put("qty", "Qty is required")
            qty <= 0 -> put("qty", "Qty must be greater than zero")
        }

        if (form.rate.isNotBlank() && rupeesToPaise(form.rate) == null) {
            put("rate", "Rate must be an amount in rupees")
        }
    }
}

@Composable
fun StaffWorkFormScreen(navController: NavHostController) {
    val viewModel: StaffWorkFormViewModel = hiltViewModel()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val staff by viewModel.staff.collectAsStateWithLifecycle()
    val plants by viewModel.plants.collectAsStateWithLifecycle()
    val amount by viewModel.amount.collectAsStateWithLifecycle()

    // The list behind this screen refreshes from the next sync, so saving just returns —
    // unless the entry was queued, which the user is told about before leaving.
    LaunchedEffect(form.saved, form.queued) {
        if (form.saved && !form.queued) {
            navController.popBackStack()
        }
    }

    FormScaffold(
        submitting = form.submitting,
        saveLabel = if (viewModel.editing) "Save work entry" else "Add work entry",
        message = form.message,
        onSave = viewModel::submit,
        banner = {
            QueuedBanner(queued = form.queued, onDismiss = { navController.popBackStack() })
        },
    ) {
        DateField(
            date = form.date,
            error = form.errors["date"],
            onChange = viewModel::onDateChange,
        )

        PickerField(
            label = "Staff member",
            selected = staff.firstOrNull { it.id == form.staffId }?.name,
            placeholder = "Choose a staff member",
            options = staff.map { it.id to it.name },
            error = form.errors["staff_id"],
            onSelect = { viewModel.onStaffChange(it) },
        )

        PickerField(
            label = "Plant",
            selected = plants.firstOrNull { it.id == form.plantId }?.name,
            placeholder = "None",
            // Work need not belong to a plant, so "None" is a real choice here.
            options = listOf<Pair<Long?, String>>(null to "None") + plants.map { it.id to it.name },
            error = form.errors["plant_id"],
            onSelect = { viewModel.onPlantChange(it) },
        )

        OutlinedTextField(
            value = form.workType,
            onValueChange = viewModel::onWorkTypeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Work type") },
            singleLine = true,
            isError = form.errors.containsKey("work_type"),
            supportingText = {
                Text(form.errors["work_type"] ?: "e.g. Stitching, Loading, Lamination")
            },
        )

        OutlinedTextField(
            value = form.qty,
            onValueChange = viewModel::onQtyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Qty") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = form.errors.containsKey("qty"),
            supportingText = {
                Text(form.errors["qty"] ?: "Days worked, or pieces produced")
            },
        )

        MoneyField(
            label = "Rate (₹)",
            value = form.rate,
            error = form.errors["rate"],
            onChange = viewModel::onRateChange,
            hint = "Leave blank to use the staff member's wage rate — or type one to override it",
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Derived, never sent: the server computes what it stores.
                AmountRow("Amount", amount, emphasis = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = false, onCheckedChange = null, enabled = false)
                    Column {
                        Text("Paid", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Set by wage settlement",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
