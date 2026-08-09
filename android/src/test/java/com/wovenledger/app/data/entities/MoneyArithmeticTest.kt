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
     * `qty * rate` is IEEE-754 double arithmetic, so a product can land a hair under a
     * whole paise: 2.3 units at ₹500.00 computes as 114999.99999999999. Truncating
     * stored that as 114999 and showed ₹1,149.99 where the portal said ₹1,150.00.
     *
     * The same expression backs [SalesInvoiceLine.amount], [PurchaseBillLine.amount]
     * and [StaffWork.getAmount], so the shortfall compounded across a document. All
     * three now round through `amountOf`.
     */
    @Test
    fun `a wage is rounded to the nearest paise, not truncated`() {
        val work = staffWork(qty = 2.3, rate = 50_000L)

        assertEquals(115_000L, work.getAmount())
    }

    @Test
    fun `a line amount is rounded to the nearest paise, not truncated`() {
        val line = SalesInvoiceLine(invoiceId = 1L, itemId = 1L, qty = 0.25, rate = 1_250L)

        assertEquals(313L, line.amount)
    }

    @Test
    fun `a bill line rounds the same way an invoice line does`() {
        val line = PurchaseBillLine(billId = 1L, itemId = 1L, qty = 33.3, rate = 15_000L)

        assertEquals(499_500L, line.amount)
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
