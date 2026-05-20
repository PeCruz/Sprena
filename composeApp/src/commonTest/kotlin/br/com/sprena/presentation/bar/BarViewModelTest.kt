package br.com.sprena.presentation.bar

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
 * TDD — BarViewModel (tela principal do Bar com tabela de clientes).
 *
 * Cenários cobertos:
 * - Estado inicial (lista vazia, sem busca)
 * - Busca filtra clientes por nome/apelido
 * - Adicionar cliente à lista
 * - Atualizar cliente existente
 * - Toggle pago/não pago
 * - Deletar cliente
 * - Clicar no cliente abre detalhe
 * - Dados da tabela (Nome, Itens, Conta, Pago)
 */
class BarViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private fun createVm(): BarViewModel = BarViewModel()

    private val sampleClient =
        BarClient(
            id = "client_1",
            name = "João Silva",
            nickname = "Joãozinho",
            phone = "11999998888",
            cpf = "12345678901",
            email = "joao@email.com",
            items =
                listOf(
                    BarItem(id = "item_1", name = "Cerveja", priceCents = 1200),
                    BarItem(id = "item_2", name = "Petisco", priceCents = 2500),
                ),
            isPaid = false,
        )

    private val sampleClient2 =
        BarClient(
            id = "client_2",
            name = "Maria Oliveira",
            nickname = null,
            phone = "11988887777",
            cpf = "98765432101",
            email = null,
            items = emptyList(),
            isPaid = true,
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
            assertFalse(s.isAddClientDialogVisible)
            assertNull(s.selectedClient)
        }

    // =========================================================================
    // Add Client
    // =========================================================================

    @Test
    fun `ClientAdded adds client to list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            val s = vm.state.first()
            assertEquals(1, s.clients.size)
            assertEquals("João Silva", s.clients.first().name)
        }

    @Test
    fun `ClientAdded updates filtered list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            val s = vm.state.first()
            assertEquals(1, s.filteredClients.size)
        }

    // =========================================================================
    // Search — filtra por nome e apelido
    // =========================================================================

    @Test
    fun `SearchQueryChanged filters clients by name`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            vm.handleIntent(BarIntent.ClientAdded(sampleClient2))
            vm.handleIntent(BarIntent.SearchQueryChanged("João"))
            val s = vm.state.first()
            assertEquals("João", s.searchQuery)
            assertEquals(1, s.filteredClients.size)
            assertEquals("João Silva", s.filteredClients.first().name)
        }

    @Test
    fun `SearchQueryChanged filters clients by nickname`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            vm.handleIntent(BarIntent.ClientAdded(sampleClient2))
            vm.handleIntent(BarIntent.SearchQueryChanged("Joãozinho"))
            val s = vm.state.first()
            assertEquals(1, s.filteredClients.size)
        }

    @Test
    fun `SearchQueryChanged is case insensitive`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            vm.handleIntent(BarIntent.SearchQueryChanged("joão"))
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
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            vm.handleIntent(BarIntent.ClientAdded(sampleClient2))
            vm.handleIntent(BarIntent.SearchQueryChanged("João"))
            vm.handleIntent(BarIntent.SearchQueryChanged(""))
            assertEquals(
                2,
                vm.state
                    .first()
                    .filteredClients.size,
            )
        }

    // =========================================================================
    // Table data — Nome, Itens, Conta, Pago
    // =========================================================================

    @Test
    fun `client has correct item count`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            val client =
                vm.state
                    .first()
                    .clients
                    .first()
            assertEquals(2, client.items.size)
        }

    @Test
    fun `client total is sum of item prices`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            val client =
                vm.state
                    .first()
                    .clients
                    .first()
            val total = client.items.sumOf { it.priceCents }
            assertEquals(3700L, total) // 1200 + 2500
        }

    @Test
    fun `client isPaid reflects payment status`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            assertFalse(
                vm.state
                    .first()
                    .clients
                    .first()
                    .isPaid,
            )
        }

    // =========================================================================
    // Toggle Paid
    // =========================================================================

    @Test
    fun `TogglePaid toggles client paid status`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            assertFalse(
                vm.state
                    .first()
                    .clients
                    .first()
                    .isPaid,
            )

            vm.handleIntent(BarIntent.TogglePaid("client_1"))
            assertTrue(
                vm.state
                    .first()
                    .clients
                    .first()
                    .isPaid,
            )
        }

    @Test
    fun `TogglePaid can untoggle paid status`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient2)) // isPaid = true
            assertTrue(
                vm.state
                    .first()
                    .clients
                    .first()
                    .isPaid,
            )

            vm.handleIntent(BarIntent.TogglePaid("client_2"))
            assertFalse(
                vm.state
                    .first()
                    .clients
                    .first()
                    .isPaid,
            )
        }

    @Test
    fun `TogglePaid updates filtered list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            vm.handleIntent(BarIntent.TogglePaid("client_1"))
            assertTrue(
                vm.state
                    .first()
                    .filteredClients
                    .first()
                    .isPaid,
            )
        }

    // =========================================================================
    // Client Update
    // =========================================================================

    @Test
    fun `ClientUpdated replaces client in list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            val updated = sampleClient.copy(name = "João Atualizado")
            vm.handleIntent(BarIntent.ClientUpdated(updated))
            assertEquals(
                "João Atualizado",
                vm.state
                    .first()
                    .clients
                    .first()
                    .name,
            )
        }

    // =========================================================================
    // Client Delete
    // =========================================================================

    @Test
    fun `ClientDeleted removes client from list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            vm.handleIntent(BarIntent.ClientAdded(sampleClient2))
            vm.handleIntent(BarIntent.ClientDeleted("client_1"))
            val s = vm.state.first()
            assertEquals(1, s.clients.size)
            assertEquals("client_2", s.clients.first().id)
        }

    // =========================================================================
    // Add Client Dialog
    // =========================================================================

    @Test
    fun `AddClientClicked shows dialog`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.AddClientClicked)
            assertTrue(vm.state.first().isAddClientDialogVisible)
        }

    @Test
    fun `DismissAddClientDialog hides dialog`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.AddClientClicked)
            vm.handleIntent(BarIntent.DismissAddClientDialog)
            assertFalse(vm.state.first().isAddClientDialogVisible)
        }

    // =========================================================================
    // Client Detail
    // =========================================================================

    @Test
    fun `ClientClicked sets selectedClient`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            vm.handleIntent(BarIntent.ClientClicked(sampleClient))
            assertEquals(sampleClient, vm.state.first().selectedClient)
        }

    @Test
    fun `DismissClientDetail clears selectedClient`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientClicked(sampleClient))
            vm.handleIntent(BarIntent.DismissClientDetail)
            assertNull(vm.state.first().selectedClient)
        }

    // =========================================================================
    // Payment Filter (Pagos / Não Pagos)
    // =========================================================================

    @Test
    fun `initial payment filter is ALL`() =
        runTest {
            val vm = createVm()
            assertEquals(PaymentFilter.ALL, vm.state.first().paymentFilter)
        }

    @Test
    fun `PaymentFilterChanged to PAID shows only paid clients`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient)) // isPaid = false, has items
            vm.handleIntent(BarIntent.ClientAdded(sampleClient2)) // isPaid = true, no items
            vm.handleIntent(BarIntent.PaymentFilterChanged(PaymentFilter.PAID))
            val s = vm.state.first()
            assertEquals(PaymentFilter.PAID, s.paymentFilter)
            assertEquals(1, s.filteredClients.size)
            assertEquals("client_2", s.filteredClients.first().id)
        }

    @Test
    fun `PaymentFilterChanged to UNPAID shows only unpaid clients with balance`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient)) // isPaid = false, has items
            vm.handleIntent(BarIntent.ClientAdded(sampleClient2)) // isPaid = true, no items
            vm.handleIntent(BarIntent.PaymentFilterChanged(PaymentFilter.UNPAID))
            val s = vm.state.first()
            assertEquals(1, s.filteredClients.size)
            assertEquals("client_1", s.filteredClients.first().id)
        }

    @Test
    fun `PaymentFilterChanged back to ALL shows all clients`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient))
            vm.handleIntent(BarIntent.ClientAdded(sampleClient2))
            vm.handleIntent(BarIntent.PaymentFilterChanged(PaymentFilter.PAID))
            vm.handleIntent(BarIntent.PaymentFilterChanged(PaymentFilter.ALL))
            assertEquals(
                2,
                vm.state
                    .first()
                    .filteredClients.size,
            )
        }

    @Test
    fun `payment filter combines with search query`() =
        runTest {
            val unpaidClient2 =
                BarClient(
                    id = "client_3",
                    name = "João Unpaid",
                    phone = "11977776666",
                    cpf = "11111111111",
                    items = listOf(BarItem(id = "i1", name = "Item", priceCents = 500)),
                    isPaid = false,
                )
            val vm = createVm()
            vm.handleIntent(BarIntent.ClientAdded(sampleClient)) // João, unpaid
            vm.handleIntent(BarIntent.ClientAdded(sampleClient2)) // Maria, paid
            vm.handleIntent(BarIntent.ClientAdded(unpaidClient2)) // João Unpaid, unpaid
            vm.handleIntent(BarIntent.PaymentFilterChanged(PaymentFilter.UNPAID))
            vm.handleIntent(BarIntent.SearchQueryChanged("João"))
            val s = vm.state.first()
            // Both Joãos are unpaid with balance, both match search
            assertEquals(2, s.filteredClients.size)
        }
}
