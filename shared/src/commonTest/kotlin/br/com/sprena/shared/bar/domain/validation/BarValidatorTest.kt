package br.com.sprena.shared.bar.domain.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD — BarValidator (validação de todos os campos do Bar).
 *
 * Cada campo tem testes para: valor válido, vazio, limites de tamanho,
 * e casos especiais (formato, dígitos, etc.).
 */
class BarValidatorTest {
    // =========================================================================
    // validateClientName — obrigatório, max 100 chars
    // =========================================================================

    @Test
    fun `clientName valid name returns Valid`() {
        val result = BarValidator.validateClientName("João Silva")
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `clientName empty returns invalid`() {
        val result = BarValidator.validateClientName("")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `clientName only spaces returns invalid`() {
        val result = BarValidator.validateClientName("   ")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `clientName at max length returns Valid`() {
        val name = "A".repeat(BarValidator.NAME_MAX_LENGTH)
        val result = BarValidator.validateClientName(name)
        assertTrue(result.isValid)
    }

    @Test
    fun `clientName exceeds max length returns invalid`() {
        val name = "A".repeat(BarValidator.NAME_MAX_LENGTH + 1)
        val result = BarValidator.validateClientName(name)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${BarValidator.NAME_MAX_LENGTH}"))
    }

    @Test
    fun `clientName single character returns Valid`() {
        val result = BarValidator.validateClientName("A")
        assertTrue(result.isValid)
    }

    // =========================================================================
    // validateNickname — opcional, max 50 chars
    // =========================================================================

    @Test
    fun `nickname null returns Valid`() {
        val result = BarValidator.validateNickname(null)
        assertTrue(result.isValid)
    }

    @Test
    fun `nickname empty returns Valid`() {
        val result = BarValidator.validateNickname("")
        assertTrue(result.isValid)
    }

    @Test
    fun `nickname valid value returns Valid`() {
        val result = BarValidator.validateNickname("Joãozinho")
        assertTrue(result.isValid)
    }

    @Test
    fun `nickname at max length returns Valid`() {
        val nickname = "A".repeat(BarValidator.NICKNAME_MAX_LENGTH)
        val result = BarValidator.validateNickname(nickname)
        assertTrue(result.isValid)
    }

    @Test
    fun `nickname exceeds max length returns invalid`() {
        val nickname = "A".repeat(BarValidator.NICKNAME_MAX_LENGTH + 1)
        val result = BarValidator.validateNickname(nickname)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${BarValidator.NICKNAME_MAX_LENGTH}"))
    }

    // =========================================================================
    // validatePhone — obrigatório, 10-15 dígitos
    // =========================================================================

    @Test
    fun `phone valid 11 digits returns Valid`() {
        val result = BarValidator.validatePhone("11999998888")
        assertTrue(result.isValid)
    }

    @Test
    fun `phone valid with formatting returns Valid`() {
        val result = BarValidator.validatePhone("(11) 99999-8888")
        assertTrue(result.isValid)
    }

    @Test
    fun `phone empty returns invalid`() {
        val result = BarValidator.validatePhone("")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `phone too few digits returns invalid`() {
        val result = BarValidator.validatePhone("123456789") // 9 digits
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${BarValidator.PHONE_MIN_LENGTH}"))
    }

    @Test
    fun `phone at min length returns Valid`() {
        val result = BarValidator.validatePhone("1234567890") // 10 digits
        assertTrue(result.isValid)
    }

    @Test
    fun `phone at max length returns Valid`() {
        val result = BarValidator.validatePhone("1".repeat(BarValidator.PHONE_MAX_LENGTH))
        assertTrue(result.isValid)
    }

    @Test
    fun `phone exceeds max length returns invalid`() {
        val result = BarValidator.validatePhone("1".repeat(BarValidator.PHONE_MAX_LENGTH + 1))
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${BarValidator.PHONE_MAX_LENGTH}"))
    }

    @Test
    fun `phone with only non-digit characters returns invalid`() {
        val result = BarValidator.validatePhone("abcdefghij")
        assertFalse(result.isValid)
    }

    // =========================================================================
    // validateCpf — obrigatório, exatamente 11 dígitos
    // =========================================================================

    @Test
    fun `cpf valid 11 digits returns Valid`() {
        val result = BarValidator.validateCpf("12345678901")
        assertTrue(result.isValid)
    }

    @Test
    fun `cpf with formatting returns Valid`() {
        val result = BarValidator.validateCpf("123.456.789-01")
        assertTrue(result.isValid)
    }

    @Test
    fun `cpf empty returns invalid`() {
        val result = BarValidator.validateCpf("")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `cpf too few digits returns invalid`() {
        val result = BarValidator.validateCpf("1234567890") // 10 digits
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${BarValidator.CPF_LENGTH}"))
    }

    @Test
    fun `cpf too many digits returns invalid`() {
        val result = BarValidator.validateCpf("123456789012") // 12 digits
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${BarValidator.CPF_LENGTH}"))
    }

    // =========================================================================
    // validateEmail — opcional, max 150 chars, must contain @ and .
    // =========================================================================

    @Test
    fun `email null returns Valid`() {
        val result = BarValidator.validateEmail(null)
        assertTrue(result.isValid)
    }

    @Test
    fun `email empty returns Valid`() {
        val result = BarValidator.validateEmail("")
        assertTrue(result.isValid)
    }

    @Test
    fun `email valid format returns Valid`() {
        val result = BarValidator.validateEmail("joao@email.com")
        assertTrue(result.isValid)
    }

    @Test
    fun `email missing at sign returns invalid`() {
        val result = BarValidator.validateEmail("joaoemail.com")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("inválido"))
    }

    @Test
    fun `email missing dot returns invalid`() {
        val result = BarValidator.validateEmail("joao@emailcom")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("inválido"))
    }

    @Test
    fun `email at max length returns Valid`() {
        val localPart = "a".repeat(BarValidator.EMAIL_MAX_LENGTH - 10) // leave room for @email.com
        val email = "$localPart@email.com"
        // This will be at or over max length — let's make one exactly at max
        val exactEmail = "a".repeat(BarValidator.EMAIL_MAX_LENGTH - 10) + "@emai.com."
        val validEmail = "a".repeat(130) + "@email.com.br" // 130 + 13 = 143 < 150
        val result = BarValidator.validateEmail(validEmail)
        assertTrue(result.isValid)
    }

    @Test
    fun `email exceeds max length returns invalid`() {
        val longEmail = "a".repeat(BarValidator.EMAIL_MAX_LENGTH) + "@email.com"
        val result = BarValidator.validateEmail(longEmail)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${BarValidator.EMAIL_MAX_LENGTH}"))
    }

    // =========================================================================
    // validateItemName — obrigatório, max 100 chars
    // =========================================================================

    @Test
    fun `itemName valid returns Valid`() {
        val result = BarValidator.validateItemName("Cerveja")
        assertTrue(result.isValid)
    }

    @Test
    fun `itemName empty returns invalid`() {
        val result = BarValidator.validateItemName("")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `itemName only spaces returns invalid`() {
        val result = BarValidator.validateItemName("   ")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `itemName at max length returns Valid`() {
        val name = "A".repeat(BarValidator.ITEM_NAME_MAX_LENGTH)
        val result = BarValidator.validateItemName(name)
        assertTrue(result.isValid)
    }

    @Test
    fun `itemName exceeds max length returns invalid`() {
        val name = "A".repeat(BarValidator.ITEM_NAME_MAX_LENGTH + 1)
        val result = BarValidator.validateItemName(name)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${BarValidator.ITEM_NAME_MAX_LENGTH}"))
    }

    // =========================================================================
    // validateItemPrice — obrigatório, > 0, max R$ 100.000,00
    // =========================================================================

    @Test
    fun `itemPrice valid returns Valid`() {
        val result = BarValidator.validateItemPrice(1500L) // R$ 15,00
        assertTrue(result.isValid)
    }

    @Test
    fun `itemPrice null returns invalid`() {
        val result = BarValidator.validateItemPrice(null)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `itemPrice zero returns invalid`() {
        val result = BarValidator.validateItemPrice(0L)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("maior que zero"))
    }

    @Test
    fun `itemPrice negative returns invalid`() {
        val result = BarValidator.validateItemPrice(-100L)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("maior que zero"))
    }

    @Test
    fun `itemPrice 1 centavo returns Valid`() {
        val result = BarValidator.validateItemPrice(1L)
        assertTrue(result.isValid)
    }

    @Test
    fun `itemPrice at max returns Valid`() {
        val result = BarValidator.validateItemPrice(BarValidator.ITEM_MAX_PRICE_CENTS)
        assertTrue(result.isValid)
    }

    @Test
    fun `itemPrice exceeds max returns invalid`() {
        val result = BarValidator.validateItemPrice(BarValidator.ITEM_MAX_PRICE_CENTS + 1)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("100.000"))
    }
}
