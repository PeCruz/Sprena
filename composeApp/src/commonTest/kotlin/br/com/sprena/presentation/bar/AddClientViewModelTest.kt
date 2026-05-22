package br.com.sprena.presentation.bar

import app.cash.turbine.test
import br.com.sprena.presentation.bar.addclient.AddClientEffect
import br.com.sprena.presentation.bar.addclient.AddClientIntent
import br.com.sprena.presentation.bar.addclient.AddClientViewModel
import br.com.sprena.test.MainDispatcherEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD — AddClientViewModel (diálogo para adicionar novo cliente).
 *
 * Cenários cobertos:
 * - Editar campos (Name, Nickname, Phone, CPF, Email)
 * - Validação de cada campo obrigatório
 * - Salvar com dados válidos emite ClientCreated
 * - Salvar com dados inválidos emite ShowError
 * - Dismiss emite Dismissed
 */
class AddClientViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private fun createVm(): AddClientViewModel = AddClientViewModel()

    // =========================================================================
    // Initial State
    // =========================================================================

    @Test
    fun `initial state has empty fields and no errors`() =
        runTest {
            val vm = createVm()
            val s = vm.state.first()
            assertEquals("", s.name)
            assertEquals("", s.nickname)
            assertEquals("", s.phone)
            assertEquals("", s.cpf)
            assertEquals("", s.email)
            assertNull(s.nameError)
            assertNull(s.phoneError)
            assertNull(s.cpfError)
            assertNull(s.emailError)
        }

    // =========================================================================
    // Name Field
    // =========================================================================

    @Test
    fun `NameChanged updates name in state`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.NameChanged("Pedro"))
            assertEquals("Pedro", vm.state.first().name)
        }

    @Test
    fun `NameChanged with empty string shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.NameChanged("Pedro"))
            vm.handleIntent(AddClientIntent.NameChanged(""))
            assertNotNull(vm.state.first().nameError)
        }

    @Test
    fun `NameChanged with too long string shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.NameChanged("A".repeat(101)))
            assertNotNull(vm.state.first().nameError)
        }

    // =========================================================================
    // Nickname Field (optional)
    // =========================================================================

    @Test
    fun `NicknameChanged updates nickname in state`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.NicknameChanged("Pedrinho"))
            assertEquals("Pedrinho", vm.state.first().nickname)
        }

    // =========================================================================
    // Phone Field
    // =========================================================================

    @Test
    fun `PhoneChanged updates phone in state`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.PhoneChanged("11999998888"))
            assertEquals("11999998888", vm.state.first().phone)
        }

    @Test
    fun `PhoneChanged with empty string shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.PhoneChanged("123"))
            vm.handleIntent(AddClientIntent.PhoneChanged(""))
            assertNotNull(vm.state.first().phoneError)
        }

    @Test
    fun `PhoneChanged with too few digits shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.PhoneChanged("1234"))
            assertNotNull(vm.state.first().phoneError)
        }

    // =========================================================================
    // CPF Field
    // =========================================================================

    @Test
    fun `CpfChanged updates cpf in state`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.CpfChanged("12345678901"))
            assertEquals("12345678901", vm.state.first().cpf)
        }

    @Test
    fun `CpfChanged with wrong digit count shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.CpfChanged("123"))
            assertNotNull(vm.state.first().cpfError)
        }

    @Test
    fun `CpfChanged with empty string shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.CpfChanged("12345678901"))
            vm.handleIntent(AddClientIntent.CpfChanged(""))
            assertNotNull(vm.state.first().cpfError)
        }

    // =========================================================================
    // Email Field (optional)
    // =========================================================================

    @Test
    fun `EmailChanged updates email in state`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.EmailChanged("test@email.com"))
            assertEquals("test@email.com", vm.state.first().email)
        }

    @Test
    fun `EmailChanged with invalid format shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.EmailChanged("invalidemail"))
            assertNotNull(vm.state.first().emailError)
        }

    @Test
    fun `EmailChanged with empty string has no error (optional)`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.EmailChanged(""))
            assertNull(vm.state.first().emailError)
        }

    // =========================================================================
    // Save — dados válidos
    // =========================================================================

    @Test
    fun `Save with valid data emits ClientCreated`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.NameChanged("Pedro Cruz"))
            vm.handleIntent(AddClientIntent.PhoneChanged("11999998888"))
            vm.handleIntent(AddClientIntent.CpfChanged("12345678901"))

            vm.effects.test {
                vm.handleIntent(AddClientIntent.Save)
                val effect = awaitItem()
                assertTrue(effect is AddClientEffect.ClientCreated)
                val client = (effect as AddClientEffect.ClientCreated).client
                assertEquals("Pedro Cruz", client.name)
                assertEquals("11999998888", client.phone)
                assertEquals("12345678901", client.cpf)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Save with valid data includes optional fields`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.NameChanged("Pedro Cruz"))
            vm.handleIntent(AddClientIntent.NicknameChanged("Pedrinho"))
            vm.handleIntent(AddClientIntent.PhoneChanged("11999998888"))
            vm.handleIntent(AddClientIntent.CpfChanged("12345678901"))
            vm.handleIntent(AddClientIntent.EmailChanged("pedro@email.com"))

            vm.effects.test {
                vm.handleIntent(AddClientIntent.Save)
                val effect = awaitItem()
                assertTrue(effect is AddClientEffect.ClientCreated)
                val client = (effect as AddClientEffect.ClientCreated).client
                assertEquals("Pedrinho", client.nickname)
                assertEquals("pedro@email.com", client.email)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Save — dados inválidos
    // =========================================================================

    @Test
    fun `Save with empty required fields emits ShowError`() =
        runTest {
            val vm = createVm()
            // Leave all fields empty
            vm.effects.test {
                vm.handleIntent(AddClientIntent.Save)
                val effect = awaitItem()
                assertTrue(effect is AddClientEffect.ShowError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Save with invalid email emits ShowError`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(AddClientIntent.NameChanged("Pedro"))
            vm.handleIntent(AddClientIntent.PhoneChanged("11999998888"))
            vm.handleIntent(AddClientIntent.CpfChanged("12345678901"))
            vm.handleIntent(AddClientIntent.EmailChanged("bademail"))

            vm.effects.test {
                vm.handleIntent(AddClientIntent.Save)
                val effect = awaitItem()
                assertTrue(effect is AddClientEffect.ShowError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Dismiss
    // =========================================================================

    @Test
    fun `Dismiss emits Dismissed effect`() =
        runTest {
            val vm = createVm()
            vm.effects.test {
                vm.handleIntent(AddClientIntent.Dismiss)
                assertEquals(AddClientEffect.Dismissed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
