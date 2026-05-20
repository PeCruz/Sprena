package br.com.sprena.presentation.kanban

import app.cash.turbine.test
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
 * Testes da tela Kanban (Home) — board + botão "Add Task".
 */
class KanbanViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    // Negative — estado inicial NÃO deve mostrar diálogo
    @Test
    fun `initial state does not show add task dialog`() =
        runTest {
            val vm = KanbanViewModel()
            assertFalse(vm.state.first().isAddTaskDialogVisible)
        }

    // Negative — estado inicial vazio (sem tasks)
    @Test
    fun `initial state has no tasks`() =
        runTest {
            val vm = KanbanViewModel()
            assertTrue(
                vm.state
                    .first()
                    .tasksByColumn
                    .isEmpty(),
            )
        }

    // Positive — clicar Add Task emite efeito de abertura de diálogo
    @Test
    fun `AddTaskClicked emits OpenAddTaskDialog effect`() =
        runTest {
            val vm = KanbanViewModel()
            vm.effects.test {
                vm.handleIntent(KanbanIntent.AddTaskClicked)
                assertEquals(KanbanEffect.OpenAddTaskDialog, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Positive — clicar Add Task marca isAddTaskDialogVisible = true
    @Test
    fun `AddTaskClicked sets dialog visible`() =
        runTest {
            val vm = KanbanViewModel()
            vm.handleIntent(KanbanIntent.AddTaskClicked)
            assertTrue(vm.state.first().isAddTaskDialogVisible)
        }

    @Test
    fun `DismissAddTaskDialog hides dialog`() =
        runTest {
            val vm = KanbanViewModel()
            vm.handleIntent(KanbanIntent.AddTaskClicked)
            vm.handleIntent(KanbanIntent.DismissAddTaskDialog)
            assertFalse(vm.state.first().isAddTaskDialogVisible)
        }

    // Positive — LoadBoard preenche o state
    @Test
    fun `LoadBoard populates columns`() =
        runTest {
            val vm = KanbanViewModel()
            vm.handleIntent(KanbanIntent.LoadBoard)
            val s = vm.state.first()
            assertTrue(s.columns.isNotEmpty(), "LoadBoard deveria popular colunas padrão")
        }

    // =========================================================================
    // Search — busca de tarefas (case-insensitive, match em qualquer posição)
    // =========================================================================

    // Negative — estado inicial sem query de busca
    @Test
    fun `initial state has empty search query`() =
        runTest {
            val vm = KanbanViewModel()
            assertEquals("", vm.state.first().searchQuery)
        }

    // Positive — alterar query atualiza o state
    @Test
    fun `SearchQueryChanged updates searchQuery in state`() =
        runTest {
            val vm = KanbanViewModel()
            vm.handleIntent(KanbanIntent.SearchQueryChanged("Pedro"))
            assertEquals("Pedro", vm.state.first().searchQuery)
        }

    // Positive — busca filtra tasks que contêm a string em qualquer posição
    @Test
    fun `search filters tasks matching anywhere in name`() =
        runTest {
            val vm = createVmWithTasks()
            vm.handleIntent(KanbanIntent.SearchQueryChanged("Pedro"))
            val filtered = vm.state.first().filteredTasksByColumn
            val allTasks = filtered.values.flatten()
            assertTrue(allTasks.all { it.name.contains("Pedro", ignoreCase = true) })
            assertTrue(allTasks.isNotEmpty())
        }

    // Positive — busca é case-insensitive
    @Test
    fun `search is case insensitive`() =
        runTest {
            val vm = createVmWithTasks()
            vm.handleIntent(KanbanIntent.SearchQueryChanged("pedro"))
            val filtered = vm.state.first().filteredTasksByColumn
            val allTasks = filtered.values.flatten()
            assertTrue(allTasks.any { it.name.contains("Pedro") })
        }

    // Positive — busca vazia retorna todas as tasks
    @Test
    fun `empty search returns all tasks`() =
        runTest {
            val vm = createVmWithTasks()
            vm.handleIntent(KanbanIntent.SearchQueryChanged("Pedro"))
            vm.handleIntent(KanbanIntent.SearchQueryChanged(""))
            val filtered = vm.state.first().filteredTasksByColumn
            val allFiltered = filtered.values.flatten()
            val allOriginal =
                vm.state
                    .first()
                    .tasksByColumn.values
                    .flatten()
            assertEquals(allOriginal.size, allFiltered.size)
        }

    // Negative — busca com termo que não existe retorna lista vazia
    @Test
    fun `search with no match returns empty`() =
        runTest {
            val vm = createVmWithTasks()
            vm.handleIntent(KanbanIntent.SearchQueryChanged("xyz_nao_existe"))
            val filtered = vm.state.first().filteredTasksByColumn
            val allTasks = filtered.values.flatten()
            assertTrue(allTasks.isEmpty())
        }

    // Positive — busca encontra match no meio da string
    @Test
    fun `search matches substring in the middle`() =
        runTest {
            val vm = createVmWithTasks()
            // "Buscar o Pedro em casa" contém "Pedro" no meio
            vm.handleIntent(KanbanIntent.SearchQueryChanged("Buscar"))
            val filtered = vm.state.first().filteredTasksByColumn
            val allTasks = filtered.values.flatten()
            assertTrue(allTasks.any { it.name.contains("Buscar") })
        }

    // =========================================================================
    // Settings — navegação para tela de configurações
    // =========================================================================

    // Positive — clicar Settings emite efeito de navegação
    @Test
    fun `SettingsClicked emits NavigateToSettings effect`() =
        runTest {
            val vm = KanbanViewModel()
            vm.effects.test {
                vm.handleIntent(KanbanIntent.SettingsClicked)
                assertEquals(KanbanEffect.NavigateToSettings, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Helper — cria VM com tasks de exemplo para testar busca
    // =========================================================================

    private fun createVmWithTasks(): KanbanViewModel {
        val vm = KanbanViewModel()
        vm.handleIntent(KanbanIntent.LoadBoard)
        // Injeta tasks de exemplo via AddTestTasks (intent especial para testes)
        vm.handleIntent(
            KanbanIntent.AddTestTasks(
                mapOf(
                    "col_todo" to
                        listOf(
                            KanbanTask("t1", "col_todo", "Buscar o Pedro em casa", 3),
                            KanbanTask("t2", "col_todo", "Comprar material escritório", 2),
                        ),
                    "col_progress" to
                        listOf(
                            KanbanTask("t3", "col_progress", "Reunião com Pedro e Maria", 4),
                        ),
                    "col_done" to
                        listOf(
                            KanbanTask("t4", "col_done", "Entregar relatório final", 1),
                        ),
                ),
            ),
        )
        return vm
    }
}
