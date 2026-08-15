package br.com.sprena.shared.establishment.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Os limites daqui espelham de proposito os da rule de `establishments` em
 * firestore.rules. Quando divergem, o usuario preenche um formulario que passa na tela e
 * e recusado pelo servidor com PERMISSION_DENIED — erro que nao aponta o campo culpado.
 * Se um limite mudar de um lado, precisa mudar do outro.
 */
class EstablishmentValidatorTest {
    @Test
    fun `nome e obrigatorio e limitado a 80 caracteres`() {
        assertTrue(EstablishmentValidator.validateName("Bar do Ze").isValid)

        val vazio = EstablishmentValidator.validateName("   ")
        assertFalse(vazio.isValid)
        assertEquals("Nome é obrigatório", vazio.errorMessage)

        assertTrue(EstablishmentValidator.validateName("a".repeat(80)).isValid)
        assertFalse(EstablishmentValidator.validateName("a".repeat(81)).isValid)
    }

    @Test
    fun `telefone e obrigatorio com DDD mais 8 ou 9 digitos`() {
        assertTrue(EstablishmentValidator.validatePhone("1198765432").isValid)
        assertTrue(EstablishmentValidator.validatePhone("(11) 98765-4321").isValid)

        val vazio = EstablishmentValidator.validatePhone("")
        assertFalse(vazio.isValid)
        assertEquals("Telefone é obrigatório", vazio.errorMessage)

        assertFalse(EstablishmentValidator.validatePhone("119876543").isValid)
        assertFalse(EstablishmentValidator.validatePhone("119876543210").isValid)
    }

    @Test
    fun `email e obrigatorio e precisa ter forma de email`() {
        assertTrue(EstablishmentValidator.validateEmail("contato@bar.com.br").isValid)

        val vazio = EstablishmentValidator.validateEmail(" ")
        assertFalse(vazio.isValid)
        assertEquals("E-mail é obrigatório", vazio.errorMessage)

        for (invalido in listOf("contato", "contato@", "@bar.com", "contato bar@x.com", "a@b")) {
            assertFalse(EstablishmentValidator.validateEmail(invalido).isValid, "aceitou $invalido")
        }

        assertFalse(EstablishmentValidator.validateEmail("a".repeat(115) + "@b.com").isValid)
    }

    @Test
    fun `razao social e opcional e limitada a 120 caracteres`() {
        assertTrue(EstablishmentValidator.validateRazaoSocial(null).isValid)
        assertTrue(EstablishmentValidator.validateRazaoSocial("").isValid)
        assertTrue(EstablishmentValidator.validateRazaoSocial("Ze Bebidas LTDA").isValid)
        assertFalse(EstablishmentValidator.validateRazaoSocial("a".repeat(121)).isValid)
    }

    @Test
    fun `cnpj delega ao CnpjValidator`() {
        assertTrue(EstablishmentValidator.validateCnpj("11.222.333/0001-81").isValid)
        assertFalse(EstablishmentValidator.validateCnpj("11222333000182").isValid)
    }
}
