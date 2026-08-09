package com.wovenledger.app.pdf

import kotlin.math.floor
import kotlin.math.max

/**
 * Page geometry for the invoice PDF, kept free of any framework type so the
 * arithmetic can be tested on the JVM. The renderer owns the ink; this owns the ruler.
 *
 * A4 at 72dpi is 595 x 842 points, which is what `PdfDocument` counts in.
 */
object InvoiceLayout {

    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842

    /** Half an inch all round, matching the portal print view's comfortable margin. */
    const val MARGIN = 36f

    const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
    const val CONTENT_RIGHT = PAGE_WIDTH - MARGIN
    const val CONTENT_BOTTOM = PAGE_HEIGHT - MARGIN

    const val ROW_HEIGHT = 16f
    const val TABLE_HEADER_HEIGHT = 18f

    const val TOTALS_TOP_GAP = 12f
    const val TOTALS_ROW_HEIGHT = 15f
    const val GRAND_TOTAL_EXTRA = 10f

    /** Room for the "For <company>" rule the portal prints at the foot. */
    const val SIGNATURE_BLOCK_HEIGHT = 62f

    /** The width of the right-aligned totals block, as the print view's 290px. */
    const val TOTALS_WIDTH = 290f

    /**
     * Height the totals and signature need under the last line row.
     *
     * Reserved on *every* page rather than only the last one. That costs a couple of
     * rows on a long invoice but guarantees the totals never spill onto a page of
     * their own, which is the failure a recipient would actually notice.
     */
    fun totalsBlockHeight(hasDiscount: Boolean): Float =
        TOTALS_TOP_GAP +
            TOTALS_ROW_HEIGHT * (if (hasDiscount) 3 else 2) +
            GRAND_TOTAL_EXTRA +
            SIGNATURE_BLOCK_HEIGHT

    /**
     * How many line rows fit in [availableHeight].
     *
     * Never negative: a header tall enough to swallow the page yields no rows rather
     * than a negative count that would loop forever.
     */
    fun rowsThatFit(availableHeight: Float): Int =
        max(0, floor(availableHeight / ROW_HEIGHT).toInt())

    /** Column edges of the line-item table, laid out left to right across [contentWidth]. */
    fun tableColumns(left: Float = MARGIN, contentWidth: Float = CONTENT_WIDTH): TableColumns {
        val index = 22f
        val hsn = 62f
        val qty = 78f
        val rate = 78f
        val amount = 82f
        val item = contentWidth - index - hsn - qty - rate - amount

        val itemLeft = left + index
        val hsnLeft = itemLeft + item
        val qtyRight = hsnLeft + hsn + qty

        return TableColumns(
            indexLeft = left,
            itemLeft = itemLeft,
            itemWidth = item,
            hsnLeft = hsnLeft,
            hsnWidth = hsn,
            qtyRight = qtyRight,
            rateRight = qtyRight + rate,
            amountRight = qtyRight + rate + amount,
        )
    }
}

/**
 * Where each column sits. Qty, rate and amount are right-aligned so figures line up,
 * and so are described by their right edge; the text columns by their left edge.
 */
data class TableColumns(
    val indexLeft: Float,
    val itemLeft: Float,
    val itemWidth: Float,
    val hsnLeft: Float,
    val hsnWidth: Float,
    val qtyRight: Float,
    val rateRight: Float,
    val amountRight: Float,
)
