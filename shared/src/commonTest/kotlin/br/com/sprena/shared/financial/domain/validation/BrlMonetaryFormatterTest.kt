package br.com.sprena.shared.financial.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes do formatador / validador monetário BRL.
 * Valores internos em centavos (Long).
 */
class BrlMonetaryFormatterTest {
    // ------------------------------------------------------------------
    // parseToCents — Negative
    // ------------------------------------------------------------------

    @Test
    fun `parse empty string returns null`() {
        assertNull(BrlMonetaryFormatter.parseToCents(""))
    }

    @Test
    fun `parse letters returns null`() {
        assertNull(BrlMonetaryFormatter.parseToCents("abc"))
    }

    @Test
    fun `parse random garbage returns null`() {
        assertNull(BrlMonetaryFormatter.parseToCents("R$ ??"))
    }

    // ------------------------------------------------------------------
    // parseToCents — Positive
    // ------------------------------------------------------------------

    @Test
    fun `parse simple decimal with comma`() {
        assertEquals(10L, BrlMonetaryFormatter.parseToCents("0,10"))
    }

    @Test
    fun `parse with R$ prefix and thousand separator`() {
        assertEquals(123_456L, BrlMonetaryFormatter.parseToCents("R$ 1.234,56"))
    }

    @Test
    fun `parse without prefix`() {
        assertEquals(123_456L, BrlMonetaryFormatter.parseToCents("1.234,56"))
    }

    @Test
    fun `parse integer part only`() {
        assertEquals(10_000L, BrlMonetaryFormatter.parseToCents("100"))
    }

    // ------------------------------------------------------------------
    // formatCents — Positive
    // ------------------------------------------------------------------

    @Test
    fun `format zero`() {
        assertEquals("R$ 0,00", BrlMonetaryFormatter.formatCents(0L))
    }

    @Test
    fun `format below one real`() {
        assertEquals("R$ 0,10", BrlMonetaryFormatter.formatCents(10L))
    }

    @Test
    fun `format with thousand separator`() {
        assertEquals("R$ 1.234,56", BrlMonetaryFormatter.formatCents(123_456L))
    }

    @Test
    fun `format large amount`() {
        assertEquals("R$ 1.000.000,00", BrlMonetaryFormatter.formatCents(100_000_000L))
    }

    // ------------------------------------------------------------------
    // validate — Negative
    // ------------------------------------------------------------------

    @Test
    fun `validate empty is invalid`() {
        assertFalse(BrlMonetaryFormatter.validate("").isValid)
    }

    @Test
    fun `validate letters is invalid`() {
        assertFalse(BrlMonetaryFormatter.validate("abc").isValid)
    }

    @Test
    fun `validate zero value is invalid`() {
        assertFalse(BrlMonetaryFormatter.validate("0,00").isValid)
    }

    // ------------------------------------------------------------------
    // validate — Positive
    // ------------------------------------------------------------------

    @Test
    fun `validate well formed BRL is valid`() {
        assertTrue(BrlMonetaryFormatter.validate("R$ 1.234,56").isValid)
    }

    @Test
    fun `validate minimum non-zero value is valid`() {
        assertTrue(BrlMonetaryFormatter.validate("0,01").isValid)
    }
}
