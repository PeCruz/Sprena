package br.com.sprena.shared.core.logger.pii

import kotlin.test.Test
import kotlin.test.assertEquals

class PiiMaskerTest {
    // --- CPF ---
    @Test
    fun `cpf with 11 digits unformatted returns masked keeping last 2 digits`() {
        assertEquals("***.***.***-90", PiiMasker.cpf("12345678990"))
    }

    @Test
    fun `cpf formatted with dots and dash returns masked`() {
        assertEquals("***.***.***-90", PiiMasker.cpf("123.456.789-90"))
    }

    @Test
    fun `cpf null returns null`() {
        assertEquals(null, PiiMasker.cpf(null))
    }

    @Test
    fun `cpf blank returns empty string`() {
        assertEquals("", PiiMasker.cpf(""))
    }

    @Test
    fun `cpf with less than 11 digits returns all asterisks no last digits exposed`() {
        assertEquals("***", PiiMasker.cpf("1234"))
    }

    // --- Phone ---
    @Test
    fun `phone 11 digits returns masked keeping ddd and last 2`() {
        assertEquals("(11)*******-21", PiiMasker.phone("11987654321"))
    }

    @Test
    fun `phone 10 digits (landline) returns masked keeping ddd and last 2`() {
        assertEquals("(11)******-21", PiiMasker.phone("1132654321"))
    }

    @Test
    fun `phone null returns null`() {
        assertEquals(null, PiiMasker.phone(null))
    }

    @Test
    fun `phone with formatting returns masked`() {
        assertEquals("(11)*******-21", PiiMasker.phone("(11) 98765-4321"))
    }

    // --- Email ---
    @Test
    fun `email returns first char of local part plus domain`() {
        assertEquals("p***@gmail.com", PiiMasker.email("pedro@gmail.com"))
    }

    @Test
    fun `email with single char local returns mask of that char plus domain`() {
        assertEquals("*@gmail.com", PiiMasker.email("a@gmail.com"))
    }

    @Test
    fun `email null returns null`() {
        assertEquals(null, PiiMasker.email(null))
    }

    @Test
    fun `email without arroba returns all asterisks`() {
        assertEquals("***", PiiMasker.email("not-an-email"))
    }
}
