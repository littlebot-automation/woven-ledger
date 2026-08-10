package com.wovenledger.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.wovenledger.app.data.auth.AuthState
import com.wovenledger.app.data.auth.SessionManager
import com.wovenledger.app.ui.navigation.NavigationRoutes
import com.wovenledger.app.ui.screens.dashboard.DashboardScreen
import com.wovenledger.app.ui.screens.login.LoginScreen
import com.wovenledger.app.ui.screens.documents.PaymentDetailScreen
import com.wovenledger.app.ui.screens.documents.PaymentFormScreen
import com.wovenledger.app.ui.screens.documents.PurchaseBillFormScreen
import com.wovenledger.app.ui.screens.documents.ReceiptFormScreen
import com.wovenledger.app.ui.screens.documents.SalesInvoiceFormScreen
import com.wovenledger.app.ui.screens.documents.PaymentsListScreen
import com.wovenledger.app.ui.screens.documents.PurchaseBillDetailScreen
import com.wovenledger.app.ui.screens.documents.PurchaseBillsListScreen
import com.wovenledger.app.ui.screens.documents.ReceiptDetailScreen
import com.wovenledger.app.ui.screens.documents.ReceiptsListScreen
import com.wovenledger.app.ui.screens.documents.SalesInvoiceDetailScreen
import com.wovenledger.app.ui.screens.documents.SalesInvoicesListScreen
import com.wovenledger.app.ui.screens.items.ItemDetailScreen
import com.wovenledger.app.ui.screens.items.ItemsListScreen
import com.wovenledger.app.ui.screens.more.MoreScreen
import com.wovenledger.app.ui.screens.outbox.OutboxViewModel
import com.wovenledger.app.ui.screens.outbox.PendingUploadsScreen
import com.wovenledger.app.ui.screens.outbox.UploadWarningsBanner
import com.wovenledger.app.ui.screens.parties.PartiesListScreen
import com.wovenledger.app.ui.screens.parties.PartyFormScreen
import com.wovenledger.app.ui.screens.reports.ReportsScreen
import com.wovenledger.app.ui.screens.settings.SettingsScreen
import com.wovenledger.app.ui.screens.stock.StockListScreen
import com.wovenledger.app.ui.screens.parties.PartyDetailScreen
import com.wovenledger.app.ui.screens.staff.StaffDetailScreen
import com.wovenledger.app.ui.screens.staff.StaffListScreen
import com.wovenledger.app.ui.screens.staff.StaffWorkFormScreen
import com.wovenledger.app.ui.screens.staff.StaffWorkListScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

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
    NavigationRoutes.PARTY_CREATE to "New party",
    NavigationRoutes.PARTY_EDIT to "Edit party",
    NavigationRoutes.ITEMS_LIST to "Items",
    NavigationRoutes.ITEM_DETAIL to "Item",
    NavigationRoutes.STOCK to "Stock",
    NavigationRoutes.SALES_INVOICES_LIST to "Sales invoices",
    NavigationRoutes.SALES_INVOICE_DETAIL to "Sales invoice",
    NavigationRoutes.SALES_INVOICE_CREATE to "New sales invoice",
    NavigationRoutes.SALES_INVOICE_EDIT to "Edit sales invoice",
    NavigationRoutes.PURCHASE_BILLS_LIST to "Purchase bills",
    NavigationRoutes.PURCHASE_BILL_DETAIL to "Purchase bill",
    NavigationRoutes.PURCHASE_BILL_CREATE to "New purchase bill",
    NavigationRoutes.PURCHASE_BILL_EDIT to "Edit purchase bill",
    NavigationRoutes.RECEIPTS_LIST to "Receipts",
    NavigationRoutes.RECEIPT_DETAIL to "Receipt",
    NavigationRoutes.RECEIPT_CREATE to "New receipt",
    NavigationRoutes.RECEIPT_EDIT to "Edit receipt",
    NavigationRoutes.PAYMENTS_LIST to "Payments",
    NavigationRoutes.PAYMENT_DETAIL to "Payment",
    NavigationRoutes.PAYMENT_CREATE to "New payment",
    NavigationRoutes.PAYMENT_EDIT to "Edit payment",
    NavigationRoutes.STAFF_LIST to "Staff",
    NavigationRoutes.STAFF_DETAIL to "Staff member",
    NavigationRoutes.STAFF_WORK_LIST to "Staff work",
    NavigationRoutes.STAFF_WORK_CREATE to "Record work entry",
    NavigationRoutes.STAFF_WORK_EDIT to "Edit work entry",
    NavigationRoutes.SETTINGS to "Settings",
    NavigationRoutes.REPORTS to "Reports",
    NavigationRoutes.PENDING_UPLOADS to "Pending uploads",
)

/** Exposes nothing but the session state, which is all the gate below needs. */
@HiltViewModel
class SessionViewModel @Inject constructor(session: SessionManager) : ViewModel() {
    val state: StateFlow<AuthState> = session.state
}

/**
 * The sign-in gate, above everything else.
 *
 * Deliberately not a NavHost destination: as one, the login screen would have carried
 * the bottom navigation bar, a back arrow, a screen title and the pending-uploads
 * badge — a set of doors that all lead somewhere a signed-out user cannot go. Sitting
 * here, it replaces the whole Scaffold instead.
 *
 * The [AuthState.UNKNOWN] arm matters as much as the other two. Room has not been read
 * yet at that point, and rendering the login screen on a guess would flash a password
 * prompt at a user who is already signed in, every single launch.
 */
@Composable
fun App() {
    val session: SessionViewModel = hiltViewModel()
    val state by session.state.collectAsStateWithLifecycle()

    when (state) {
        AuthState.UNKNOWN -> Box(modifier = Modifier.fillMaxSize())
        AuthState.SIGNED_OUT -> LoginScreen()
        // Composed only when signed in, which is also what keeps the outbox
        // ViewModel's flows from starting behind the login screen.
        AuthState.SIGNED_IN -> Ledger()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Ledger() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val currentRoute = currentDestination?.route
    val isTopLevel = BOTTOM_DESTINATIONS.any { it.route == currentRoute }

    // What is waiting to upload belongs on every screen, not on one the user would
    // have to think to visit. A queue nobody can see is worse than an error.
    val outbox: OutboxViewModel = hiltViewModel()
    val pending by outbox.summary.collectAsStateWithLifecycle()

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
                actions = {
                    if (pending.hasWork) {
                        IconButton(
                            onClick = { navController.navigate(NavigationRoutes.PENDING_UPLOADS) }
                        ) {
                            BadgedBox(badge = { Badge { Text("${pending.badge}") } }) {
                                Icon(
                                    imageVector = if (pending.failed > 0) {
                                        Icons.Filled.CloudOff
                                    } else {
                                        Icons.Filled.CloudUpload
                                    },
                                    contentDescription = if (pending.failed > 0) {
                                        "${pending.failed} writes could not be sent"
                                    } else {
                                        "${pending.pending} writes waiting to upload"
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
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
        Column(modifier = Modifier.padding(paddingValues)) {
            // A queued document is checked against stock only when it uploads, which
            // may be long after the form that would have shown the answer is gone. The
            // warning follows the user here rather than being dropped.
            UploadWarningsBanner(
                warnings = pending.unreadWarnings,
                onDismiss = outbox::dismiss,
            )

            NavHost(
                navController = navController,
                startDestination = NavigationRoutes.DASHBOARD,
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
            composable(NavigationRoutes.PARTY_CREATE) {
                PartyFormScreen(navController)
            }
            composable(
                NavigationRoutes.PARTY_EDIT,
                arguments = listOf(navArgument("partyId") { type = NavType.LongType })
            ) {
                PartyFormScreen(navController)
            }

            composable(NavigationRoutes.STOCK) {
                StockListScreen(navController)
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
            // One form serves both: the create route carries no id, the edit route does,
            // and the ViewModel reads it straight off the back stack entry.
            composable(NavigationRoutes.SALES_INVOICE_CREATE) {
                SalesInvoiceFormScreen(navController)
            }
            composable(
                NavigationRoutes.SALES_INVOICE_EDIT,
                arguments = listOf(navArgument("invoiceId") { type = NavType.LongType })
            ) {
                SalesInvoiceFormScreen(navController)
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
                PurchaseBillFormScreen(navController)
            }
            composable(
                NavigationRoutes.PURCHASE_BILL_EDIT,
                arguments = listOf(navArgument("billId") { type = NavType.LongType })
            ) {
                PurchaseBillFormScreen(navController)
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
                ReceiptFormScreen(navController)
            }
            composable(
                NavigationRoutes.RECEIPT_EDIT,
                arguments = listOf(navArgument("receiptId") { type = NavType.LongType })
            ) {
                ReceiptFormScreen(navController)
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
                PaymentFormScreen(navController)
            }
            composable(
                NavigationRoutes.PAYMENT_EDIT,
                arguments = listOf(navArgument("paymentId") { type = NavType.LongType })
            ) {
                PaymentFormScreen(navController)
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
                StaffWorkFormScreen(navController)
            }
            composable(
                NavigationRoutes.STAFF_WORK_EDIT,
                arguments = listOf(navArgument("workId") { type = NavType.LongType })
            ) {
                StaffWorkFormScreen(navController)
            }

            composable(NavigationRoutes.SETTINGS) {
                SettingsScreen(navController)
            }

            composable(NavigationRoutes.REPORTS) {
                ReportsScreen(navController)
            }

            composable(NavigationRoutes.PENDING_UPLOADS) {
                PendingUploadsScreen(navController)
            }
            }
        }
    }
}
