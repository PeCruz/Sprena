package br.com.sprena.shared.core.privacy

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TDD — masking de telefone para exibição (F1.6a).
 *
 * Regra: entrada válida mostra DDD + os 4 últimos dígitos. Entrada inválida
 * NUNCA vaza dígito — mascara tudo, mesma postura de [maskCpf].
 */
class PhoneMaskerTest {
    @Test
    fun `mascara celular de 11 digitos preservando DDD e os quatro ultimos`() {
        assertEquals("(11) *****-4321", maskPhone("11987654321"))
    }

    @Test
    fun `mascara telefone fixo de 10 digitos`() {
        assertEquals("(11) ****-4444", maskPhone("1133334444"))
    }

    @Test
    fun `mascara telefone ja formatado — ignora pontuacao na entrada`() {
        assertEquals("(11) *****-4321", maskPhone("(11) 98765-4321"))
    }

    @Test
    fun `mascara telefone com DDI vira mascara completa — DDI nao e suportado`() {
        // "+5511987654321" normaliza para 13 dígitos: fora da faixa 10–11 do campo,
        // que guarda apenas DDD + número.
        assertEquals("(**) *****-****", maskPhone("+5511987654321"))
    }

    @Test
    fun `entrada vazia vira mascara completa`() {
        assertEquals("(**) *****-****", maskPhone(""))
    }

    @Test
    fun `entrada curta vira mascara completa — nao vaza digito parcial`() {
        assertEquals("(**) *****-****", maskPhone("11987"))
    }

    @Test
    fun `entrada longa demais vira mascara completa`() {
        assertEquals("(**) *****-****", maskPhone("119876543210"))
    }

    @Test
    fun `entrada sem digitos vira mascara completa`() {
        assertEquals("(**) *****-****", maskPhone("telefone"))
    }

    @Test
    fun `formata celular valido para exibicao`() {
        assertEquals("(11) 98765-4321", formatPhone("11987654321"))
    }

    @Test
    fun `formata fixo valido para exibicao`() {
        assertEquals("(11) 3333-4444", formatPhone("1133334444"))
    }

    @Test
    fun `formata devolve a entrada crua quando nao ha 10 ou 11 digitos`() {
        assertEquals("11987", formatPhone("11987"))
    }
}
