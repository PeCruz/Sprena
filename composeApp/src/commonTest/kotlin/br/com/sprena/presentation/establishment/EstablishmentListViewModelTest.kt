package br.com.sprena.presentation.establishment

import app.cash.turbine.test
import br.com.sprena.shared.establishment.domain.usecase.ObserveEstablishmentsUseCase
import br.com.sprena.shared.establishment.domain.usecase.SetEstablishmentActiveUseCase
import br.com.sprena.test.MainDispatcherEnv
import kotlinx.coroutines.test.advanceUntilIdle
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
 * `MainDispatcherEnv` instala um `StandardTestDispatcher`, que nao executa nada
 * automaticamente. A coleta do fluxo comeca no `init` do ViewModel, entao todo teste precisa
 * de `advanceUntilIdle()` antes de ler o estado — sem isso o estado lido e o inicial.
 */
class EstablishmentListViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private fun vm(repo: FakeEstablishmentRepo) =
        EstablishmentListViewModel(
            observeEstablishments = ObserveEstablishmentsUseCase(repo),
            setActive = SetEstablishmentActiveUseCase(repo),
        )

    @Test
    fun `carrega a lista e sai do estado de carregamento`() =
        runTest {
            val repo = FakeEstablishmentRepo()
            repo.all.value = Result.success(listOf(establishment("e1", "Arena")))

            val model = vm(repo)
            advanceUntilIdle()
            val state = model.state.value

            assertEquals(listOf("Arena"), state.establishments.map { it.name })
            assertFalse(state.isLoading)
            assertNull(state.error)
        }

    @Test
    fun `inativos continuam na lista`() =
        runTest {
            val repo = FakeEstablishmentRepo()
            repo.all.value =
                Result.success(
                    listOf(establishment("e1", "Arena"), establishment("e2", "Bar", active = false)),
                )

            val model = vm(repo)
            advanceUntilIdle()
            val state = model.state.value

            // Desativar é o "excluir" do produto — some da barra de quem trabalha lá, mas o
            // ADM precisa continuar vendo para poder reativar.
            assertEquals(2, state.establishments.size)
            assertEquals(false, state.establishments.last().active)
        }

    @Test
    fun `falha de leitura vira erro legivel, nao lista vazia`() =
        runTest {
            val repo = FakeEstablishmentRepo()
            repo.all.value = Result.failure(RuntimeException("permission denied"))

            val model = vm(repo)
            advanceUntilIdle()
            val state = model.state.value

            // Lista vazia diria "não há estabelecimentos", que é outra coisa — e mandaria o
            // ADM cadastrar um que talvez já exista.
            assertNotNull(state.error)
            assertFalse(state.isLoading)
            assertTrue(state.establishments.isEmpty())
            // A causa técnica fica no log, nunca na tela.
            assertFalse(state.error!!.contains("permission denied"))
        }

    @Test
    fun `recuperacao apos falha limpa o erro`() =
        runTest {
            val repo = FakeEstablishmentRepo()
            repo.all.value = Result.failure(RuntimeException("offline"))
            val model = vm(repo)
            advanceUntilIdle()
            assertNotNull(model.state.value.error)

            repo.all.value = Result.success(listOf(establishment("e1")))
            advanceUntilIdle()

            assertNull(model.state.value.error)
            assertEquals(1, model.state.value.establishments.size)
        }

    @Test
    fun `alternar ativo chama o caso de uso com o valor invertido`() =
        runTest {
            val repo = FakeEstablishmentRepo()
            repo.all.value = Result.success(listOf(establishment("e1", active = true)))
            val model = vm(repo)

            model.handleIntent(EstablishmentListIntent.ToggleActive("e1", active = false))
            advanceUntilIdle()

            assertEquals(listOf("e1" to false), repo.setActiveCalls)
        }

    @Test
    fun `falha ao alternar avisa o usuario`() =
        runTest {
            val repo = FakeEstablishmentRepo(setActiveResult = Result.failure(RuntimeException("boom")))
            repo.all.value = Result.success(listOf(establishment("e1")))
            val model = vm(repo)

            model.effects.test {
                model.handleIntent(EstablishmentListIntent.ToggleActive("e1", active = false))
                val effect = awaitItem()
                assertTrue(effect is EstablishmentListEffect.ShowMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `criar e editar emitem navegacao com o id certo`() =
        runTest {
            val repo = FakeEstablishmentRepo()
            repo.all.value = Result.success(listOf(establishment("e1")))
            val model = vm(repo)

            model.effects.test {
                model.handleIntent(EstablishmentListIntent.CreateClicked)
                // null significa "novo" — é o mesmo formulário, sem id para carregar.
                assertEquals(EstablishmentListEffect.NavigateToEdit(null), awaitItem())

                model.handleIntent(EstablishmentListIntent.EstablishmentClicked("e1"))
                assertEquals(EstablishmentListEffect.NavigateToEdit("e1"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
