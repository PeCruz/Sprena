package br.com.sprena.presentation.core.navigation

import app.cash.turbine.test
import br.com.sprena.test.MainDispatcherEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * TDD — BottomNavViewModel (barra de navegação inferior).
 *
 * Ordem das abas: HOME, EVENTOS, BAR, FINANCIAL, SETTINGS
 *
 * Cenários cobertos:
 * - Tab inicial é HOME
 * - Transição entre todas as abas
 * - Efeitos de navegação emitidos corretamente
 */
class BottomNavViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    // =========================================================================
    // Initial State
    // =========================================================================

    @Test
    fun `initial tab is HOME`() =
        runTest {
            val vm = BottomNavViewModel()
            assertEquals(BottomTab.HOME, vm.state.first().current)
        }

    @Test
    fun `initial tab is not EVENTOS`() =
        runTest {
            val vm = BottomNavViewModel()
            assertNotEquals(BottomTab.EVENTOS, vm.state.first().current)
        }

    // =========================================================================
    // HOME tab
    // =========================================================================

    @Test
    fun `selecting HOME tab updates state`() =
        runTest {
            val vm = BottomNavViewModel()
            vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.EVENTOS))
            vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.HOME))
            assertEquals(BottomTab.HOME, vm.state.first().current)
        }

    @Test
    fun `selecting HOME tab emits NavigateTo effect`() =
        runTest {
            val vm = BottomNavViewModel()
            vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.EVENTOS))
            vm.effects.test {
                vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.HOME))
                assertEquals(BottomNavEffect.NavigateTo(BottomTab.HOME), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // EVENTOS tab (antigo KANBAN)
    // =========================================================================

    @Test
    fun `selecting EVENTOS tab updates state`() =
        runTest {
            val vm = BottomNavViewModel()
            vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.EVENTOS))
            assertEquals(BottomTab.EVENTOS, vm.state.first().current)
        }

    @Test
    fun `selecting EVENTOS tab emits NavigateTo effect`() =
        runTest {
            val vm = BottomNavViewModel()
            vm.effects.test {
                vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.EVENTOS))
                assertEquals(BottomNavEffect.NavigateTo(BottomTab.EVENTOS), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // BAR tab
    // =========================================================================

    @Test
    fun `selecting BAR tab updates state`() =
        runTest {
            val vm = BottomNavViewModel()
            vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.BAR))
            assertEquals(BottomTab.BAR, vm.state.first().current)
        }

    // =========================================================================
    // FINANCIAL tab
    // =========================================================================

    @Test
    fun `selecting FINANCIAL tab updates state`() =
        runTest {
            val vm = BottomNavViewModel()
            vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.FINANCIAL))
            assertEquals(BottomTab.FINANCIAL, vm.state.first().current)
        }

    @Test
    fun `selecting tab emits NavigateTo effect`() =
        runTest {
            val vm = BottomNavViewModel()
            vm.effects.test {
                vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.FINANCIAL))
                assertEquals(BottomNavEffect.NavigateTo(BottomTab.FINANCIAL), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // SETTINGS tab
    // =========================================================================

    @Test
    fun `selecting SETTINGS tab updates state`() =
        runTest {
            val vm = BottomNavViewModel()
            vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.SETTINGS))
            assertEquals(BottomTab.SETTINGS, vm.state.first().current)
        }

    @Test
    fun `selecting SETTINGS tab emits NavigateTo effect`() =
        runTest {
            val vm = BottomNavViewModel()
            vm.effects.test {
                vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.SETTINGS))
                assertEquals(BottomNavEffect.NavigateTo(BottomTab.SETTINGS), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Tab transitions
    // =========================================================================

    @Test
    fun `selecting HOME after SETTINGS reverts state`() =
        runTest {
            val vm = BottomNavViewModel()
            vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.SETTINGS))
            vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.HOME))
            assertEquals(BottomTab.HOME, vm.state.first().current)
        }

    @Test
    fun `selecting HOME after EVENTOS reverts state`() =
        runTest {
            val vm = BottomNavViewModel()
            vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.EVENTOS))
            vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.HOME))
            assertEquals(BottomTab.HOME, vm.state.first().current)
        }

    @Test
    fun `full cycle through all tabs`() =
        runTest {
            val vm = BottomNavViewModel()
            val tabs =
                listOf(
                    BottomTab.HOME,
                    BottomTab.EVENTOS,
                    BottomTab.BAR,
                    BottomTab.FINANCIAL,
                    BottomTab.SETTINGS,
                )
            for (tab in tabs) {
                vm.handleIntent(BottomNavIntent.TabSelected(tab))
                assertEquals(tab, vm.state.first().current)
            }
        }
}
