package com.wovenledger.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

// ================================================================ Dashboard
@Composable
fun DashboardScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Dashboard",
        description = "Overview and key metrics",
        navController = navController
    )
}

// ================================================================ Parties
@Composable
fun PartiesListScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Parties",
        description = "Customers and Suppliers"
    )
}

@Composable
fun PartyDetailScreen(navController: NavHostController, partyId: Long) {
    ScreenPlaceholder(
        title = "Party Detail",
        description = "Party ID: $partyId"
    )
}

// ================================================================ Items
@Composable
fun ItemsListScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Items",
        description = "Raw materials and finished goods"
    )
}

@Composable
fun ItemDetailScreen(navController: NavHostController, itemId: Long) {
    ScreenPlaceholder(
        title = "Item Detail",
        description = "Item ID: $itemId"
    )
}

// ================================================================ Sales Invoices
@Composable
fun SalesInvoicesListScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Sales Invoices",
        description = "View all sales invoices"
    )
}

@Composable
fun SalesInvoiceDetailScreen(navController: NavHostController, invoiceId: Long) {
    ScreenPlaceholder(
        title = "Sales Invoice",
        description = "Invoice ID: $invoiceId"
    )
}

@Composable
fun SalesInvoiceCreateScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Create Sales Invoice",
        description = "New invoice"
    )
}

// ================================================================ Purchase Bills
@Composable
fun PurchaseBillsListScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Purchase Bills",
        description = "View all purchase bills"
    )
}

@Composable
fun PurchaseBillDetailScreen(navController: NavHostController, billId: Long) {
    ScreenPlaceholder(
        title = "Purchase Bill",
        description = "Bill ID: $billId"
    )
}

@Composable
fun PurchaseBillCreateScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Create Purchase Bill",
        description = "New bill"
    )
}

// ================================================================ Receipts
@Composable
fun ReceiptsListScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Receipts",
        description = "View all receipts"
    )
}

@Composable
fun ReceiptDetailScreen(navController: NavHostController, receiptId: Long) {
    ScreenPlaceholder(
        title = "Receipt",
        description = "Receipt ID: $receiptId"
    )
}

@Composable
fun ReceiptCreateScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Create Receipt",
        description = "New receipt"
    )
}

// ================================================================ Payments
@Composable
fun PaymentsListScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Payments",
        description = "View all payments"
    )
}

@Composable
fun PaymentDetailScreen(navController: NavHostController, paymentId: Long) {
    ScreenPlaceholder(
        title = "Payment",
        description = "Payment ID: $paymentId"
    )
}

@Composable
fun PaymentCreateScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Create Payment",
        description = "New payment"
    )
}

// ================================================================ Staff
@Composable
fun StaffListScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Staff",
        description = "View all staff members"
    )
}

@Composable
fun StaffDetailScreen(navController: NavHostController, staffId: Long) {
    ScreenPlaceholder(
        title = "Staff Detail",
        description = "Staff ID: $staffId"
    )
}

// ================================================================ Staff Work
@Composable
fun StaffWorkListScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Staff Work & Wages",
        description = "Daily work and wage settlement"
    )
}

@Composable
fun StaffWorkCreateScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Record Staff Work",
        description = "New work entry"
    )
}

// ================================================================ Settings
@Composable
fun SettingsScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Settings",
        description = "Company settings and preferences"
    )
}

// ================================================================ Reports
@Composable
fun ReportsScreen(navController: NavHostController) {
    ScreenPlaceholder(
        title = "Reports & Analytics",
        description = "Financial reports and analytics"
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
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { navController?.popBackStack() }) {
            Text("Back")
        }
    }
}
