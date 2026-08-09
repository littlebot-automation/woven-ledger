package com.wovenledger.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The page arithmetic behind the invoice PDF. No framework classes, so it runs on the JVM. */
class InvoiceLayoutTest {

    @Test
    fun `the page is A4 at 72dpi`() {
        assertEquals(595, InvoiceLayout.PAGE_WIDTH)
        assertEquals(842, InvoiceLayout.PAGE_HEIGHT)
    }

    @Test
    fun `content sits inside the margins`() {
        assertEquals(523f, InvoiceLayout.CONTENT_WIDTH, 0.001f)
        assertEquals(559f, InvoiceLayout.CONTENT_RIGHT, 0.001f)
        assertEquals(806f, InvoiceLayout.CONTENT_BOTTOM, 0.001f)
    }

    @Test
    fun `the table spans exactly the content width`() {
        val columns = InvoiceLayout.tableColumns()

        assertEquals(InvoiceLayout.MARGIN, columns.indexLeft, 0.001f)
        assertEquals(InvoiceLayout.CONTENT_RIGHT, columns.amountRight, 0.001f)
    }

    @Test
    fun `columns run left to right without overlapping`() {
        val columns = InvoiceLayout.tableColumns()

        assertTrue(columns.indexLeft < columns.itemLeft)
        assertTrue(columns.itemLeft < columns.hsnLeft)
        assertTrue(columns.hsnLeft + columns.hsnWidth <= columns.qtyRight)
        assertTrue(columns.qtyRight < columns.rateRight)
        assertTrue(columns.rateRight < columns.amountRight)
    }

    @Test
    fun `the item column takes whatever the fixed columns leave`() {
        val columns = InvoiceLayout.tableColumns()

        assertEquals(columns.itemLeft + columns.itemWidth, columns.hsnLeft, 0.001f)
        assertTrue("item names need the widest column", columns.itemWidth > columns.hsnWidth)
    }

    @Test
    fun `a narrower page narrows the item column, not the figures`() {
        val standard = InvoiceLayout.tableColumns()
        val narrow = InvoiceLayout.tableColumns(left = 0f, contentWidth = 400f)

        assertEquals(400f, narrow.amountRight, 0.001f)
        assertEquals(standard.hsnWidth, narrow.hsnWidth, 0.001f)
        assertEquals(standard.itemWidth - 123f, narrow.itemWidth, 0.001f)
    }

    @Test
    fun `a discount line makes the totals block taller`() {
        val without = InvoiceLayout.totalsBlockHeight(hasDiscount = false)
        val with = InvoiceLayout.totalsBlockHeight(hasDiscount = true)

        assertEquals(InvoiceLayout.TOTALS_ROW_HEIGHT, with - without, 0.001f)
    }

    @Test
    fun `rows fill the space they are given`() {
        assertEquals(10, InvoiceLayout.rowsThatFit(160f))
        assertEquals(10, InvoiceLayout.rowsThatFit(175f))
        assertEquals(11, InvoiceLayout.rowsThatFit(176f))
    }

    @Test
    fun `a page with no room reports no rows rather than a negative count`() {
        assertEquals(0, InvoiceLayout.rowsThatFit(0f))
        assertEquals(0, InvoiceLayout.rowsThatFit(-200f))
    }

    @Test
    fun `a first page still fits a useful number of rows once the totals are reserved`() {
        // A tall header (company block, meta strip, buyer block, table head) plus the
        // reserved totals must still leave the page usable, or every invoice paginates.
        val header = 260f
        val available = InvoiceLayout.CONTENT_BOTTOM - header -
            InvoiceLayout.totalsBlockHeight(hasDiscount = true)

        assertTrue(InvoiceLayout.rowsThatFit(available) >= 20)
    }
}
