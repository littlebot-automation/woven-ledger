package com.wovenledger.app.ui.screens.outbox

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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.google.gson.Gson
import com.wovenledger.app.data.outbox.OutboxEntry
import com.wovenledger.app.data.outbox.OutboxManager
import com.wovenledger.app.data.outbox.OutboxOperation
import com.wovenledger.app.data.outbox.OutboxStatus
import com.wovenledger.app.ui.components.EmptyState
import com.wovenledger.app.ui.components.formatDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** One queued write as the user sees it. */
data class PendingRow(
    val id: Long,
    val title: String,
    val detail: String,
    val status: OutboxStatus,
    val error: String?,
    val warnings: List<String>,
)

/**
 * What the app is holding on the user's behalf.
 *
 * A silent queue is worse than an error: work that looks saved but is going nowhere
 * only shows up when someone asks the office why the invoice never arrived. This is
 * the screen that answers that question, and the one place a failed write can be
 * retried or abandoned.
 */
data class OutboxSummary(
    val pending: Int = 0,
    val failed: Int = 0,
    /** Warnings from documents that have uploaded and that nobody has read yet. */
    val unreadWarnings: List<PendingRow> = emptyList(),
) {
    val hasWork: Boolean get() = pending > 0 || failed > 0
    val badge: Int get() = pending + failed
}

@HiltViewModel
class OutboxViewModel @Inject constructor(
    private val manager: OutboxManager,
    private val gson: Gson,
) : ViewModel() {

    val rows: StateFlow<List<PendingRow>> = manager.entries()
        .map { entries -> entries.map { rowOf(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<OutboxSummary> = rows
        .map { all ->
            OutboxSummary(
                pending = all.count { it.status == OutboxStatus.PENDING },
                failed = all.count { it.status == OutboxStatus.FAILED },
                unreadWarnings = all.filter { it.status == OutboxStatus.UPLOADED },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutboxSummary())

    fun retry(id: Long) {
        viewModelScope.launch { manager.retryFailed(id) }
    }

    fun dismiss(id: Long) {
        viewModelScope.launch { manager.discard(id) }
    }

    fun sendNow() {
        viewModelScope.launch { manager.drainNow() }
    }

    private fun rowOf(entry: OutboxEntry): PendingRow {
        val written = Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault())
        val verb = if (entry.operation == OutboxOperation.CREATE) "New" else "Edit to"

        return PendingRow(
            id = entry.id,
            title = "$verb ${entry.target.label.lowercase()}",
            detail = "Written ${formatDate(written.toLocalDate())}" +
                if (entry.attempts > 0) " · ${entry.attempts} attempts" else "",
            status = entry.status,
            error = entry.lastError,
            warnings = entry.warnings
                ?.let { runCatching { gson.fromJson(it, Array<String>::class.java).toList() }.getOrNull() }
                .orEmpty(),
        )
    }
}

/**
 * Warnings from documents that uploaded while nobody was looking at them.
 *
 * The server reports a stock shortfall beside a save it accepted. For a document that
 * was queued, that answer arrives minutes or hours after the form that would have
 * shown it has gone, so it is carried here instead — app-wide, and staying put until
 * it is acknowledged. A warning nobody sees is the same as no warning at all.
 */
@Composable
fun UploadWarningsBanner(
    warnings: List<PendingRow>,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (warnings.isEmpty()) {
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            warnings.forEach { row ->
                Text(
                    text = "${row.title} uploaded, with warnings",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                row.warnings.forEach { warning ->
                    Text(
                        text = "• $warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                TextButton(onClick = { onDismiss(row.id) }) { Text("Got it") }
            }
        }
    }
}

@Composable
fun PendingUploadsScreen(navController: NavHostController) {
    val viewModel: OutboxViewModel = hiltViewModel()
    val rows by viewModel.rows.collectAsStateWithLifecycle()

    if (rows.isEmpty()) {
        EmptyState(
            "Everything has been uploaded. Anything written without a signal waits here.",
            icon = Icons.Filled.CloudUpload,
        )

        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(rows, key = { it.id }) { row ->
            PendingCard(
                row = row,
                onRetry = { viewModel.retry(row.id) },
                onDismiss = { viewModel.dismiss(row.id) },
            )
        }
    }
}

@Composable
private fun PendingCard(row: PendingRow, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (row.status) {
                OutboxStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                OutboxStatus.UPLOADED -> MaterialTheme.colorScheme.tertiaryContainer
                OutboxStatus.PENDING -> MaterialTheme.colorScheme.surface
            }
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(row.title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = when (row.status) {
                    OutboxStatus.PENDING -> "${row.detail} · waiting for a connection"
                    OutboxStatus.FAILED -> "${row.detail} · not sent"
                    OutboxStatus.UPLOADED -> "${row.detail} · uploaded, with warnings"
                },
                style = MaterialTheme.typography.bodySmall,
            )

            row.error?.takeIf { row.status == OutboxStatus.FAILED }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            row.warnings.forEach { warning ->
                Text(
                    text = "• $warning",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.status == OutboxStatus.FAILED) {
                    TextButton(onClick = onRetry) { Text("Try again") }
                    // Discarding is destructive, and it is the only way out of a write
                    // the server will never accept — so it is offered, and named plainly.
                    TextButton(onClick = onDismiss) { Text("Discard") }
                }
                if (row.status == OutboxStatus.UPLOADED) {
                    TextButton(onClick = onDismiss) { Text("Got it") }
                }
            }
        }
    }
}
