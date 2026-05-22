package br.com.sprena.presentation.kanban.addtask

import app.cash.turbine.test
import br.com.sprena.test.MainDispatcherEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD Red — AddTaskViewModel
 *
 * Campos:
 *  - Name obrigatório, máx 50
 *  - Priority obrigatório, 1..5 (dropdown)
 *  - Description opcional, máx 3000
 *  - Comment opcional, máx 2000
 *  - Attachment máx 100 MB
 *  - End Date obrigatório, >= hoje
 *  - Start Date = hoje (auto)
 */
class AddTaskViewModelTest {
    private val env = MainDispatcherEnv()
    private val today: Long = 20_000L

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private fun vm() = AddTaskViewModel(today = { today })

    // ── Start Date ────────────────────────────────────────

    @Test
    fun `initial start date is today`() =
        runTest {
            assertEquals(today, vm().state.first().startEpochDay)
        }

    // ── Name — obrigatório, máx 50 ───────────────────────

    @Test
    fun `empty name sets nameError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.NameChanged(""))
            assertNotNull(vm.state.first().nameError)
        }

    @Test
    fun `name exceeding 50 chars sets nameError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.NameChanged("a".repeat(51)))
            assertNotNull(vm.state.first().nameError)
        }

    @Test
    fun `name at boundary of 50 chars clears nameError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.NameChanged("a".repeat(50)))
            assertNull(vm.state.first().nameError)
        }

    @Test
    fun `valid name clears nameError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.NameChanged("Comprar remo"))
            assertNull(vm.state.first().nameError)
        }

    // ── Priority — dropdown 1..5, obrigatório ─────────────

    @Test
    fun `priority options are exactly 1 through 5`() {
        assertEquals(listOf(1, 2, 3, 4, 5), AddTaskState.PRIORITY_OPTIONS)
    }

    @Test
    fun `priority null sets priorityError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.PriorityChanged(null))
            assertNotNull(vm.state.first().priorityError)
        }

    @Test
    fun `priority out of range sets priorityError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.PriorityChanged(6))
            assertNotNull(vm.state.first().priorityError)
        }

    @Test
    fun `priority 0 below range sets priorityError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.PriorityChanged(0))
            assertNotNull(vm.state.first().priorityError)
        }

    @Test
    fun `priority in range clears priorityError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.PriorityChanged(3))
            assertNull(vm.state.first().priorityError)
        }

    // ── Description — opcional, máx 3000 ─────────────────

    @Test
    fun `description empty is accepted`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.DescriptionChanged(""))
            assertNull(vm.state.first().descriptionError)
        }

    @Test
    fun `description at boundary of 3000 chars is accepted`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.DescriptionChanged("x".repeat(3000)))
            assertNull(vm.state.first().descriptionError)
        }

    @Test
    fun `description exceeding 3000 chars sets descriptionError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.DescriptionChanged("x".repeat(3001)))
            assertNotNull(vm.state.first().descriptionError)
        }

    // ── Comment — opcional, máx 2000 ─────────────────────

    @Test
    fun `comment empty is accepted`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.CommentChanged(""))
            assertNull(vm.state.first().commentError)
        }

    @Test
    fun `comment at boundary of 2000 chars is accepted`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.CommentChanged("c".repeat(2000)))
            assertNull(vm.state.first().commentError)
        }

    @Test
    fun `comment exceeding 2000 chars sets commentError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.CommentChanged("c".repeat(2001)))
            assertNotNull(vm.state.first().commentError)
        }

    @Test
    fun `comment updates state`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.CommentChanged("Observação"))
            assertEquals("Observação", vm.state.first().comment)
        }

    // ── Attachment — máx 100 MB ──────────────────────────

    @Test
    fun `attachment above 100MB sets attachmentError`() =
        runTest {
            val vm = vm()
            val tooBig = 100L * 1024L * 1024L + 1L
            vm.handleIntent(AddTaskIntent.AttachmentSelected("big.pdf", tooBig))
            assertNotNull(vm.state.first().attachmentError)
        }

    @Test
    fun `attachment at exactly 100MB is accepted`() =
        runTest {
            val vm = vm()
            val exact = 100L * 1024L * 1024L
            vm.handleIntent(AddTaskIntent.AttachmentSelected("ok.pdf", exact))
            val s = vm.state.first()
            assertNull(s.attachmentError)
            assertEquals("ok.pdf", s.attachmentName)
        }

    @Test
    fun `attachment cleared resets fields`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.AttachmentSelected("ok.pdf", 1024L))
            vm.handleIntent(AddTaskIntent.AttachmentCleared)
            val s = vm.state.first()
            assertNull(s.attachmentName)
            assertEquals(0L, s.attachmentSizeBytes)
        }

    // ── End Date — obrigatório, >= hoje ──────────────────

    @Test
    fun `end date null sets endDateError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.EndDateChanged(null))
            assertNotNull(vm.state.first().endDateError)
        }

    @Test
    fun `end date before today sets endDateError`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.EndDateChanged(today - 1))
            assertNotNull(vm.state.first().endDateError)
        }

    @Test
    fun `end date equal to today is accepted`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.EndDateChanged(today))
            assertNull(vm.state.first().endDateError)
        }

    @Test
    fun `end date in the future is accepted`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.EndDateChanged(today + 5))
            assertNull(vm.state.first().endDateError)
        }

    // ── Submit / canSubmit ───────────────────────────────

    @Test
    fun `canSubmit is false when required fields are missing`() =
        runTest {
            assertFalse(vm().state.first().canSubmit)
        }

    @Test
    fun `canSubmit is true when all required fields valid`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.NameChanged("Teste"))
            vm.handleIntent(AddTaskIntent.PriorityChanged(2))
            vm.handleIntent(AddTaskIntent.EndDateChanged(today + 1))
            assertTrue(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit remains true with optional fields filled`() =
        runTest {
            val vm = vm()
            vm.handleIntent(AddTaskIntent.NameChanged("Teste"))
            vm.handleIntent(AddTaskIntent.PriorityChanged(2))
            vm.handleIntent(AddTaskIntent.EndDateChanged(today + 1))
            vm.handleIntent(AddTaskIntent.DescriptionChanged("Desc"))
            vm.handleIntent(AddTaskIntent.CommentChanged("Obs"))
            assertTrue(vm.state.first().canSubmit)
        }

    @Test
    fun `submit with invalid form emits ShowError`() =
        runTest {
            val vm = vm()
            vm.effects.test {
                vm.handleIntent(AddTaskIntent.Submit)
                val e = awaitItem()
                assertTrue(e is AddTaskEffect.ShowError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submit with valid form emits TaskCreated`() =
        runTest {
            val vm = vm()
            vm.effects.test {
                vm.handleIntent(AddTaskIntent.NameChanged("Teste"))
                vm.handleIntent(AddTaskIntent.PriorityChanged(3))
                vm.handleIntent(AddTaskIntent.EndDateChanged(today + 2))
                vm.handleIntent(AddTaskIntent.Submit)
                assertEquals(AddTaskEffect.TaskCreated, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dismiss emits Dismissed`() =
        runTest {
            val vm = vm()
            vm.effects.test {
                vm.handleIntent(AddTaskIntent.Dismiss)
                assertEquals(AddTaskEffect.Dismissed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
