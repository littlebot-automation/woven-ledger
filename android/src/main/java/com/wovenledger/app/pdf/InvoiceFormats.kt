package com.wovenledger.app.pdf

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The non-money text rules the PDF shares with the portal's print view.
 *
 * Rupee figures are NOT here: they go through
 * [com.wovenledger.app.ui.components.formatMoney] so the PDF can never disagree with
 * the screen. What is left is quantities — which are counts in an item's unit — and
 * the name the recipient sees on the shared file.
 */

/**
 * Quantities as `IndianNumberFormatter::qty` renders them: three decimals, trailing
 * zeros dropped, Indian digit grouping. "12.5", not "12.500"; "1,20,000", not "120,000".
 */
fun formatQty(qty: Double): String {
    val rounded = BigDecimal.valueOf(qty).abs().setScale(3, RoundingMode.HALF_UP)
    val whole = rounded.toBigInteger().toString()
    val decimals = rounded.toPlainString().substringAfter('.').trimEnd('0')
    val sign = if (qty < 0 && rounded.signum() != 0) "-" else ""

    return sign + groupIndian(whole) + if (decimals.isEmpty()) "" else ".$decimals"
}

/** "1,200 kg". A unitless item just reads as the number. */
fun formatQtyWithUnit(qty: Double, unit: String): String {
    val quantity = formatQty(qty)

    return if (unit.isBlank()) quantity else "$quantity ${unit.trim()}"
}

/**
 * Indian digit grouping: the last three digits, then pairs — 12,34,567.
 * Mirrors `IndianNumberFormatter::group` rather than trusting a locale's grouping.
 */
private fun groupIndian(whole: String): String {
    if (whole.length <= 3) return whole

    val head = whole.dropLast(3)
    val pairs = head.reversed().chunked(2).joinToString(",").reversed()

    return "$pairs,${whole.takeLast(3)}"
}

/**
 * The filename the recipient sees in WhatsApp or their mail client, so it is the
 * document number and nothing else — "SI-0015.pdf".
 *
 * Document numbers may carry slashes ("SI/2026/15"), which would be read as path
 * separators, so anything that is not safe in a filename becomes a hyphen.
 */
fun invoicePdfFileName(invoiceNumber: String): String {
    val safe = invoiceNumber.trim()
        .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')

    return if (safe.isEmpty()) "invoice.pdf" else "$safe.pdf"
}
