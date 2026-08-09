package com.wovenledger.app.ui.screens.items

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
import com.wovenledger.app.data.entities.Item
import com.wovenledger.app.data.repository.ItemRepository
import com.wovenledger.app.ui.components.EmptyState
import com.wovenledger.app.ui.components.MoneyText
import com.wovenledger.app.ui.navigation.NavigationRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ItemsListViewModel @Inject constructor(
    items: ItemRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val results: StateFlow<List<Item>> = combine(items.getAll(), _query) { all, query ->
        all.filter {
            query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.hsn.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }
}

@Composable
fun ItemsListScreen(navController: NavHostController) {
    val viewModel: ItemsListViewModel = hiltViewModel()
    val items by viewModel.results.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search items or HSN") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        if (items.isEmpty()) {
            EmptyState("No items yet.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate("${NavigationRoutes.ITEM_DETAIL_BASE}/${item.id}")
                            },
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
                                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "${item.type.readable()} · per ${item.unit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MoneyText(item.defaultRate)
                        }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    items: ItemRepository,
) : ViewModel() {

    private val itemId: Long = savedStateHandle.get<Long>("itemId") ?: 0L

    val item: StateFlow<Item?> = items.read(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ItemDetailScreen(navController: NavHostController, itemId: Long) {
    val viewModel: ItemDetailViewModel = hiltViewModel()
    val item by viewModel.item.collectAsStateWithLifecycle()

    val current = item
    if (current == null) {
        EmptyState("That item isn't in the local copy yet.")
        return
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(current.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = current.type.readable(),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Default rate", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(current.defaultRate, emphasis = true)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Unit", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(current.unit)
                }
                if (current.hsn.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("HSN", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(current.hsn)
                    }
                }
            }
        }

        Text(
            text = "Plant-wise stock arrives with the inventory endpoint.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

private fun com.wovenledger.app.data.entities.ItemType.readable(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
