package br.com.sprena.shared.kanban.domain.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD Red — TaskValidator
 *
 * Regras:
 *  - Name: obrigatório, máx 50
 *  - Priority: obrigatório, 1..5
 *  - Description: opcional, máx 3000
 *  - Comment: opcional, máx 2000
 *  - Attachment: máx 100 MB
 *  - Start Date: = hoje
 *  - End Date: obrigatório, >= hoje
 */
class TaskValidatorTest {
    // ── Name — obrigatório, máx 50 ───────────────────────

    @Test
    fun `name blank is invalid`() {
        val result = TaskValidator.validateName("   ")
        assertFalse(result.isValid, "Nome em branco deveria ser inválido")
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `name empty is invalid`() {
        val result = TaskValidator.validateName("")
        assertFalse(result.isValid)
    }

    @Test
    fun `name exceeding 50 chars is invalid`() {
        val result = TaskValidator.validateName("a".repeat(51))
        assertFalse(result.isValid)
    }

    @Test
    fun `name at boundary of 50 chars is valid`() {
        val result = TaskValidator.validateName("a".repeat(50))
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun `name with valid content is valid`() {
        val result = TaskValidator.validateName("Comprar remo")
        assertTrue(result.isValid)
    }

    // ── Priority — obrigatório, 1..5 ─────────────────────

    @Test
    fun `priority null is invalid`() {
        assertFalse(TaskValidator.validatePriority(null).isValid)
    }

    @Test
    fun `priority 0 below range is invalid`() {
        assertFalse(TaskValidator.validatePriority(0).isValid)
    }

    @Test
    fun `priority 6 above range is invalid`() {
        assertFalse(TaskValidator.validatePriority(6).isValid)
    }

    @Test
    fun `priority negative is invalid`() {
        assertFalse(TaskValidator.validatePriority(-1).isValid)
    }

    @Test
    fun `priority 1 lower bound is valid`() {
        assertTrue(TaskValidator.validatePriority(1).isValid)
    }

    @Test
    fun `priority 5 upper bound is valid`() {
        assertTrue(TaskValidator.validatePriority(5).isValid)
    }

    @Test
    fun `priority 3 mid range is valid`() {
        assertTrue(TaskValidator.validatePriority(3).isValid)
    }

    // ── Description — opcional, máx 3000 ─────────────────

    @Test
    fun `description null is valid because optional`() {
        assertTrue(TaskValidator.validateDescription(null).isValid)
    }

    @Test
    fun `description empty is valid because optional`() {
        assertTrue(TaskValidator.validateDescription("").isValid)
    }

    @Test
    fun `description at boundary of 3000 chars is valid`() {
        assertTrue(TaskValidator.validateDescription("x".repeat(3000)).isValid)
    }

    @Test
    fun `description exceeding 3000 chars is invalid`() {
        assertFalse(TaskValidator.validateDescription("x".repeat(3001)).isValid)
    }

    @Test
    fun `description with valid text is valid`() {
        assertTrue(TaskValidator.validateDescription("Detalhes da tarefa aqui").isValid)
    }

    // ── Comment — opcional, máx 2000 ─────────────────────

    @Test
    fun `comment null is valid because optional`() {
        assertTrue(TaskValidator.validateComment(null).isValid)
    }

    @Test
    fun `comment empty is valid because optional`() {
        assertTrue(TaskValidator.validateComment("").isValid)
    }

    @Test
    fun `comment at boundary of 2000 chars is valid`() {
        assertTrue(TaskValidator.validateComment("c".repeat(2000)).isValid)
    }

    @Test
    fun `comment exceeding 2000 chars is invalid`() {
        assertFalse(TaskValidator.validateComment("c".repeat(2001)).isValid)
    }

    @Test
    fun `comment with valid text is valid`() {
        assertTrue(TaskValidator.validateComment("Observação sobre a task").isValid)
    }

    // ── Attachment — máx 100 MB ──────────────────────────

    @Test
    fun `attachment zero bytes means no attachment and is valid`() {
        assertTrue(TaskValidator.validateAttachmentSize(0L).isValid)
    }

    @Test
    fun `attachment at exactly 100MB boundary is valid`() {
        assertTrue(TaskValidator.validateAttachmentSize(TaskValidator.ATTACHMENT_MAX_BYTES).isValid)
    }

    @Test
    fun `attachment above 100MB is invalid`() {
        val oversize = TaskValidator.ATTACHMENT_MAX_BYTES + 1
        assertFalse(TaskValidator.validateAttachmentSize(oversize).isValid)
    }

    @Test
    fun `attachment 50MB is valid`() {
        assertTrue(TaskValidator.validateAttachmentSize(50L * 1024L * 1024L).isValid)
    }

    // ── End Date — obrigatório, >= hoje ──────────────────

    @Test
    fun `end date null is invalid because required`() {
        val today = 20_000L
        assertFalse(TaskValidator.validateEndDate(null, today).isValid)
    }

    @Test
    fun `end date before today is invalid`() {
        val today = 20_000L
        assertFalse(TaskValidator.validateEndDate(today - 1, today).isValid)
    }

    @Test
    fun `end date equal to today is valid`() {
        val today = 20_000L
        assertTrue(TaskValidator.validateEndDate(today, today).isValid)
    }

    @Test
    fun `end date in the future is valid`() {
        val today = 20_000L
        assertTrue(TaskValidator.validateEndDate(today + 30, today).isValid)
    }

    // ── Start Date — = dia atual ─────────────────────────

    @Test
    fun `start date equal to today is valid`() {
        val today = 20_000L
        assertTrue(TaskValidator.validateStartDate(today, today).isValid)
    }

    @Test
    fun `start date different from today is invalid`() {
        val today = 20_000L
        assertFalse(TaskValidator.validateStartDate(today + 1, today).isValid)
    }

    @Test
    fun `start date before today is also invalid`() {
        val today = 20_000L
        assertFalse(TaskValidator.validateStartDate(today - 1, today).isValid)
    }
}
