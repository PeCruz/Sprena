package br.com.sprena.presentation.menu

import app.cash.turbine.test
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
 * TDD — MenuViewModel (tela de configuração do Cardápio).
 *
 * Cenários cobertos:
 * - Estado inicial (lista vazia, sem busca, dialogs fechados)
 * - Busca filtra itens por nome
 * - Adicionar item ao cardápio
 * - Editar item existente (nome, preço, descrição)
 * - Deletar item com confirmação
 * - Abrir/fechar dialog de adicionar
 * - Selecionar item para edição
 * - Validação de campos (nome obrigatório, preço obrigatório)
 */
class MenuViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private fun createVm(): MenuViewModel = MenuViewModel()

    private val sampleItem =
        MenuItem(
            id = "menu_1",
            name = "Cerveja Artesanal",
            priceCents = 1500L,
            description = "IPA 600ml",
        )

    private val sampleItem2 =
        MenuItem(
            id = "menu_2",
            name = "Petisco Calabresa",
            priceCents = 2500L,
            description = null,
        )

    // =========================================================================
    // Initial State
    // =========================================================================

    @Test
    fun `initial state has empty menu list`() =
        runTest {
            val vm = createVm()
            val s = vm.state.first()
            assertTrue(s.items.isEmpty())
            assertTrue(s.filteredItems.isEmpty())
            assertEquals("", s.searchQuery)
            assertFalse(s.isLoading)
            assertFalse(s.isAddDialogVisible)
            assertNull(s.selectedItem)
        }

    // =========================================================================
    // Add Item
    // =========================================================================

    @Test
    fun `ItemAdded adds item to list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            val s = vm.state.first()
            assertEquals(1, s.items.size)
            assertEquals("Cerveja Artesanal", s.items.first().name)
        }

    @Test
    fun `ItemAdded updates filtered list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            val s = vm.state.first()
            assertEquals(1, s.filteredItems.size)
        }

    // =========================================================================
    // Search — filtra por nome
    // =========================================================================

    @Test
    fun `SearchQueryChanged filters items by name`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem2))
            vm.handleIntent(MenuIntent.SearchQueryChanged("Cerveja"))
            val s = vm.state.first()
            assertEquals("Cerveja", s.searchQuery)
            assertEquals(1, s.filteredItems.size)
            assertEquals("Cerveja Artesanal", s.filteredItems.first().name)
        }

    @Test
    fun `SearchQueryChanged is case insensitive`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            vm.handleIntent(MenuIntent.SearchQueryChanged("cerveja"))
            assertEquals(
                1,
                vm.state
                    .first()
                    .filteredItems.size,
            )
        }

    @Test
    fun `empty search shows all items`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem2))
            vm.handleIntent(MenuIntent.SearchQueryChanged("Cerveja"))
            vm.handleIntent(MenuIntent.SearchQueryChanged(""))
            assertEquals(
                2,
                vm.state
                    .first()
                    .filteredItems.size,
            )
        }

    @Test
    fun `search with no match returns empty list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            vm.handleIntent(MenuIntent.SearchQueryChanged("Whisky"))
            assertTrue(
                vm.state
                    .first()
                    .filteredItems
                    .isEmpty(),
            )
        }

    // =========================================================================
    // Item Update
    // =========================================================================

    @Test
    fun `ItemUpdated replaces item in list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            val updated = sampleItem.copy(name = "Cerveja Premium", priceCents = 2000L)
            vm.handleIntent(MenuIntent.ItemUpdated(updated))
            val s = vm.state.first()
            assertEquals("Cerveja Premium", s.items.first().name)
            assertEquals(2000L, s.items.first().priceCents)
        }

    @Test
    fun `ItemUpdated updates filtered list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            val updated = sampleItem.copy(name = "Cerveja Premium")
            vm.handleIntent(MenuIntent.ItemUpdated(updated))
            assertEquals(
                "Cerveja Premium",
                vm.state
                    .first()
                    .filteredItems
                    .first()
                    .name,
            )
        }

    @Test
    fun `ItemUpdated clears selectedItem`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            vm.handleIntent(MenuIntent.ItemClicked(sampleItem))
            val updated = sampleItem.copy(name = "Cerveja Premium")
            vm.handleIntent(MenuIntent.ItemUpdated(updated))
            assertNull(vm.state.first().selectedItem)
        }

    // =========================================================================
    // Item Delete
    // =========================================================================

    @Test
    fun `ItemDeleted removes item from list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem2))
            vm.handleIntent(MenuIntent.ItemDeleted("menu_1"))
            val s = vm.state.first()
            assertEquals(1, s.items.size)
            assertEquals("menu_2", s.items.first().id)
        }

    @Test
    fun `ItemDeleted updates filtered list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            vm.handleIntent(MenuIntent.ItemDeleted("menu_1"))
            assertTrue(
                vm.state
                    .first()
                    .filteredItems
                    .isEmpty(),
            )
        }

    @Test
    fun `ItemDeleted clears selectedItem`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            vm.handleIntent(MenuIntent.ItemClicked(sampleItem))
            vm.handleIntent(MenuIntent.ItemDeleted("menu_1"))
            assertNull(vm.state.first().selectedItem)
        }

    // =========================================================================
    // Add Dialog
    // =========================================================================

    @Test
    fun `AddItemClicked shows dialog`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.AddItemClicked)
            assertTrue(vm.state.first().isAddDialogVisible)
        }

    @Test
    fun `DismissAddDialog hides dialog`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.AddItemClicked)
            vm.handleIntent(MenuIntent.DismissAddDialog)
            assertFalse(vm.state.first().isAddDialogVisible)
        }

    // =========================================================================
    // Item Detail / Edit
    // =========================================================================

    @Test
    fun `ItemClicked sets selectedItem`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            vm.handleIntent(MenuIntent.ItemClicked(sampleItem))
            assertEquals(sampleItem, vm.state.first().selectedItem)
        }

    @Test
    fun `DismissItemDetail clears selectedItem`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemClicked(sampleItem))
            vm.handleIntent(MenuIntent.DismissItemDetail)
            assertNull(vm.state.first().selectedItem)
        }

    // =========================================================================
    // Effects
    // =========================================================================

    @Test
    fun `NavigateBack emits effect`() =
        runTest {
            val vm = createVm()
            vm.effects.test {
                vm.handleIntent(MenuIntent.NavigateBackClicked)
                assertEquals(MenuEffect.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Item data integrity
    // =========================================================================

    @Test
    fun `item has correct price`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            val item =
                vm.state
                    .first()
                    .items
                    .first()
            assertEquals(1500L, item.priceCents)
        }

    @Test
    fun `item description can be null`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem2))
            val item =
                vm.state
                    .first()
                    .items
                    .first()
            assertNull(item.description)
        }

    @Test
    fun `item description can have value`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(MenuIntent.ItemAdded(sampleItem))
            val item =
                vm.state
                    .first()
                    .items
                    .first()
            assertEquals("IPA 600ml", item.description)
        }
}
