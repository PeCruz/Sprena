package br.com.sprena.shared.auth.domain.validation

import br.com.sprena.shared.core.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoginValidatorTest {
    // --- validateEmail ---
    @Test
    fun `validateEmail accepts a typical email`() {
        assertTrue(LoginValidator.validateEmail("pedro@gmail.com").isValid)
    }

    @Test
    fun `validateEmail rejects blank`() {
        val result = LoginValidator.validateEmail("")
        assertEquals(false, result.isValid)
        assertEquals("Email é obrigatório", result.errorMessage)
    }

    @Test
    fun `validateEmail rejects whitespace only`() {
        assertEquals(false, LoginValidator.validateEmail("   ").isValid)
    }

    @Test
    fun `validateEmail rejects missing arroba`() {
        val result = LoginValidator.validateEmail("pedro.gmail.com")
        assertEquals(false, result.isValid)
        assertEquals("Email inválido", result.errorMessage)
    }

    @Test
    fun `validateEmail rejects missing dot`() {
        assertEquals(false, LoginValidator.validateEmail("pedro@gmail").isValid)
    }

    @Test
    fun `validateEmail rejects internal whitespace`() {
        assertEquals(false, LoginValidator.validateEmail("pe dro@gmail.com").isValid)
    }

    @Test
    fun `validateEmail rejects over 254 chars`() {
        val long = "a".repeat(250) + "@b.co" // 255 chars total
        assertEquals(false, LoginValidator.validateEmail(long).isValid)
    }

    @Test
    fun `validateEmail trims and accepts`() {
        assertTrue(LoginValidator.validateEmail("  pedro@gmail.com  ").isValid)
    }

    // --- validatePassword ---
    @Test
    fun `validatePassword accepts 6 chars`() {
        assertTrue(LoginValidator.validatePassword("abc123").isValid)
    }

    @Test
    fun `validatePassword accepts mixed chars`() {
        assertTrue(LoginValidator.validatePassword("S3nha@x").isValid)
    }

    @Test
    fun `validatePassword rejects blank`() {
        val result = LoginValidator.validatePassword("")
        assertEquals(false, result.isValid)
        assertEquals("Senha é obrigatória", result.errorMessage)
    }

    @Test
    fun `validatePassword rejects fewer than 6 chars`() {
        val result = LoginValidator.validatePassword("abc12")
        assertEquals(false, result.isValid)
        assertEquals("Senha deve ter no mínimo 6 caracteres", result.errorMessage)
    }

    @Test
    fun `validatePassword rejects leading whitespace`() {
        assertEquals(false, LoginValidator.validatePassword(" abc123").isValid)
    }

    @Test
    fun `validatePassword rejects trailing whitespace`() {
        assertEquals(false, LoginValidator.validatePassword("abc123 ").isValid)
    }
}
