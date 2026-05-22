package br.com.sprena.presentation.category

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

class CategoryViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private fun vm() = CategoryViewModel()

    // ── Initial State ─────────────────────────────────────

    @Test
    fun `initial categories has default items`() =
        runTest {
            val s = vm().state.first()
            assertTrue(s.categories.isNotEmpty())
        }

    @Test
    fun `initial isAddDialogVisible is false`() =
        runTest {
            assertFalse(vm().state.first().isAddDialogVisible)
        }

    @Test
    fun `initial editingCategory is null`() =
        runTest {
            assertNull(vm().state.first().editingCategory)
        }

    // ── Add Dialog ────────────────────────────────────────

    @Test
    fun `AddCategoryClicked shows add dialog`() =
        runTest {
            val vm = vm()
            vm.handleIntent(CategoryIntent.AddCategoryClicked)
            assertTrue(vm.state.first().isAddDialogVisible)
        }

    @Test
    fun `DismissAddDialog hides add dialog`() =
        runTest {
            val vm = vm()
            vm.handleIntent(CategoryIntent.AddCategoryClicked)
            vm.handleIntent(CategoryIntent.DismissAddDialog)
            assertFalse(vm.state.first().isAddDialogVisible)
        }

    // ── Add Category ──────────────────────────────────────

    @Test
    fun `CategoryAdded appends new category to list`() =
        runTest {
            val vm = vm()
            val initialSize =
                vm.state
                    .first()
                    .categories.size
            vm.handleIntent(CategoryIntent.CategoryAdded("Investimento"))
            val s = vm.state.first()
            assertEquals(initialSize + 1, s.categories.size)
            assertTrue(s.categories.contains("Investimento"))
        }

    @Test
    fun `CategoryAdded closes add dialog`() =
        runTest {
            val vm = vm()
            vm.handleIntent(CategoryIntent.AddCategoryClicked)
            vm.handleIntent(CategoryIntent.CategoryAdded("Novo"))
            assertFalse(vm.state.first().isAddDialogVisible)
        }

    @Test
    fun `CategoryAdded with duplicate name is ignored`() =
        runTest {
            val vm = vm()
            val initialSize =
                vm.state
                    .first()
                    .categories.size
            val firstCategory =
                vm.state
                    .first()
                    .categories
                    .first()
            vm.handleIntent(CategoryIntent.CategoryAdded(firstCategory))
            assertEquals(
                initialSize,
                vm.state
                    .first()
                    .categories.size,
            )
        }

    @Test
    fun `CategoryAdded with blank name is ignored`() =
        runTest {
            val vm = vm()
            val initialSize =
                vm.state
                    .first()
                    .categories.size
            vm.handleIntent(CategoryIntent.CategoryAdded("   "))
            assertEquals(
                initialSize,
                vm.state
                    .first()
                    .categories.size,
            )
        }

    // ── Edit Category ─────────────────────────────────────

    @Test
    fun `CategoryClicked sets editingCategory`() =
        runTest {
            val vm = vm()
            val cat =
                vm.state
                    .first()
                    .categories
                    .first()
            vm.handleIntent(CategoryIntent.CategoryClicked(cat))
            assertEquals(cat, vm.state.first().editingCategory)
        }

    @Test
    fun `DismissEditDialog clears editingCategory`() =
        runTest {
            val vm = vm()
            val cat =
                vm.state
                    .first()
                    .categories
                    .first()
            vm.handleIntent(CategoryIntent.CategoryClicked(cat))
            vm.handleIntent(CategoryIntent.DismissEditDialog)
            assertNull(vm.state.first().editingCategory)
        }

    @Test
    fun `CategoryRenamed updates category name in list`() =
        runTest {
            val vm = vm()
            val oldName =
                vm.state
                    .first()
                    .categories
                    .first()
            vm.handleIntent(CategoryIntent.CategoryRenamed(oldName, "Novo Nome"))
            val s = vm.state.first()
            assertFalse(s.categories.contains(oldName))
            assertTrue(s.categories.contains("Novo Nome"))
        }

    @Test
    fun `CategoryRenamed clears editingCategory`() =
        runTest {
            val vm = vm()
            val cat =
                vm.state
                    .first()
                    .categories
                    .first()
            vm.handleIntent(CategoryIntent.CategoryClicked(cat))
            vm.handleIntent(CategoryIntent.CategoryRenamed(cat, "Renomeado"))
            assertNull(vm.state.first().editingCategory)
        }

    @Test
    fun `CategoryRenamed with blank newName is ignored`() =
        runTest {
            val vm = vm()
            val cat =
                vm.state
                    .first()
                    .categories
                    .first()
            vm.handleIntent(CategoryIntent.CategoryRenamed(cat, "  "))
            assertTrue(
                vm.state
                    .first()
                    .categories
                    .contains(cat),
            )
        }

    // ── Delete Category ───────────────────────────────────

    @Test
    fun `CategoryDeleted removes category from list`() =
        runTest {
            val vm = vm()
            val cat =
                vm.state
                    .first()
                    .categories
                    .first()
            val initialSize =
                vm.state
                    .first()
                    .categories.size
            vm.handleIntent(CategoryIntent.CategoryDeleted(cat))
            val s = vm.state.first()
            assertEquals(initialSize - 1, s.categories.size)
            assertFalse(s.categories.contains(cat))
        }

    @Test
    fun `CategoryDeleted clears editingCategory`() =
        runTest {
            val vm = vm()
            val cat =
                vm.state
                    .first()
                    .categories
                    .first()
            vm.handleIntent(CategoryIntent.CategoryClicked(cat))
            vm.handleIntent(CategoryIntent.CategoryDeleted(cat))
            assertNull(vm.state.first().editingCategory)
        }

    @Test
    fun `CategoryDeleted with unknown name does nothing`() =
        runTest {
            val vm = vm()
            val initialSize =
                vm.state
                    .first()
                    .categories.size
            vm.handleIntent(CategoryIntent.CategoryDeleted("Inexistente"))
            assertEquals(
                initialSize,
                vm.state
                    .first()
                    .categories.size,
            )
        }
}
