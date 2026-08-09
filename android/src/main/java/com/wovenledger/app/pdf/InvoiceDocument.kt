package com.wovenledger.app.pdf

import java.time.LocalDate

/**
 * Everything the invoice PDF prints, already resolved.
 *
 * Assembling this is deliberately separate from drawing it: the renderer never touches
 * a repository, and every figure on the page has been settled before a page is opened.
 * Money is integer paise, exactly as it is stored — see
 * [com.wovenledger.app.ui.components.formatMoney] for the one place it becomes rupees.
 */
data class InvoiceDocument(
    val company: InvoiceParty,
    val buyer: InvoiceParty,
    val number: String,
    val date: LocalDate,
    val plantName: String,
    val lines: List<InvoiceLine>,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
)

/** Seller and buyer print the same four facts, so they share a shape. */
data class InvoiceParty(
    val name: String,
    val address: String = "",
    val phone: String = "",
    val gstin: String = "",
)

data class InvoiceLine(
    val itemName: String,
    val hsn: String,
    /** A count in [unit] — never money, so it is never formatted as money. */
    val qty: Double,
    val unit: String,
    val rate: Long,
    val amount: Long,
)
