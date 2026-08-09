package com.wovenledger.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wovenledger.app.ui.navigation.NavigationRoutes
import com.wovenledger.app.ui.screens.PurchaseBillCreateScreen
import com.wovenledger.app.ui.screens.PaymentCreateScreen
import com.wovenledger.app.ui.screens.ReceiptCreateScreen
import com.wovenledger.app.ui.screens.ReceiptDetailScreen
import com.wovenledger.app.ui.screens.ReceiptsListScreen
import com.wovenledger.app.ui.screens.ReportsScreen
import com.wovenledger.app.ui.screens.SalesInvoiceCreateScreen
import com.wovenledger.app.ui.screens.SettingsScreen
import com.wovenledger.app.ui.screens.dashboard.DashboardScreen
import com.wovenledger.app.ui.screens.documents.PaymentDetailScreen
import com.wovenledger.app.ui.screens.documents.PaymentsListScreen
import com.wovenledger.app.ui.screens.documents.PurchaseBillDetailScreen
import com.wovenledger.app.ui.screens.documents.PurchaseBillsListScreen
import com.wovenledger.app.ui.screens.documents.SalesInvoiceDetailScreen
import com.wovenledger.app.ui.screens.documents.SalesInvoicesListScreen
import com.wovenledger.app.ui.screens.items.ItemDetailScreen
import com.wovenledger.app.ui.screens.items.ItemsListScreen
import com.wovenledger.app.ui.screens.more.MoreScreen
import com.wovenledger.app.ui.screens.parties.PartiesListScreen
import com.wovenledger.app.ui.screens.parties.PartyDetailScreen
import com.wovenledger.app.ui.screens.staff.StaffDetailScreen
import com.wovenledger.app.ui.screens.staff.StaffListScreen
import com.wovenledger.app.ui.screens.staff.StaffWorkCreateScreen
import com.wovenledger.app.ui.screens.staff.StaffWorkListScreen

private data class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val BOTTOM_DESTINATIONS = listOf(
    Destination(NavigationRoutes.DASHBOARD, "Dashboard", Icons.Filled.Dashboard),
    Destination(NavigationRoutes.PARTIES_LIST, "Parties", Icons.Filled.Group),
    Destination(NavigationRoutes.SALES_INVOICES_LIST, "Invoices", Icons.AutoMirrored.Filled.ReceiptLong),
    Destination(NavigationRoutes.MORE, "More", Icons.Filled.MoreHoriz),
)

/** Screen titles for the top bar, so it says where you are rather than always "Woven Ledger". */
private val TITLES = mapOf(
    NavigationRoutes.DASHBOARD to "Dashboard",
    NavigationRoutes.MORE to "More",
    NavigationRoutes.PARTIES_LIST to "Parties",
    NavigationRoutes.PARTY_DETAIL to "Party",
    NavigationRoutes.ITEMS_LIST to "Items",
    NavigationRoutes.ITEM_DETAIL to "Item",
    NavigationRoutes.SALES_INVOICES_LIST to "Sales invoices",
    NavigationRoutes.SALES_INVOICE_DETAIL to "Sales invoice",
    NavigationRoutes.PURCHASE_BILLS_LIST to "Purchase bills",
    NavigationRoutes.PURCHASE_BILL_DETAIL to "Purchase bill",
    NavigationRoutes.RECEIPTS_LIST to "Receipts",
    NavigationRoutes.PAYMENTS_LIST to "Payments",
    NavigationRoutes.PAYMENT_DETAIL to "Payment",
    NavigationRoutes.STAFF_LIST to "Staff",
    NavigationRoutes.STAFF_WORK_LIST to "Staff work",
    NavigationRoutes.SETTINGS to "Settings",
    NavigationRoutes.REPORTS to "Reports",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val currentRoute = currentDestination?.route
    val isTopLevel = BOTTOM_DESTINATIONS.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(TITLES[currentRoute] ?: "Woven Ledger") },
                navigationIcon = {
                    // Detail screens are reached by tapping through, so they need a way back.
                    if (!isTopLevel && navController.previousBackStackEntry != null) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                )
            )
        },
        bottomBar = {
            NavigationBar {
                BOTTOM_DESTINATIONS.forEach { destination ->
                    NavigationBarItem(
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        // Selection is derived from the live back stack; it was previously
                        // hardcoded false, so no tab ever highlighted.
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Keep a single copy of each top-level screen on the stack,
                                // and remember where the user was within it.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NavigationRoutes.DASHBOARD,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(NavigationRoutes.DASHBOARD) {
                DashboardScreen(navController)
            }

            composable(NavigationRoutes.MORE) {
                MoreScreen(navController)
            }

            composable(NavigationRoutes.PARTIES_LIST) {
                PartiesListScreen(navController)
            }
            composable(
                NavigationRoutes.PARTY_DETAIL,
                arguments = listOf(navArgument("partyId") { type = NavType.LongType })
            ) { entry ->
                PartyDetailScreen(navController, entry.arguments?.getLong("partyId") ?: 0L)
            }

            composable(NavigationRoutes.ITEMS_LIST) {
                ItemsListScreen(navController)
            }
            composable(
                NavigationRoutes.ITEM_DETAIL,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) { entry ->
                ItemDetailScreen(navController, entry.arguments?.getLong("itemId") ?: 0L)
            }

            composable(NavigationRoutes.SALES_INVOICES_LIST) {
                SalesInvoicesListScreen(navController)
            }
            composable(
                NavigationRoutes.SALES_INVOICE_DETAIL,
                arguments = listOf(navArgument("invoiceId") { type = NavType.LongType })
            ) { entry ->
                SalesInvoiceDetailScreen(navController, entry.arguments?.getLong("invoiceId") ?: 0L)
            }
            composable(NavigationRoutes.SALES_INVOICE_CREATE) {
                SalesInvoiceCreateScreen(navController)
            }

            composable(NavigationRoutes.PURCHASE_BILLS_LIST) {
                PurchaseBillsListScreen(navController)
            }
            composable(
                NavigationRoutes.PURCHASE_BILL_DETAIL,
                arguments = listOf(navArgument("billId") { type = NavType.LongType })
            ) { entry ->
                PurchaseBillDetailScreen(navController, entry.arguments?.getLong("billId") ?: 0L)
            }
            composable(NavigationRoutes.PURCHASE_BILL_CREATE) {
                PurchaseBillCreateScreen(navController)
            }

            composable(NavigationRoutes.RECEIPTS_LIST) {
                ReceiptsListScreen(navController)
            }
            composable(
                NavigationRoutes.RECEIPT_DETAIL,
                arguments = listOf(navArgument("receiptId") { type = NavType.LongType })
            ) { entry ->
                ReceiptDetailScreen(navController, entry.arguments?.getLong("receiptId") ?: 0L)
            }
            composable(NavigationRoutes.RECEIPT_CREATE) {
                ReceiptCreateScreen(navController)
            }

            composable(NavigationRoutes.PAYMENTS_LIST) {
                PaymentsListScreen(navController)
            }
            composable(
                NavigationRoutes.PAYMENT_DETAIL,
                arguments = listOf(navArgument("paymentId") { type = NavType.LongType })
            ) { entry ->
                PaymentDetailScreen(navController, entry.arguments?.getLong("paymentId") ?: 0L)
            }
            composable(NavigationRoutes.PAYMENT_CREATE) {
                PaymentCreateScreen(navController)
            }

            composable(NavigationRoutes.STAFF_LIST) {
                StaffListScreen(navController)
            }
            composable(
                NavigationRoutes.STAFF_DETAIL,
                arguments = listOf(navArgument("staffId") { type = NavType.LongType })
            ) { entry ->
                StaffDetailScreen(navController, entry.arguments?.getLong("staffId") ?: 0L)
            }

            composable(NavigationRoutes.STAFF_WORK_LIST) {
                StaffWorkListScreen(navController)
            }
            composable(NavigationRoutes.STAFF_WORK_CREATE) {
                StaffWorkCreateScreen(navController)
            }

            composable(NavigationRoutes.SETTINGS) {
                SettingsScreen(navController)
            }

            composable(NavigationRoutes.REPORTS) {
                ReportsScreen(navController)
            }
        }
    }
}
