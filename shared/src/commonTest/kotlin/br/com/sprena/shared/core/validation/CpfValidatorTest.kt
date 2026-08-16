package br.com.sprena.shared.core.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O CPF vira identificador de vinculo em F1.7.5: e por ele que um pre-cadastro feito pelo
 * CLIENT encontra a conta da pessoa no primeiro login. Um digito trocado nao "erra por
 * pouco" — cria um vinculo pendente que nunca sera reclamado, ou pior, reclamavel por
 * outra pessoa. Dai a validacao de digito verificador, que os validadores anteriores
 * (SportClientValidator, ProfileValidator) nao fazem: eles so contam 11 digitos.
 */
class CpfValidatorTest {
    @Test
    fun `aceita CPF valido sem formatacao`() {
        assertTrue(CpfValidator.isValid("11144477735"))
        assertTrue(CpfValidator.isValid("52998224725"))
    }

    @Test
    fun `aceita CPF valido com pontuacao`() {
        assertTrue(CpfValidator.isValid("111.444.777-35"))
        assertTrue(CpfValidator.isValid("529.982.247-25"))
    }

    @Test
    fun `rejeita digito verificador errado`() {
        assertFalse(CpfValidator.isValid("11144477700"))
        assertFalse(CpfValidator.isValid("11144477734"))
        assertFalse(CpfValidator.isValid("52998224726"))
    }

    @Test
    fun `rejeita quantidade de digitos diferente de 11`() {
        assertFalse(CpfValidator.isValid("1114447773"))
        assertFalse(CpfValidator.isValid("111444777351"))
        assertFalse(CpfValidator.isValid(""))
    }

    @Test
    fun `rejeita sequencia de digitos repetidos`() {
        // Passam na aritmetica dos digitos verificadores — por isso precisam de checagem
        // propria. Sem ela, 111.111.111-11 seria aceito como CPF valido.
        for (d in '0'..'9') {
            assertFalse(CpfValidator.isValid(d.toString().repeat(11)), "repetido $d deveria falhar")
        }
    }

    @Test
    fun `normaliza descartando tudo que nao e digito`() {
        assertTrue(CpfValidator.isValid(" 111.444.777-35 "))
        assertFalse(CpfValidator.isValid("abc"))
    }

    @Test
    fun `digits devolve apenas os digitos`() {
        kotlin.test.assertEquals("11144477735", CpfValidator.digits("111.444.777-35"))
        kotlin.test.assertEquals("", CpfValidator.digits("sem digitos"))
    }

    @Test
    fun `validate devolve mensagem propria para formato e para digito verificador`() {
        assertTrue(CpfValidator.validate("111.444.777-35").isValid)

        val curto = CpfValidator.validate("111")
        assertFalse(curto.isValid)
        kotlin.test.assertEquals("CPF deve ter 11 dígitos", curto.errorMessage)

        val invalido = CpfValidator.validate("11144477700")
        assertFalse(invalido.isValid)
        kotlin.test.assertEquals("CPF inválido", invalido.errorMessage)
    }

    @Test
    fun `validate exige valor — em branco nao e estado legitimo aqui`() {
        // Diferente de ProfileValidator, onde o CPF e autodeclarado e opcional. Neste
        // caminho o CPF E a chave do vinculo: sem ele nao ha o que validar nem vincular.
        assertFalse(CpfValidator.validate("").isValid)
        assertFalse(CpfValidator.validate("   ").isValid)
    }
}
