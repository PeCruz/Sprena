package br.com.sprena.presentation.sportclient

import app.cash.turbine.test
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.sportclient.domain.validation.PaymentMethod
import br.com.sprena.shared.sportclient.domain.validation.SportModality
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
 * TDD — SportClientViewModel (tela Home — gestão de clientes de esportes).
 *
 * Campos do cliente:
 * - Nome (obrigatório)
 * - Apelido (opcional, max 50 chars)
 * - CPF (obrigatório, 11 dígitos)
 * - Telefone (obrigatório, 10-15 dígitos)
 * - Modalidade (FUTEVOLEI, BEACH_TENNIS, VOLEI)
 * - Frequência (1~4)
 * - Pagamento (WELLHUB, TOTALPASS, CASH)
 * - Valor em dinheiro (obrigatório para TODOS os métodos)
 * - Mês Pagamento (último mês pago, MM/YYYY)
 *
 * Cenários cobertos:
 * - Estado inicial
 * - Adicionar cliente com todos os campos
 * - Busca filtra por nome
 * - Editar, deletar, dialogs
 * - Integridade dos dados (campos mantidos corretamente)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SportClientViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private class FakeSessionStore(
        private val role: UserRole?,
    ) : SessionStore {
        override suspend fun save(user: SessionUser) = Unit

        override suspend fun load(): SessionUser? =
            role?.let {
                SessionUser(
                    uid = "uid_1",
                    email = "user@sprena.com",
                    role = it,
                    lastLoginEpochMillis = 1L,
                )
            }

        override suspend fun clear() = Unit
    }

    private fun createVm(role: UserRole? = UserRole.CLIENT): SportClientViewModel =
        SportClientViewModel(sessionStore = FakeSessionStore(role))

    private val sampleClient =
        SportClient(
            id = "sport_1",
            name = "Pedro Cruz",
            apelido = "Pedrinho",
            cpf = "12345678901",
            phone = "11999998888",
            modalities = listOf(SportModality.FUTEVOLEI),
            attendance = 3,
            paymentMethod = PaymentMethod.CASH,
            cashAmountCents = 5000L,
            paymentHistory = listOf("04/2026"),
        )

    private val sampleClient2 =
        SportClient(
            id = "sport_2",
            name = "Ana Silva",
            apelido = "",
            cpf = "98765432101",
            phone = "11988887777",
            modalities = listOf(SportModality.BEACH_TENNIS),
            attendance = 2,
            paymentMethod = PaymentMethod.WELLHUB,
            cashAmountCents = 1500L,
            paymentHistory = listOf("03/2026"),
        )

    // =========================================================================
    // Initial State
    // =========================================================================

    @Test
    fun `initial state has empty client list`() =
        runTest {
            val vm = createVm()
            val s = vm.state.first()
            assertTrue(s.clients.isEmpty())
            assertTrue(s.filteredClients.isEmpty())
            assertEquals("", s.searchQuery)
            assertFalse(s.isLoading)
            assertFalse(s.isAddDialogVisible)
            assertNull(s.selectedClient)
        }

    // =========================================================================
    // Add Client
    // =========================================================================

    @Test
    fun `ClientAdded adds client to list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            val s = vm.state.first()
            assertEquals(1, s.clients.size)
            assertEquals("Pedro Cruz", s.clients.first().name)
        }

    @Test
    fun `ClientAdded updates filtered list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            assertEquals(
                1,
                vm.state
                    .first()
                    .filteredClients.size,
            )
        }

    // =========================================================================
    // Client data integrity
    // =========================================================================

    @Test
    fun `client preserves cpf`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            assertEquals(
                "12345678901",
                vm.state
                    .first()
                    .clients
                    .first()
                    .cpf,
            )
        }

    @Test
    fun `client preserves phone`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            assertEquals(
                "11999998888",
                vm.state
                    .first()
                    .clients
                    .first()
                    .phone,
            )
        }

    @Test
    fun `client preserves attendance`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            assertEquals(
                3,
                vm.state
                    .first()
                    .clients
                    .first()
                    .attendance,
            )
        }

    @Test
    fun `client preserves payment method CASH`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            assertEquals(
                PaymentMethod.CASH,
                vm.state
                    .first()
                    .clients
                    .first()
                    .paymentMethod,
            )
        }

    @Test
    fun `client with CASH preserves cashAmountCents`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            assertEquals(
                5000L,
                vm.state
                    .first()
                    .clients
                    .first()
                    .cashAmountCents,
            )
        }

    @Test
    fun `client preserves payment method WELLHUB with cash amount`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient2))
            val client =
                vm.state
                    .first()
                    .clients
                    .first()
            assertEquals(PaymentMethod.WELLHUB, client.paymentMethod)
            assertEquals(1500L, client.cashAmountCents)
        }

    @Test
    fun `client with TOTALPASS preserves cash amount`() =
        runTest {
            val totalPassClient =
                sampleClient.copy(
                    id = "sport_3",
                    paymentMethod = PaymentMethod.TOTALPASS,
                    cashAmountCents = 2000L,
                )
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(totalPassClient))
            val client =
                vm.state
                    .first()
                    .clients
                    .first()
            assertEquals(PaymentMethod.TOTALPASS, client.paymentMethod)
            assertEquals(2000L, client.cashAmountCents)
        }

    @Test
    fun `client preserves modalities FUTEVOLEI`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            assertEquals(
                listOf(SportModality.FUTEVOLEI),
                vm.state
                    .first()
                    .clients
                    .first()
                    .modalities,
            )
        }

    @Test
    fun `client preserves modalities BEACH_TENNIS`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient2))
            assertEquals(
                listOf(SportModality.BEACH_TENNIS),
                vm.state
                    .first()
                    .clients
                    .first()
                    .modalities,
            )
        }

    @Test
    fun `client preserves modalities VOLEI`() =
        runTest {
            val voleiClient =
                sampleClient.copy(
                    id = "sport_3",
                    modalities = listOf(SportModality.VOLEI),
                )
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(voleiClient))
            assertEquals(
                listOf(SportModality.VOLEI),
                vm.state
                    .first()
                    .clients
                    .first()
                    .modalities,
            )
        }

    @Test
    fun `client preserves multiple modalities`() =
        runTest {
            val multiClient =
                sampleClient.copy(
                    id = "sport_4",
                    modalities = listOf(SportModality.FUTEVOLEI, SportModality.BEACH_TENNIS),
                )
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(multiClient))
            assertEquals(
                listOf(SportModality.FUTEVOLEI, SportModality.BEACH_TENNIS),
                vm.state
                    .first()
                    .clients
                    .first()
                    .modalities,
            )
        }

    @Test
    fun `client preserves apelido`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            assertEquals(
                "Pedrinho",
                vm.state
                    .first()
                    .clients
                    .first()
                    .apelido,
            )
        }

    @Test
    fun `client preserves empty apelido`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient2))
            assertEquals(
                "",
                vm.state
                    .first()
                    .clients
                    .first()
                    .apelido,
            )
        }

    @Test
    fun `client preserves lastPaymentMonth`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            assertEquals(
                "04/2026",
                vm.state
                    .first()
                    .clients
                    .first()
                    .lastPaymentMonth,
            )
        }

    @Test
    fun `ClientUpdated can change modalities`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            val updated = sampleClient.copy(modalities = listOf(SportModality.VOLEI, SportModality.BEACH_TENNIS))
            vm.handleIntent(SportClientIntent.ClientUpdated(updated))
            assertEquals(
                listOf(SportModality.VOLEI, SportModality.BEACH_TENNIS),
                vm.state
                    .first()
                    .clients
                    .first()
                    .modalities,
            )
        }

    @Test
    fun `ClientUpdated can change apelido`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            val updated = sampleClient.copy(apelido = "Pedrão")
            vm.handleIntent(SportClientIntent.ClientUpdated(updated))
            assertEquals(
                "Pedrão",
                vm.state
                    .first()
                    .clients
                    .first()
                    .apelido,
            )
        }

    @Test
    fun `ClientUpdated can change paymentHistory`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            val updated = sampleClient.copy(paymentHistory = listOf("04/2026", "05/2026"))
            vm.handleIntent(SportClientIntent.ClientUpdated(updated))
            val client =
                vm.state
                    .first()
                    .clients
                    .first()
            assertEquals(listOf("04/2026", "05/2026"), client.paymentHistory)
            assertEquals("05/2026", client.lastPaymentMonth)
        }

    // =========================================================================
    // Search
    // =========================================================================

    @Test
    fun `SearchQueryChanged filters clients by name`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient2))
            vm.handleIntent(SportClientIntent.SearchQueryChanged("Pedro"))
            val s = vm.state.first()
            assertEquals("Pedro", s.searchQuery)
            assertEquals(1, s.filteredClients.size)
            assertEquals("Pedro Cruz", s.filteredClients.first().name)
        }

    @Test
    fun `SearchQueryChanged filters clients by apelido`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient2))
            vm.handleIntent(SportClientIntent.SearchQueryChanged("Pedrinho"))
            val s = vm.state.first()
            assertEquals(1, s.filteredClients.size)
            assertEquals("Pedro Cruz", s.filteredClients.first().name)
        }

    @Test
    fun `SearchQueryChanged is case insensitive`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            vm.handleIntent(SportClientIntent.SearchQueryChanged("pedro"))
            assertEquals(
                1,
                vm.state
                    .first()
                    .filteredClients.size,
            )
        }

    @Test
    fun `empty search shows all clients`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient2))
            vm.handleIntent(SportClientIntent.SearchQueryChanged("Pedro"))
            vm.handleIntent(SportClientIntent.SearchQueryChanged(""))
            assertEquals(
                2,
                vm.state
                    .first()
                    .filteredClients.size,
            )
        }

    @Test
    fun `search with no match returns empty`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            vm.handleIntent(SportClientIntent.SearchQueryChanged("Carlos"))
            assertTrue(
                vm.state
                    .first()
                    .filteredClients
                    .isEmpty(),
            )
        }

    // =========================================================================
    // Add Dialog
    // =========================================================================

    @Test
    fun `AddClientClicked shows dialog`() =
        runTest {
            val vm = createVm(role = UserRole.ADM)
            advanceUntilIdle()
            vm.handleIntent(SportClientIntent.AddClientClicked)
            assertTrue(vm.state.first().isAddDialogVisible)
        }

    @Test
    fun `DismissAddDialog hides dialog`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.AddClientClicked)
            vm.handleIntent(SportClientIntent.DismissAddDialog)
            assertFalse(vm.state.first().isAddDialogVisible)
        }

    // =========================================================================
    // Client Detail / Edit
    // =========================================================================

    @Test
    fun `ClientClicked sets selectedClient`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            vm.handleIntent(SportClientIntent.ClientClicked(sampleClient))
            assertEquals(sampleClient, vm.state.first().selectedClient)
        }

    @Test
    fun `DismissClientDetail clears selectedClient`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientClicked(sampleClient))
            vm.handleIntent(SportClientIntent.DismissClientDetail)
            assertNull(vm.state.first().selectedClient)
        }

    // =========================================================================
    // Client Update
    // =========================================================================

    @Test
    fun `ClientUpdated replaces client in list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            val updated = sampleClient.copy(name = "Pedro Atualizado", attendance = 4)
            vm.handleIntent(SportClientIntent.ClientUpdated(updated))
            val client =
                vm.state
                    .first()
                    .clients
                    .first()
            assertEquals("Pedro Atualizado", client.name)
            assertEquals(4, client.attendance)
        }

    @Test
    fun `ClientUpdated clears selectedClient`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            vm.handleIntent(SportClientIntent.ClientClicked(sampleClient))
            vm.handleIntent(SportClientIntent.ClientUpdated(sampleClient.copy(name = "Novo")))
            assertNull(vm.state.first().selectedClient)
        }

    @Test
    fun `ClientUpdated can change payment method`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            val updated =
                sampleClient.copy(
                    paymentMethod = PaymentMethod.WELLHUB,
                    cashAmountCents = 3000L,
                )
            vm.handleIntent(SportClientIntent.ClientUpdated(updated))
            val client =
                vm.state
                    .first()
                    .clients
                    .first()
            assertEquals(PaymentMethod.WELLHUB, client.paymentMethod)
            assertEquals(3000L, client.cashAmountCents)
        }

    // =========================================================================
    // Client Delete
    // =========================================================================

    @Test
    fun `ClientDeleted removes client from list`() =
        runTest {
            val vm = createVm(role = UserRole.ADM)
            advanceUntilIdle()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient2))
            vm.handleIntent(SportClientIntent.ClientDeleted("sport_1"))
            val s = vm.state.first()
            assertEquals(1, s.clients.size)
            assertEquals("sport_2", s.clients.first().id)
        }

    @Test
    fun `ClientDeleted clears selectedClient if same`() =
        runTest {
            val vm = createVm(role = UserRole.ADM)
            advanceUntilIdle()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            vm.handleIntent(SportClientIntent.ClientClicked(sampleClient))
            vm.handleIntent(SportClientIntent.ClientDeleted("sport_1"))
            assertNull(vm.state.first().selectedClient)
        }

    @Test
    fun `ClientDeleted updates filtered list`() =
        runTest {
            val vm = createVm(role = UserRole.ADM)
            advanceUntilIdle()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            vm.handleIntent(SportClientIntent.ClientDeleted("sport_1"))
            assertTrue(
                vm.state
                    .first()
                    .filteredClients
                    .isEmpty(),
            )
        }

    // =========================================================================
    // CPF — mascarado por padrão, revelação restrita a ADM/MOD (F1.5)
    // =========================================================================

    private val cpfClient = sampleClient.copy(cpf = "12345678900")

    private fun openDetail(vm: SportClientViewModel) {
        vm.handleIntent(SportClientIntent.ClientAdded(cpfClient))
        vm.handleIntent(SportClientIntent.ClientClicked(cpfClient))
    }

    @Test
    fun `CPF aparece mascarado por padrao no detalhe`() =
        runTest {
            val vm = createVm(role = UserRole.ADM)
            advanceUntilIdle()
            openDetail(vm)

            val state = vm.state.first()
            assertTrue(state.canRevealCpf)
            assertFalse(state.isCpfRevealed)
            assertEquals("***.***.789-00", state.displayCpf)
        }

    @Test
    fun `CLIENT nao pode revelar o CPF no detalhe`() =
        runTest {
            val vm = createVm(role = UserRole.CLIENT)
            advanceUntilIdle()
            openDetail(vm)

            vm.handleIntent(SportClientIntent.ToggleCpfReveal)
            advanceUntilIdle()

            val state = vm.state.first()
            assertFalse(state.canRevealCpf)
            assertFalse(state.isCpfRevealed)
            assertEquals("***.***.789-00", state.displayCpf)
        }

    @Test
    fun `ADM revela o CPF completo no detalhe`() =
        runTest {
            val vm = createVm(role = UserRole.ADM)
            advanceUntilIdle()
            openDetail(vm)

            vm.handleIntent(SportClientIntent.ToggleCpfReveal)
            advanceUntilIdle()

            assertEquals("123.456.789-00", vm.state.first().displayCpf)
        }

    @Test
    fun `MOD revela o CPF completo no detalhe`() =
        runTest {
            val vm = createVm(role = UserRole.MOD)
            advanceUntilIdle()
            openDetail(vm)

            vm.handleIntent(SportClientIntent.ToggleCpfReveal)
            advanceUntilIdle()

            assertEquals("123.456.789-00", vm.state.first().displayCpf)
        }

    @Test
    fun `sem sessao o CPF permanece mascarado no detalhe`() =
        runTest {
            val vm = createVm(role = null)
            advanceUntilIdle()
            openDetail(vm)

            vm.handleIntent(SportClientIntent.ToggleCpfReveal)
            advanceUntilIdle()

            val state = vm.state.first()
            assertFalse(state.canRevealCpf)
            assertEquals("***.***.789-00", state.displayCpf)
        }

    @Test
    fun `reabrir o detalhe volta a mascarar o CPF revelado`() =
        runTest {
            val vm = createVm(role = UserRole.ADM)
            advanceUntilIdle()
            openDetail(vm)
            vm.handleIntent(SportClientIntent.ToggleCpfReveal)
            advanceUntilIdle()
            assertEquals("123.456.789-00", vm.state.first().displayCpf)

            vm.handleIntent(SportClientIntent.DismissClientDetail)
            vm.handleIntent(SportClientIntent.ClientClicked(cpfClient))
            advanceUntilIdle()

            assertEquals("***.***.789-00", vm.state.first().displayCpf)
        }

    // =========================================================================
    // canManageClients — autoriza escrita (add/edit/delete), independente do masking (F1.5)
    // =========================================================================

    @Test
    fun `ADM tem canManageClients true`() =
        runTest {
            val vm = createVm(role = UserRole.ADM)
            advanceUntilIdle()
            assertTrue(vm.state.first().canManageClients)
        }

    @Test
    fun `MOD tem canManageClients true`() =
        runTest {
            val vm = createVm(role = UserRole.MOD)
            advanceUntilIdle()
            assertTrue(vm.state.first().canManageClients)
        }

    @Test
    fun `CLIENT tem canManageClients false`() =
        runTest {
            val vm = createVm(role = UserRole.CLIENT)
            advanceUntilIdle()
            assertFalse(vm.state.first().canManageClients)
        }

    @Test
    fun `sem sessao canManageClients e false`() =
        runTest {
            val vm = createVm(role = null)
            advanceUntilIdle()
            assertFalse(vm.state.first().canManageClients)
        }

    @Test
    fun `AddClientClicked sem canManageClients nao abre o dialogo`() =
        runTest {
            val vm = createVm(role = UserRole.CLIENT)
            advanceUntilIdle()
            vm.handleIntent(SportClientIntent.AddClientClicked)
            assertFalse(vm.state.first().isAddDialogVisible)
        }

    @Test
    fun `EditClientClicked sem canManageClients nao emite efeito de navegacao`() =
        runTest {
            val vm = createVm(role = UserRole.CLIENT)
            advanceUntilIdle()
            openDetail(vm)

            vm.effects.test {
                vm.handleIntent(SportClientIntent.EditClientClicked(cpfClient))
                advanceUntilIdle()
                expectNoEvents()
            }

            // O intent também não deve alterar o state (detalhe continua aberto).
            assertEquals(cpfClient, vm.state.first().selectedClient)
        }

    @Test
    fun `ClientDeleted sem canManageClients nao remove o cliente`() =
        runTest {
            val vm = createVm(role = UserRole.CLIENT)
            advanceUntilIdle()
            vm.handleIntent(SportClientIntent.ClientAdded(sampleClient))
            vm.handleIntent(SportClientIntent.ClientDeleted(sampleClient.id))
            advanceUntilIdle()

            assertEquals(
                1,
                vm.state
                    .first()
                    .clients.size,
            )
        }
}
