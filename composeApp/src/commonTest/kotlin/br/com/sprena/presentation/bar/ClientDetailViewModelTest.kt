package br.com.sprena.presentation.bar

import app.cash.turbine.test
import br.com.sprena.presentation.bar.clientdetail.ClientDetailEffect
import br.com.sprena.presentation.bar.clientdetail.ClientDetailIntent
import br.com.sprena.presentation.bar.clientdetail.ClientDetailViewModel
import br.com.sprena.presentation.bar.clientdetail.MenuItem
import br.com.sprena.test.MainDispatcherEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test fixture — simulates items from the Cardápio (MenuViewModel). */
private val TEST_MENU_ITEMS =
    listOf(
        MenuItem(name = "Agua", priceCents = 500),
        MenuItem(name = "Almoço", priceCents = 2500),
        MenuItem(name = "Gatorade", priceCents = 1000),
        MenuItem(name = "Xeque-Mate", priceCents = 1250),
    )

/**
 * TDD — ClientDetailViewModel (detalhe do cliente com itens consumidos).
 *
 * Cenários cobertos:
 * - Carregar dados do cliente
 * - Adicionar item (name + price)
 * - Remover item
 * - Validar campos do item
 * - Toggle pago
 * - Calcular total
 * - Deletar cliente com confirmação
 */
class ClientDetailViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

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

    private fun createVm(client: BarClient = sampleClient): ClientDetailViewModel =
        ClientDetailViewModel(client = client)

    // =========================================================================
    // Load — carregar dados do cliente
    // =========================================================================

    @Test
    fun `initial state loads client data`() =
        runTest {
            val vm = createVm()
            val s = vm.state.first()
            assertEquals("client_1", s.clientId)
            assertEquals("João Silva", s.clientName)
            assertEquals("Joãozinho", s.clientNickname)
            assertEquals("11999998888", s.clientPhone)
            assertEquals("12345678901", s.clientCpf)
            assertEquals("joao@email.com", s.clientEmail)
        }

    @Test
    fun `initial state loads items`() =
        runTest {
            val vm = createVm()
            val s = vm.state.first()
            assertEquals(2, s.items.size)
            assertEquals("Cerveja", s.items[0].name)
            assertEquals(1200L, s.items[0].priceCents)
        }

    @Test
    fun `initial state calculates total`() =
        runTest {
            val vm = createVm()
            assertEquals(3700L, vm.state.first().totalCents)
        }

    @Test
    fun `initial state loads payment status`() =
        runTest {
            val vm = createVm()
            assertFalse(vm.state.first().isPaid)
        }

    // =========================================================================
    // Add Item — form fields
    // =========================================================================

    @Test
    fun `AddItemClicked shows add item form`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.AddItemClicked)
            assertTrue(vm.state.first().isAddItemVisible)
        }

    @Test
    fun `DismissAddItem hides add item form`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.AddItemClicked)
            vm.handleIntent(ClientDetailIntent.DismissAddItem)
            assertFalse(vm.state.first().isAddItemVisible)
        }

    @Test
    fun `NewItemNameChanged updates newItemName`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.NewItemNameChanged("Caipirinha"))
            assertEquals("Caipirinha", vm.state.first().newItemName)
        }

    @Test
    fun `NewItemPriceChanged updates newItemPriceCents`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(1500L))
            assertEquals(1500L, vm.state.first().newItemPriceCents)
        }

    // =========================================================================
    // Add Item — validation
    // =========================================================================

    @Test
    fun `NewItemNameChanged with empty shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.NewItemNameChanged("Cerveja"))
            vm.handleIntent(ClientDetailIntent.NewItemNameChanged(""))
            assertNotNull(vm.state.first().newItemNameError)
        }

    @Test
    fun `NewItemNameChanged with too long name shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.NewItemNameChanged("A".repeat(101)))
            assertNotNull(vm.state.first().newItemNameError)
        }

    @Test
    fun `NewItemPriceChanged with null shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(1500L))
            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(null))
            assertNotNull(vm.state.first().newItemPriceError)
        }

    @Test
    fun `NewItemPriceChanged with zero shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(0L))
            assertNotNull(vm.state.first().newItemPriceError)
        }

    @Test
    fun `NewItemPriceChanged with negative shows error`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(-100L))
            assertNotNull(vm.state.first().newItemPriceError)
        }

    // =========================================================================
    // Save Item
    // =========================================================================

    @Test
    fun `SaveItem adds item to list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.NewItemNameChanged("Caipirinha"))
            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(1800L))
            vm.handleIntent(ClientDetailIntent.SaveItem)
            val s = vm.state.first()
            assertEquals(3, s.items.size)
            assertEquals("Caipirinha", s.items.last().name)
            assertEquals(1800L, s.items.last().priceCents)
        }

    @Test
    fun `SaveItem updates total`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.NewItemNameChanged("Caipirinha"))
            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(1800L))
            vm.handleIntent(ClientDetailIntent.SaveItem)
            assertEquals(5500L, vm.state.first().totalCents) // 3700 + 1800
        }

    @Test
    fun `SaveItem clears form fields`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.NewItemNameChanged("Caipirinha"))
            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(1800L))
            vm.handleIntent(ClientDetailIntent.SaveItem)
            val s = vm.state.first()
            assertEquals("", s.newItemName)
            assertNull(s.newItemPriceCents)
        }

    @Test
    fun `SaveItem hides add item form`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.AddItemClicked)
            vm.handleIntent(ClientDetailIntent.NewItemNameChanged("Caipirinha"))
            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(1800L))
            vm.handleIntent(ClientDetailIntent.SaveItem)
            assertFalse(vm.state.first().isAddItemVisible)
        }

    @Test
    fun `SaveItem with invalid fields does not add item`() =
        runTest {
            val vm = createVm()
            // empty name and null price
            vm.handleIntent(ClientDetailIntent.SaveItem)
            assertEquals(
                2,
                vm.state
                    .first()
                    .items.size,
            ) // unchanged
        }

    @Test
    fun `SaveItem emits ClientUpdated effect`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.NewItemNameChanged("Caipirinha"))
            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(1800L))
            vm.effects.test {
                vm.handleIntent(ClientDetailIntent.SaveItem)
                val effect = awaitItem()
                assertTrue(effect is ClientDetailEffect.ClientUpdated)
                assertEquals(3, (effect as ClientDetailEffect.ClientUpdated).client.items.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Remove Item
    // =========================================================================

    @Test
    fun `RemoveItem removes item from list`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.RemoveItem("item_1"))
            val s = vm.state.first()
            assertEquals(1, s.items.size)
            assertEquals("item_2", s.items.first().id)
        }

    @Test
    fun `RemoveItem updates total`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.RemoveItem("item_1"))
            assertEquals(2500L, vm.state.first().totalCents)
        }

    @Test
    fun `RemoveItem emits ClientUpdated effect`() =
        runTest {
            val vm = createVm()
            vm.effects.test {
                vm.handleIntent(ClientDetailIntent.RemoveItem("item_1"))
                val effect = awaitItem()
                assertTrue(effect is ClientDetailEffect.ClientUpdated)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Toggle Paid
    // =========================================================================

    @Test
    fun `TogglePaid toggles isPaid`() =
        runTest {
            val vm = createVm()
            assertFalse(vm.state.first().isPaid)
            vm.handleIntent(ClientDetailIntent.TogglePaid)
            assertTrue(vm.state.first().isPaid)
        }

    @Test
    fun `TogglePaid emits ClientUpdated effect`() =
        runTest {
            val vm = createVm()
            vm.effects.test {
                vm.handleIntent(ClientDetailIntent.TogglePaid)
                val effect = awaitItem()
                assertTrue(effect is ClientDetailEffect.ClientUpdated)
                assertTrue((effect as ClientDetailEffect.ClientUpdated).client.isPaid)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Menu Item Selected — preenche nome e preço a partir do cardápio
    // =========================================================================

    @Test
    fun `MenuItemSelected fills name and price`() =
        runTest {
            val vm = createVm()
            val menuItem = MenuItem(name = "Agua", priceCents = 500)
            vm.handleIntent(ClientDetailIntent.MenuItemSelected(menuItem))
            val s = vm.state.first()
            assertEquals("Agua", s.newItemName)
            assertEquals(500L, s.newItemPriceCents)
        }

    @Test
    fun `MenuItemSelected clears previous errors`() =
        runTest {
            val vm = createVm()
            // First trigger errors
            vm.handleIntent(ClientDetailIntent.NewItemNameChanged(""))
            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(null))
            assertNotNull(vm.state.first().newItemNameError)
            assertNotNull(vm.state.first().newItemPriceError)

            // Now select from menu — errors should clear
            val menuItem = MenuItem(name = "Almoço", priceCents = 2500)
            vm.handleIntent(ClientDetailIntent.MenuItemSelected(menuItem))
            assertNull(vm.state.first().newItemNameError)
            assertNull(vm.state.first().newItemPriceError)
        }

    @Test
    fun `MenuItemSelected Agua sets correct values`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.MenuItemSelected(TEST_MENU_ITEMS[0]))
            val s = vm.state.first()
            assertEquals("Agua", s.newItemName)
            assertEquals(500L, s.newItemPriceCents)
        }

    @Test
    fun `MenuItemSelected Almoco sets correct values`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.MenuItemSelected(TEST_MENU_ITEMS[1]))
            val s = vm.state.first()
            assertEquals("Almoço", s.newItemName)
            assertEquals(2500L, s.newItemPriceCents)
        }

    @Test
    fun `MenuItemSelected Gatorade sets correct values`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.MenuItemSelected(TEST_MENU_ITEMS[2]))
            val s = vm.state.first()
            assertEquals("Gatorade", s.newItemName)
            assertEquals(1000L, s.newItemPriceCents)
        }

    @Test
    fun `MenuItemSelected XequeMate sets correct values`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.MenuItemSelected(TEST_MENU_ITEMS[3]))
            val s = vm.state.first()
            assertEquals("Xeque-Mate", s.newItemName)
            assertEquals(1250L, s.newItemPriceCents)
        }

    @Test
    fun `MenuItemSelected can be overridden by manual name edit`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.MenuItemSelected(TEST_MENU_ITEMS[0]))
            assertEquals("Agua", vm.state.first().newItemName)

            vm.handleIntent(ClientDetailIntent.NewItemNameChanged("Agua com gás"))
            assertEquals("Agua com gás", vm.state.first().newItemName)
            // Price should remain from menu selection
            assertEquals(500L, vm.state.first().newItemPriceCents)
        }

    @Test
    fun `MenuItemSelected can be overridden by manual price edit`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.MenuItemSelected(TEST_MENU_ITEMS[1]))
            assertEquals(2500L, vm.state.first().newItemPriceCents)

            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(3000L))
            assertEquals(3000L, vm.state.first().newItemPriceCents)
            // Name should remain from menu selection
            assertEquals("Almoço", vm.state.first().newItemName)
        }

    @Test
    fun `MenuItemSelected replaces previous menu selection`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.MenuItemSelected(TEST_MENU_ITEMS[0]))
            assertEquals("Agua", vm.state.first().newItemName)

            vm.handleIntent(ClientDetailIntent.MenuItemSelected(TEST_MENU_ITEMS[3]))
            val s = vm.state.first()
            assertEquals("Xeque-Mate", s.newItemName)
            assertEquals(1250L, s.newItemPriceCents)
        }

    @Test
    fun `MenuItemSelected then SaveItem adds correct item`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.MenuItemSelected(TEST_MENU_ITEMS[2]))
            vm.handleIntent(ClientDetailIntent.SaveItem)

            val s = vm.state.first()
            assertEquals(3, s.items.size)
            val addedItem = s.items.last()
            assertEquals("Gatorade", addedItem.name)
            assertEquals(1000L, addedItem.priceCents)
        }

    @Test
    fun `MenuItemSelected then SaveItem updates total correctly`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.MenuItemSelected(TEST_MENU_ITEMS[2])) // Gatorade 1000
            vm.handleIntent(ClientDetailIntent.SaveItem)
            // Original total was 3700 (1200 + 2500), plus 1000 = 4700
            assertEquals(4700L, vm.state.first().totalCents)
        }

    @Test
    fun `test fixture menu items has 4 items`() {
        assertEquals(4, TEST_MENU_ITEMS.size)
    }

    @Test
    fun `MenuItemSelected clears form after DismissAddItem and reopen`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.AddItemClicked)
            vm.handleIntent(ClientDetailIntent.MenuItemSelected(TEST_MENU_ITEMS[0]))
            assertEquals("Agua", vm.state.first().newItemName)

            // Dismiss clears form
            vm.handleIntent(ClientDetailIntent.DismissAddItem)
            assertEquals("", vm.state.first().newItemName)
            assertNull(vm.state.first().newItemPriceCents)

            // Reopen — form should be empty
            vm.handleIntent(ClientDetailIntent.AddItemClicked)
            assertEquals("", vm.state.first().newItemName)
            assertNull(vm.state.first().newItemPriceCents)
        }

    // =========================================================================
    // Delete — com confirmação
    // =========================================================================

    @Test
    fun `DeleteClicked shows confirmation`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.DeleteClicked)
            assertTrue(vm.state.first().isDeleteConfirmVisible)
        }

    @Test
    fun `DeleteCancelled hides confirmation`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.DeleteClicked)
            vm.handleIntent(ClientDetailIntent.DeleteCancelled)
            assertFalse(vm.state.first().isDeleteConfirmVisible)
        }

    @Test
    fun `DeleteConfirmed emits ClientDeleted effect`() =
        runTest {
            val vm = createVm()
            vm.effects.test {
                vm.handleIntent(ClientDetailIntent.DeleteClicked)
                vm.handleIntent(ClientDetailIntent.DeleteConfirmed)
                val effect = awaitItem()
                assertTrue(effect is ClientDetailEffect.ClientDeleted)
                assertEquals("client_1", (effect as ClientDetailEffect.ClientDeleted).clientId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `DeleteConfirmed hides confirmation`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.DeleteClicked)
            vm.handleIntent(ClientDetailIntent.DeleteConfirmed)
            assertFalse(vm.state.first().isDeleteConfirmVisible)
        }

    // =========================================================================
    // Dismiss
    // =========================================================================

    @Test
    fun `Dismiss emits Dismissed effect`() =
        runTest {
            val vm = createVm()
            vm.effects.test {
                vm.handleIntent(ClientDetailIntent.Dismiss)
                assertEquals(ClientDetailEffect.Dismissed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Pagination — exibir apenas 4 itens por página
    // =========================================================================

    @Test
    fun `initial state has itemsPage 0`() =
        runTest {
            val vm = createVm()
            assertEquals(0, vm.state.first().itemsPage)
        }

    @Test
    fun `paginatedItems returns at most 4 items`() =
        runTest {
            val client =
                sampleClient.copy(
                    items = (1..6).map { BarItem(id = "item_$it", name = "Item $it", priceCents = 100) },
                )
            val vm = createVm(client)
            val s = vm.state.first()
            assertEquals(6, s.items.size)
            assertEquals(4, s.paginatedItems.size)
        }

    @Test
    fun `paginatedItems returns remaining items on last page`() =
        runTest {
            val client =
                sampleClient.copy(
                    items = (1..6).map { BarItem(id = "item_$it", name = "Item $it", priceCents = 100) },
                )
            val vm = createVm(client)
            vm.handleIntent(ClientDetailIntent.NextItemsPage)
            val s = vm.state.first()
            assertEquals(1, s.itemsPage)
            assertEquals(2, s.paginatedItems.size)
        }

    @Test
    fun `totalPages is correct`() =
        runTest {
            val client =
                sampleClient.copy(
                    items = (1..6).map { BarItem(id = "item_$it", name = "Item $it", priceCents = 100) },
                )
            val vm = createVm(client)
            assertEquals(2, vm.state.first().totalPages)
        }

    @Test
    fun `totalPages is 1 when no items`() =
        runTest {
            val client = sampleClient.copy(items = emptyList())
            val vm = createVm(client)
            assertEquals(1, vm.state.first().totalPages)
        }

    @Test
    fun `totalPages is 1 when 4 or fewer items`() =
        runTest {
            val client =
                sampleClient.copy(
                    items = (1..4).map { BarItem(id = "item_$it", name = "Item $it", priceCents = 100) },
                )
            val vm = createVm(client)
            assertEquals(1, vm.state.first().totalPages)
        }

    @Test
    fun `NextItemsPage increments page`() =
        runTest {
            val client =
                sampleClient.copy(
                    items = (1..6).map { BarItem(id = "item_$it", name = "Item $it", priceCents = 100) },
                )
            val vm = createVm(client)
            vm.handleIntent(ClientDetailIntent.NextItemsPage)
            assertEquals(1, vm.state.first().itemsPage)
        }

    @Test
    fun `NextItemsPage does not go beyond last page`() =
        runTest {
            val client =
                sampleClient.copy(
                    items = (1..6).map { BarItem(id = "item_$it", name = "Item $it", priceCents = 100) },
                )
            val vm = createVm(client)
            vm.handleIntent(ClientDetailIntent.NextItemsPage)
            vm.handleIntent(ClientDetailIntent.NextItemsPage) // already on last page
            assertEquals(1, vm.state.first().itemsPage) // stays at 1
        }

    @Test
    fun `PrevItemsPage decrements page`() =
        runTest {
            val client =
                sampleClient.copy(
                    items = (1..6).map { BarItem(id = "item_$it", name = "Item $it", priceCents = 100) },
                )
            val vm = createVm(client)
            vm.handleIntent(ClientDetailIntent.NextItemsPage)
            vm.handleIntent(ClientDetailIntent.PrevItemsPage)
            assertEquals(0, vm.state.first().itemsPage)
        }

    @Test
    fun `PrevItemsPage does not go below 0`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.PrevItemsPage)
            assertEquals(0, vm.state.first().itemsPage)
        }

    // =========================================================================
    // Quantity — BarItem com quantity, merge de duplicatas
    // =========================================================================

    @Test
    fun `initial items have quantity 1`() =
        runTest {
            val vm = createVm()
            val s = vm.state.first()
            s.items.forEach { assertEquals(1, it.quantity) }
        }

    @Test
    fun `SaveItem with duplicate name and price merges into existing item`() =
        runTest {
            val vm = createVm()
            // Add "Cerveja" at 1200 — same as item_1
            vm.handleIntent(ClientDetailIntent.NewItemNameChanged("Cerveja"))
            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(1200L))
            vm.handleIntent(ClientDetailIntent.SaveItem)
            val s = vm.state.first()
            // Should NOT add a 3rd item — should merge
            assertEquals(2, s.items.size)
            val cerveja = s.items.first { it.name == "Cerveja" }
            assertEquals(2, cerveja.quantity)
        }

    @Test
    fun `SaveItem with duplicate name but different price adds new item`() =
        runTest {
            val vm = createVm()
            // Add "Cerveja" at different price
            vm.handleIntent(ClientDetailIntent.NewItemNameChanged("Cerveja"))
            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(1500L))
            vm.handleIntent(ClientDetailIntent.SaveItem)
            val s = vm.state.first()
            assertEquals(3, s.items.size) // new item added
        }

    @Test
    fun `SaveItem merge updates total with quantity`() =
        runTest {
            val vm = createVm()
            // Original total: 1200 + 2500 = 3700
            // Add Cerveja 1200 again → quantity becomes 2 → total = 1200*2 + 2500 = 4900
            vm.handleIntent(ClientDetailIntent.NewItemNameChanged("Cerveja"))
            vm.handleIntent(ClientDetailIntent.NewItemPriceChanged(1200L))
            vm.handleIntent(ClientDetailIntent.SaveItem)
            assertEquals(4900L, vm.state.first().totalCents)
        }

    @Test
    fun `total uses priceCents times quantity`() =
        runTest {
            val client =
                sampleClient.copy(
                    items =
                        listOf(
                            BarItem(id = "item_1", name = "Cerveja", priceCents = 1200, quantity = 3),
                        ),
                )
            val vm = createVm(client)
            assertEquals(3600L, vm.state.first().totalCents) // 1200 * 3
        }

    // =========================================================================
    // Increment / Decrement — +/- controles de quantidade
    // =========================================================================

    @Test
    fun `IncrementItem increases quantity by 1`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.IncrementItem("item_1"))
            val cerveja =
                vm.state
                    .first()
                    .items
                    .first { it.id == "item_1" }
            assertEquals(2, cerveja.quantity)
        }

    @Test
    fun `IncrementItem updates total`() =
        runTest {
            val vm = createVm()
            // Original total: 1200 + 2500 = 3700
            vm.handleIntent(ClientDetailIntent.IncrementItem("item_1"))
            // After: 1200*2 + 2500 = 4900
            assertEquals(4900L, vm.state.first().totalCents)
        }

    @Test
    fun `IncrementItem emits ClientUpdated effect`() =
        runTest {
            val vm = createVm()
            vm.effects.test {
                vm.handleIntent(ClientDetailIntent.IncrementItem("item_1"))
                val effect = awaitItem()
                assertTrue(effect is ClientDetailEffect.ClientUpdated)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `DecrementItem with quantity greater than 1 decreases quantity`() =
        runTest {
            val client =
                sampleClient.copy(
                    items =
                        listOf(
                            BarItem(id = "item_1", name = "Cerveja", priceCents = 1200, quantity = 3),
                            BarItem(id = "item_2", name = "Petisco", priceCents = 2500),
                        ),
                )
            val vm = createVm(client)
            vm.handleIntent(ClientDetailIntent.DecrementItem("item_1"))
            val cerveja =
                vm.state
                    .first()
                    .items
                    .first { it.id == "item_1" }
            assertEquals(2, cerveja.quantity)
        }

    @Test
    fun `DecrementItem with quantity greater than 1 updates total`() =
        runTest {
            val client =
                sampleClient.copy(
                    items =
                        listOf(
                            BarItem(id = "item_1", name = "Cerveja", priceCents = 1200, quantity = 3),
                            BarItem(id = "item_2", name = "Petisco", priceCents = 2500),
                        ),
                )
            val vm = createVm(client)
            // Total: 1200*3 + 2500 = 6100
            vm.handleIntent(ClientDetailIntent.DecrementItem("item_1"))
            // After: 1200*2 + 2500 = 4900
            assertEquals(4900L, vm.state.first().totalCents)
        }

    @Test
    fun `DecrementItem with quantity greater than 1 emits ClientUpdated`() =
        runTest {
            val client =
                sampleClient.copy(
                    items =
                        listOf(
                            BarItem(id = "item_1", name = "Cerveja", priceCents = 1200, quantity = 2),
                        ),
                )
            val vm = createVm(client)
            vm.effects.test {
                vm.handleIntent(ClientDetailIntent.DecrementItem("item_1"))
                val effect = awaitItem()
                assertTrue(effect is ClientDetailEffect.ClientUpdated)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // Delete on Decrement — confirmation when quantity is 1
    // =========================================================================

    @Test
    fun `DecrementItem with quantity 1 shows delete confirmation`() =
        runTest {
            val vm = createVm() // items have quantity 1 by default
            vm.handleIntent(ClientDetailIntent.DecrementItem("item_1"))
            val s = vm.state.first()
            assertEquals("item_1", s.itemToDeleteId)
        }

    @Test
    fun `DecrementItem with quantity 1 does not remove item yet`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.DecrementItem("item_1"))
            assertEquals(
                2,
                vm.state
                    .first()
                    .items.size,
            ) // still 2 items
        }

    @Test
    fun `ConfirmDeleteItem removes the item`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.DecrementItem("item_1"))
            vm.handleIntent(ClientDetailIntent.ConfirmDeleteItem)
            val s = vm.state.first()
            assertEquals(1, s.items.size)
            assertEquals("item_2", s.items.first().id)
            assertNull(s.itemToDeleteId)
        }

    @Test
    fun `ConfirmDeleteItem updates total`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.DecrementItem("item_1"))
            vm.handleIntent(ClientDetailIntent.ConfirmDeleteItem)
            assertEquals(2500L, vm.state.first().totalCents)
        }

    @Test
    fun `ConfirmDeleteItem emits ClientUpdated effect`() =
        runTest {
            val vm = createVm()
            vm.effects.test {
                vm.handleIntent(ClientDetailIntent.DecrementItem("item_1"))
                vm.handleIntent(ClientDetailIntent.ConfirmDeleteItem)
                val effect = awaitItem()
                assertTrue(effect is ClientDetailEffect.ClientUpdated)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `CancelDeleteItem clears itemToDeleteId`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.DecrementItem("item_1"))
            assertEquals("item_1", vm.state.first().itemToDeleteId)
            vm.handleIntent(ClientDetailIntent.CancelDeleteItem)
            assertNull(vm.state.first().itemToDeleteId)
        }

    @Test
    fun `CancelDeleteItem keeps items unchanged`() =
        runTest {
            val vm = createVm()
            vm.handleIntent(ClientDetailIntent.DecrementItem("item_1"))
            vm.handleIntent(ClientDetailIntent.CancelDeleteItem)
            assertEquals(
                2,
                vm.state
                    .first()
                    .items.size,
            )
        }

    // =========================================================================
    // Pagination + Item operations interaction
    // =========================================================================

    @Test
    fun `page resets to last valid page when items are removed`() =
        runTest {
            // 5 items → 2 pages. Go to page 2, remove item → should go back to page 1 if needed
            val client =
                sampleClient.copy(
                    items = (1..5).map { BarItem(id = "item_$it", name = "Item $it", priceCents = 100) },
                )
            val vm = createVm(client)
            vm.handleIntent(ClientDetailIntent.NextItemsPage) // page 1 (shows item_5)
            assertEquals(1, vm.state.first().itemsPage)
            // Remove item_5 — now only 4 items, 1 page, should reset to page 0
            vm.handleIntent(ClientDetailIntent.RemoveItem("item_5"))
            assertEquals(0, vm.state.first().itemsPage)
        }
}
