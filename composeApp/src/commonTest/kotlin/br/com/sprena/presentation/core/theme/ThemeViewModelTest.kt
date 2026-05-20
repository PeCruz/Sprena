package br.com.sprena.presentation.core.theme

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
 * Testes do botão Light/Dark Mode.
 */
class ThemeViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    // Negative — modo inicial é SYSTEM, não DARK
    @Test
    fun `initial mode is SYSTEM and not DARK`() =
        runTest {
            val vm = ThemeViewModel()
            val mode = vm.state.first().mode
            assertEquals(ThemeMode.SYSTEM, mode)
            assertNotEquals(ThemeMode.DARK, mode)
        }

    // Positive — Set(DARK) muda o modo
    @Test
    fun `Set DARK updates mode`() =
        runTest {
            val vm = ThemeViewModel()
            vm.handleIntent(ThemeIntent.Set(ThemeMode.DARK))
            assertEquals(ThemeMode.DARK, vm.state.first().mode)
        }

    @Test
    fun `Set LIGHT updates mode`() =
        runTest {
            val vm = ThemeViewModel()
            vm.handleIntent(ThemeIntent.Set(ThemeMode.LIGHT))
            assertEquals(ThemeMode.LIGHT, vm.state.first().mode)
        }

    // Positive — Toggle alterna Light/Dark
    @Test
    fun `Toggle from LIGHT yields DARK`() =
        runTest {
            val vm = ThemeViewModel()
            vm.handleIntent(ThemeIntent.Set(ThemeMode.LIGHT))
            vm.handleIntent(ThemeIntent.Toggle)
            assertEquals(ThemeMode.DARK, vm.state.first().mode)
        }

    @Test
    fun `Toggle from DARK yields LIGHT`() =
        runTest {
            val vm = ThemeViewModel()
            vm.handleIntent(ThemeIntent.Set(ThemeMode.DARK))
            vm.handleIntent(ThemeIntent.Toggle)
            assertEquals(ThemeMode.LIGHT, vm.state.first().mode)
        }

    // Positive — Set emite efeito
    @Test
    fun `Set emits ThemeChanged effect`() =
        runTest {
            val vm = ThemeViewModel()
            vm.effects.test {
                vm.handleIntent(ThemeIntent.Set(ThemeMode.DARK))
                assertEquals(ThemeEffect.ThemeChanged(ThemeMode.DARK), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
