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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.wovenledger.app.ui.navigation.NavigationRoutes

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

@Composable
fun MoreScreen(navController: NavHostController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ENTRIES, key = { it.route }) { entry ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(entry.route) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    ) {
                        Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = entry.caption,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
