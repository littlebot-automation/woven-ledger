package com.wovenledger.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The invoice PDF must read exactly like the portal's print view, so the quantity
 * rules are checked against `IndianNumberFormatter::qty`, and the filename against
 * what a recipient can actually be sent.
 *
 * Money is deliberately absent: the PDF calls the app's own `formatMoney`, which
 * already has its own test.
 */
class InvoiceFormatsTest {

    @Test
    fun `whole quantities lose their decimals`() {
        assertEquals("1,200", formatQty(1200.0))
        assertEquals("0", formatQty(0.0))
    }

    @Test
    fun `fractional quantities keep only the digits that matter`() {
        assertEquals("12.5", formatQty(12.5))
        assertEquals("12.505", formatQty(12.505))
        assertEquals("0.25", formatQty(0.25))
    }

    @Test
    fun `quantities round to three decimals, half up`() {
        assertEquals("1.235", formatQty(1.23456))
        assertEquals("1", formatQty(0.9999))
    }

    @Test
    fun `whole part uses Indian grouping`() {
        assertEquals("1,000", formatQty(1000.0))
        assertEquals("12,34,567", formatQty(1234567.0))
        assertEquals("999", formatQty(999.0))
        assertEquals("1,20,000.5", formatQty(120000.5))
    }

    @Test
    fun `a negative quantity keeps its sign, but a rounded-away one does not`() {
        assertEquals("-12.5", formatQty(-12.5))
        assertEquals("0", formatQty(-0.0001))
    }

    @Test
    fun `quantity carries the item's unit`() {
        assertEquals("1,200 kg", formatQtyWithUnit(1200.0, "kg"))
        assertEquals("12.5 pcs", formatQtyWithUnit(12.5, "pcs"))
    }

    @Test
    fun `a unitless item is just the number`() {
        assertEquals("40", formatQtyWithUnit(40.0, ""))
        assertEquals("40", formatQtyWithUnit(40.0, "   "))
    }

    @Test
    fun `the filename is the document number`() {
        assertEquals("SI-0015.pdf", invoicePdfFileName("SI-0015"))
        assertEquals("INV0015.pdf", invoicePdfFileName("INV0015"))
    }

    @Test
    fun `separators in a document number cannot become path segments`() {
        assertEquals("SI-2026-15.pdf", invoicePdfFileName("SI/2026/15"))
        assertEquals("SI-2026-15.pdf", invoicePdfFileName("SI\\2026\\15"))
        assertEquals("SI-15.pdf", invoicePdfFileName("../SI 15"))
    }

    @Test
    fun `a document number with nothing usable still yields a filename`() {
        assertEquals("invoice.pdf", invoicePdfFileName(""))
        assertEquals("invoice.pdf", invoicePdfFileName("///"))
    }
}
