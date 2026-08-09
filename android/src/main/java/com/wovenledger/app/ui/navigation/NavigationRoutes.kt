package com.wovenledger.app.ui.navigation

/**
 * All 12 screens in the Woven Ledger app
 */
object NavigationRoutes {
    // Dashboard & Overview
    const val DASHBOARD = "dashboard"

    // Parties (Customers/Suppliers)
    const val PARTIES_LIST = "parties_list"
    const val PARTY_DETAIL = "party_detail/{partyId}"
    const val PARTY_DETAIL_BASE = "party_detail"

    // Items & Inventory
    const val ITEMS_LIST = "items_list"
    const val ITEM_DETAIL = "item_detail/{itemId}"
    const val ITEM_DETAIL_BASE = "item_detail"

    // Sales Invoices
    const val SALES_INVOICES_LIST = "sales_invoices_list"
    const val SALES_INVOICE_DETAIL = "sales_invoice_detail/{invoiceId}"
    const val SALES_INVOICE_DETAIL_BASE = "sales_invoice_detail"
    const val SALES_INVOICE_CREATE = "sales_invoice_create"

    // Purchase Bills
    const val PURCHASE_BILLS_LIST = "purchase_bills_list"
    const val PURCHASE_BILL_DETAIL = "purchase_bill_detail/{billId}"
    const val PURCHASE_BILL_DETAIL_BASE = "purchase_bill_detail"
    const val PURCHASE_BILL_CREATE = "purchase_bill_create"

    // Receipts
    const val RECEIPTS_LIST = "receipts_list"
    const val RECEIPT_DETAIL = "receipt_detail/{receiptId}"
    const val RECEIPT_DETAIL_BASE = "receipt_detail"
    const val RECEIPT_CREATE = "receipt_create"

    // Payments
    const val PAYMENTS_LIST = "payments_list"
    const val PAYMENT_DETAIL = "payment_detail/{paymentId}"
    const val PAYMENT_DETAIL_BASE = "payment_detail"
    const val PAYMENT_CREATE = "payment_create"

    // Staff & Wages
    const val STAFF_LIST = "staff_list"
    const val STAFF_DETAIL = "staff_detail/{staffId}"
    const val STAFF_DETAIL_BASE = "staff_detail"

    // Staff Work & Wages Settlement
    const val STAFF_WORK_LIST = "staff_work_list"
    const val STAFF_WORK_CREATE = "staff_work_create"

    // Settings
    const val SETTINGS = "settings"

    // Reports & Analytics
    const val REPORTS = "reports"
}
