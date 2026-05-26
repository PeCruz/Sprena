package br.com.sprena.presentation.login

import app.cash.turbine.test
import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.UserModel
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.domain.usecase.LoginUseCase
import br.com.sprena.shared.auth.domain.usecase.RequestPasswordResetUseCase
import br.com.sprena.shared.auth.domain.validation.LoginValidator
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.core.time.Clock
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD — LoginViewModel (autenticação Firebase Auth + reset de senha).
 *
 * Cenários cobertos:
 *  - Estado inicial (defaults)
 *  - Digitação de email: atualiza state, trunca em EMAIL_MAX_LENGTH chars
 *  - Digitação de password: atualiza state
 *  - Erros de validação inline (só exibidos quando campo não-vazio e inválido)
 *  - canSubmit: combinações válido/inválido
 *  - Toggle de visibilidade de senha
 *  - Submit com campos inválidos: erros no state + efeito ShowError
 *  - Submit com credenciais corretas: loading, NavigateHome com UserModel completo
 *  - Submit com falha do repo: ShowError
 *  - Loading state reseta após submit (sucesso e erro)
 *  - Fluxo de reset de senha: abrir dialog, validar email, enviar, sucesso/erro
 */
class LoginViewModelTest {
    private val env = MainDispatcherEnv()

    private class FakeAuthRepository : AuthRepository {
        var nextResult: AuthResult =
            AuthResult.Success(
                UserModel(id = "u1", email = "admin@sprena.com", name = "Pedro Admin", role = UserRole.ADM),
            )
        var nextPasswordResetResult: Result<Unit> = Result.success(Unit)
        var lastEmail: String? = null
        var lastPassword: String? = null
        var lastResetEmail: String? = null

        override suspend fun authenticate(
            email: String,
            password: String,
        ): AuthResult {
            lastEmail = email
            lastPassword = password
            return nextResult
        }

        override suspend fun sendPasswordReset(email: String): Result<Unit> {
            lastResetEmail = email
            return nextPasswordResetResult
        }

        override suspend fun signOut() = Unit

        override fun currentUid(): String? = null
    }

    // Simulação de erro de rede para o reset (nome contém "UnknownHostException"
    // para acionar a branch isNetworkError do use case).
    private class FakeUnknownHostException(
        message: String,
    ) : RuntimeException(message)

    private class FakeSessionStore : SessionStore {
        var saved: SessionUser? = null
        var cleared = false

        override suspend fun save(user: SessionUser) {
            saved = user
        }

        override suspend fun load(): SessionUser? = saved

        override suspend fun clear() {
            saved = null
            cleared = true
        }
    }

    private class FixedClock(
        private val now: Long,
    ) : Clock {
        override fun nowEpochMillis(): Long = now
    }

    private lateinit var authRepo: FakeAuthRepository
    private lateinit var sessionStore: FakeSessionStore
    private lateinit var loginUseCase: LoginUseCase
    private lateinit var requestPasswordReset: RequestPasswordResetUseCase

    @BeforeTest
    fun setUp() {
        env.install()
        authRepo = FakeAuthRepository()
        sessionStore = FakeSessionStore()
        loginUseCase = LoginUseCase(authRepo, sessionStore, FixedClock(1_000L), NoOpLogger())
        requestPasswordReset = RequestPasswordResetUseCase(authRepo, NoOpLogger())
    }

    @AfterTest
    fun tearDown() = env.uninstall()

    private fun createViewModel() = LoginViewModel(loginUseCase, requestPasswordReset)

    // =========================================================================
    // Estado Inicial
    // =========================================================================

    @Test
    fun `initial state has empty email`() =
        runTest {
            val vm = createViewModel()
            assertEquals("", vm.state.first().email)
        }

    @Test
    fun `initial state has empty password`() =
        runTest {
            val vm = createViewModel()
            assertEquals("", vm.state.first().password)
        }

    @Test
    fun `initial state has no email error`() =
        runTest {
            val vm = createViewModel()
            assertNull(vm.state.first().emailError)
        }

    @Test
    fun `initial state has no password error`() =
        runTest {
            val vm = createViewModel()
            assertNull(vm.state.first().passwordError)
        }

    @Test
    fun `initial state has password hidden`() =
        runTest {
            val vm = createViewModel()
            assertFalse(vm.state.first().isPasswordVisible)
        }

    @Test
    fun `initial state is not loading`() =
        runTest {
            val vm = createViewModel()
            assertFalse(vm.state.first().isLoading)
        }

    @Test
    fun `initial state cannot submit`() =
        runTest {
            val vm = createViewModel()
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `initial state has password reset dialog closed`() =
        runTest {
            val vm = createViewModel()
            assertFalse(vm.state.first().passwordResetDialogOpen)
        }

    // =========================================================================
    // Email — digitação e truncamento
    // =========================================================================

    @Test
    fun `email changed updates state`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.EmailChanged("pedro@gmail.com"))
            assertEquals("pedro@gmail.com", vm.state.first().email)
        }

    @Test
    fun `email truncated at max length`() =
        runTest {
            val vm = createViewModel()
            val tooLong = "a".repeat(LoginValidator.EMAIL_MAX_LENGTH + 10)
            vm.handleIntent(LoginIntent.EmailChanged(tooLong))
            assertEquals(
                LoginValidator.EMAIL_MAX_LENGTH,
                vm.state
                    .first()
                    .email.length,
            )
        }

    // =========================================================================
    // Email — erros de validação inline
    // =========================================================================

    @Test
    fun `email empty does not show error`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.EmailChanged(""))
            assertNull(vm.state.first().emailError)
        }

    @Test
    fun `email malformed shows error`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.EmailChanged("nao-eh-email"))
            assertTrue(vm.state.first().emailError != null)
        }

    @Test
    fun `email valid clears error`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.EmailChanged("ruim"))
            assertTrue(vm.state.first().emailError != null)
            vm.handleIntent(LoginIntent.EmailChanged("pedro@gmail.com"))
            assertNull(vm.state.first().emailError)
        }

    // =========================================================================
    // Password — digitação e validação
    // =========================================================================

    @Test
    fun `password changed updates state`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.PasswordChanged("abc123"))
            assertEquals("abc123", vm.state.first().password)
        }

    @Test
    fun `password empty does not show error`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.PasswordChanged(""))
            assertNull(vm.state.first().passwordError)
        }

    @Test
    fun `password below required length shows error`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.PasswordChanged("abc12"))
            assertTrue(vm.state.first().passwordError != null)
        }

    @Test
    fun `password at min length clears error`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.PasswordChanged("abc12"))
            assertTrue(vm.state.first().passwordError != null)
            vm.handleIntent(LoginIntent.PasswordChanged("abc123"))
            assertNull(vm.state.first().passwordError)
        }

    // =========================================================================
    // Toggle visibilidade da senha
    // =========================================================================

    @Test
    fun `toggle password visibility flips flag to true`() =
        runTest {
            val vm = createViewModel()
            assertFalse(vm.state.first().isPasswordVisible)
            vm.handleIntent(LoginIntent.TogglePasswordVisibility)
            assertTrue(vm.state.first().isPasswordVisible)
        }

    @Test
    fun `toggle password visibility twice returns to false`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.TogglePasswordVisibility)
            vm.handleIntent(LoginIntent.TogglePasswordVisibility)
            assertFalse(vm.state.first().isPasswordVisible)
        }

    // =========================================================================
    // canSubmit — combinações
    // =========================================================================

    @Test
    fun `canSubmit false with empty email and valid password`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.PasswordChanged("abc123"))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit false with valid email and empty password`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.EmailChanged("pedro@gmail.com"))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit false with invalid email and valid password`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.EmailChanged("ruim"))
            vm.handleIntent(LoginIntent.PasswordChanged("abc123"))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit false with valid email and short password`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.EmailChanged("pedro@gmail.com"))
            vm.handleIntent(LoginIntent.PasswordChanged("abc"))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit true with valid email and valid password`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.EmailChanged("pedro@gmail.com"))
            vm.handleIntent(LoginIntent.PasswordChanged("abc123"))
            assertTrue(vm.state.first().canSubmit)
        }

    // =========================================================================
    // Submit — validação falha
    // =========================================================================

    @Test
    fun `submit with empty fields emits ShowError`() =
        runTest {
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.Submit)
                val effect = awaitItem()
                assertTrue(effect is LoginEffect.ShowError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submit with empty fields sets emailError in state`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.Submit)
            assertTrue(vm.state.first().emailError != null)
        }

    @Test
    fun `submit with empty fields sets passwordError in state`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.Submit)
            assertTrue(vm.state.first().passwordError != null)
        }

    @Test
    fun `submit with invalid fields does not set isLoading`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.Submit)
            assertFalse(vm.state.first().isLoading)
        }

    // =========================================================================
    // Submit — autenticação com sucesso
    // =========================================================================

    @Test
    fun `submit with valid credentials emits NavigateHome with user`() =
        runTest {
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.EmailChanged("admin@sprena.com"))
                vm.handleIntent(LoginIntent.PasswordChanged("abc123"))
                vm.handleIntent(LoginIntent.Submit)
                val effect = awaitItem()
                assertTrue(effect is LoginEffect.NavigateHome)
                assertEquals(UserRole.ADM, (effect as LoginEffect.NavigateHome).user.role)
                assertEquals("admin@sprena.com", effect.user.email)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Submit — autenticação com falha
    // =========================================================================

    @Test
    fun `submit with repo error emits ShowError`() =
        runTest {
            authRepo.nextResult = AuthResult.Error("Email ou senha incorretos")
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.EmailChanged("admin@sprena.com"))
                vm.handleIntent(LoginIntent.PasswordChanged("abc123"))
                vm.handleIntent(LoginIntent.Submit)
                val effect = awaitItem()
                assertTrue(effect is LoginEffect.ShowError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Loading state
    // =========================================================================

    @Test
    fun `loading is false after successful submit`() =
        runTest {
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.EmailChanged("admin@sprena.com"))
                vm.handleIntent(LoginIntent.PasswordChanged("abc123"))
                vm.handleIntent(LoginIntent.Submit)
                awaitItem() // NavigateHome
                assertFalse(vm.state.first().isLoading)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loading is false after failed submit`() =
        runTest {
            authRepo.nextResult = AuthResult.Error("falhou")
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.EmailChanged("admin@sprena.com"))
                vm.handleIntent(LoginIntent.PasswordChanged("abc123"))
                vm.handleIntent(LoginIntent.Submit)
                awaitItem() // ShowError
                assertFalse(vm.state.first().isLoading)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Esqueci a senha — abertura do dialog
    // =========================================================================

    @Test
    fun `open password reset dialog sets flag true`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.OpenPasswordResetDialog)
            assertTrue(vm.state.first().passwordResetDialogOpen)
        }

    @Test
    fun `open password reset dialog prefills email from login form`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.EmailChanged("pedro@gmail.com"))
            vm.handleIntent(LoginIntent.OpenPasswordResetDialog)
            assertEquals("pedro@gmail.com", vm.state.first().passwordResetEmail)
        }

    @Test
    fun `dismiss password reset dialog clears state`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.OpenPasswordResetDialog)
            vm.handleIntent(LoginIntent.UpdatePasswordResetEmail("x@x.com"))
            vm.handleIntent(LoginIntent.DismissPasswordResetDialog)
            val s = vm.state.first()
            assertFalse(s.passwordResetDialogOpen)
            assertEquals("", s.passwordResetEmail)
            assertNull(s.passwordResetEmailError)
        }

    // =========================================================================
    // Esqueci a senha — submissão
    // =========================================================================

    @Test
    fun `submit password reset with Sent closes dialog and emits ShowPasswordResetSent`() =
        runTest {
            authRepo.nextPasswordResetResult = Result.success(Unit)
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.OpenPasswordResetDialog)
                vm.handleIntent(LoginIntent.UpdatePasswordResetEmail("pedro@gmail.com"))
                vm.handleIntent(LoginIntent.SubmitPasswordReset)
                val effect = awaitItem()
                assertTrue(effect is LoginEffect.ShowPasswordResetSent)
                assertFalse(vm.state.first().passwordResetDialogOpen)
                assertEquals("pedro@gmail.com", authRepo.lastResetEmail)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `submit password reset with invalid email sets error without calling repo`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.OpenPasswordResetDialog)
            vm.handleIntent(LoginIntent.UpdatePasswordResetEmail("nao-eh-email"))
            vm.handleIntent(LoginIntent.SubmitPasswordReset)
            advanceUntilIdle()
            val s = vm.state.first()
            assertTrue(s.passwordResetEmailError != null)
            assertTrue(s.passwordResetDialogOpen)
            assertFalse(s.passwordResetSending)
            assertNull(authRepo.lastResetEmail)
        }

    @Test
    fun `submit password reset with network error emits ShowPasswordResetError`() =
        runTest {
            authRepo.nextPasswordResetResult =
                Result.failure(FakeUnknownHostException("offline"))
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.OpenPasswordResetDialog)
                vm.handleIntent(LoginIntent.UpdatePasswordResetEmail("pedro@gmail.com"))
                vm.handleIntent(LoginIntent.SubmitPasswordReset)
                val effect = awaitItem()
                assertTrue(effect is LoginEffect.ShowPasswordResetError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submit password reset with unknown error emits ShowPasswordResetError`() =
        runTest {
            authRepo.nextPasswordResetResult = Result.failure(RuntimeException("boom"))
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.OpenPasswordResetDialog)
                vm.handleIntent(LoginIntent.UpdatePasswordResetEmail("pedro@gmail.com"))
                vm.handleIntent(LoginIntent.SubmitPasswordReset)
                val effect = awaitItem()
                assertTrue(effect is LoginEffect.ShowPasswordResetError)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
