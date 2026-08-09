package com.wovenledger.app.ui.navigation

/**
 * All 12 screens in the Woven Ledger app
 */
object NavigationRoutes {
    // Dashboard & Overview
    const val DASHBOARD = "dashboard"

    // Everything the four bottom-bar destinations don't cover
    const val MORE = "more"

    // Parties (Customers/Suppliers)
    const val PARTIES_LIST = "parties_list"
    const val PARTY_DETAIL = "party_detail/{partyId}"
    const val PARTY_DETAIL_BASE = "party_detail"
    const val PARTY_CREATE = "party_create"
    const val PARTY_EDIT = "party_edit/{partyId}"
    const val PARTY_EDIT_BASE = "party_edit"

    // Items & Inventory
    const val STOCK = "stock"
    const val ITEMS_LIST = "items_list"
    const val ITEM_DETAIL = "item_detail/{itemId}"
    const val ITEM_DETAIL_BASE = "item_detail"

    // Sales Invoices
    const val SALES_INVOICES_LIST = "sales_invoices_list"
    const val SALES_INVOICE_DETAIL = "sales_invoice_detail/{invoiceId}"
    const val SALES_INVOICE_DETAIL_BASE = "sales_invoice_detail"
    const val SALES_INVOICE_CREATE = "sales_invoice_create"
    const val SALES_INVOICE_EDIT = "sales_invoice_edit/{invoiceId}"
    const val SALES_INVOICE_EDIT_BASE = "sales_invoice_edit"

    // Purchase Bills
    const val PURCHASE_BILLS_LIST = "purchase_bills_list"
    const val PURCHASE_BILL_DETAIL = "purchase_bill_detail/{billId}"
    const val PURCHASE_BILL_DETAIL_BASE = "purchase_bill_detail"
    const val PURCHASE_BILL_CREATE = "purchase_bill_create"
    const val PURCHASE_BILL_EDIT = "purchase_bill_edit/{billId}"
    const val PURCHASE_BILL_EDIT_BASE = "purchase_bill_edit"

    // Receipts
    const val RECEIPTS_LIST = "receipts_list"
    const val RECEIPT_DETAIL = "receipt_detail/{receiptId}"
    const val RECEIPT_DETAIL_BASE = "receipt_detail"
    const val RECEIPT_CREATE = "receipt_create"
    const val RECEIPT_EDIT = "receipt_edit/{receiptId}"
    const val RECEIPT_EDIT_BASE = "receipt_edit"

    // Payments
    const val PAYMENTS_LIST = "payments_list"
    const val PAYMENT_DETAIL = "payment_detail/{paymentId}"
    const val PAYMENT_DETAIL_BASE = "payment_detail"
    const val PAYMENT_CREATE = "payment_create"
    const val PAYMENT_EDIT = "payment_edit/{paymentId}"
    const val PAYMENT_EDIT_BASE = "payment_edit"

    // Staff & Wages
    const val STAFF_LIST = "staff_list"
    const val STAFF_DETAIL = "staff_detail/{staffId}"
    const val STAFF_DETAIL_BASE = "staff_detail"

    // Staff Work & Wages Settlement
    const val STAFF_WORK_LIST = "staff_work_list"
    const val STAFF_WORK_CREATE = "staff_work_create"
    const val STAFF_WORK_EDIT = "staff_work_edit/{workId}"
    const val STAFF_WORK_EDIT_BASE = "staff_work_edit"

    // Settings
    const val SETTINGS = "settings"

    // Reports & Analytics
    const val REPORTS = "reports"
}
