package br.com.sprena.presentation.consent

import app.cash.turbine.test
import br.com.sprena.presentation.privacy.PolicyTextLoader
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.privacy.domain.model.ConsentRecord
import br.com.sprena.shared.privacy.domain.model.PrivacyPolicy
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository
import br.com.sprena.shared.privacy.domain.usecase.AcceptConsentUseCase
import br.com.sprena.shared.privacy.domain.usecase.CheckConsentUseCase
import br.com.sprena.test.MainDispatcherEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TDD — ConsentViewModel (gate LGPD, F1.5).
 *
 * Cenários: carga do texto, falha de carga com retry, habilitação do aceite,
 * gravação com sucesso e com falha, sessão ausente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConsentViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private val session =
        SessionUser(
            uid = "uid_1",
            email = "adm@sprena.com",
            role = UserRole.ADM,
            lastLoginEpochMillis = 1L,
        )

    private class FakeLoader(
        var text: String = "Política de Privacidade do Sprena",
        var failure: Throwable? = null,
    ) : PolicyTextLoader {
        override suspend fun load(): String = failure?.let { throw it } ?: text
    }

    private class FakeConsentRepo(
        var acceptResult: Result<Unit> = Result.success(Unit),
        var currentResult: Result<ConsentRecord?> = Result.success(null),
    ) : ConsentRepository {
        override suspend fun current(uid: String): Result<ConsentRecord?> = currentResult

        override suspend fun accept(
            uid: String,
            policyVersion: String,
        ): Result<Unit> = acceptResult
    }

    /** Aceite já registrado na versão vigente — o caso `ConsentStatus.Granted`. */
    private fun grantedRecord() =
        ConsentRecord(
            uid = session.uid,
            policyVersion = PrivacyPolicy.VERSION,
            acceptedAtEpochMillis = 1L,
        )

    private class FakeStore(
        var current: SessionUser?,
    ) : SessionStore {
        override suspend fun save(user: SessionUser) {
            current = user
        }

        override suspend fun load(): SessionUser? = current

        override suspend fun clear() {
            current = null
        }
    }

    private fun viewModel(
        loader: FakeLoader = FakeLoader(),
        repo: FakeConsentRepo = FakeConsentRepo(),
        store: FakeStore = FakeStore(session),
    ) = ConsentViewModel(
        policyLoader = loader,
        acceptConsent = AcceptConsentUseCase(repository = repo, logger = NoOpLogger()),
        checkConsent = CheckConsentUseCase(repository = repo, logger = NoOpLogger()),
        sessionStore = store,
    )

    @Test
    fun `carrega o texto da politica na inicializacao`() =
        runTest {
            val vm = viewModel(loader = FakeLoader(text = "Texto da politica"))

            advanceUntilIdle()

            val state = vm.state.first()
            assertEquals("Texto da politica", state.policyText)
            assertFalse(state.isLoading)
            assertEquals(null, state.error)
        }

    @Test
    fun `falha ao carregar o texto vira erro e o aceite fica bloqueado`() =
        runTest {
            val vm = viewModel(loader = FakeLoader(failure = RuntimeException("io")))

            advanceUntilIdle()

            val state = vm.state.first()
            assertNotNull(state.error)
            assertFalse(state.canAccept)
        }

    @Test
    fun `retry recarrega o texto depois de uma falha`() =
        runTest {
            val loader = FakeLoader(failure = RuntimeException("io"))
            val vm = viewModel(loader = loader)
            advanceUntilIdle()

            loader.failure = null
            loader.text = "Carregou na segunda"
            vm.handleIntent(ConsentIntent.Retry)
            advanceUntilIdle()

            val state = vm.state.first()
            assertEquals("Carregou na segunda", state.policyText)
            assertEquals(null, state.error)
        }

    @Test
    fun `aceite so habilita depois de marcar a leitura`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()
            assertFalse(vm.state.first().canAccept)

            vm.handleIntent(ConsentIntent.ToggleRead)
            advanceUntilIdle()

            assertTrue(vm.state.first().canAccept)
        }

    @Test
    fun `aceite gravado com sucesso navega para a home com a sessao`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()
            vm.handleIntent(ConsentIntent.ToggleRead)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleIntent(ConsentIntent.Accept)
                advanceUntilIdle()
                assertEquals(ConsentEffect.NavigateHome(session), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `falha na gravacao mantem a tela com erro e nao navega`() =
        runTest {
            val repo = FakeConsentRepo(acceptResult = Result.failure(RuntimeException("offline")))
            val vm = viewModel(repo = repo)
            advanceUntilIdle()
            vm.handleIntent(ConsentIntent.ToggleRead)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleIntent(ConsentIntent.Accept)
                advanceUntilIdle()
                expectNoEvents()
            }

            val state = vm.state.first()
            assertNotNull(state.error)
            assertFalse(state.isAccepting)
        }

    // =========================================================================
    // Reconsulta do consentimento no init e no retry
    // =========================================================================

    @Test
    fun `consentimento ja concedido navega direto para a home`() =
        runTest {
            val repo = FakeConsentRepo(currentResult = Result.success(grantedRecord()))
            val vm = viewModel(repo = repo)

            vm.effects.test {
                advanceUntilIdle()
                assertEquals(ConsentEffect.NavigateHome(session), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `falha de leitura do consentimento fica visivel e nao navega`() =
        runTest {
            val repo = FakeConsentRepo(currentResult = Result.failure(RuntimeException("offline")))
            val vm = viewModel(repo = repo)

            vm.effects.test {
                advanceUntilIdle()
                expectNoEvents()
            }

            val state = vm.state.first()
            assertNotNull(state.error)
            assertFalse(state.isLoading)
            // O texto carregou: o bloqueio é da leitura do aceite, não da política.
            assertTrue(state.policyText.isNotBlank())
        }

    @Test
    fun `consentimento pendente mantem a tela pedindo o aceite`() =
        runTest {
            val repo = FakeConsentRepo(currentResult = Result.success(null))
            val vm = viewModel(repo = repo)

            vm.effects.test {
                advanceUntilIdle()
                expectNoEvents()
            }

            val state = vm.state.first()
            assertEquals(null, state.error)
            assertTrue(state.policyText.isNotBlank())
        }

    @Test
    fun `retry depois de falha de leitura navega quando o aceite reaparece`() =
        runTest {
            val repo = FakeConsentRepo(currentResult = Result.failure(RuntimeException("offline")))
            val vm = viewModel(repo = repo)
            advanceUntilIdle()
            assertNotNull(vm.state.first().error)

            repo.currentResult = Result.success(grantedRecord())

            vm.effects.test {
                vm.handleIntent(ConsentIntent.Retry)
                advanceUntilIdle()
                assertEquals(ConsentEffect.NavigateHome(session), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sessao ausente nao consulta consentimento e segue na tela`() =
        runTest {
            val repo = FakeConsentRepo(currentResult = Result.success(grantedRecord()))
            val vm = viewModel(repo = repo, store = FakeStore(null))

            vm.effects.test {
                advanceUntilIdle()
                expectNoEvents()
            }

            assertEquals(null, vm.state.first().error)
        }

    @Test
    fun `sessao ausente manda de volta para o login`() =
        runTest {
            val vm = viewModel(store = FakeStore(null))
            advanceUntilIdle()
            vm.handleIntent(ConsentIntent.ToggleRead)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleIntent(ConsentIntent.Accept)
                advanceUntilIdle()
                assertEquals(ConsentEffect.NavigateLogin, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
