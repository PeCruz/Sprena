package br.com.sprena.presentation.login

import app.cash.turbine.test
import br.com.sprena.shared.auth.data.repository.MockAuthRepository
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.domain.usecase.LoginUseCase
import br.com.sprena.shared.auth.domain.validation.LoginValidator
import br.com.sprena.shared.core.logger.NoOpLogger
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
 * TDD — LoginViewModel (tela de autenticação com roles).
 *
 * Cenários cobertos:
 *  - Estado inicial (defaults)
 *  - Digitação de username: atualiza state, trunca em 8 chars
 *  - Digitação de password: filtra só dígitos, trunca em 6
 *  - Erros de validação inline (só exibidos quando campo não-vazio e inválido)
 *  - canSubmit: todas as combinações válido/inválido
 *  - Toggle de visibilidade de senha
 *  - Submit com campos inválidos: erros no state + efeito ShowError
 *  - Submit com credenciais corretas: loading, NavigateHome com UserModel completo
 *  - Submit com senha errada / usuário desconhecido: ShowError
 *  - Login case-insensitive
 *  - Loading state reseta após submit (sucesso e erro)
 */
class LoginViewModelTest {
    private val env = MainDispatcherEnv()
    private lateinit var loginUseCase: LoginUseCase

    @BeforeTest
    fun setUp() {
        env.install()
        loginUseCase = LoginUseCase(MockAuthRepository(), NoOpLogger())
    }

    @AfterTest
    fun tearDown() = env.uninstall()

    private fun createViewModel() = LoginViewModel(loginUseCase)

    // =========================================================================
    // Estado Inicial
    // =========================================================================

    @Test
    fun `initial state has empty username`() =
        runTest {
            val vm = createViewModel()
            assertEquals("", vm.state.first().username)
        }

    @Test
    fun `initial state has empty password`() =
        runTest {
            val vm = createViewModel()
            assertEquals("", vm.state.first().password)
        }

    @Test
    fun `initial state has no username error`() =
        runTest {
            val vm = createViewModel()
            assertNull(vm.state.first().usernameError)
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

    // =========================================================================
    // Username — digitação e truncamento
    // =========================================================================

    @Test
    fun `username changed updates state`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.UsernameChanged("admin"))
            assertEquals("admin", vm.state.first().username)
        }

    @Test
    fun `username truncated at max length`() =
        runTest {
            val vm = createViewModel()
            val tooLong = "a".repeat(LoginValidator.USERNAME_MAX_LENGTH + 1)
            vm.handleIntent(LoginIntent.UsernameChanged(tooLong))
            assertEquals(
                LoginValidator.USERNAME_MAX_LENGTH,
                vm.state
                    .first()
                    .username.length,
            )
        }

    @Test
    fun `username at exactly max length is preserved`() =
        runTest {
            val vm = createViewModel()
            val atMax = "b".repeat(LoginValidator.USERNAME_MAX_LENGTH)
            vm.handleIntent(LoginIntent.UsernameChanged(atMax))
            assertEquals(atMax, vm.state.first().username)
        }

    // =========================================================================
    // Username — erros de validação inline
    // =========================================================================

    @Test
    fun `username empty does not show error`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.UsernameChanged(""))
            assertNull(vm.state.first().usernameError)
        }

    @Test
    fun `username below min length shows error`() =
        runTest {
            val vm = createViewModel()
            val short = "a".repeat(LoginValidator.USERNAME_MIN_LENGTH - 1)
            vm.handleIntent(LoginIntent.UsernameChanged(short))
            assertTrue(vm.state.first().usernameError != null)
        }

    @Test
    fun `username at min length clears error`() =
        runTest {
            val vm = createViewModel()
            val atMin = "a".repeat(LoginValidator.USERNAME_MIN_LENGTH)
            vm.handleIntent(LoginIntent.UsernameChanged(atMin))
            assertNull(vm.state.first().usernameError)
        }

    @Test
    fun `username valid clears error`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.UsernameChanged("ab"))
            assertTrue(vm.state.first().usernameError != null)
            vm.handleIntent(LoginIntent.UsernameChanged("admin"))
            assertNull(vm.state.first().usernameError)
        }

    // =========================================================================
    // Password — digitação, filtragem e truncamento
    // =========================================================================

    @Test
    fun `password changed updates state with digits only`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.PasswordChanged("123456"))
            assertEquals("123456", vm.state.first().password)
        }

    @Test
    fun `password filters non-digit characters`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.PasswordChanged("1a2b3c4d5e6f"))
            assertEquals("123456", vm.state.first().password)
        }

    @Test
    fun `password truncated at 6 digits`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.PasswordChanged("1234567890"))
            assertEquals("123456", vm.state.first().password)
        }

    @Test
    fun `password all letters results in empty`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.PasswordChanged("abcdef"))
            assertEquals("", vm.state.first().password)
        }

    // =========================================================================
    // Password — erros de validação inline
    // =========================================================================

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
            vm.handleIntent(LoginIntent.PasswordChanged("12345"))
            assertTrue(vm.state.first().passwordError != null)
        }

    @Test
    fun `password at exact length clears error`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.PasswordChanged("12345"))
            assertTrue(vm.state.first().passwordError != null)
            vm.handleIntent(LoginIntent.PasswordChanged("123456"))
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
    fun `canSubmit false with empty username and valid password`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.PasswordChanged("123456"))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit false with valid username and empty password`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.UsernameChanged("admin"))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit false with short username and valid password`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.UsernameChanged("ab"))
            vm.handleIntent(LoginIntent.PasswordChanged("123456"))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit false with valid username and short password`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.UsernameChanged("admin"))
            vm.handleIntent(LoginIntent.PasswordChanged("123"))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit true with valid username and valid password`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.UsernameChanged("admin"))
            vm.handleIntent(LoginIntent.PasswordChanged("123456"))
            assertTrue(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit becomes false when username cleared after being valid`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.UsernameChanged("admin"))
            vm.handleIntent(LoginIntent.PasswordChanged("123456"))
            assertTrue(vm.state.first().canSubmit)
            vm.handleIntent(LoginIntent.UsernameChanged(""))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit becomes false when password cleared after being valid`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.UsernameChanged("admin"))
            vm.handleIntent(LoginIntent.PasswordChanged("123456"))
            assertTrue(vm.state.first().canSubmit)
            vm.handleIntent(LoginIntent.PasswordChanged(""))
            assertFalse(vm.state.first().canSubmit)
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
    fun `submit with empty fields sets usernameError in state`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.Submit)
            assertTrue(vm.state.first().usernameError != null)
        }

    @Test
    fun `submit with empty fields sets passwordError in state`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.Submit)
            assertTrue(vm.state.first().passwordError != null)
        }

    @Test
    fun `submit with valid username but empty password sets passwordError`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.UsernameChanged("admin"))
            vm.effects.test {
                vm.handleIntent(LoginIntent.Submit)
                awaitItem() // ShowError
                assertNull(vm.state.first().usernameError)
                assertTrue(vm.state.first().passwordError != null)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submit with empty username but valid password sets usernameError`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.PasswordChanged("123456"))
            vm.effects.test {
                vm.handleIntent(LoginIntent.Submit)
                awaitItem() // ShowError
                assertTrue(vm.state.first().usernameError != null)
                assertNull(vm.state.first().passwordError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submit with invalid fields does not set isLoading`() =
        runTest {
            val vm = createViewModel()
            vm.handleIntent(LoginIntent.Submit)
            assertFalse(vm.state.first().isLoading)
        }

    // =========================================================================
    // Submit — autenticação com sucesso (todos os roles)
    // =========================================================================

    @Test
    fun `submit admin emits NavigateHome with ADM role`() =
        runTest {
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.UsernameChanged("admin"))
                vm.handleIntent(LoginIntent.PasswordChanged("123456"))
                vm.handleIntent(LoginIntent.Submit)
                val effect = awaitItem()
                assertTrue(effect is LoginEffect.NavigateHome)
                assertEquals(UserRole.ADM, (effect as LoginEffect.NavigateHome).user.role)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submit admin returns correct user data`() =
        runTest {
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.UsernameChanged("admin"))
                vm.handleIntent(LoginIntent.PasswordChanged("123456"))
                vm.handleIntent(LoginIntent.Submit)
                val effect = awaitItem() as LoginEffect.NavigateHome
                assertEquals("1", effect.user.id)
                assertEquals("admin", effect.user.username)
                assertEquals("Pedro Admin", effect.user.name)
                assertEquals(UserRole.ADM, effect.user.role)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submit mod emits NavigateHome with MOD role`() =
        runTest {
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.UsernameChanged("mod"))
                vm.handleIntent(LoginIntent.PasswordChanged("654321"))
                vm.handleIntent(LoginIntent.Submit)
                val effect = awaitItem() as LoginEffect.NavigateHome
                assertEquals(UserRole.MOD, effect.user.role)
                assertEquals("Maria Moderadora", effect.user.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submit func emits NavigateHome with CLIENT role`() =
        runTest {
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.UsernameChanged("func"))
                vm.handleIntent(LoginIntent.PasswordChanged("111111"))
                vm.handleIntent(LoginIntent.Submit)
                val effect = awaitItem() as LoginEffect.NavigateHome
                assertEquals(UserRole.CLIENT, effect.user.role)
                assertEquals("João Funcionário", effect.user.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Submit — autenticação com falha
    // =========================================================================

    @Test
    fun `submit with wrong password emits ShowError`() =
        runTest {
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.UsernameChanged("admin"))
                vm.handleIntent(LoginIntent.PasswordChanged("999999"))
                vm.handleIntent(LoginIntent.Submit)
                val effect = awaitItem()
                assertTrue(effect is LoginEffect.ShowError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submit with unknown user emits ShowError`() =
        runTest {
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.UsernameChanged("nobody"))
                vm.handleIntent(LoginIntent.PasswordChanged("123456"))
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
                vm.handleIntent(LoginIntent.UsernameChanged("admin"))
                vm.handleIntent(LoginIntent.PasswordChanged("123456"))
                vm.handleIntent(LoginIntent.Submit)
                awaitItem() // NavigateHome
                assertFalse(vm.state.first().isLoading)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loading is false after failed submit`() =
        runTest {
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.UsernameChanged("admin"))
                vm.handleIntent(LoginIntent.PasswordChanged("999999"))
                vm.handleIntent(LoginIntent.Submit)
                awaitItem() // ShowError
                assertFalse(vm.state.first().isLoading)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Case-insensitive login
    // =========================================================================

    @Test
    fun `submit with uppercase username works`() =
        runTest {
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.UsernameChanged("ADMIN"))
                vm.handleIntent(LoginIntent.PasswordChanged("123456"))
                vm.handleIntent(LoginIntent.Submit)
                val effect = awaitItem()
                assertTrue(effect is LoginEffect.NavigateHome)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submit with mixed case username works`() =
        runTest {
            val vm = createViewModel()
            vm.effects.test {
                vm.handleIntent(LoginIntent.UsernameChanged("Admin"))
                vm.handleIntent(LoginIntent.PasswordChanged("123456"))
                vm.handleIntent(LoginIntent.Submit)
                val effect = awaitItem()
                assertTrue(effect is LoginEffect.NavigateHome)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
