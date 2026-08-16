package br.com.sprena.shared.establishment.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O CNPJ e a chave de unicidade do estabelecimento: o id do doc em `cnpj_index` sao os
 * 14 digitos, e e o `create` sobre id existente que impede cadastrar o mesmo
 * estabelecimento duas vezes. As rules so conseguem checar a forma (`^[0-9]{14}$`) —
 * aritmetica de digito verificador nao existe no motor de rules. E aqui que ela mora.
 */
class CnpjValidatorTest {
    @Test
    fun `aceita CNPJ valido sem formatacao`() {
        assertTrue(CnpjValidator.isValid("11222333000181"))
        assertTrue(CnpjValidator.isValid("34028316000103"))
    }

    @Test
    fun `aceita CNPJ valido com mascara`() {
        assertTrue(CnpjValidator.isValid("11.222.333/0001-81"))
        assertTrue(CnpjValidator.isValid("34.028.316/0001-03"))
    }

    @Test
    fun `rejeita digito verificador errado`() {
        assertFalse(CnpjValidator.isValid("11222333000182"))
        assertFalse(CnpjValidator.isValid("11222333000191"))
        assertFalse(CnpjValidator.isValid("34028316000100"))
    }

    @Test
    fun `rejeita quantidade de digitos diferente de 14`() {
        assertFalse(CnpjValidator.isValid("1122233300018"))
        assertFalse(CnpjValidator.isValid("112223330001811"))
        assertFalse(CnpjValidator.isValid(""))
    }

    @Test
    fun `rejeita sequencia de digitos repetidos`() {
        for (d in '0'..'9') {
            assertFalse(CnpjValidator.isValid(d.toString().repeat(14)), "repetido $d deveria falhar")
        }
    }

    @Test
    fun `digits devolve apenas os digitos — e o que vai para o id do indice`() {
        assertEquals("11222333000181", CnpjValidator.digits("11.222.333/0001-81"))
        assertEquals("", CnpjValidator.digits("sem digitos"))
    }

    @Test
    fun `validate distingue campo vazio, formato e digito verificador`() {
        assertTrue(CnpjValidator.validate("11.222.333/0001-81").isValid)

        val vazio = CnpjValidator.validate("  ")
        assertFalse(vazio.isValid)
        assertEquals("CNPJ é obrigatório", vazio.errorMessage)

        val curto = CnpjValidator.validate("11222333")
        assertFalse(curto.isValid)
        assertEquals("CNPJ deve ter 14 dígitos", curto.errorMessage)

        val invalido = CnpjValidator.validate("11222333000182")
        assertFalse(invalido.isValid)
        assertEquals("CNPJ inválido", invalido.errorMessage)
    }
}
