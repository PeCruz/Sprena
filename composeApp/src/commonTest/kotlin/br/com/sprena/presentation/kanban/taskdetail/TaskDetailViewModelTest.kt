package br.com.sprena.presentation.kanban.taskdetail

import app.cash.turbine.test
import br.com.sprena.presentation.kanban.KanbanColumn
import br.com.sprena.presentation.kanban.KanbanTask
import br.com.sprena.test.MainDispatcherEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD — TaskDetail (tela de edição de tarefa que aparece sobre o Kanban).
 *
 * Cenários cobertos:
 * - Carregar task existente no state
 * - Editar nome, prioridade, descrição, comentário, data de fim
 * - Mover task para outra coluna
 * - Deletar task (com confirmação)
 * - Salvar alterações
 */
class TaskDetailViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private val sampleColumns =
        listOf(
            KanbanColumn(id = "col_backlog", title = "Backlog"),
            KanbanColumn(id = "col_todo", title = "A Fazer"),
            KanbanColumn(id = "col_progress", title = "Em Progresso"),
            KanbanColumn(id = "col_done", title = "Concluido"),
        )

    private val sampleTask =
        KanbanTask(
            id = "task_1",
            columnId = "col_todo",
            name = "Comprar material",
            priority = 3,
        )

    private fun createVm(
        task: KanbanTask = sampleTask,
        columns: List<KanbanColumn> = sampleColumns,
    ): TaskDetailViewModel = TaskDetailViewModel(task = task, columns = columns)

    // =========================================================================
    // Load — carregar tarefa no state
    // =========================================================================

    @Test
    fun `initial state loads task data`() =
        runTest {
            val vm = createVm()
            val s = vm.state.first()
            assertEquals("task_1", s.taskId)
            assertEquals("Comprar material", s.name)
            assertEquals(3, s.priority)
            assertEquals("col_todo", s.columnId)
        }

    @Test
    fun `initial state loads available columns`() =
        runTest {
            val vm = createVm()
            val s = vm.state.first()
            assertEquals(4, s.availableColumns.size)
            assertEquals("Backlog", s.availableColumns.first().title)
        }

    // =========================================================================
    // Edit Name
    // =========================================================================

    @Test
    fun `NameChanged updates name in state`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.NameChanged("Nome novo"))
            assertEquals("Nome novo", vm.state.first().name)
        }

    @Test
    fun `NameChanged with empty string shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.NameChanged(""))
            val s = vm.state.first()
            assertEquals("", s.name)
            assertTrue(s.nameError != null)
        }

    @Test
    fun `NameChanged with too long string shows error`() =
        runTest {
            val vm = createVm()
            val longName = "A".repeat(51)
            vm.handleIntent(TaskDetailIntent.NameChanged(longName))
            assertTrue(vm.state.first().nameError != null)
        }

    // =========================================================================
    // Edit Priority
    // =========================================================================

    @Test
    fun `PriorityChanged updates priority in state`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.PriorityChanged(5))
            assertEquals(5, vm.state.first().priority)
        }

    @Test
    fun `PriorityChanged with null shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.PriorityChanged(null))
            assertTrue(vm.state.first().priorityError != null)
        }

    // =========================================================================
    // Edit Description
    // =========================================================================

    @Test
    fun `DescriptionChanged updates description in state`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.DescriptionChanged("Nova desc"))
            assertEquals("Nova desc", vm.state.first().description)
        }

    @Test
    fun `DescriptionChanged too long shows error`() =
        runTest {
            val vm = createVm()
            val longDesc = "A".repeat(3001)
            vm.handleIntent(TaskDetailIntent.DescriptionChanged(longDesc))
            assertTrue(vm.state.first().descriptionError != null)
        }

    // =========================================================================
    // Edit Comment
    // =========================================================================

    @Test
    fun `CommentChanged updates comment in state`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.CommentChanged("Novo comentário"))
            assertEquals("Novo comentário", vm.state.first().comment)
        }

    @Test
    fun `CommentChanged too long shows error`() =
        runTest {
            val vm = createVm()
            val longComment = "A".repeat(2001)
            vm.handleIntent(TaskDetailIntent.CommentChanged(longComment))
            assertTrue(vm.state.first().commentError != null)
        }

    // =========================================================================
    // Edit End Date
    // =========================================================================

    @Test
    fun `EndDateChanged updates endEpochDay in state`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.EndDateChanged(20000L))
            assertEquals(20000L, vm.state.first().endEpochDay)
        }

    // =========================================================================
    // Move Task (mudar coluna)
    // =========================================================================

    @Test
    fun `ColumnChanged updates columnId in state`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.ColumnChanged("col_progress"))
            assertEquals("col_progress", vm.state.first().columnId)
        }

    @Test
    fun `ColumnChanged marks hasChanges true`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.ColumnChanged("col_done"))
            assertTrue(vm.state.first().hasChanges)
        }

    // =========================================================================
    // Delete — com confirmação
    // =========================================================================

    @Test
    fun `DeleteClicked shows confirmation dialog`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.DeleteClicked)
            assertTrue(vm.state.first().isDeleteConfirmVisible)
        }

    @Test
    fun `DeleteCancelled hides confirmation dialog`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.DeleteClicked)
            vm.handleIntent(TaskDetailIntent.DeleteCancelled)
            assertFalse(vm.state.first().isDeleteConfirmVisible)
        }

    @Test
    fun `DeleteConfirmed emits TaskDeleted effect`() =
        runTest {
            val vm = createVm()
            vm.effects.test {
                vm.handleIntent(TaskDetailIntent.DeleteClicked)
                vm.handleIntent(TaskDetailIntent.DeleteConfirmed)
                val effect = awaitItem()
                assertTrue(effect is TaskDetailEffect.TaskDeleted)
                assertEquals("task_1", (effect as TaskDetailEffect.TaskDeleted).taskId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `DeleteConfirmed hides dialog`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.DeleteClicked)
            vm.handleIntent(TaskDetailIntent.DeleteConfirmed)
            assertFalse(vm.state.first().isDeleteConfirmVisible)
        }

    // =========================================================================
    // Save — salvar alterações
    // =========================================================================

    @Test
    fun `Save emits TaskUpdated effect with current state data`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.NameChanged("Tarefa editada"))
            vm.handleIntent(TaskDetailIntent.PriorityChanged(5))
            vm.effects.test {
                vm.handleIntent(TaskDetailIntent.Save)
                val effect = awaitItem()
                assertTrue(effect is TaskDetailEffect.TaskUpdated)
                val updated = (effect as TaskDetailEffect.TaskUpdated)
                assertEquals("task_1", updated.taskId)
                assertEquals("Tarefa editada", updated.name)
                assertEquals(5, updated.priority)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Save with invalid fields emits ShowError`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.NameChanged("")) // invalida o name
            vm.effects.test {
                vm.handleIntent(TaskDetailIntent.Save)
                val effect = awaitItem()
                assertTrue(effect is TaskDetailEffect.ShowError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Dismiss — fechar sem salvar
    // =========================================================================

    @Test
    fun `Dismiss emits Dismissed effect`() =
        runTest {
            val vm = createVm()
            vm.effects.test {
                vm.handleIntent(TaskDetailIntent.Dismiss)
                assertEquals(TaskDetailEffect.Dismissed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // hasChanges — detecta mudanças
    // =========================================================================

    @Test
    fun `initial state has no changes`() =
        runTest {
            val vm = createVm()
            assertFalse(vm.state.first().hasChanges)
        }

    @Test
    fun `editing name marks hasChanges`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.NameChanged("Outro nome"))
            assertTrue(vm.state.first().hasChanges)
        }

    @Test
    fun `reverting name to original clears hasChanges`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(TaskDetailIntent.NameChanged("Outro nome"))
            vm.handleIntent(TaskDetailIntent.NameChanged("Comprar material"))
            assertFalse(vm.state.first().hasChanges)
        }
}
