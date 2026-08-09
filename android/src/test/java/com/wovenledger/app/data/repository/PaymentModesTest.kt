package com.wovenledger.app.data.repository

import com.wovenledger.app.data.entities.PaymentMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The payment mode crosses the wire as a human label ("Bank Transfer") and comes back
 * as an enum. The two halves of that translation live apart — one is used when saving,
 * the other when syncing — so nothing but a test holds them together.
 */
class PaymentModesTest {

    /**
     * The round trip is the contract: whatever the app sends for a mode has to parse
     * back to that same mode. Rename a label on one side only and payments quietly
     * come home as cash.
     */
    @Test
    fun `every mode survives a round trip through the wire label`() {
        PaymentMode.entries.forEach { mode ->
            assertEquals(mode, paymentModeOf(mode.toWire()))
        }
    }

    /** The labels the server's allow-list accepts, spelled as it spells them. */
    @Test
    fun `the wire labels are the ones the server accepts`() {
        assertEquals("Cash", PaymentMode.CASH.toWire())
        assertEquals("Bank Transfer", PaymentMode.BANK_TRANSFER.toWire())
        assertEquals("UPI", PaymentMode.UPI.toWire())
        assertEquals("Cheque", PaymentMode.CHEQUE.toWire())
    }

    /**
     * Parsing is deliberately forgiving — an unknown or absent mode becomes cash
     * instead of throwing, because one odd label must not cost the user a voucher.
     */
    @Test
    fun `an unknown or absent label falls back to cash`() {
        assertEquals(PaymentMode.CASH, paymentModeOf(null))
        assertEquals(PaymentMode.CASH, paymentModeOf(""))
        assertEquals(PaymentMode.CASH, paymentModeOf("Crypto"))
    }
}
