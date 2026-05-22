package br.com.sprena.shared.menu.domain.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD — MenuValidator (validação dos campos do item do Cardápio).
 *
 * Cenários cobertos:
 * - validateMenuItemName: obrigatório, max 100 chars
 * - validateMenuItemPrice: obrigatório, > 0, max R$ 100.000,00
 * - validateMenuItemDescription: opcional, max 500 chars
 */
class MenuValidatorTest {
    // =========================================================================
    // validateMenuItemName — obrigatório, max 100 chars
    // =========================================================================

    @Test
    fun `itemName valid returns Valid`() {
        val result = MenuValidator.validateMenuItemName("Cerveja Artesanal")
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `itemName empty returns invalid`() {
        val result = MenuValidator.validateMenuItemName("")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `itemName only spaces returns invalid`() {
        val result = MenuValidator.validateMenuItemName("   ")
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `itemName at max length returns Valid`() {
        val name = "A".repeat(MenuValidator.NAME_MAX_LENGTH)
        val result = MenuValidator.validateMenuItemName(name)
        assertTrue(result.isValid)
    }

    @Test
    fun `itemName exceeds max length returns invalid`() {
        val name = "A".repeat(MenuValidator.NAME_MAX_LENGTH + 1)
        val result = MenuValidator.validateMenuItemName(name)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${MenuValidator.NAME_MAX_LENGTH}"))
    }

    @Test
    fun `itemName single character returns Valid`() {
        val result = MenuValidator.validateMenuItemName("X")
        assertTrue(result.isValid)
    }

    // =========================================================================
    // validateMenuItemPrice — obrigatório, > 0, max R$ 100.000,00
    // =========================================================================

    @Test
    fun `itemPrice valid returns Valid`() {
        val result = MenuValidator.validateMenuItemPrice(1500L) // R$ 15,00
        assertTrue(result.isValid)
    }

    @Test
    fun `itemPrice null returns invalid`() {
        val result = MenuValidator.validateMenuItemPrice(null)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("obrigatório"))
    }

    @Test
    fun `itemPrice zero returns invalid`() {
        val result = MenuValidator.validateMenuItemPrice(0L)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("maior que zero"))
    }

    @Test
    fun `itemPrice negative returns invalid`() {
        val result = MenuValidator.validateMenuItemPrice(-100L)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("maior que zero"))
    }

    @Test
    fun `itemPrice 1 centavo returns Valid`() {
        val result = MenuValidator.validateMenuItemPrice(1L)
        assertTrue(result.isValid)
    }

    @Test
    fun `itemPrice at max returns Valid`() {
        val result = MenuValidator.validateMenuItemPrice(MenuValidator.MAX_PRICE_CENTS)
        assertTrue(result.isValid)
    }

    @Test
    fun `itemPrice exceeds max returns invalid`() {
        val result = MenuValidator.validateMenuItemPrice(MenuValidator.MAX_PRICE_CENTS + 1)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("100.000"))
    }

    // =========================================================================
    // validateMenuItemDescription — opcional, max 500 chars
    // =========================================================================

    @Test
    fun `description null returns Valid`() {
        val result = MenuValidator.validateMenuItemDescription(null)
        assertTrue(result.isValid)
    }

    @Test
    fun `description empty returns Valid`() {
        val result = MenuValidator.validateMenuItemDescription("")
        assertTrue(result.isValid)
    }

    @Test
    fun `description valid value returns Valid`() {
        val result = MenuValidator.validateMenuItemDescription("IPA de maracujá, 600ml")
        assertTrue(result.isValid)
    }

    @Test
    fun `description at max length returns Valid`() {
        val desc = "A".repeat(MenuValidator.DESCRIPTION_MAX_LENGTH)
        val result = MenuValidator.validateMenuItemDescription(desc)
        assertTrue(result.isValid)
    }

    @Test
    fun `description exceeds max length returns invalid`() {
        val desc = "A".repeat(MenuValidator.DESCRIPTION_MAX_LENGTH + 1)
        val result = MenuValidator.validateMenuItemDescription(desc)
        assertFalse(result.isValid)
        assertTrue(result.errorMessage!!.contains("${MenuValidator.DESCRIPTION_MAX_LENGTH}"))
    }
}
