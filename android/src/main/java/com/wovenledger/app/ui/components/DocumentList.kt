package com.wovenledger.app.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One row of any transaction document. Sales invoices, purchase bills, receipts and
 * payments all present the same way — number, a line of context, and an amount — so
 * they share this shape rather than each growing their own copy of it.
 */
data class DocumentSummary(
    val id: Long,
    val number: String,
    val subtitle: String,
    val amount: Long,
)

@Composable
fun DocumentList(
    documents: List<DocumentSummary>,
    emptyMessage: String,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onCreate: (() -> Unit)? = null,
    createLabel: String = "New",
) {
    Box(modifier = modifier) {
        if (documents.isEmpty()) {
            EmptyState(emptyMessage)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(documents, key = { it.id }) { document ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(document.id) },
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
                                DocumentNumberText(document.number)
                                Text(
                                    text = document.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MoneyText(document.amount)
                        }
                    }
                }
            }
        }

        // The way to add a document sits on its own list, which is where someone
        // looking at yesterday's invoices already is when they need to raise one.
        if (onCreate != null) {
            FloatingActionButton(
                onClick = onCreate,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = createLabel)
            }
        }
    }
}

/** A labelled figure, used across the document detail screens. */
@Composable
fun AmountRow(label: String, paise: Long, emphasis: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyText(paise, emphasis = emphasis)
    }
}
