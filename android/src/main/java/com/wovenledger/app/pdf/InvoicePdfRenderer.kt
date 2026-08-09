package com.wovenledger.app.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import com.wovenledger.app.ui.components.formatDate
import com.wovenledger.app.ui.components.formatMoney
import java.io.OutputStream

/**
 * Draws a sales invoice onto A4 pages with the platform's own [PdfDocument].
 *
 * The layout mirrors `templates/admin/sales_print.html.twig` — the same "TAX INVOICE"
 * heading, the same Invoice No / Date / Plant / Buyer meta strip, the same
 * #-Item-HSN-Qty-Rate-Amount table and the same subtotal/discount/total block — so a
 * PDF mailed from the phone reads as the document the office prints. The portal shows
 * no amount in words, so neither does this.
 *
 * No third-party PDF library is involved, deliberately: iText and PDFBox are heavy and
 * their licences (AGPL / commercial) do not suit a shipped app.
 */
object InvoicePdfRenderer {

    private const val INK = 0xFF20242B.toInt()
    private const val PRIMARY = 0xFF1D3557.toInt()
    private const val MUTED = 0xFF565F6B.toInt()
    private const val BORDER = 0xFFE3DDCE.toInt()
    private const val BAND = 0xFFF5F2EA.toInt()

    private const val MAX_PAGES = 200

    private val sans: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val sansBold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    private val mono: Typeface = Typeface.create("monospace", Typeface.NORMAL)
    private val monoBold: Typeface = Typeface.create("monospace", Typeface.BOLD)

    /**
     * Renders [document] and writes it to [out]. The caller owns [out] and closes it.
     *
     * Needs a device: [PdfDocument], [Canvas] and [Paint] are framework classes, which
     * is why the arithmetic worth testing lives in [InvoiceLayout] and [formatQty].
     */
    fun render(document: InvoiceDocument, out: OutputStream) {
        val pdf = PdfDocument()

        try {
            val columns = InvoiceLayout.tableColumns()
            val reserved = InvoiceLayout.totalsBlockHeight(document.discount != 0L)
            var next = 0
            var pageNumber = 1

            do {
                val page = pdf.startPage(
                    PdfDocument.PageInfo.Builder(
                        InvoiceLayout.PAGE_WIDTH,
                        InvoiceLayout.PAGE_HEIGHT,
                        pageNumber,
                    ).create()
                )
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                var y = if (pageNumber == 1) {
                    drawHeader(canvas, document)
                } else {
                    drawContinuationHeader(canvas, document)
                }

                y = drawTableHeader(canvas, columns, y)

                // Rows only get the space left once the totals have had their share,
                // so the last page always has room for them.
                val fit = InvoiceLayout.rowsThatFit(InvoiceLayout.CONTENT_BOTTOM - y - reserved)
                val end = minOf(next + fit, document.lines.size)

                y = if (document.lines.isEmpty()) {
                    drawNoLines(canvas, y)
                } else {
                    drawRows(canvas, columns, document.lines.subList(next, end), next, y)
                }

                next = end

                val done = next >= document.lines.size
                if (done) {
                    drawTotals(canvas, document, y)
                }

                pdf.finishPage(page)
                pageNumber++
                // A page that somehow fits no rows would otherwise loop for ever; a
                // truncated PDF is a bug, an app that hangs on Share is a worse one.
            } while (!done && pageNumber <= MAX_PAGES)

            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    // ------------------------------------------------------------------ header

    /** @return the y the body may start at. */
    private fun drawHeader(canvas: Canvas, document: InvoiceDocument): Float {
        val left = InvoiceLayout.MARGIN
        val right = InvoiceLayout.CONTENT_RIGHT
        var y = InvoiceLayout.MARGIN + 14f

        canvas.drawText(document.company.name, left, y, paint(15f, sansBold, PRIMARY))
        canvas.drawText(
            "TAX INVOICE",
            right,
            y,
            paint(13f, sansBold, PRIMARY, Paint.Align.RIGHT),
        )

        val muted = paint(8.5f, sans, MUTED)
        detailLines(document.company).forEach {
            y += 11f
            canvas.drawText(it, left, y, muted)
        }

        y += 12f
        canvas.drawLine(left, y, right, y, rule(2f, PRIMARY))
        y += 20f

        // Invoice No / Date / Plant sit side by side, as the print view's meta strip.
        val cells = listOf(
            "INVOICE NO" to document.number,
            "DATE" to formatDate(document.date),
            "PLANT" to document.plantName.ifBlank { "—" },
        )
        val label = paint(7.5f, sansBold, MUTED)
        cells.forEachIndexed { index, (caption, value) ->
            val x = left + index * 150f
            canvas.drawText(caption, x, y, label)
            val face = if (index == 0) monoBold else sans
            canvas.drawText(value, x, y + 13f, paint(10f, face, INK))
        }

        y += 30f
        canvas.drawText("BUYER", left, y, label)
        y += 13f
        canvas.drawText(document.buyer.name.ifBlank { "—" }, left, y, paint(10.5f, sansBold, INK))
        detailLines(document.buyer).forEach {
            y += 11f
            canvas.drawText(it, left, y, muted)
        }

        return y + 14f
    }

    /** Page 2 onward only needs enough to identify the document it belongs to. */
    private fun drawContinuationHeader(canvas: Canvas, document: InvoiceDocument): Float {
        val left = InvoiceLayout.MARGIN
        val right = InvoiceLayout.CONTENT_RIGHT
        var y = InvoiceLayout.MARGIN + 12f

        canvas.drawText(document.company.name, left, y, paint(11f, sansBold, PRIMARY))
        canvas.drawText(
            "TAX INVOICE ${document.number} (contd.)",
            right,
            y,
            paint(9f, sans, MUTED, Paint.Align.RIGHT),
        )

        y += 8f
        canvas.drawLine(left, y, right, y, rule(1f, PRIMARY))

        return y + 16f
    }

    /** Address, phone and GSTIN, each omitted when blank, exactly as the template does. */
    private fun detailLines(party: InvoiceParty): List<String> = buildList {
        party.address.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
        if (party.phone.isNotBlank()) add("Phone: ${party.phone}")
        if (party.gstin.isNotBlank()) add("GSTIN: ${party.gstin}")
    }

    // ------------------------------------------------------------------ table

    private fun drawTableHeader(canvas: Canvas, columns: TableColumns, top: Float): Float {
        val bottom = top + InvoiceLayout.TABLE_HEADER_HEIGHT
        canvas.drawRect(
            InvoiceLayout.MARGIN,
            top,
            InvoiceLayout.CONTENT_RIGHT,
            bottom,
            Paint().apply { color = BAND },
        )

        val heading = paint(7.5f, sansBold, MUTED)
        val headingRight = paint(7.5f, sansBold, MUTED, Paint.Align.RIGHT)
        val baseline = top + 12.5f

        canvas.drawText("#", columns.indexLeft + 4f, baseline, heading)
        canvas.drawText("ITEM", columns.itemLeft, baseline, heading)
        canvas.drawText("HSN", columns.hsnLeft, baseline, heading)
        canvas.drawText("QTY", columns.qtyRight, baseline, headingRight)
        canvas.drawText("RATE", columns.rateRight, baseline, headingRight)
        canvas.drawText("AMOUNT", columns.amountRight, baseline, headingRight)

        canvas.drawLine(
            InvoiceLayout.MARGIN,
            bottom,
            InvoiceLayout.CONTENT_RIGHT,
            bottom,
            rule(0.8f, BORDER),
        )

        return bottom
    }

    private fun drawRows(
        canvas: Canvas,
        columns: TableColumns,
        lines: List<InvoiceLine>,
        firstIndex: Int,
        top: Float,
    ): Float {
        val text = paint(9f, sans, INK)
        val figures = paint(9f, mono, INK, Paint.Align.RIGHT)
        val hsnPaint = paint(8.5f, mono, INK)
        val border = rule(0.7f, BORDER)
        var y = top

        lines.forEachIndexed { offset, line ->
            val baseline = y + 11f

            canvas.drawText("${firstIndex + offset + 1}", columns.indexLeft + 4f, baseline, text)
            canvas.drawText(
                // A long item name is clipped rather than allowed to run into the
                // HSN column, where it would look like a different field.
                ellipsize(line.itemName, text, columns.itemWidth - 6f),
                columns.itemLeft,
                baseline,
                text,
            )
            canvas.drawText(
                ellipsize(line.hsn.ifBlank { "—" }, hsnPaint, columns.hsnWidth - 6f),
                columns.hsnLeft,
                baseline,
                hsnPaint,
            )
            canvas.drawText(formatQtyWithUnit(line.qty, line.unit), columns.qtyRight, baseline, figures)
            canvas.drawText(formatMoney(line.rate), columns.rateRight, baseline, figures)
            canvas.drawText(formatMoney(line.amount), columns.amountRight, baseline, figures)

            y += InvoiceLayout.ROW_HEIGHT
            canvas.drawLine(InvoiceLayout.MARGIN, y, InvoiceLayout.CONTENT_RIGHT, y, border)
        }

        return y
    }

    private fun drawNoLines(canvas: Canvas, top: Float): Float {
        canvas.drawText(
            "No line items.",
            InvoiceLayout.MARGIN,
            top + 12f,
            paint(9f, sans, MUTED),
        )

        return top + InvoiceLayout.ROW_HEIGHT
    }

    // ------------------------------------------------------------------ totals

    private fun drawTotals(canvas: Canvas, document: InvoiceDocument, top: Float) {
        val right = InvoiceLayout.CONTENT_RIGHT
        val left = right - InvoiceLayout.TOTALS_WIDTH
        val caption = paint(9.5f, sans, INK)
        val figure = paint(9.5f, mono, INK, Paint.Align.RIGHT)
        var y = top + InvoiceLayout.TOTALS_TOP_GAP + 10f

        canvas.drawText("Subtotal", left, y, caption)
        canvas.drawText(formatMoney(document.subtotal), right, y, figure)

        if (document.discount != 0L) {
            y += InvoiceLayout.TOTALS_ROW_HEIGHT
            canvas.drawText("Discount", left, y, caption)
            canvas.drawText("− ${formatMoney(document.discount)}", right, y, figure)
        }

        y += InvoiceLayout.GRAND_TOTAL_EXTRA
        canvas.drawLine(left, y, right, y, rule(2f, PRIMARY))
        y += InvoiceLayout.TOTALS_ROW_HEIGHT

        canvas.drawText("Total", left, y, paint(11.5f, sansBold, INK))
        canvas.drawText(formatMoney(document.total), right, y, paint(11.5f, monoBold, INK, Paint.Align.RIGHT))

        // "For <company>", over a rule, as the print view signs off.
        y += 44f
        canvas.drawLine(right - 210f, y, right, y, rule(0.8f, INK))
        if (document.company.name.isNotBlank()) {
            canvas.drawText(
                "For ${document.company.name}",
                right - 105f,
                y + 12f,
                paint(9f, sans, INK, Paint.Align.CENTER),
            )
        }
    }

    // ------------------------------------------------------------------ paint helpers

    private fun paint(
        size: Float,
        face: Typeface,
        colour: Int,
        align: Paint.Align = Paint.Align.LEFT,
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = face
        color = colour
        textAlign = align
    }

    private fun rule(width: Float, colour: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        strokeWidth = width
    }

    private fun ellipsize(text: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()
}
