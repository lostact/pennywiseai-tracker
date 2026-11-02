package com.pennywiseai.tracker.utils

import com.pennywiseai.tracker.data.preferences.IranianAmountUnit
import com.pennywiseai.tracker.data.preferences.NumberFormatStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

/**
 * Iranian display options: Rial vs Toman (Rial ÷ 10), and Persian vs Latin digits.
 *
 * Toman is a *display* choice only — the parsers and the database keep Rial because
 * that is what the bank SMS quotes, so `formatCurrency` must divide by ten at render
 * time and never suggest a different stored value.
 */
class IranianAmountFormatTest {

    private val balance = BigDecimal("314143824") // 314,143,824 Rial = 31,414,382 Toman

    private lateinit var originalDefault: Locale

    @Before
    fun setUp() {
        originalDefault = Locale.getDefault()
        // Latin default keeps `ltr()` inert, so the assertions don't depend on UI language.
        Locale.setDefault(Locale.US)
        CurrencyFormatter.numberFormatStyle = NumberFormatStyle.AUTO
        CurrencyFormatter.iranianAmountUnit = IranianAmountUnit.RIAL
        CurrencyFormatter.iranianPersianDigits = true
    }

    @After
    fun tearDown() {
        CurrencyFormatter.numberFormatStyle = NumberFormatStyle.AUTO
        CurrencyFormatter.iranianAmountUnit = IranianAmountUnit.RIAL
        CurrencyFormatter.iranianPersianDigits = true
        Locale.setDefault(originalDefault)
    }

    @Test
    fun `rial with English digits renders the Latin code after the figure`() {
        CurrencyFormatter.iranianPersianDigits = false
        assertEquals("314,143,824 IRR", CurrencyFormatter.formatCurrency(balance, "IRR"))
    }

    @Test
    fun `toman with English digits divides by ten and trails a Latin T`() {
        CurrencyFormatter.iranianAmountUnit = IranianAmountUnit.TOMAN
        CurrencyFormatter.iranianPersianDigits = false
        assertEquals("31,414,382 T", CurrencyFormatter.formatCurrency(balance, "IRR"))
    }

    @Test
    fun `toman keeps no decimals so a whole figure never shows a fraction`() {
        CurrencyFormatter.iranianAmountUnit = IranianAmountUnit.TOMAN
        CurrencyFormatter.iranianPersianDigits = false
        // 3,353,975 Rial = 335,397.5 Toman — shown whole.
        assertEquals("335,398 T", CurrencyFormatter.formatCurrency(BigDecimal("3353975"), "IRR"))
    }

    @Test
    fun `the unit trails the figure in Persian digits too`() {
        CurrencyFormatter.iranianAmountUnit = IranianAmountUnit.TOMAN
        val persian = CurrencyFormatter.formatCurrency(balance, "IRR")
        assertTrue("unit must trail the figure: $persian", persian.trimEnd().endsWith("T"))
        assertTrue("expected Persian digits in $persian", persian.any { it in '۰'..'۹' })
    }

    @Test
    fun `persian digits stay the default and change with the flag`() {
        val persian = CurrencyFormatter.formatCurrency(balance, "IRR")
        assertTrue("expected Persian digits in $persian", persian.any { it in '۰'..'۹' })

        CurrencyFormatter.iranianPersianDigits = false
        val latin = CurrencyFormatter.formatCurrency(balance, "IRR")
        assertFalse("expected no Persian digits in $latin", latin.any { it in '۰'..'۹' })
    }

    @Test
    fun `other currencies ignore the Iranian unit and digits`() {
        // Compare each currency against itself: the Iranian options must be a no-op
        // for non-IRR amounts, whatever the platform's own grouping looks like.
        val usd = CurrencyFormatter.formatCurrency(BigDecimal("1234.56"), "USD")
        val inr = CurrencyFormatter.formatCurrency(BigDecimal("150000"), "INR")

        CurrencyFormatter.iranianAmountUnit = IranianAmountUnit.TOMAN
        CurrencyFormatter.iranianPersianDigits = false

        assertEquals(usd, CurrencyFormatter.formatCurrency(BigDecimal("1234.56"), "USD"))
        assertEquals(inr, CurrencyFormatter.formatCurrency(BigDecimal("150000"), "INR"))
        assertTrue("expected a dollar figure, got $usd", usd.startsWith("$"))
    }

    @Test
    fun `entry fields keep the stored unit so typing stays Rial`() {
        CurrencyFormatter.iranianAmountUnit = IranianAmountUnit.TOMAN
        // Display says "T"; an input field must still say Rial, or a typed 30,000,000
        // would be saved as 30,000,000 Rial (a tenth of the intended figure).
        assertEquals("T", CurrencyFormatter.getCurrencySymbol("IRR"))
        assertFalse("T" == CurrencyFormatter.inputSymbol("IRR"))
        CurrencyFormatter.iranianPersianDigits = false
        assertEquals("IRR", CurrencyFormatter.inputSymbol("IRR"))
    }
}
