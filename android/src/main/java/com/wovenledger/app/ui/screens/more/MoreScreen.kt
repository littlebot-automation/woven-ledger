package com.wovenledger.app.ui.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.wovenledger.app.data.auth.SessionManager
import com.wovenledger.app.ui.navigation.NavigationRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class MoreEntry(
    val label: String,
    val caption: String,
    val icon: ImageVector,
    val route: String,
)

/**
 * The bottom bar holds four destinations; everything else lives here.
 *
 * Grouping follows docs/SPEC.md §2 — masters, then transactions, then insights and
 * system — so the app reads in the same order as the portal's sidebar.
 */
private val ENTRIES = listOf(
    MoreEntry("Items", "Raw materials and finished goods", Icons.Filled.Inventory2, NavigationRoutes.ITEMS_LIST),
    MoreEntry("Stock", "Plant-wise quantities and low stock", Icons.Filled.Warehouse, NavigationRoutes.STOCK),
    MoreEntry("Staff", "Workers and their wage rates", Icons.Filled.Badge, NavigationRoutes.STAFF_LIST),
    MoreEntry("Daily staff work", "Work entries and unpaid wages", Icons.Filled.Work, NavigationRoutes.STAFF_WORK_LIST),
    MoreEntry("Purchase bills", "What we bought", Icons.Filled.ShoppingCart, NavigationRoutes.PURCHASE_BILLS_LIST),
    MoreEntry("Receipts", "Money received", Icons.Filled.ReceiptLong, NavigationRoutes.RECEIPTS_LIST),
    MoreEntry("Payments", "Money paid out", Icons.Filled.Payments, NavigationRoutes.PAYMENTS_LIST),
    MoreEntry("Reports", "Sales, stock and outstanding", Icons.Filled.Assessment, NavigationRoutes.REPORTS),
    MoreEntry("Settings", "Company details and numbering", Icons.Filled.Settings, NavigationRoutes.SETTINGS),
)

/** Ends the session. Everything else on this screen is plain navigation. */
@HiltViewModel
class MoreViewModel @Inject constructor(private val session: SessionManager) : ViewModel() {
    fun signOut() {
        viewModelScope.launch { session.signOut() }
    }
}

@Composable
fun MoreScreen(navController: NavHostController) {
    val viewModel: MoreViewModel = hiltViewModel()
    var confirmingSignOut by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ENTRIES, key = { it.route }) { entry ->
            MoreRow(
                icon = entry.icon,
                label = entry.label,
                caption = entry.caption,
                onClick = { navController.navigate(entry.route) },
            )
        }

        // Last, and the only row that is an action rather than a door — hence the error
        // tint and no chevron, which would promise a screen that does not exist.
        item {
            MoreRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "Sign out",
                caption = "Ends the session on this phone",
                onClick = { confirmingSignOut = true },
                tint = MaterialTheme.colorScheme.error,
                chevron = false,
            )
        }
    }

    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "You will need the password to sign in again. Anything still waiting " +
                        "to upload stays on this phone and goes up after the next sign-in."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingSignOut = false
                        // No navigation to do: clearing the token flips the session flow
                        // that App() gates on, and the login screen replaces the Scaffold
                        // this row is sitting in.
                        viewModel.signOut()
                    },
                ) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSignOut = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    label: String,
    caption: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    chevron: Boolean = true,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (chevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
