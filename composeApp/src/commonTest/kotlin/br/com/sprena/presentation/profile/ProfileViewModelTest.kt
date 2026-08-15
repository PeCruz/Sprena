package br.com.sprena.presentation.profile

import app.cash.turbine.test
import br.com.sprena.shared.account.domain.model.AccountDeletionResult
import br.com.sprena.shared.account.domain.model.ProfilePatch
import br.com.sprena.shared.account.domain.model.UserProfile
import br.com.sprena.shared.account.domain.repository.AccountDeletionRepository
import br.com.sprena.shared.account.domain.repository.UserProfileRepository
import br.com.sprena.shared.account.domain.usecase.DeleteMyAccountUseCase
import br.com.sprena.shared.account.domain.usecase.ExportMyDataUseCase
import br.com.sprena.shared.account.domain.usecase.GetMyProfileUseCase
import br.com.sprena.shared.account.domain.usecase.SaveMyProfileUseCase
import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.domain.usecase.LogoutUseCase
import br.com.sprena.shared.auth.domain.usecase.RequestPasswordResetUseCase
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.core.time.Clock
import br.com.sprena.shared.privacy.domain.model.ConsentRecord
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository
import br.com.sprena.shared.sportclient.domain.validation.SportModality
import br.com.sprena.test.MainDispatcherEnv
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD — tela "Meus dados" (F1.6a).
 *
 * Os testes que mais importam aqui são os de exclusão: falha **precisa** manter o
 * diálogo aberto e a sessão intacta, senão o titular fica sem saber se a conta foi
 * apagada ou não.
 */
class ProfileViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest
    fun setUp() = env.install()

    private val session =
        SessionUser("uid-1", "pedro@example.com", UserRole.MOD, lastLoginEpochMillis = 1_000L)

    private val profile =
        UserProfile(
            uid = "uid-1",
            email = "pedro@example.com",
            role = UserRole.MOD,
            name = "Pedro",
            apelido = "Pe",
            cpf = "12345678900",
            phone = "11987654321",
            modalities = listOf(SportModality.VOLEI),
        )

    @Test
    fun `carrega o perfil no init`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.isLoading)
            assertEquals(profile, state.profile)
            assertEquals("Pedro", state.displayName)
        }

    @Test
    fun `falha de leitura vira erro com retry, nunca perfil parcial`() =
        runTest {
            val vm = viewModel(profileResult = Result.failure(IllegalStateException("offline")))
            advanceUntilIdle()

            val state = vm.state.value
            assertNull(state.profile)
            assertNotNull(state.error)
        }

    @Test
    fun `CPF e telefone comecam mascarados e revelam no toggle`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()

            assertEquals("***.***.789-00", vm.state.value.displayCpf)
            assertEquals("(11) *****-4321", vm.state.value.displayPhone)

            vm.handleIntent(ProfileIntent.ToggleCpfReveal)
            vm.handleIntent(ProfileIntent.TogglePhoneReveal)

            assertEquals("123.456.789-00", vm.state.value.displayCpf)
            assertEquals("(11) 98765-4321", vm.state.value.displayPhone)
        }

    @Test
    fun `campo ausente vira Nao informado`() =
        runTest {
            val vazio = profile.copy(apelido = null, cpf = null, phone = null, modalities = emptyList())
            val vm = viewModel(profileResult = Result.success(vazio))
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(NOT_INFORMED, state.displayApelido)
            assertEquals(NOT_INFORMED, state.displayCpf)
            assertEquals(NOT_INFORMED, state.displayPhone)
            assertEquals(NONE_INFORMED, state.displayModalities)
        }

    @Test
    fun `editar carrega o rascunho a partir do perfil salvo`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()

            vm.handleIntent(ProfileIntent.EditClicked)

            val draft = vm.state.value.draft
            assertTrue(vm.state.value.isEditing)
            assertEquals("Pe", draft.apelido)
            assertEquals("12345678900", draft.cpf)
            assertEquals(setOf(SportModality.VOLEI), draft.modalities)
        }

    @Test
    fun `cancelar a edicao descarta o rascunho`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()

            vm.handleIntent(ProfileIntent.EditClicked)
            vm.handleIntent(ProfileIntent.ApelidoChanged("Outro"))
            vm.handleIntent(ProfileIntent.EditCancelled)
            vm.handleIntent(ProfileIntent.EditClicked)

            assertEquals("Pe", vm.state.value.draft.apelido)
        }

    @Test
    fun `salvar persiste o rascunho e recarrega o perfil`() =
        runTest {
            val repo = FakeProfileRepo(Result.success(profile))
            val vm = viewModel(repo = repo)
            advanceUntilIdle()

            vm.handleIntent(ProfileIntent.EditClicked)
            vm.handleIntent(ProfileIntent.ApelidoChanged("Pedrinho"))
            vm.handleIntent(ProfileIntent.ModalityToggled(SportModality.FUTEVOLEI))
            vm.handleIntent(ProfileIntent.SaveClicked)
            advanceUntilIdle()

            assertEquals("Pedrinho", repo.lastSavedPatch?.apelido)
            assertTrue(
                repo.lastSavedPatch!!.modalities.containsAll(
                    listOf(SportModality.VOLEI, SportModality.FUTEVOLEI),
                ),
            )
            assertFalse(vm.state.value.isEditing)
        }

    @Test
    fun `CPF invalido marca o campo e nao sai da edicao`() =
        runTest {
            val repo = FakeProfileRepo(Result.success(profile))
            val vm = viewModel(repo = repo)
            advanceUntilIdle()

            vm.handleIntent(ProfileIntent.EditClicked)
            vm.handleIntent(ProfileIntent.CpfChanged("123"))
            vm.handleIntent(ProfileIntent.SaveClicked)
            advanceUntilIdle()

            assertNotNull(vm.state.value.cpfError)
            assertTrue(vm.state.value.isEditing)
            assertNull(repo.lastSavedPatch)
        }

    @Test
    fun `reset de senha usa o email do perfil e emite mensagem`() =
        runTest {
            val auth = FakeAuthRepo()
            val vm = viewModel(auth = auth)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleIntent(ProfileIntent.PasswordResetRequested)
                advanceUntilIdle()

                assertIs<ProfileEffect.ShowMessage>(awaitItem())
                assertEquals("pedro@example.com", auth.lastResetEmail)
            }
        }

    @Test
    fun `exportacao emite ShareExport com o JSON do use case`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()

            vm.effects.test {
                vm.handleIntent(ProfileIntent.ExportConfirmed)
                advanceUntilIdle()

                val effect = assertIs<ProfileEffect.ShareExport>(awaitItem())
                assertTrue(effect.fileName.endsWith(".json"))
                assertTrue(effect.json.contains("12345678900"))
            }
        }

    @Test
    fun `exportacao NAO inclui dados de clientes cadastrados`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()

            vm.effects.test {
                vm.handleIntent(ProfileIntent.ExportConfirmed)
                advanceUntilIdle()

                val effect = assertIs<ProfileEffect.ShareExport>(awaitItem())
                assertFalse(effect.json.contains("sport_clients"))
            }
        }

    @Test
    fun `botao excluir so habilita depois de digitar EXCLUIR`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()

            vm.handleIntent(ProfileIntent.DeleteClicked)
            assertFalse(vm.state.value.canConfirmDelete)

            vm.handleIntent(ProfileIntent.DeleteConfirmationChanged("excluir minha conta"))
            assertFalse(vm.state.value.canConfirmDelete)

            vm.handleIntent(ProfileIntent.DeleteConfirmationChanged("EXCLUIR"))
            assertTrue(vm.state.value.canConfirmDelete)
        }

    @Test
    fun `exclusao com sucesso limpa a sessao e navega para o login`() =
        runTest {
            val store = FakeStore(session)
            val auth = FakeAuthRepo()
            val vm = viewModel(store = store, auth = auth, deletion = AccountDeletionResult.Deleted)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleIntent(ProfileIntent.DeleteClicked)
                vm.handleIntent(ProfileIntent.DeleteConfirmationChanged("EXCLUIR"))
                vm.handleIntent(ProfileIntent.DeleteConfirmed)
                advanceUntilIdle()

                assertIs<ProfileEffect.NavigateToLogin>(awaitItem())
            }
            assertTrue(store.cleared)
            assertTrue(auth.signedOut)
        }

    @Test
    fun `exclusao com falha mantem o dialogo aberto e a sessao intacta`() =
        runTest {
            val store = FakeStore(session)
            val auth = FakeAuthRepo()
            val vm =
                viewModel(
                    store = store,
                    auth = auth,
                    deletion = AccountDeletionResult.Failed("Sem conexão."),
                )
            advanceUntilIdle()

            vm.handleIntent(ProfileIntent.DeleteClicked)
            vm.handleIntent(ProfileIntent.DeleteConfirmationChanged("EXCLUIR"))
            vm.handleIntent(ProfileIntent.DeleteConfirmed)
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state.isDeleteDialogVisible)
            assertEquals("Sem conexão.", state.deleteError)
            assertFalse(state.isDeleting)
            assertFalse(store.cleared)
            assertFalse(auth.signedOut)
        }

    @Test
    fun `logout emite NavigateToLogin`() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()

            vm.effects.test {
                vm.handleIntent(ProfileIntent.Logout)
                advanceUntilIdle()

                assertIs<ProfileEffect.NavigateToLogin>(awaitItem())
            }
        }

    private fun viewModel(
        profileResult: Result<UserProfile?> = Result.success(profile),
        repo: FakeProfileRepo = FakeProfileRepo(profileResult),
        store: FakeStore = FakeStore(session),
        auth: FakeAuthRepo = FakeAuthRepo(),
        deletion: AccountDeletionResult = AccountDeletionResult.Deleted,
    ): ProfileViewModel {
        val logger = NoOpLogger()
        return ProfileViewModel(
            getProfile = GetMyProfileUseCase(repo, store, logger),
            saveProfile = SaveMyProfileUseCase(repo, store, logger),
            exportMyData =
                ExportMyDataUseCase(repo, FakeConsentRepo(), store, FixedClock(1_786_731_725_000L), logger),
            deleteMyAccount = DeleteMyAccountUseCase(FakeDeletionRepo(deletion), auth, store, logger),
            requestPasswordReset = RequestPasswordResetUseCase(auth, logger),
            logout = LogoutUseCase(auth, store, logger),
        )
    }
}

private class FakeProfileRepo(
    private val result: Result<UserProfile?>,
) : UserProfileRepository {
    var lastSavedPatch: ProfilePatch? = null
        private set

    override suspend fun current(uid: String): Result<UserProfile?> = result

    override suspend fun save(
        uid: String,
        patch: ProfilePatch,
    ): Result<Unit> {
        lastSavedPatch = patch
        return Result.success(Unit)
    }
}

private class FakeDeletionRepo(
    private val result: AccountDeletionResult,
) : AccountDeletionRepository {
    override suspend fun deleteMyAccount(): AccountDeletionResult = result
}

private class FakeConsentRepo : ConsentRepository {
    override suspend fun current(uid: String): Result<ConsentRecord?> = Result.success(null)

    override suspend fun accept(
        uid: String,
        policyVersion: String,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun history(uid: String): Result<List<ConsentRecord>> = Result.success(emptyList())
}

private class FakeStore(
    private var current: SessionUser?,
) : SessionStore {
    var cleared = false
        private set

    override suspend fun save(user: SessionUser) {
        current = user
    }

    override suspend fun load(): SessionUser? = current

    override suspend fun clear() {
        cleared = true
        current = null
    }
}

private class FakeAuthRepo : AuthRepository {
    var signedOut = false
        private set
    var lastResetEmail: String? = null
        private set

    override suspend fun authenticate(
        email: String,
        password: String,
    ): AuthResult = AuthResult.Error("não usado")

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        lastResetEmail = email
        return Result.success(Unit)
    }

    override suspend fun signOut() {
        signedOut = true
    }

    override fun currentUid(): String? = if (signedOut) null else "uid-1"

    override suspend fun refreshToken(): Result<Unit> = Result.success(Unit)
}

private class FixedClock(
    private val instant: Long,
) : Clock {
    override fun nowEpochMillis(): Long = instant
}
