package br.com.sprena.presentation.home

import br.com.sprena.shared.auth.domain.model.UserModel
import br.com.sprena.shared.auth.domain.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * TDD — HomeViewModel (tela principal adaptativa por role).
 *
 * Cenários cobertos:
 *  - Estado inicial (defaults)
 *  - UserLoaded com ADM: título, subtítulo e role corretos
 *  - UserLoaded com MOD: título, subtítulo e role corretos
 *  - UserLoaded com CLIENT: título, subtítulo e role corretos
 *  - Preservação do userName em todos os roles
 *  - OnRefresh reseta loading
 */
class HomeViewModelTest {
    private fun createVm() = HomeViewModel()

    private val admUser =
        UserModel(
            id = "1",
            email = "admin",
            name = "Pedro Admin",
            role = UserRole.ADM,
        )

    private val modUser =
        UserModel(
            id = "2",
            email = "mod",
            name = "Maria Moderadora",
            role = UserRole.MOD,
        )

    private val clientUser =
        UserModel(
            id = "3",
            email = "func",
            name = "João Funcionário",
            role = UserRole.CLIENT,
        )

    // =========================================================================
    // Estado Inicial
    // =========================================================================

    @Test
    fun `initial state has title Sprena`() =
        runTest {
            val vm = createVm()
            assertEquals("Sprena", vm.uiState.first().title)
        }

    @Test
    fun `initial state has default subtitle`() =
        runTest {
            val vm = createVm()
            assertEquals("Bem-vindo ao Sprena", vm.uiState.first().subtitle)
        }

    @Test
    fun `initial state has empty userName`() =
        runTest {
            val vm = createVm()
            assertEquals("", vm.uiState.first().userName)
        }

    @Test
    fun `initial state has CLIENT role`() =
        runTest {
            val vm = createVm()
            assertEquals(UserRole.CLIENT, vm.uiState.first().userRole)
        }

    @Test
    fun `initial state is not loading`() =
        runTest {
            val vm = createVm()
            assertFalse(vm.uiState.first().isLoading)
        }

    // =========================================================================
    // UserLoaded — ADM
    // =========================================================================

    @Test
    fun `UserLoaded ADM sets title to Painel Admin`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(admUser))
            assertEquals("Painel Admin", vm.uiState.first().title)
        }

    @Test
    fun `UserLoaded ADM sets subtitle with name and full access`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(admUser))
            val subtitle = vm.uiState.first().subtitle
            assertEquals("Olá, Pedro Admin! Acesso total ao sistema.", subtitle)
        }

    @Test
    fun `UserLoaded ADM sets userRole to ADM`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(admUser))
            assertEquals(UserRole.ADM, vm.uiState.first().userRole)
        }

    @Test
    fun `UserLoaded ADM preserves userName`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(admUser))
            assertEquals("Pedro Admin", vm.uiState.first().userName)
        }

    // =========================================================================
    // UserLoaded — MOD
    // =========================================================================

    @Test
    fun `UserLoaded MOD sets title to Painel Moderador`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(modUser))
            assertEquals("Painel Moderador", vm.uiState.first().title)
        }

    @Test
    fun `UserLoaded MOD sets subtitle with name and event management`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(modUser))
            val subtitle = vm.uiState.first().subtitle
            assertEquals("Olá, Maria Moderadora! Gestão de eventos e visualizações.", subtitle)
        }

    @Test
    fun `UserLoaded MOD sets userRole to MOD`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(modUser))
            assertEquals(UserRole.MOD, vm.uiState.first().userRole)
        }

    @Test
    fun `UserLoaded MOD preserves userName`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(modUser))
            assertEquals("Maria Moderadora", vm.uiState.first().userName)
        }

    // =========================================================================
    // UserLoaded — CLIENT
    // =========================================================================

    @Test
    fun `UserLoaded CLIENT sets title to Sprena`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(clientUser))
            assertEquals("Sprena", vm.uiState.first().title)
        }

    @Test
    fun `UserLoaded CLIENT sets subtitle with name and activity tracking`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(clientUser))
            val subtitle = vm.uiState.first().subtitle
            assertEquals("Olá, João Funcionário! Acompanhe suas atividades.", subtitle)
        }

    @Test
    fun `UserLoaded CLIENT sets userRole to CLIENT`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(clientUser))
            assertEquals(UserRole.CLIENT, vm.uiState.first().userRole)
        }

    @Test
    fun `UserLoaded CLIENT preserves userName`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(clientUser))
            assertEquals("João Funcionário", vm.uiState.first().userName)
        }

    // =========================================================================
    // UserLoaded — troca de role (re-login)
    // =========================================================================

    @Test
    fun `UserLoaded can switch from ADM to CLIENT`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(admUser))
            assertEquals(UserRole.ADM, vm.uiState.first().userRole)

            vm.onEvent(HomeUiEvent.UserLoaded(clientUser))
            assertEquals(UserRole.CLIENT, vm.uiState.first().userRole)
            assertEquals("Sprena", vm.uiState.first().title)
            assertEquals("João Funcionário", vm.uiState.first().userName)
        }

    @Test
    fun `UserLoaded can switch from CLIENT to MOD`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(clientUser))
            vm.onEvent(HomeUiEvent.UserLoaded(modUser))
            assertEquals(UserRole.MOD, vm.uiState.first().userRole)
            assertEquals("Painel Moderador", vm.uiState.first().title)
        }

    // =========================================================================
    // OnRefresh
    // =========================================================================

    @Test
    fun `OnRefresh sets loading to false`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.OnRefresh)
            assertFalse(vm.uiState.first().isLoading)
        }

    @Test
    fun `OnRefresh preserves user data`() =
        runTest {
            val vm = createVm()
            vm.onEvent(HomeUiEvent.UserLoaded(admUser))
            vm.onEvent(HomeUiEvent.OnRefresh)
            val state = vm.uiState.first()
            assertEquals("Pedro Admin", state.userName)
            assertEquals(UserRole.ADM, state.userRole)
            assertEquals("Painel Admin", state.title)
        }
}
