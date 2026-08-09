package com.wovenledger.app.ui.screens.stock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.wovenledger.app.data.api.StockRowDto
import com.wovenledger.app.data.repository.ItemStockRepository
import com.wovenledger.app.ui.components.EmptyState
import com.wovenledger.app.ui.components.ErrorState
import com.wovenledger.app.ui.components.LoadingState
import com.wovenledger.app.ui.navigation.NavigationRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

// ------------------------------------------------------------------ Quantities
//
// Stock is measured, not priced. Nothing on these screens goes through MoneyText or
// formatMoney: those divide by 100 and prepend a rupee sign, which would report 1,200
// kilograms as ₹12.00. Quantities keep three decimals because that is the precision
// the server stores them at.

private val INDIA = Locale("en", "IN")

private fun formatQty(qty: Double): String {
    val format = NumberFormat.getNumberInstance(INDIA).apply {
        minimumFractionDigits = 3
        maximumFractionDigits = 3
    }

    return format.format(qty)
}

/** A quantity with the item's own unit, e.g. "1,200.000 kg". */
private fun formatQty(qty: Double, unit: String): String = "${formatQty(qty)} $unit"

/** "raw_material" reads as a database value; the report shows what the portal shows. */
private fun typeLabel(itemType: String): String = when (itemType) {
    "raw_material" -> "Raw material"
    "finished_good" -> "Finished good"
    else -> itemType.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

// ------------------------------------------------------------------ State

/** One column of the report: a plant, named once rather than on every row. */
data class StockPlantColumn(val plantId: Int, val name: String)

sealed interface StockUiState {
    data object Loading : StockUiState
    data class Content(
        val rows: List<StockRowDto>,
        val plants: List<StockPlantColumn>,
    ) : StockUiState

    data class Failed(val message: String) : StockUiState
}

/**
 * Backs both the stock list and the Reports "Stock" tab.
 *
 * The figures come from /api/stock rather than from Room because the report needs two
 * things the local tables cannot supply: the plant names, and the low-stock verdict,
 * which spec §4.3 defines against a threshold that falls back to a company-wide
 * default. Re-deriving that rule on the phone is how the two copies drift apart.
 * Room stays the offline cache and is filled by the sync layer, not by this screen.
 */
@HiltViewModel
class StockViewModel @Inject constructor(
    private val stock: ItemStockRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<StockUiState>(StockUiState.Loading)
    val state: StateFlow<StockUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = StockUiState.Loading

            runCatching { stock.fetchReport() }
                .onSuccess { rows -> _state.value = StockUiState.Content(rows, plantColumns(rows)) }
                .onFailure {
                    _state.value = StockUiState.Failed(
                        it.message ?: it::class.simpleName ?: "Couldn't load stock"
                    )
                }
        }
    }

    /**
     * Every plant that appears on any row, in the order the server sends them.
     *
     * Taking the union rather than the first row's breakdown means a row that somehow
     * arrives short still lines up under the right headings.
     */
    private fun plantColumns(rows: List<StockRowDto>): List<StockPlantColumn> =
        rows.asSequence()
            .flatMap { it.byPlant.asSequence() }
            .map { StockPlantColumn(it.plantId, it.plantName) }
            .distinctBy { it.plantId }
            .toList()
}

// ------------------------------------------------------------------ Report tab

private val ITEM_COLUMN = 168.dp
private val TYPE_COLUMN = 116.dp
private val PLANT_COLUMN = 116.dp
private val TOTAL_COLUMN = 140.dp

/**
 * The Stock Report of spec §6.3 — Item / Type / a column per plant / Total, with units
 * — as a bare body with no Scaffold, top bar or navigation of its own, so it can be
 * dropped straight into the Reports screen's "Stock" tab.
 *
 * The table scrolls sideways as one piece: the heading row and every data row share a
 * single scroll state, so the plant columns stay under their own headings however many
 * plants the company grows to.
 */
@Composable
fun StockReportBody() {
    val viewModel: StockViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is StockUiState.Loading -> LoadingState()
        is StockUiState.Failed -> ErrorState(current.message, onRetry = viewModel::load)
        is StockUiState.Content -> {
            if (current.rows.isEmpty()) {
                EmptyState("No items to report on yet.")
                return
            }

            val scroll = rememberScrollState()
            val lowCount = current.rows.count { it.isLow }

            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = if (lowCount == 0) {
                        "${current.rows.size} items · none low"
                    } else {
                        "${current.rows.size} items · $lowCount low on stock"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (lowCount == 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                StockTableRow(
                    scroll = scroll,
                    plants = current.plants,
                    item = { HeadingCell("Item", ITEM_COLUMN) },
                    type = { HeadingCell("Type", TYPE_COLUMN) },
                    plant = { column -> HeadingCell(column.name, PLANT_COLUMN, TextAlign.End) },
                    total = { HeadingCell("Total", TOTAL_COLUMN, TextAlign.End) },
                )
                HorizontalDivider()

                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(current.rows, key = { it.itemId }) { row ->
                        val quantities = row.byPlant.associate { it.plantId to it.qty }
                        val tone = if (row.isLow) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }

                        StockTableRow(
                            scroll = scroll,
                            plants = current.plants,
                            item = {
                                Column(modifier = Modifier.width(ITEM_COLUMN)) {
                                    Text(
                                        text = row.itemName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tone,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (row.isLow) {
                                        Text(
                                            text = "Low stock",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                            type = {
                                Text(
                                    text = typeLabel(row.itemType),
                                    modifier = Modifier.width(TYPE_COLUMN),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            plant = { column ->
                                QuantityCell(
                                    text = formatQty(quantities[column.plantId] ?: 0.0),
                                    width = PLANT_COLUMN,
                                    color = tone,
                                )
                            },
                            total = {
                                QuantityCell(
                                    text = formatQty(row.totalQty, row.unit),
                                    width = TOTAL_COLUMN,
                                    color = tone,
                                    emphasis = true,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One line of the table. The heading and the data rows differ only in what they put in
 * each cell, so they share a single definition of where the columns are.
 */
@Composable
private fun StockTableRow(
    scroll: androidx.compose.foundation.ScrollState,
    plants: List<StockPlantColumn>,
    item: @Composable () -> Unit,
    type: @Composable () -> Unit,
    plant: @Composable (StockPlantColumn) -> Unit,
    total: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item()
        type()
        plants.forEach { column -> plant(column) }
        total()
    }
}

@Composable
private fun HeadingCell(text: String, width: androidx.compose.ui.unit.Dp, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Figures are monospaced and right-aligned so a column of them can be read down. */
@Composable
private fun QuantityCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    color: Color,
    emphasis: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        fontFamily = FontFamily.Monospace,
        fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Medium,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
    )
}

// ------------------------------------------------------------------ Stock list

/**
 * Stock for every item, with its total and its plant-by-plant breakdown.
 *
 * A card per item rather than the report's table: this screen is for finding one item
 * and seeing where its stock sits, which a phone-width column of names and figures does
 * better than a table that has to be scrolled sideways to read. The table lives in the
 * Reports tab, where reading across plants is the point.
 */
@Composable
fun StockListScreen(navController: NavHostController) {
    val viewModel: StockViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var lowOnly by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search items") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = lowOnly,
                onClick = { lowOnly = !lowOnly },
                label = { Text("Low stock only") },
            )
        }

        when (val current = state) {
            is StockUiState.Loading -> LoadingState()
            is StockUiState.Failed -> ErrorState(current.message, onRetry = viewModel::load)
            is StockUiState.Content -> {
                val rows = current.rows.filter { row ->
                    (!lowOnly || row.isLow) &&
                        (query.isBlank() || row.itemName.contains(query, ignoreCase = true))
                }

                if (rows.isEmpty()) {
                    EmptyState(
                        if (lowOnly) "Nothing is low on stock." else "No items match."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(rows, key = { it.itemId }) { row ->
                            StockCard(row) {
                                navController.navigate(
                                    "${NavigationRoutes.ITEM_DETAIL_BASE}/${row.itemId}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockCard(row: StockRowDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            // A low item is tinted rather than merely annotated: spec §4.3 wants it
            // obvious at a glance in a long list.
            containerColor = if (row.isLow) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.itemName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = typeLabel(row.itemType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatQty(row.totalQty, row.unit),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (row.isLow) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = if (row.isLow) "low on stock" else "in stock",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (row.isLow) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            if (row.byPlant.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                row.byPlant.forEach { plant ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = plant.plantName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatQty(plant.qty, row.unit),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
