package br.com.sprena.shared.core.logger.pii

import kotlin.test.Test
import kotlin.test.assertEquals

class PiiScrubberTest {
    @Test
    fun `scrubs formatted cpf in middle of message`() {
        val input = "Cliente cadastrado: CPF 123.456.789-90 confirmado"
        val expected = "Cliente cadastrado: CPF ***.***.***-** confirmado"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun `scrubs unformatted 11-digit cpf when prefixed by cpf keyword`() {
        val input = "cpf=12345678990 falhou"
        val expected = "cpf=*********** falhou"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun `scrubs email anywhere in message`() {
        val input = "Falha no login para pedro@gmail.com retry"
        val expected = "Falha no login para ***@*** retry"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun `scrubs multiple PIIs in same message`() {
        val input = "User pedro@gmail.com com CPF 111.222.333-44 logou"
        val expected = "User ***@*** com CPF ***.***.***-** logou"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun `scrubs password keyword followed by value`() {
        val input = "tentativa com password=secret123 negada"
        val expected = "tentativa com password=*** negada"
        assertEquals(expected, PiiScrubber.scrub(input))
    }

    @Test
    fun `passes through message without PII unchanged`() {
        val input = "Firestore add document ok"
        assertEquals(input, PiiScrubber.scrub(input))
    }

    @Test
    fun `null returns null`() {
        assertEquals(null, PiiScrubber.scrub(null))
    }

    @Test
    fun `empty returns empty`() {
        assertEquals("", PiiScrubber.scrub(""))
    }
}
