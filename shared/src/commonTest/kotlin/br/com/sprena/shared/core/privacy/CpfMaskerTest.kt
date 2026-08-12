package br.com.sprena.shared.core.privacy

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TDD — masking de CPF para exibição (F1.5).
 *
 * Regra: entrada válida mostra só os 3 últimos dígitos do corpo + os 2 do DV.
 * Entrada inválida NUNCA vaza dígito — mascara tudo.
 */
class CpfMaskerTest {
    @Test
    fun `mascara CPF valido preservando os tres ultimos digitos e o DV`() {
        assertEquals("***.***.789-00", maskCpf("12345678900"))
    }

    @Test
    fun `mascara CPF ja formatado — ignora pontuacao na entrada`() {
        assertEquals("***.***.789-00", maskCpf("123.456.789-00"))
    }

    @Test
    fun `entrada vazia vira mascara completa`() {
        assertEquals("***.***.***-**", maskCpf(""))
    }

    @Test
    fun `entrada curta vira mascara completa — nao vaza digito parcial`() {
        assertEquals("***.***.***-**", maskCpf("123"))
    }

    @Test
    fun `entrada longa demais vira mascara completa`() {
        assertEquals("***.***.***-**", maskCpf("123456789012"))
    }

    @Test
    fun `entrada sem digitos vira mascara completa`() {
        assertEquals("***.***.***-**", maskCpf("abcdefghijk"))
    }

    @Test
    fun `formata CPF valido para exibicao`() {
        assertEquals("123.456.789-00", formatCpf("12345678900"))
    }

    @Test
    fun `formata devolve a entrada crua quando nao ha 11 digitos`() {
        assertEquals("123", formatCpf("123"))
    }
}
