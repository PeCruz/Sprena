package br.com.sprena.presentation.core.navigation

import app.cash.turbine.test
import br.com.sprena.shared.auth.domain.model.UserRole
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
 * A barra deixou de ser fixa em F1.7.3. Antes destes testes afirmarem que a aba inicial é
 * `HOME`, hoje eles afirmam que ela é a primeira aba **do papel** — `HOME` nem existe para ADM
 * e USER.
 */
class BottomNavViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private fun vmFor(
        role: UserRole,
        hasEstablishment: Boolean = true,
    ) = BottomNavViewModel().apply {
        handleIntent(BottomNavIntent.TabsResolved(tabsFor(role, hasEstablishment)))
    }

    @Test
    fun `antes de resolver nao ha aba nem barra`() =
        runTest {
            val state = BottomNavViewModel().state.first()

            assertNull(state.current)
            assertTrue(state.tabs.isEmpty())
            // Ainda não resolveu: é carregamento, não ausência de estabelecimento.
            assertFalse(state.isWithoutEstablishment)
        }

    @Test
    fun `aba inicial e a primeira do papel`() =
        runTest {
            assertEquals(BottomTab.HOME, vmFor(UserRole.MOD).state.first().current)
            assertEquals(BottomTab.EVENTOS, vmFor(UserRole.ADM).state.first().current)
            assertEquals(BottomTab.BAR, vmFor(UserRole.USER).state.first().current)
        }

    @Test
    fun `sem estabelecimento sinaliza a tela de erro em vez de carregamento`() =
        runTest {
            val state = vmFor(UserRole.USER, hasEstablishment = false).state.first()

            assertTrue(state.isWithoutEstablishment)
            assertNull(state.current)
        }

    @Test
    fun `troca de aba atualiza o estado e emite o efeito`() =
        runTest {
            val vm = vmFor(UserRole.MOD)

            vm.effects.test {
                vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.FINANCIAL))
                assertEquals(BottomNavEffect.NavigateTo(BottomTab.FINANCIAL), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(BottomTab.FINANCIAL, vm.state.first().current)
        }

    @Test
    fun `ciclo completo pelas abas do papel`() =
        runTest {
            val vm = vmFor(UserRole.MOD)

            for (tab in tabsFor(UserRole.MOD, hasEstablishment = true)) {
                vm.handleIntent(BottomNavIntent.TabSelected(tab))
                assertEquals(tab, vm.state.first().current)
            }
        }

    @Test
    fun `aba fora do papel e ignorada`() =
        runTest {
            val vm = vmFor(UserRole.CLIENT)
            val antes = vm.state.first().current

            vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.FINANCIAL))

            // CLIENT não tem Financeiro. Ignorar mantém a barra com um item selecionado;
            // aceitar deixaria a barra sem nenhum item marcado.
            assertEquals(antes, vm.state.first().current)
        }

    @Test
    fun `mudanca de papel preserva a aba quando ela sobrevive`() =
        runTest {
            val vm = vmFor(UserRole.MOD)
            vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.BAR))

            vm.handleIntent(BottomNavIntent.TabsResolved(tabsFor(UserRole.CLIENT, true)))

            assertEquals(BottomTab.BAR, vm.state.first().current)
        }

    @Test
    fun `mudanca de papel realoca a aba quando ela desaparece`() =
        runTest {
            val vm = vmFor(UserRole.MOD)
            vm.handleIntent(BottomNavIntent.TabSelected(BottomTab.FINANCIAL))

            // Rebaixado a CLIENT enquanto estava no Financeiro.
            vm.handleIntent(BottomNavIntent.TabsResolved(tabsFor(UserRole.CLIENT, true)))

            assertEquals(BottomTab.HOME, vm.state.first().current)
        }
}
