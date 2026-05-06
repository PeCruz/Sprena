package br.com.sprena.shared.auth.domain.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD — LoginValidator
 *
 * Regras:
 *  - Username: obrigatório, mín 3, máx 8
 *  - Password: obrigatório, exatamente 6 dígitos numéricos
 */
class LoginValidatorTest {

    // ── Username ─────────────────────────────────────────

    @Test
    fun `username empty is invalid`() {
        val result = LoginValidator.validateUsername("")
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `username blank spaces is invalid`() {
        assertFalse(LoginValidator.validateUsername("   ").isValid)
    }

    @Test
    fun `username with 2 chars below min is invalid`() {
        val short = "a".repeat(LoginValidator.USERNAME_MIN_LENGTH - 1)
        assertFalse(LoginValidator.validateUsername(short).isValid)
    }

    @Test
    fun `username with 3 chars at min boundary is valid`() {
        val atMin = "a".repeat(LoginValidator.USERNAME_MIN_LENGTH)
        assertTrue(LoginValidator.validateUsername(atMin).isValid)
        assertNull(LoginValidator.validateUsername(atMin).errorMessage)
    }

    @Test
    fun `username with 8 chars at max boundary is valid`() {
        val atMax = "a".repeat(LoginValidator.USERNAME_MAX_LENGTH)
        assertTrue(LoginValidator.validateUsername(atMax).isValid)
    }

    @Test
    fun `username with 9 chars above max is invalid`() {
        val overMax = "a".repeat(LoginValidator.USERNAME_MAX_LENGTH + 1)
        assertFalse(LoginValidator.validateUsername(overMax).isValid)
    }

    @Test
    fun `username with valid content is valid`() {
        assertTrue(LoginValidator.validateUsername("admin").isValid)
    }

    // ── Password ─────────────────────────────────────────

    @Test
    fun `password empty is invalid`() {
        val result = LoginValidator.validatePassword("")
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `password blank spaces is invalid`() {
        assertFalse(LoginValidator.validatePassword("     ").isValid)
    }

    @Test
    fun `password with 5 digits below required length is invalid`() {
        assertFalse(LoginValidator.validatePassword("12345").isValid)
    }

    @Test
    fun `password with exactly 6 digits is valid`() {
        val result = LoginValidator.validatePassword("123456")
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `password with 7 digits above required length is invalid`() {
        assertFalse(LoginValidator.validatePassword("1234567").isValid)
    }

    @Test
    fun `password with letters is invalid`() {
        assertFalse(LoginValidator.validatePassword("abc123").isValid)
    }

    @Test
    fun `password with special chars is invalid`() {
        assertFalse(LoginValidator.validatePassword("12345!").isValid)
    }

    @Test
    fun `password with spaces is invalid`() {
        assertFalse(LoginValidator.validatePassword("12 456").isValid)
    }
}
