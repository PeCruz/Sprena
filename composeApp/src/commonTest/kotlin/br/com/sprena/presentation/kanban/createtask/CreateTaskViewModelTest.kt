package br.com.sprena.presentation.kanban.createtask

import app.cash.turbine.test
import br.com.sprena.test.MainDispatcherEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD — Tela "Criando Tarefa" (CreateTask).
 *
 * Campos: Name, Priority, Description, Comment, EndDate, Attachment.
 * Validação via TaskValidator. hasUnsavedChanges para confirmação de saída.
 */
class CreateTaskViewModelTest {
    private val env = MainDispatcherEnv()
    private val today = 19833L // epoch day fixo para testes

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    // =========================================================================
    // Estado inicial
    // =========================================================================

    @Test
    fun `initial state has empty fields`() =
        runTest {
            val vm = createVm()
            val s = vm.state.first()
            assertEquals("", s.name)
            assertNull(s.priority)
            assertEquals("", s.description)
            assertEquals("", s.comment)
            assertNull(s.endEpochDay)
            assertNull(s.attachmentName)
            assertFalse(s.canSubmit)
        }

    @Test
    fun `initial state has no unsaved changes`() =
        runTest {
            val vm = createVm()
            assertFalse(vm.state.first().hasUnsavedChanges)
        }

    // =========================================================================
    // Name
    // =========================================================================

    @Test
    fun `NameChanged updates name in state`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.NameChanged("Tarefa 1"))
            assertEquals("Tarefa 1", vm.state.first().name)
        }

    @Test
    fun `empty name shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.NameChanged("a"))
            vm.handleIntent(CreateTaskIntent.NameChanged(""))
            assertEquals("Nome é obrigatório", vm.state.first().nameError)
        }

    @Test
    fun `name over 50 chars shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.NameChanged("a".repeat(51)))
            assertTrue(vm.state.first().nameError != null)
        }

    // =========================================================================
    // Priority
    // =========================================================================

    @Test
    fun `PriorityChanged updates priority`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.PriorityChanged(3))
            assertEquals(3, vm.state.first().priority)
        }

    @Test
    fun `null priority shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.PriorityChanged(3))
            vm.handleIntent(CreateTaskIntent.PriorityChanged(null))
            assertEquals("Prioridade é obrigatória", vm.state.first().priorityError)
        }

    @Test
    fun `priority out of range shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.PriorityChanged(6))
            assertTrue(vm.state.first().priorityError != null)
        }

    // =========================================================================
    // Description
    // =========================================================================

    @Test
    fun `DescriptionChanged updates description`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.DescriptionChanged("desc"))
            assertEquals("desc", vm.state.first().description)
        }

    @Test
    fun `description over 3000 chars shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.DescriptionChanged("a".repeat(3001)))
            assertTrue(vm.state.first().descriptionError != null)
        }

    // =========================================================================
    // Comment
    // =========================================================================

    @Test
    fun `CommentChanged updates comment`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.CommentChanged("comentário"))
            assertEquals("comentário", vm.state.first().comment)
        }

    @Test
    fun `comment over 2000 chars shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.CommentChanged("a".repeat(2001)))
            assertTrue(vm.state.first().commentError != null)
        }

    // =========================================================================
    // EndDate
    // =========================================================================

    @Test
    fun `EndDateChanged updates endDate`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.EndDateChanged(today + 5))
            assertEquals(today + 5, vm.state.first().endEpochDay)
        }

    @Test
    fun `end date in the past shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.EndDateChanged(today - 1))
            assertTrue(vm.state.first().endDateError != null)
        }

    // =========================================================================
    // Attachment
    // =========================================================================

    @Test
    fun `AttachmentSelected updates attachment`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.AttachmentSelected("doc.pdf", 1024L))
            assertEquals("doc.pdf", vm.state.first().attachmentName)
        }

    @Test
    fun `attachment over 100MB shows error`() =
        runTest {
            val vm = createVm()
            val over100mb = 100L * 1024L * 1024L + 1
            vm.handleIntent(CreateTaskIntent.AttachmentSelected("big.zip", over100mb))
            assertTrue(vm.state.first().attachmentError != null)
        }

    @Test
    fun `AttachmentCleared removes attachment`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.AttachmentSelected("f.pdf", 100L))
            vm.handleIntent(CreateTaskIntent.AttachmentCleared)
            assertNull(vm.state.first().attachmentName)
        }

    // =========================================================================
    // canSubmit
    // =========================================================================

    @Test
    fun `canSubmit is true when required fields are valid`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.NameChanged("Task válida"))
            vm.handleIntent(CreateTaskIntent.PriorityChanged(3))
            vm.handleIntent(CreateTaskIntent.EndDateChanged(today + 1))
            assertTrue(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit is false when name is missing`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.PriorityChanged(3))
            vm.handleIntent(CreateTaskIntent.EndDateChanged(today + 1))
            assertFalse(vm.state.first().canSubmit)
        }

    // =========================================================================
    // Submit
    // =========================================================================

    @Test
    fun `Submit emits TaskCreated when valid`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.NameChanged("Task"))
            vm.handleIntent(CreateTaskIntent.PriorityChanged(2))
            vm.handleIntent(CreateTaskIntent.EndDateChanged(today + 1))

            vm.effects.test {
                vm.handleIntent(CreateTaskIntent.Submit)
                assertEquals(CreateTaskEffect.TaskCreated, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Submit emits ShowError when invalid`() =
        runTest {
            val vm = createVm()
            vm.effects.test {
                vm.handleIntent(CreateTaskIntent.Submit)
                val effect = awaitItem()
                assertTrue(effect is CreateTaskEffect.ShowError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // hasUnsavedChanges — para confirmação de saída
    // =========================================================================

    @Test
    fun `hasUnsavedChanges is true when name is filled`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.NameChanged("algo"))
            assertTrue(vm.state.first().hasUnsavedChanges)
        }

    @Test
    fun `hasUnsavedChanges is true when description is filled`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.DescriptionChanged("texto"))
            assertTrue(vm.state.first().hasUnsavedChanges)
        }

    @Test
    fun `hasUnsavedChanges is true when comment is filled`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.CommentChanged("nota"))
            assertTrue(vm.state.first().hasUnsavedChanges)
        }

    @Test
    fun `hasUnsavedChanges is false when all strings are empty`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(CreateTaskIntent.NameChanged("x"))
            vm.handleIntent(CreateTaskIntent.NameChanged(""))
            assertFalse(vm.state.first().hasUnsavedChanges)
        }

    // =========================================================================
    // NavigateBack
    // =========================================================================

    @Test
    fun `NavigateBack emits GoBack effect`() =
        runTest {
            val vm = createVm()
            vm.effects.test {
                vm.handleIntent(CreateTaskIntent.NavigateBack)
                assertEquals(CreateTaskEffect.GoBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Helper
    // =========================================================================

    private fun createVm() = CreateTaskViewModel(today = { today })
}
