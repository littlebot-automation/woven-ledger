package com.wovenledger.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wovenledger.app.ui.navigation.NavigationRoutes
import com.wovenledger.app.ui.screens.*

@Composable
fun App() {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Woven Ledger") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, "Dashboard") },
                    label = { Text("Dashboard") },
                    selected = false,
                    onClick = {
                        navController.navigate(NavigationRoutes.DASHBOARD) {
                            popUpTo(NavigationRoutes.DASHBOARD) { inclusive = true }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, "Parties") },
                    label = { Text("Parties") },
                    selected = false,
                    onClick = {
                        navController.navigate(NavigationRoutes.PARTIES_LIST) {
                            popUpTo(NavigationRoutes.PARTIES_LIST) { inclusive = true }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, "Invoices") },
                    label = { Text("Invoices") },
                    selected = false,
                    onClick = {
                        navController.navigate(NavigationRoutes.SALES_INVOICES_LIST) {
                            popUpTo(NavigationRoutes.SALES_INVOICES_LIST) { inclusive = true }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, "More") },
                    label = { Text("More") },
                    selected = false,
                    onClick = {
                        navController.navigate(NavigationRoutes.ITEMS_LIST) {
                            popUpTo(NavigationRoutes.ITEMS_LIST) { inclusive = true }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NavigationRoutes.DASHBOARD,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Dashboard
            composable(NavigationRoutes.DASHBOARD) {
                DashboardScreen(navController)
            }

            // Parties
            composable(NavigationRoutes.PARTIES_LIST) {
                PartiesListScreen(navController)
            }
            composable(
                NavigationRoutes.PARTY_DETAIL,
                arguments = listOf(navArgument("partyId") { type = NavType.LongType })
            ) { backStackEntry ->
                val partyId = backStackEntry.arguments?.getLong("partyId") ?: 0L
                PartyDetailScreen(navController, partyId)
            }

            // Items
            composable(NavigationRoutes.ITEMS_LIST) {
                ItemsListScreen(navController)
            }
            composable(
                NavigationRoutes.ITEM_DETAIL,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
                ItemDetailScreen(navController, itemId)
            }

            // Sales Invoices
            composable(NavigationRoutes.SALES_INVOICES_LIST) {
                SalesInvoicesListScreen(navController)
            }
            composable(
                NavigationRoutes.SALES_INVOICE_DETAIL,
                arguments = listOf(navArgument("invoiceId") { type = NavType.LongType })
            ) { backStackEntry ->
                val invoiceId = backStackEntry.arguments?.getLong("invoiceId") ?: 0L
                SalesInvoiceDetailScreen(navController, invoiceId)
            }
            composable(NavigationRoutes.SALES_INVOICE_CREATE) {
                SalesInvoiceCreateScreen(navController)
            }

            // Purchase Bills
            composable(NavigationRoutes.PURCHASE_BILLS_LIST) {
                PurchaseBillsListScreen(navController)
            }
            composable(
                NavigationRoutes.PURCHASE_BILL_DETAIL,
                arguments = listOf(navArgument("billId") { type = NavType.LongType })
            ) { backStackEntry ->
                val billId = backStackEntry.arguments?.getLong("billId") ?: 0L
                PurchaseBillDetailScreen(navController, billId)
            }
            composable(NavigationRoutes.PURCHASE_BILL_CREATE) {
                PurchaseBillCreateScreen(navController)
            }

            // Receipts
            composable(NavigationRoutes.RECEIPTS_LIST) {
                ReceiptsListScreen(navController)
            }
            composable(
                NavigationRoutes.RECEIPT_DETAIL,
                arguments = listOf(navArgument("receiptId") { type = NavType.LongType })
            ) { backStackEntry ->
                val receiptId = backStackEntry.arguments?.getLong("receiptId") ?: 0L
                ReceiptDetailScreen(navController, receiptId)
            }
            composable(NavigationRoutes.RECEIPT_CREATE) {
                ReceiptCreateScreen(navController)
            }

            // Payments
            composable(NavigationRoutes.PAYMENTS_LIST) {
                PaymentsListScreen(navController)
            }
            composable(
                NavigationRoutes.PAYMENT_DETAIL,
                arguments = listOf(navArgument("paymentId") { type = NavType.LongType })
            ) { backStackEntry ->
                val paymentId = backStackEntry.arguments?.getLong("paymentId") ?: 0L
                PaymentDetailScreen(navController, paymentId)
            }
            composable(NavigationRoutes.PAYMENT_CREATE) {
                PaymentCreateScreen(navController)
            }

            // Staff
            composable(NavigationRoutes.STAFF_LIST) {
                StaffListScreen(navController)
            }
            composable(
                NavigationRoutes.STAFF_DETAIL,
                arguments = listOf(navArgument("staffId") { type = NavType.LongType })
            ) { backStackEntry ->
                val staffId = backStackEntry.arguments?.getLong("staffId") ?: 0L
                StaffDetailScreen(navController, staffId)
            }

            // Staff Work
            composable(NavigationRoutes.STAFF_WORK_LIST) {
                StaffWorkListScreen(navController)
            }
            composable(NavigationRoutes.STAFF_WORK_CREATE) {
                StaffWorkCreateScreen(navController)
            }

            // Settings
            composable(NavigationRoutes.SETTINGS) {
                SettingsScreen(navController)
            }

            // Reports
            composable(NavigationRoutes.REPORTS) {
                ReportsScreen(navController)
            }
        }
    }
}

