package com.wovenledger.app.data.entities

import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import java.time.LocalDate

/**
 * Line amounts and wages are derived, not stored: a quantity (a Double) times a rate
 * (integer paise). Those derivations are the only arithmetic in the entity layer and
 * they feed straight into what a customer is invoiced and what a worker is paid.
 */
class MoneyArithmeticTest {

    @Test
    fun `a line amount is quantity times rate, in paise`() {
        val line = SalesInvoiceLine(invoiceId = 1L, itemId = 1L, qty = 3.0, rate = 12_500L)

        assertEquals(37_500L, line.amount)
    }

    @Test
    fun `a fractional quantity scales the rate`() {
        // 12.5 kg at ₹80.00/kg = ₹1,000.00
        val line = SalesInvoiceLine(invoiceId = 1L, itemId = 1L, qty = 12.5, rate = 8_000L)

        assertEquals(100_000L, line.amount)
    }

    @Test
    fun `an explicit amount overrides the derived one`() {
        // The default is only a default: a negotiated line price must survive.
        val line = SalesInvoiceLine(
            invoiceId = 1L,
            itemId = 1L,
            qty = 3.0,
            rate = 12_500L,
            amount = 35_000L,
        )

        assertEquals(35_000L, line.amount)
    }

    @Test
    fun `a day's work is worth quantity times rate`() {
        val work = staffWork(qty = 2.0, rate = 50_000L)

        assertEquals(100_000L, work.getAmount())
    }

    @Test
    fun `the effective wage rate follows the wage type`() {
        val daily = staff(wageType = StaffWageType.DAILY, dailyRate = 50_000L, pieceRate = 250L)
        val piece = staff(wageType = StaffWageType.PIECE, dailyRate = 50_000L, pieceRate = 250L)

        assertEquals(50_000L, daily.getEffectiveRate())
        assertEquals(250L, piece.getEffectiveRate())
    }

    /**
     * DISABLED — this documents a production bug, it is not a regression guard.
     *
     * `qty * rate` is IEEE-754 double arithmetic and `.toLong()` TRUNCATES, so any
     * product that lands a hair under a whole paise loses one. 2.3 units at ₹500.00
     * gives 114999.99999999999, stored as 114999 paise: the app shows ₹1,149.99 where
     * every other system in the business says ₹1,150.00.
     *
     * Reproduced with 2.3 × 50000 here; the same holds for 0.25 × 1250 (312 not 313),
     * 33.3 × 15000 (499499 not 499500) and many other ordinary quantities. The same
     * expression appears in [SalesInvoiceLine.amount], [PurchaseBillLine.amount] and
     * [StaffWork.getAmount], so an invoice line, a bill line and a wage can all be a
     * paise short, and the discrepancy compounds across a multi-line document.
     *
     * The fix is `Math.round(qty * rate)` (or BigDecimal) in all three places — not
     * applied here: production code is owned elsewhere in this change.
     */
    @Ignore("Bug: (qty * rate).toLong() truncates instead of rounding — see kdoc")
    @Test
    fun `a wage is rounded to the nearest paise, not truncated`() {
        val work = staffWork(qty = 2.3, rate = 50_000L)

        assertEquals(115_000L, work.getAmount())
    }

    @Ignore("Bug: (qty * rate).toLong() truncates instead of rounding — see kdoc above")
    @Test
    fun `a line amount is rounded to the nearest paise, not truncated`() {
        val line = SalesInvoiceLine(invoiceId = 1L, itemId = 1L, qty = 0.25, rate = 1_250L)

        assertEquals(313L, line.amount)
    }

    private fun staffWork(qty: Double, rate: Long) = StaffWork(
        id = 1L,
        date = LocalDate.of(2026, 4, 1),
        staffId = 1L,
        plantId = 1L,
        workType = "weaving",
        qty = qty,
        rate = rate,
    )

    private fun staff(wageType: StaffWageType, dailyRate: Long, pieceRate: Long) = Staff(
        id = 1L,
        name = "Ramesh",
        role = "weaver",
        plantId = 1L,
        wageType = wageType,
        dailyRate = dailyRate,
        pieceRate = pieceRate,
    )
}
