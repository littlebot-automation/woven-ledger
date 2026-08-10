package com.wovenledger.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The parts every create and edit form is built from.
 *
 * They live here rather than beside one screen because the six forms differ only in
 * their fields: all of them post online, disable their button while in flight, key
 * their errors by the server's field names, and pin the save button above the fold.
 */

/**
 * Typed rupees to integer paise, the only unit the wire and Room use.
 *
 * Held as text while typing so a half-entered "12." need not round-trip through a
 * number; null means blank or not a number, which the caller decides how to treat.
 */
fun rupeesToPaise(typed: String): Long? {
    if (typed.isBlank()) {
        return null
    }

    return runCatching {
        BigDecimal(typed.trim()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }.getOrNull()
}

/** Paise back to the plain rupee text a field is edited as — no symbol, no grouping. */
fun paiseToTyped(paise: Long): String =
    BigDecimal(paise).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

@Composable
fun DateField(
    date: LocalDate,
    error: String?,
    onChange: (LocalDate) -> Unit,
    label: String = "Date",
) {
    var picking by remember { mutableStateOf(false) }

    Box {
        OutlinedTextField(
            value = formatDate(date),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            readOnly = true,
            isError = error != null,
            supportingText = { if (error != null) Text(error) },
        )
        // A read-only field still swallows taps, so the whole row opens the picker.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { picking = true }
        )
    }

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/** A read-only field backed by a dropdown — every party, item, plant and mode chooser. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PickerField(
    label: String,
    selected: String?,
    placeholder: String,
    options: List<Pair<T, String>>,
    error: String?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected ?: placeholder,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                // MenuAnchorType arrived after the Compose BOM this project pins
                // (2024.09.00 / material3 1.3.0), so the no-arg anchor is what exists here.
                .menuAnchor(),
            label = { Text(label) },
            readOnly = true,
            isError = error != null,
            supportingText = { if (error != null) Text(error) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** A rupee amount, typed. Converted to paise once, at the point of sending. */
@Composable
fun MoneyField(
    label: String,
    value: String,
    error: String?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = error != null,
        supportingText = { (error ?: hint)?.let { Text(it) } },
    )
}

/**
 * A form's frame: fields that scroll, and an action that does not.
 *
 * Saving must never require scrolling to find the button, and the button is disabled
 * while the request is in flight so a slow connection cannot produce two documents.
 */
@Composable
fun FormScaffold(
    submitting: Boolean,
    saveLabel: String,
    message: String?,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    banner: (@Composable () -> Unit)? = null,
    fields: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        banner?.invoke()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = fields,
        )

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !submitting,
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Saving…")
                    } else {
                        Text(saveLabel)
                    }
                }
            }
        }
    }
}

/**
 * Says that a save was written down here rather than sent.
 *
 * The record exists and will upload by itself, but two things about it are not true
 * yet and saying so is the honest part: it has no document number, because only the
 * server issues those, and nothing has checked it against stock, because stock is the
 * server's figure too. Quietly returning to the list as though this were an ordinary
 * save would leave the user to discover both later.
 */
@Composable
fun QueuedBanner(queued: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (!queued) {
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Saved on this phone",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "There was no connection, so it is waiting to upload. " +
                    "It gets its number, and its stock check, when it reaches the server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    }
}

/**
 * What the server warned about while saving successfully.
 *
 * Stock shortfalls are warnings, never errors — the portal lets a sale go through
 * short and the phone must not be stricter — so this reports rather than blocks,
 * and the document is already saved by the time it appears.
 */
@Composable
fun WarningBanner(warnings: List<String>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (warnings.isEmpty()) {
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Saved, with warnings",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            warnings.forEach { warning ->
                Text(
                    text = "• $warning",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    }
}
