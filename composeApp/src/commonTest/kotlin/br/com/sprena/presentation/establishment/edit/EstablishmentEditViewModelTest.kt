package br.com.sprena.presentation.establishment.edit

import app.cash.turbine.test
import br.com.sprena.presentation.establishment.FakeEstablishmentRepo
import br.com.sprena.presentation.establishment.establishment
import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.establishment.domain.usecase.GetEstablishmentUseCase
import br.com.sprena.shared.establishment.domain.usecase.SaveEstablishmentUseCase
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

class EstablishmentEditViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private fun vm(
        repo: FakeEstablishmentRepo,
        id: String? = null,
    ) = EstablishmentEditViewModel(
        establishmentId = id,
        getEstablishment = GetEstablishmentUseCase(repo),
        saveEstablishment = SaveEstablishmentUseCase(repo, NoOpLogger()),
    )

    /** Preenche o rascunho com um cadastro válido — o CNPJ tem dígito verificador correto. */
    private fun EstablishmentEditViewModel.preencherValido() {
        handleIntent(EstablishmentEditIntent.NameChanged("Bar do Ze"))
        handleIntent(EstablishmentEditIntent.CnpjChanged("11222333000181"))
        handleIntent(EstablishmentEditIntent.PhoneChanged("11987654321"))
        handleIntent(EstablishmentEditIntent.EmailChanged("contato@bar.com.br"))
    }

    @Test
    fun `sem id abre o formulario em branco e nao consulta o repositorio`() =
        runTest {
            val repo = FakeEstablishmentRepo()

            val model = vm(repo, id = null)
            advanceUntilIdle()

            assertTrue(model.state.value.isCreating)
            assertFalse(model.state.value.isLoading)
            assertEquals("", model.state.value.draft.name)
            assertNull(repo.requestedId)
        }

    @Test
    fun `com id carrega o cadastro existente`() =
        runTest {
            val repo = FakeEstablishmentRepo(byId = Result.success(establishment("e1", "Arena")))

            val model = vm(repo, id = "e1")
            advanceUntilIdle()

            assertFalse(model.state.value.isCreating)
            assertEquals("Arena", model.state.value.draft.name)
            assertEquals("e1", repo.requestedId)
        }

    @Test
    fun `id que nao existe mais vira erro em vez de formulario em branco`() =
        runTest {
            val repo = FakeEstablishmentRepo(byId = Result.success(null))

            val model = vm(repo, id = "removido")
            advanceUntilIdle()

            // Um formulário em branco aqui salvaria como novo, criando um duplicado do que o
            // ADM pensou estar editando.
            assertNotNull(model.state.value.error)
        }

    @Test
    fun `falha ao carregar vira erro`() =
        runTest {
            val repo = FakeEstablishmentRepo(byId = Result.failure(RuntimeException("offline")))

            val model = vm(repo, id = "e1")
            advanceUntilIdle()

            assertNotNull(model.state.value.error)
            assertFalse(
                model.state.value.error!!
                    .contains("offline"),
            )
        }

    @Test
    fun `salvar valido cria e navega de volta`() =
        runTest {
            val repo = FakeEstablishmentRepo()
            val model = vm(repo)
            advanceUntilIdle()
            model.preencherValido()

            model.effects.test {
                model.handleIntent(EstablishmentEditIntent.SaveClicked)
                advanceUntilIdle()
                assertEquals(EstablishmentEditEffect.SavedAndClose, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals("11222333000181", repo.created?.cnpj)
        }

    @Test
    fun `campo invalido marca o campo e nao grava`() =
        runTest {
            val repo = FakeEstablishmentRepo()
            val model = vm(repo)
            advanceUntilIdle()
            model.preencherValido()
            model.handleIntent(EstablishmentEditIntent.CnpjChanged("11222333000182"))

            model.handleIntent(EstablishmentEditIntent.SaveClicked)
            advanceUntilIdle()

            assertEquals("CNPJ inválido", model.state.value.cnpjError)
            assertNull(repo.created)
            assertFalse(model.state.value.isSaving)
        }

    @Test
    fun `editar um campo limpa o erro daquele campo`() =
        runTest {
            val repo = FakeEstablishmentRepo()
            val model = vm(repo)
            advanceUntilIdle()
            model.handleIntent(EstablishmentEditIntent.SaveClicked)
            advanceUntilIdle()
            assertNotNull(model.state.value.nameError)

            model.handleIntent(EstablishmentEditIntent.NameChanged("Bar"))

            assertNull(model.state.value.nameError)
        }

    @Test
    fun `CNPJ duplicado tem mensagem propria, nao erro generico`() =
        runTest {
            val repo = FakeEstablishmentRepo(cnpjTaken = Result.success(true))
            val model = vm(repo)
            advanceUntilIdle()
            model.preencherValido()

            model.handleIntent(EstablishmentEditIntent.SaveClicked)
            advanceUntilIdle()

            // O número está certo; o problema é que já existe outro estabelecimento com ele.
            // Sem essa distinção o ADM ficaria procurando erro de digitação.
            val erro = model.state.value.cnpjError
            assertNotNull(erro)
            assertTrue(erro.contains("já", ignoreCase = true), "mensagem foi: $erro")
            assertNull(repo.created)
        }

    @Test
    fun `falha de gravacao avisa sem fechar a tela`() =
        runTest {
            val repo = FakeEstablishmentRepo(createResult = Result.failure(RuntimeException("boom")))
            val model = vm(repo)
            advanceUntilIdle()
            model.preencherValido()

            model.effects.test {
                model.handleIntent(EstablishmentEditIntent.SaveClicked)
                advanceUntilIdle()
                val effect = awaitItem()
                // Fechar a tela aqui perderia o que foi digitado.
                assertTrue(effect is EstablishmentEditEffect.ShowMessage)
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(model.state.value.isSaving)
        }

    @Test
    fun `editar preserva o id ao salvar`() =
        runTest {
            val repo = FakeEstablishmentRepo(byId = Result.success(establishment("e1", "Arena")))
            val model = vm(repo, id = "e1")
            advanceUntilIdle()

            model.handleIntent(EstablishmentEditIntent.NameChanged("Arena Nova"))
            model.handleIntent(EstablishmentEditIntent.SaveClicked)
            advanceUntilIdle()

            assertEquals("e1", repo.updated?.id)
            assertEquals("Arena Nova", repo.updated?.name)
            assertNull(repo.created)
        }
}
