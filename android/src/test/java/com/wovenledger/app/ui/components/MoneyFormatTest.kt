package com.wovenledger.app.ui.components

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.util.Locale

/**
 * Every rupee figure in the app goes through [formatMoney], so a regression here
 * misstates every screen at once and does it silently — the output still looks like
 * money. The expected strings below are therefore spelled out in full rather than
 * derived from a NumberFormat in the test, which would only restate the implementation.
 *
 * The currency symbol asserted is U+20B9 INDIAN RUPEE SIGN, not "Rs".
 */
class MoneyFormatTest {

    private lateinit var originalLocale: Locale

    /**
     * The JVM default locale is deliberately set to something that is neither Indian
     * nor English for the duration of these tests: the amounts must be formatted from
     * the app's own en-IN locale, never from whatever the device happens to be set to.
     * A switch to `NumberFormat.getCurrencyInstance()` (no locale) would render euros
     * here and pass unnoticed if the tests ran under the default locale.
     */
    @Before
    fun forceForeignDefaultLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
    }

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `zero renders as a rupee amount, not a blank or a dash`() {
        assertEquals("₹0.00", formatMoney(0L))
    }

    @Test
    fun `a whole number of rupees keeps its two decimal places`() {
        assertEquals("₹1,000.00", formatMoney(100_000L))
    }

    /**
     * The single most valuable case: paise are integers, so dropping the `/ 100`
     * would render this as ₹91,575.00 — a hundredfold overstatement that still reads
     * as a plausible figure.
     */
    @Test
    fun `paise are rendered as the fractional part, not as whole rupees`() {
        assertEquals("₹915.75", formatMoney(91_575L))
    }

    /** Guards the same division against being done in integer arithmetic. */
    @Test
    fun `sub-rupee amounts survive the conversion`() {
        assertEquals("₹0.01", formatMoney(1L))
        assertEquals("₹0.99", formatMoney(99L))
    }

    @Test
    fun `a negative amount keeps its sign ahead of the symbol`() {
        assertEquals("-₹2,500.50", formatMoney(-250_050L))
        assertEquals("-₹0.01", formatMoney(-1L))
    }

    /**
     * Lakh-scale amounts, asserted without committing to a grouping style.
     *
     * The digits are what a scaling regression would corrupt: dropping the `/ 100`
     * turns this into 91151000.00. Grouping separators are excluded because they
     * differ between the JVM and the device — see
     * [lakh scale uses Indian digit grouping] below.
     */
    @Test
    fun `a lakh-scale amount is scaled correctly whatever the grouping`() {
        val formatted = formatMoney(91_151_000L)

        assertEquals("911510.00", formatted.filter { it.isDigit() || it == '.' })
        assertTrue("expected a rupee symbol in $formatted", formatted.startsWith("₹"))
    }

    /**
     * Disabled: this asserts the DEVICE's output, which a JVM unit test cannot produce.
     *
     * On Android `java.text.NumberFormat` is backed by ICU, whose en-IN currency
     * pattern is `¤#,##,##0.00`, so the app really does render ₹9,11,510.00 on a phone.
     * The JDK's own locale data for en-IN uses `¤#,##0.00` (verified on JDK 17 with
     * jdk.localedata present — it has no Indian grouping for any Indian locale), so the
     * same call returns "₹911,510.00" here.
     *
     * This is a JVM/Android divergence, NOT a production bug: nothing in the app is
     * wrong. It is kept as a disabled test so the intended device behaviour is written
     * down, and because it is the reason the test above asserts digits only. Move it to
     * androidTest if the grouping ever needs a real guard.
     */
    @Ignore("JVM locale data has no Indian digit grouping; only reproducible on device/ICU")
    @Test
    fun `lakh scale uses Indian digit grouping`() {
        assertEquals("₹9,11,510.00", formatMoney(91_151_000L))
    }
}
