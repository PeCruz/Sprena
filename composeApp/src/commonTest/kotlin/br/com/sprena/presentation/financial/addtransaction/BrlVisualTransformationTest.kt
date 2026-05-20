package br.com.sprena.presentation.financial.addtransaction

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TDD — BrlVisualTransformation
 *
 * Testa a máscara de formatação BRL em tempo real.
 * O campo recebe apenas dígitos (ex.: "12345") e a
 * VisualTransformation exibe "R$ 123,45".
 *
 * Cenários:
 *  - Entrada vazia → R$ 0,00
 *  - 1 dígito → centavos (R$ 0,01)
 *  - 2 dígitos → centavos (R$ 0,10)
 *  - 3 dígitos → 1 real + centavos
 *  - Separador de milhar
 *  - Valores altos (milhão)
 *  - Offset mapping: original→transformed e transformed→original
 */
class BrlVisualTransformationTest {
    private val transformation = BrlVisualTransformation()

    private fun transform(digits: String): String {
        val result = transformation.filter(AnnotatedString(digits))
        return result.text.text
    }

    // ── Formatação — Valores básicos ────────────────────

    @Test
    fun `empty input formats as R$ 0,00`() {
        assertEquals("R$ 0,00", transform(""))
    }

    @Test
    fun `single digit 1 formats as R$ 0,01`() {
        assertEquals("R$ 0,01", transform("1"))
    }

    @Test
    fun `single digit 5 formats as R$ 0,05`() {
        assertEquals("R$ 0,05", transform("5"))
    }

    @Test
    fun `two digits 10 formats as R$ 0,10`() {
        assertEquals("R$ 0,10", transform("10"))
    }

    @Test
    fun `two digits 99 formats as R$ 0,99`() {
        assertEquals("R$ 0,99", transform("99"))
    }

    @Test
    fun `three digits 100 formats as R$ 1,00`() {
        assertEquals("R$ 1,00", transform("100"))
    }

    @Test
    fun `three digits 123 formats as R$ 1,23`() {
        assertEquals("R$ 1,23", transform("123"))
    }

    // ── Formatação — Separador de milhar ────────────────

    @Test
    fun `four digits 1000 formats as R$ 10,00`() {
        assertEquals("R$ 10,00", transform("1000"))
    }

    @Test
    fun `five digits 10000 formats as R$ 100,00`() {
        assertEquals("R$ 100,00", transform("10000"))
    }

    @Test
    fun `six digits 100000 formats as R$ 1.000,00`() {
        assertEquals("R$ 1.000,00", transform("100000"))
    }

    @Test
    fun `six digits 123456 formats as R$ 1.234,56`() {
        assertEquals("R$ 1.234,56", transform("123456"))
    }

    // ── Formatação — Valores altos ──────────────────────

    @Test
    fun `seven digits formats with thousand separator`() {
        assertEquals("R$ 12.345,67", transform("1234567"))
    }

    @Test
    fun `nine digits formats as million`() {
        assertEquals("R$ 1.234.567,89", transform("123456789"))
    }

    @Test
    fun `ten digits formats as ten millions`() {
        assertEquals("R$ 12.345.678,90", transform("1234567890"))
    }

    // ── Formatação — Zero explícito ─────────────────────

    @Test
    fun `all zeros 000 formats as R$ 0,00`() {
        assertEquals("R$ 0,00", transform("000"))
    }

    @Test
    fun `single zero formats as R$ 0,00`() {
        assertEquals("R$ 0,00", transform("0"))
    }

    // ── Offset Mapping ──────────────────────────────────

    @Test
    fun `originalToTransformed maps to end of formatted string`() {
        val result = transformation.filter(AnnotatedString("12345"))
        val formatted = result.text.text // "R$ 123,45"
        // Qualquer offset no original deve mapear para o final do texto formatado
        assertEquals(formatted.length, result.offsetMapping.originalToTransformed(0))
        assertEquals(formatted.length, result.offsetMapping.originalToTransformed(3))
        assertEquals(formatted.length, result.offsetMapping.originalToTransformed(5))
    }

    @Test
    fun `transformedToOriginal maps to end of digits string`() {
        val result = transformation.filter(AnnotatedString("12345"))
        // Qualquer offset no transformado deve mapear para o final dos dígitos
        assertEquals(5, result.offsetMapping.transformedToOriginal(0))
        assertEquals(5, result.offsetMapping.transformedToOriginal(5))
        assertEquals(5, result.offsetMapping.transformedToOriginal(9))
    }

    @Test
    fun `offset mapping for empty input`() {
        val result = transformation.filter(AnnotatedString(""))
        assertEquals(result.text.text.length, result.offsetMapping.originalToTransformed(0))
        assertEquals(0, result.offsetMapping.transformedToOriginal(0))
    }
}
