package com.wovenledger.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

/**
 * Screens still awaiting their backing API.
 *
 * Dashboard, parties, items, sales invoices, purchase bills and payments have real
 * implementations under their own packages. What remains here needs endpoints that
 * do not exist yet (receipts, staff, staff work, settings) or write support (the
 * create forms), and each says so rather than pretending to be finished.
 */

// ================================================================ Sales / purchase creation
@Composable
fun SalesInvoiceCreateScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "New sales invoice",
        description = "Creating invoices needs the write API.",
        navController = navController
    )
}

@Composable
fun PurchaseBillCreateScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "New purchase bill",
        description = "Creating bills needs the write API.",
        navController = navController
    )
}

// ================================================================ Receipts
@Composable
fun ReceiptCreateScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "New receipt",
        description = "Creating receipts needs the write API.",
        navController = navController
    )
}

// ================================================================ Payments creation
@Composable
fun PaymentCreateScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "New payment",
        description = "Creating payments needs the write API.",
        navController = navController
    )
}

// Staff, staff work and work entry now live in ui/screens/staff/.

// ================================================================ Settings & reports
@Composable
fun SettingsScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Settings",
        description = "Company settings aren't exposed by the API yet.",
        navController = navController
    )
}

// ================================================================ Placeholder
@Composable
fun ScreenPlaceholder(
    title: String,
    description: String,
    navController: NavHostController? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
