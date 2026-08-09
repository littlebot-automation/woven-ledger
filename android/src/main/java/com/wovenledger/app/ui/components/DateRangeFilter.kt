package com.wovenledger.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * An inclusive date window. Either end may be open, so "everything since April" and
 * "everything up to today" are both expressible without a sentinel date.
 */
data class DateRange(val from: LocalDate? = null, val to: LocalDate? = null) {

    val isActive: Boolean get() = from != null || to != null

    fun contains(date: LocalDate): Boolean =
        (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))

    /**
     * Keeps the window coherent when one end crosses the other: picking a "from" after
     * the current "to" moves "to" along with it, rather than leaving a range that can
     * never match anything.
     */
    fun withFrom(value: LocalDate): DateRange =
        DateRange(value, if (to != null && to.isBefore(value)) value else to)

    fun withTo(value: LocalDate): DateRange =
        DateRange(if (from != null && from.isAfter(value)) value else from, value)
}

/** Two chips that open date pickers, and a clear button once the window is set. */
@Composable
fun DateRangeFilter(
    range: DateRange,
    onChange: (DateRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf<Edge?>(null) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = range.from != null,
            onClick = { picking = Edge.From },
            label = { Text(range.from?.let { "From ${formatDate(it)}" } ?: "From") },
        )
        FilterChip(
            selected = range.to != null,
            onClick = { picking = Edge.To },
            label = { Text(range.to?.let { "To ${formatDate(it)}" } ?: "To") },
        )

        if (range.isActive) {
            IconButton(onClick = { onChange(DateRange()) }) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Clear date filter",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    picking?.let { edge ->
        val current = if (edge == Edge.From) range.from else range.to
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (current ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(
                            if (edge == Edge.From) range.withFrom(picked) else range.withTo(picked)
                        )
                    }
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = {
                // Clearing one end is only reachable from inside the picker; the chip
                // itself opens it.
                Row {
                    if (current != null) {
                        TextButton(onClick = {
                            onChange(
                                if (edge == Edge.From) range.copy(from = null) else range.copy(to = null)
                            )
                            picking = null
                        }) { Text("Clear") }
                    }
                    TextButton(onClick = { picking = null }) { Text("Cancel") }
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

private enum class Edge { From, To }

/** A running total for whatever the filter left on screen. */
@Composable
fun FilterSummary(count: Int, total: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count == 1) "1 document" else "$count documents",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyText(total, emphasis = true)
    }
}
