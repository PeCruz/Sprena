package br.com.sprena.presentation.eventos.createevent

import app.cash.turbine.test
import br.com.sprena.presentation.eventos.EventCategory
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

/**
 * TDD — Testes do form de criacao/edicao de evento (refatorado).
 *
 * Requisitos cobertos:
 * - 3 categorias disponiveis: Reserva, Aluguel, Day Use
 * - Campos obrigatorios: Nome, Categoria, Data
 * - Campos opcionais: Contato (mascara de telefone), Descricao
 * - Validacao com mensagens de erro por campo
 * - Modo criacao (eventId null) e modo edicao (eventId preenchido)
 * - Submit trima nome, aplica mascara de telefone
 * - Descricao em branco vira null no efeito
 * - Contato em branco vira null no efeito
 * - Controle de unsaved changes
 * - Navegacao (voltar)
 */
class CreateEventViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest
    fun setUp() = env.install()

    @AfterTest
    fun tearDown() = env.uninstall()

    // =========================================================================
    // 1. Estado Inicial
    // =========================================================================

    @Test
    fun `initial state has empty name`() =
        runTest {
            val vm = CreateEventViewModel()
            assertEquals("", vm.state.first().name)
        }

    @Test
    fun `initial state has no category selected`() =
        runTest {
            val vm = CreateEventViewModel()
            assertNull(vm.state.first().category)
        }

    @Test
    fun `initial state has no date selected`() =
        runTest {
            val vm = CreateEventViewModel()
            assertNull(vm.state.first().dateEpochDay)
        }

    @Test
    fun `initial state has empty contact`() =
        runTest {
            val vm = CreateEventViewModel()
            assertEquals("", vm.state.first().contact)
        }

    @Test
    fun `initial state has empty description`() =
        runTest {
            val vm = CreateEventViewModel()
            assertEquals("", vm.state.first().description)
        }

    @Test
    fun `initial state cannot submit`() =
        runTest {
            val vm = CreateEventViewModel()
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `initial state has no unsaved changes`() =
        runTest {
            val vm = CreateEventViewModel()
            assertFalse(vm.state.first().hasUnsavedChanges)
        }

    @Test
    fun `initial state is not in edit mode`() =
        runTest {
            val vm = CreateEventViewModel()
            assertFalse(vm.state.first().isEditMode)
        }

    @Test
    fun `initial state has no editing event id`() =
        runTest {
            val vm = CreateEventViewModel()
            assertNull(vm.state.first().editingEventId)
        }

    @Test
    fun `initial state has no field errors`() =
        runTest {
            val vm = CreateEventViewModel()
            val state = vm.state.first()
            assertNull(state.nameError)
            assertNull(state.categoryError)
            assertNull(state.dateError)
        }

    // =========================================================================
    // 2. Preenchimento de Campos
    // =========================================================================

    @Test
    fun `NameChanged updates name`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa Junina"))
            assertEquals("Festa Junina", vm.state.first().name)
        }

    @Test
    fun `NameChanged clears nameError`() =
        runTest {
            val vm = CreateEventViewModel()
            // Trigger error first
            vm.handleIntent(CreateEventIntent.Submit)
            assertNotNull(vm.state.first().nameError)
            // Now fix it
            vm.handleIntent(CreateEventIntent.NameChanged("Nome valido"))
            assertNull(vm.state.first().nameError)
        }

    @Test
    fun `CategoryChanged updates category to RESERVA`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            assertEquals(EventCategory.RESERVA, vm.state.first().category)
        }

    @Test
    fun `CategoryChanged updates category to ALUGUEL`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.ALUGUEL))
            assertEquals(EventCategory.ALUGUEL, vm.state.first().category)
        }

    @Test
    fun `CategoryChanged updates category to DAY_USE`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.DAY_USE))
            assertEquals(EventCategory.DAY_USE, vm.state.first().category)
        }

    @Test
    fun `CategoryChanged clears categoryError`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.handleIntent(CreateEventIntent.Submit)
            assertNotNull(vm.state.first().categoryError)
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            assertNull(vm.state.first().categoryError)
        }

    @Test
    fun `CategoryChanged can switch between categories`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            assertEquals(EventCategory.RESERVA, vm.state.first().category)
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.DAY_USE))
            assertEquals(EventCategory.DAY_USE, vm.state.first().category)
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.ALUGUEL))
            assertEquals(EventCategory.ALUGUEL, vm.state.first().category)
        }

    @Test
    fun `DateChanged updates dateEpochDay`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            assertEquals(20000L, vm.state.first().dateEpochDay)
        }

    @Test
    fun `DateChanged clears dateError`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.Submit)
            assertNotNull(vm.state.first().dateError)
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            assertNull(vm.state.first().dateError)
        }

    @Test
    fun `ContactChanged updates contact`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.ContactChanged("11999998888"))
            assertEquals("11999998888", vm.state.first().contact)
        }

    @Test
    fun `DescriptionChanged updates description`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.DescriptionChanged("Descricao detalhada"))
            assertEquals("Descricao detalhada", vm.state.first().description)
        }

    // =========================================================================
    // 3. Unsaved Changes
    // =========================================================================

    @Test
    fun `changing name marks unsaved changes`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("A"))
            assertTrue(vm.state.first().hasUnsavedChanges)
        }

    @Test
    fun `changing category marks unsaved changes`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.ALUGUEL))
            assertTrue(vm.state.first().hasUnsavedChanges)
        }

    @Test
    fun `changing date marks unsaved changes`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            assertTrue(vm.state.first().hasUnsavedChanges)
        }

    @Test
    fun `changing contact marks unsaved changes`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.ContactChanged("11"))
            assertTrue(vm.state.first().hasUnsavedChanges)
        }

    @Test
    fun `changing description marks unsaved changes`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.DescriptionChanged("Algo"))
            assertTrue(vm.state.first().hasUnsavedChanges)
        }

    @Test
    fun `hasUnsavedChanges stays true once set`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("A"))
            assertTrue(vm.state.first().hasUnsavedChanges)
            vm.handleIntent(CreateEventIntent.NameChanged(""))
            // Still true — once flagged, it stays
            assertTrue(vm.state.first().hasUnsavedChanges)
        }

    // =========================================================================
    // 4. Validacao — canSubmit
    // =========================================================================

    @Test
    fun `canSubmit true with RESERVA category`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Reserva"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            assertTrue(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit true with ALUGUEL category`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Aluguel"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.ALUGUEL))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            assertTrue(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit true with DAY_USE category`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Day Use"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.DAY_USE))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            assertTrue(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit false when only name is filled`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit false when only category is filled`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit false when only date is filled`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit false when name is only whitespace`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("   "))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            assertFalse(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit true regardless of optional fields`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            // Optional fields remain empty
            assertTrue(vm.state.first().canSubmit)
        }

    @Test
    fun `canSubmit remains true with optional fields filled`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.handleIntent(CreateEventIntent.ContactChanged("11999998888"))
            vm.handleIntent(CreateEventIntent.DescriptionChanged("Descricao"))
            assertTrue(vm.state.first().canSubmit)
        }

    // =========================================================================
    // 5. Validacao — Erros de Campo no Submit
    // =========================================================================

    @Test
    fun `Submit with all fields empty sets all three errors`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.Submit)
            val state = vm.state.first()
            assertNotNull(state.nameError)
            assertNotNull(state.categoryError)
            assertNotNull(state.dateError)
        }

    @Test
    fun `Submit with empty name sets nameError`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.handleIntent(CreateEventIntent.Submit)
            assertNotNull(vm.state.first().nameError)
            assertNull(vm.state.first().categoryError)
            assertNull(vm.state.first().dateError)
        }

    @Test
    fun `Submit with whitespace-only name sets nameError`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("   "))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.handleIntent(CreateEventIntent.Submit)
            assertNotNull(vm.state.first().nameError)
        }

    @Test
    fun `Submit with no category sets categoryError`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.handleIntent(CreateEventIntent.Submit)
            assertNotNull(vm.state.first().categoryError)
            assertNull(vm.state.first().nameError)
            assertNull(vm.state.first().dateError)
        }

    @Test
    fun `Submit with no date sets dateError`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.Submit)
            assertNotNull(vm.state.first().dateError)
            assertNull(vm.state.first().nameError)
            assertNull(vm.state.first().categoryError)
        }

    @Test
    fun `valid Submit clears all previous errors`() =
        runTest {
            val vm = CreateEventViewModel()
            // First: trigger all errors
            vm.handleIntent(CreateEventIntent.Submit)
            assertNotNull(vm.state.first().nameError)
            assertNotNull(vm.state.first().categoryError)
            assertNotNull(vm.state.first().dateError)
            // Then: fill all and submit again
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.handleIntent(CreateEventIntent.Submit)
            val state = vm.state.first()
            assertNull(state.nameError)
            assertNull(state.categoryError)
            assertNull(state.dateError)
        }

    @Test
    fun `invalid Submit does NOT emit effect`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // 6. Submit — Efeito EventSaved (Modo Criacao)
    // =========================================================================

    @Test
    fun `valid Submit emits EventSaved with null eventId`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Reserva Teste"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val effect = awaitItem()
                assertTrue(effect is CreateEventEffect.EventSaved)
                val saved = effect as CreateEventEffect.EventSaved
                assertNull(saved.eventId)
                assertEquals("Reserva Teste", saved.name)
                assertEquals(EventCategory.RESERVA, saved.category)
                assertEquals(20000L, saved.dateEpochDay)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Submit trims name whitespace`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("  Festa  "))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val saved = awaitItem() as CreateEventEffect.EventSaved
                assertEquals("Festa", saved.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Submit with ALUGUEL category emits correct category`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Aluguel Quadra"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.ALUGUEL))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val saved = awaitItem() as CreateEventEffect.EventSaved
                assertEquals(EventCategory.ALUGUEL, saved.category)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Submit with DAY_USE category emits correct category`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Day Use Piscina"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.DAY_USE))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val saved = awaitItem() as CreateEventEffect.EventSaved
                assertEquals(EventCategory.DAY_USE, saved.category)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Submit with blank contact emits null contact`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            // Contact remains empty
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val saved = awaitItem() as CreateEventEffect.EventSaved
                assertNull(saved.contact)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Submit with blank description emits null description`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            // Description remains empty
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val saved = awaitItem() as CreateEventEffect.EventSaved
                assertNull(saved.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Submit applies phone mask to contact`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.handleIntent(CreateEventIntent.ContactChanged("11999998888"))
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val saved = awaitItem() as CreateEventEffect.EventSaved
                assertEquals("(11) 99999-8888", saved.contact)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Submit with description includes it in effect`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.handleIntent(CreateEventIntent.DescriptionChanged("Espaco coberto"))
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val saved = awaitItem() as CreateEventEffect.EventSaved
                assertEquals("Espaco coberto", saved.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Submit with all optional fields includes them in effect`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Aluguel"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.ALUGUEL))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.handleIntent(CreateEventIntent.ContactChanged("11999998888"))
            vm.handleIntent(CreateEventIntent.DescriptionChanged("Espaco coberto"))
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val saved = awaitItem() as CreateEventEffect.EventSaved
                assertEquals("(11) 99999-8888", saved.contact)
                assertEquals("Espaco coberto", saved.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // 7. Modo Edicao — LoadForEdit
    // =========================================================================

    @Test
    fun `LoadForEdit sets edit mode true`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(
                CreateEventIntent.LoadForEdit(
                    eventId = "e1",
                    name = "Festa",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 20010L,
                    contact = null,
                    description = null,
                ),
            )
            assertTrue(vm.state.first().isEditMode)
        }

    @Test
    fun `LoadForEdit stores editing event id`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(
                CreateEventIntent.LoadForEdit(
                    eventId = "e42",
                    name = "Festa",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 20010L,
                    contact = null,
                    description = null,
                ),
            )
            assertEquals("e42", vm.state.first().editingEventId)
        }

    @Test
    fun `LoadForEdit populates all fields`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(
                CreateEventIntent.LoadForEdit(
                    eventId = "e1",
                    name = "Festa Original",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 20010L,
                    contact = "11999998888",
                    description = "Descricao original",
                ),
            )
            val state = vm.state.first()
            assertEquals("Festa Original", state.name)
            assertEquals(EventCategory.RESERVA, state.category)
            assertEquals(20010L, state.dateEpochDay)
            assertEquals("11999998888", state.contact)
            assertEquals("Descricao original", state.description)
        }

    @Test
    fun `LoadForEdit with ALUGUEL category`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(
                CreateEventIntent.LoadForEdit(
                    eventId = "e1",
                    name = "Aluguel",
                    category = EventCategory.ALUGUEL,
                    dateEpochDay = 20010L,
                    contact = null,
                    description = null,
                ),
            )
            assertEquals(EventCategory.ALUGUEL, vm.state.first().category)
        }

    @Test
    fun `LoadForEdit with DAY_USE category`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(
                CreateEventIntent.LoadForEdit(
                    eventId = "e1",
                    name = "Day Use",
                    category = EventCategory.DAY_USE,
                    dateEpochDay = 20010L,
                    contact = null,
                    description = null,
                ),
            )
            assertEquals(EventCategory.DAY_USE, vm.state.first().category)
        }

    @Test
    fun `LoadForEdit with null optional fields sets empty strings`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(
                CreateEventIntent.LoadForEdit(
                    eventId = "e2",
                    name = "Sem Contato",
                    category = EventCategory.ALUGUEL,
                    dateEpochDay = 20010L,
                    contact = null,
                    description = null,
                ),
            )
            val state = vm.state.first()
            assertEquals("", state.contact)
            assertEquals("", state.description)
        }

    @Test
    fun `LoadForEdit strips non-digit chars from contact`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(
                CreateEventIntent.LoadForEdit(
                    eventId = "e1",
                    name = "Festa",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 20010L,
                    contact = "(11) 99999-8888",
                    description = null,
                ),
            )
            assertEquals("11999998888", vm.state.first().contact)
        }

    @Test
    fun `LoadForEdit enables canSubmit`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(
                CreateEventIntent.LoadForEdit(
                    eventId = "e1",
                    name = "Festa",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 20010L,
                    contact = null,
                    description = null,
                ),
            )
            assertTrue(vm.state.first().canSubmit)
        }

    @Test
    fun `LoadForEdit clears previous errors`() =
        runTest {
            val vm = CreateEventViewModel()
            // Trigger errors first
            vm.handleIntent(CreateEventIntent.Submit)
            assertNotNull(vm.state.first().nameError)
            // Load for edit clears them
            vm.handleIntent(
                CreateEventIntent.LoadForEdit(
                    eventId = "e1",
                    name = "Festa",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 20010L,
                    contact = null,
                    description = null,
                ),
            )
            val state = vm.state.first()
            assertNull(state.nameError)
            assertNull(state.categoryError)
            assertNull(state.dateError)
        }

    // =========================================================================
    // 8. Submit — Modo Edicao
    // =========================================================================

    @Test
    fun `Submit in edit mode emits EventSaved with eventId`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(
                CreateEventIntent.LoadForEdit(
                    eventId = "e1",
                    name = "Festa",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 20010L,
                    contact = null,
                    description = null,
                ),
            )
            vm.handleIntent(CreateEventIntent.NameChanged("Festa Atualizada"))
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val saved = awaitItem() as CreateEventEffect.EventSaved
                assertEquals("e1", saved.eventId)
                assertEquals("Festa Atualizada", saved.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Submit in edit mode preserves unchanged fields`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(
                CreateEventIntent.LoadForEdit(
                    eventId = "e1",
                    name = "Festa",
                    category = EventCategory.ALUGUEL,
                    dateEpochDay = 20010L,
                    contact = "11999998888",
                    description = "Descricao",
                ),
            )
            // Only change name
            vm.handleIntent(CreateEventIntent.NameChanged("Festa Editada"))
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val saved = awaitItem() as CreateEventEffect.EventSaved
                assertEquals("e1", saved.eventId)
                assertEquals("Festa Editada", saved.name)
                assertEquals(EventCategory.ALUGUEL, saved.category)
                assertEquals(20010L, saved.dateEpochDay)
                assertEquals("(11) 99999-8888", saved.contact)
                assertEquals("Descricao", saved.description)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `Submit in edit mode can change category`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(
                CreateEventIntent.LoadForEdit(
                    eventId = "e1",
                    name = "Festa",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 20010L,
                    contact = null,
                    description = null,
                ),
            )
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.DAY_USE))
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val saved = awaitItem() as CreateEventEffect.EventSaved
                assertEquals(EventCategory.DAY_USE, saved.category)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // 9. Navegacao
    // =========================================================================

    @Test
    fun `BackClicked emits NavigateBack effect`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.BackClicked)
                assertEquals(CreateEventEffect.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `BackClicked works from edit mode too`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(
                CreateEventIntent.LoadForEdit(
                    eventId = "e1",
                    name = "Festa",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 20010L,
                    contact = null,
                    description = null,
                ),
            )
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.BackClicked)
                assertEquals(CreateEventEffect.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // 10. Phone Mask — applyPhoneMask (funcao interna testada via Submit)
    // =========================================================================

    @Test
    fun `phone mask formats 11 digit number correctly`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.handleIntent(CreateEventIntent.ContactChanged("11999998888"))
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val saved = awaitItem() as CreateEventEffect.EventSaved
                assertEquals("(11) 99999-8888", saved.contact)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `phone mask formats 10 digit number correctly`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.handleIntent(CreateEventIntent.ContactChanged("1199998888"))
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val saved = awaitItem() as CreateEventEffect.EventSaved
                assertEquals("(11) 9999-8888", saved.contact)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `empty contact produces null in effect`() =
        runTest {
            val vm = CreateEventViewModel()
            vm.handleIntent(CreateEventIntent.NameChanged("Festa"))
            vm.handleIntent(CreateEventIntent.CategoryChanged(EventCategory.RESERVA))
            vm.handleIntent(CreateEventIntent.DateChanged(20000L))
            vm.handleIntent(CreateEventIntent.ContactChanged(""))
            vm.effects.test {
                vm.handleIntent(CreateEventIntent.Submit)
                val saved = awaitItem() as CreateEventEffect.EventSaved
                assertNull(saved.contact)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
