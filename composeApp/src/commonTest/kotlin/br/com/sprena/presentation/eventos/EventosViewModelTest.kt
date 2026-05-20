package br.com.sprena.presentation.eventos

import app.cash.turbine.test
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
 * TDD — Testes da tela Eventos (refatorada).
 *
 * Requisitos cobertos:
 * - 2 tabs: "Eventos" (futuros, ascendente) e "Eventos Realizados" (passados, descendente)
 * - 3 categorias: Reserva, Aluguel, Day Use
 * - Contador de eventos na tab "Eventos"
 * - Filtro por categoria (dropdown: Todos, Reserva, Aluguel, Day Use)
 * - Busca por nome DENTRO da tab ativa
 * - Navegador de mes (mantido)
 * - Sem date picker
 * - Cores: Aluguel=#6959CD, Reserva=#9370DB, DayUse=#EE82EE, completed=#FF0000
 *   (cores testadas apenas implicitamente — sao constantes no Screen)
 */
class EventosViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest
    fun setUp() = env.install()

    @AfterTest
    fun tearDown() = env.uninstall()

    // =========================================================================
    // 1. Estado Inicial
    // =========================================================================

    @Test
    fun `initial state has Eventos as selected tab`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            assertEquals(EventTab.EVENTOS, vm.state.first().selectedTab)
        }

    @Test
    fun `initial state has exactly two tabs`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            val tabs = vm.state.first().tabs
            assertEquals(2, tabs.size)
            assertEquals(EventTab.EVENTOS, tabs[0])
            assertEquals(EventTab.EVENTOS_REALIZADOS, tabs[1])
        }

    @Test
    fun `initial state has no events`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            assertTrue(
                vm.state
                    .first()
                    .events
                    .isEmpty(),
            )
        }

    @Test
    fun `initial state has empty filtered events`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .isEmpty(),
            )
        }

    @Test
    fun `initial state has empty search query`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            assertEquals("", vm.state.first().searchQuery)
        }

    @Test
    fun `initial state has search inactive`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            assertFalse(vm.state.first().isSearchActive)
        }

    @Test
    fun `initial state has no category filter (Todos)`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            assertNull(vm.state.first().categoryFilter)
        }

    @Test
    fun `initial state has zero event count`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            assertEquals(0, vm.state.first().eventCount)
        }

    @Test
    fun `initial state stores todayEpochDay`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            assertEquals(20000L, vm.state.first().todayEpochDay)
        }

    @Test
    fun `initial state is not loading`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            assertFalse(vm.state.first().isLoading)
        }

    @Test
    fun `initial state has no error`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            assertNull(vm.state.first().error)
        }

    @Test
    fun `initial state computes month and year from todayEpochDay`() =
        runTest {
            // 20000 epoch days = 2024-10-04
            val vm = EventosViewModel(todayEpochDay = 20000L)
            val state = vm.state.first()
            assertEquals(10, state.filterMonth)
            assertEquals(2024, state.filterYear)
        }

    // =========================================================================
    // 2. Estrutura de Tabs — Selecao
    // =========================================================================

    @Test
    fun `TabSelected changes to Eventos Realizados`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            assertEquals(EventTab.EVENTOS_REALIZADOS, vm.state.first().selectedTab)
        }

    @Test
    fun `TabSelected back to Eventos works`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS))
            assertEquals(EventTab.EVENTOS, vm.state.first().selectedTab)
        }

    @Test
    fun `switching tab recomputes filtered events`() =
        runTest {
            val vm = createVmWithMixedEvents()
            val eventosCount =
                vm.state
                    .first()
                    .filteredEvents.size
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            val realizadosCount =
                vm.state
                    .first()
                    .filteredEvents.size
            // Both tabs should have events (our helper creates mixed data)
            assertTrue(eventosCount > 0)
            assertTrue(realizadosCount > 0)
        }

    // =========================================================================
    // 3. Tab "Eventos" — Exibicao e Ordenacao
    // =========================================================================

    @Test
    fun `Eventos tab shows only future events when dateEpochDay is today or later`() =
        runTest {
            val vm = createVmWithMixedEvents()
            val filtered = vm.state.first().filteredEvents
            assertTrue(filtered.isNotEmpty())
            assertTrue(filtered.all { it.dateEpochDay >= 20000L })
        }

    @Test
    fun `Eventos tab does NOT include past events`() =
        runTest {
            val vm = createVmWithMixedEvents()
            val filtered = vm.state.first().filteredEvents
            assertFalse(filtered.any { it.dateEpochDay < 20000L })
        }

    @Test
    fun `Eventos tab events are sorted ascending by date (nearest first)`() =
        runTest {
            val vm = createVmWithMixedEvents()
            val dates =
                vm.state
                    .first()
                    .filteredEvents
                    .map { it.dateEpochDay }
            assertTrue(dates.size > 1, "Need multiple events to verify sort")
            assertEquals(dates.sorted(), dates)
        }

    @Test
    fun `Eventos tab includes event happening today (dateEpochDay == today)`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Hoje", EventCategory.RESERVA, 20000L),
                        Event("e2", "Ontem", EventCategory.RESERVA, 19999L),
                    ),
                ),
            )
            val filtered = vm.state.first().filteredEvents
            assertEquals(1, filtered.size)
            assertEquals("e1", filtered[0].id)
        }

    @Test
    fun `Eventos tab shows events from all categories when no filter`() =
        runTest {
            val vm = createVmWithMixedEvents()
            val categories =
                vm.state
                    .first()
                    .filteredEvents
                    .map { it.category }
                    .toSet()
            assertTrue(categories.size > 1, "Multiple categories should be visible")
        }

    // =========================================================================
    // 4. Tab "Eventos Realizados" — Exibicao e Ordenacao
    // =========================================================================

    @Test
    fun `Eventos Realizados tab shows only past events (dateEpochDay less than today)`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            val filtered = vm.state.first().filteredEvents
            assertTrue(filtered.isNotEmpty())
            assertTrue(filtered.all { it.dateEpochDay < 20000L })
        }

    @Test
    fun `Eventos Realizados tab does NOT include future events`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            val filtered = vm.state.first().filteredEvents
            assertFalse(filtered.any { it.dateEpochDay >= 20000L })
        }

    @Test
    fun `Eventos Realizados tab events are sorted descending by date (most recent first)`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            val dates =
                vm.state
                    .first()
                    .filteredEvents
                    .map { it.dateEpochDay }
            assertTrue(dates.size > 1, "Need multiple events to verify sort")
            assertEquals(dates.sortedDescending(), dates)
        }

    @Test
    fun `Eventos Realizados tab does NOT include event happening today`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Hoje", EventCategory.RESERVA, 20000L),
                        Event("e2", "Ontem", EventCategory.RESERVA, 19999L),
                    ),
                ),
            )
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            val filtered = vm.state.first().filteredEvents
            assertEquals(1, filtered.size)
            assertEquals("e2", filtered[0].id)
        }

    @Test
    fun `Eventos Realizados shows events from all categories when no filter`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            val categories =
                vm.state
                    .first()
                    .filteredEvents
                    .map { it.category }
                    .toSet()
            assertTrue(categories.size > 1, "Multiple categories should be visible in Realizados")
        }

    // =========================================================================
    // 5. Contador de Eventos (badge na tab Eventos)
    // =========================================================================

    @Test
    fun `eventCount counts all future events regardless of filters`() =
        runTest {
            val vm = createVmWithMixedEvents()
            val allFuture =
                vm.state
                    .first()
                    .events
                    .count { it.dateEpochDay >= 20000L }
            assertEquals(allFuture, vm.state.first().eventCount)
        }

    @Test
    fun `eventCount excludes past events`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20010L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Passado", EventCategory.RESERVA, 20005L),
                        Event("e2", "Futuro1", EventCategory.ALUGUEL, 20020L),
                        Event("e3", "Futuro2", EventCategory.DAY_USE, 20030L),
                    ),
                ),
            )
            assertEquals(2, vm.state.first().eventCount)
        }

    @Test
    fun `eventCount includes event happening today`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Hoje", EventCategory.RESERVA, 20000L),
                    ),
                ),
            )
            assertEquals(1, vm.state.first().eventCount)
        }

    @Test
    fun `eventCount is zero when all events are in the past`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20010L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Passado1", EventCategory.RESERVA, 20005L),
                        Event("e2", "Passado2", EventCategory.ALUGUEL, 20003L),
                    ),
                ),
            )
            assertEquals(0, vm.state.first().eventCount)
        }

    @Test
    fun `eventCount is not affected by category filter`() =
        runTest {
            val vm = createVmWithMixedEvents()
            val countBefore = vm.state.first().eventCount
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.ALUGUEL))
            val countAfter = vm.state.first().eventCount
            assertEquals(countBefore, countAfter)
        }

    @Test
    fun `eventCount is not affected by search query`() =
        runTest {
            val vm = createVmWithMixedEvents()
            val countBefore = vm.state.first().eventCount
            vm.handleIntent(EventosIntent.SearchQueryChanged("Pedro"))
            val countAfter = vm.state.first().eventCount
            assertEquals(countBefore, countAfter)
        }

    @Test
    fun `eventCount is not affected by tab selection`() =
        runTest {
            val vm = createVmWithMixedEvents()
            val countOnEventos = vm.state.first().eventCount
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            val countOnRealizados = vm.state.first().eventCount
            assertEquals(countOnEventos, countOnRealizados)
        }

    @Test
    fun `eventCount updates when event is created`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            assertEquals(0, vm.state.first().eventCount)
            vm.handleIntent(
                EventosIntent.EventCreated(
                    name = "Novo",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 20010L,
                    contact = null,
                    description = null,
                ),
            )
            assertEquals(1, vm.state.first().eventCount)
        }

    @Test
    fun `eventCount updates when event is deleted`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Futuro", EventCategory.RESERVA, 20010L),
                    ),
                ),
            )
            assertEquals(1, vm.state.first().eventCount)
            vm.handleIntent(EventosIntent.EventDeleted("e1"))
            assertEquals(0, vm.state.first().eventCount)
        }

    // =========================================================================
    // 6. Filtro de Categoria (Dropdown)
    // =========================================================================

    @Test
    fun `CategoryFilterChanged to RESERVA sets filter`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.RESERVA))
            assertEquals(EventCategory.RESERVA, vm.state.first().categoryFilter)
        }

    @Test
    fun `CategoryFilterChanged to ALUGUEL sets filter`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.ALUGUEL))
            assertEquals(EventCategory.ALUGUEL, vm.state.first().categoryFilter)
        }

    @Test
    fun `CategoryFilterChanged to DAY_USE sets filter`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.DAY_USE))
            assertEquals(EventCategory.DAY_USE, vm.state.first().categoryFilter)
        }

    @Test
    fun `CategoryFilterChanged to null resets to Todos`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.ALUGUEL))
            vm.handleIntent(EventosIntent.CategoryFilterChanged(null))
            assertNull(vm.state.first().categoryFilter)
        }

    @Test
    fun `category filter RESERVA shows only Reserva events in Eventos tab`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.RESERVA))
            val filtered = vm.state.first().filteredEvents
            assertTrue(filtered.isNotEmpty())
            assertTrue(filtered.all { it.category == EventCategory.RESERVA })
            assertTrue(filtered.all { it.dateEpochDay >= 20000L })
        }

    @Test
    fun `category filter ALUGUEL shows only Aluguel events in Eventos tab`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.ALUGUEL))
            val filtered = vm.state.first().filteredEvents
            assertTrue(filtered.isNotEmpty())
            assertTrue(filtered.all { it.category == EventCategory.ALUGUEL })
        }

    @Test
    fun `category filter DAY_USE shows only Day Use events in Eventos tab`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.DAY_USE))
            val filtered = vm.state.first().filteredEvents
            assertTrue(filtered.isNotEmpty())
            assertTrue(filtered.all { it.category == EventCategory.DAY_USE })
        }

    @Test
    fun `category filter works on Eventos Realizados tab`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.RESERVA))
            val filtered = vm.state.first().filteredEvents
            assertTrue(filtered.isNotEmpty())
            assertTrue(filtered.all { it.category == EventCategory.RESERVA })
            assertTrue(filtered.all { it.dateEpochDay < 20000L })
        }

    @Test
    fun `category filter ALUGUEL on Realizados tab shows only past Aluguel events`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.ALUGUEL))
            val filtered = vm.state.first().filteredEvents
            assertTrue(filtered.all { it.category == EventCategory.ALUGUEL })
            assertTrue(filtered.all { it.dateEpochDay < 20000L })
        }

    @Test
    fun `removing category filter shows more events`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.ALUGUEL))
            val countWithFilter =
                vm.state
                    .first()
                    .filteredEvents.size
            vm.handleIntent(EventosIntent.CategoryFilterChanged(null))
            val countWithoutFilter =
                vm.state
                    .first()
                    .filteredEvents.size
            assertTrue(countWithoutFilter >= countWithFilter)
        }

    @Test
    fun `category filter with no matching events returns empty`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Reserva", EventCategory.RESERVA, 20010L),
                    ),
                ),
            )
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.DAY_USE))
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .isEmpty(),
            )
        }

    @Test
    fun `category filter persists across tab switches`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.ALUGUEL))
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            assertEquals(EventCategory.ALUGUEL, vm.state.first().categoryFilter)
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .all { it.category == EventCategory.ALUGUEL },
            )
        }

    // =========================================================================
    // 7. Busca — dentro da tab ativa
    // =========================================================================

    @Test
    fun `SearchQueryChanged updates searchQuery`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(EventosIntent.SearchQueryChanged("festa"))
            assertEquals("festa", vm.state.first().searchQuery)
        }

    @Test
    fun `search activates isSearchActive`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(EventosIntent.SearchQueryChanged("algo"))
            assertTrue(vm.state.first().isSearchActive)
        }

    @Test
    fun `search filters by name within Eventos tab (only future events)`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.SearchQueryChanged("Pedro"))
            val results = vm.state.first().filteredEvents
            assertTrue(results.isNotEmpty())
            assertTrue(results.all { it.name.contains("Pedro", ignoreCase = true) })
            assertTrue(results.all { it.dateEpochDay >= 20000L })
        }

    @Test
    fun `search does NOT show past events when on Eventos tab`() =
        runTest {
            val vm = createVmWithMixedEvents()
            // "Passado" exists in past events but should not appear in Eventos tab search
            vm.handleIntent(EventosIntent.SearchQueryChanged("Passado"))
            val results = vm.state.first().filteredEvents
            assertTrue(results.isEmpty(), "Past events should not appear in Eventos tab search")
        }

    @Test
    fun `search on Eventos Realizados tab filters within past events only`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            vm.handleIntent(EventosIntent.SearchQueryChanged("Passado"))
            val results = vm.state.first().filteredEvents
            assertTrue(results.isNotEmpty())
            assertTrue(results.all { it.dateEpochDay < 20000L })
            assertTrue(results.all { it.name.contains("Passado", ignoreCase = true) })
        }

    @Test
    fun `search on Realizados tab does NOT show future events`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            vm.handleIntent(EventosIntent.SearchQueryChanged("Churrasqueira"))
            val results = vm.state.first().filteredEvents
            // "Day Use Churrasqueira" is a FUTURE event — should NOT appear
            assertTrue(results.isEmpty())
        }

    @Test
    fun `search is case insensitive`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.SearchQueryChanged("pedro"))
            val results = vm.state.first().filteredEvents
            assertTrue(results.any { it.name.contains("Pedro") })
        }

    @Test
    fun `search with no match returns empty`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.SearchQueryChanged("xyz_nao_existe"))
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .isEmpty(),
            )
        }

    @Test
    fun `clearing search returns to full tab view`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.SearchQueryChanged("Pedro"))
            assertTrue(vm.state.first().isSearchActive)
            vm.handleIntent(EventosIntent.SearchQueryChanged(""))
            assertFalse(vm.state.first().isSearchActive)
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents.size > 1,
            )
        }

    @Test
    fun `search trims whitespace`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.SearchQueryChanged("  Pedro  "))
            val results = vm.state.first().filteredEvents
            assertTrue(results.isNotEmpty())
            assertTrue(results.all { it.name.contains("Pedro", ignoreCase = true) })
        }

    @Test
    fun `whitespace-only search is treated as inactive`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.SearchQueryChanged("   "))
            assertFalse(vm.state.first().isSearchActive)
        }

    // =========================================================================
    // 8. Navegador de Mes
    // =========================================================================

    @Test
    fun `MonthNavigatedForward advances month`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L) // Oct 2024
            vm.handleIntent(EventosIntent.MonthNavigatedForward)
            val state = vm.state.first()
            assertEquals(11, state.filterMonth)
            assertEquals(2024, state.filterYear)
        }

    @Test
    fun `MonthNavigatedForward wraps December to January next year`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L) // Oct 2024
            repeat(3) { vm.handleIntent(EventosIntent.MonthNavigatedForward) } // Nov, Dec, Jan
            val state = vm.state.first()
            assertEquals(1, state.filterMonth)
            assertEquals(2025, state.filterYear)
        }

    @Test
    fun `MonthNavigatedBack goes to previous month`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L) // Oct 2024
            vm.handleIntent(EventosIntent.MonthNavigatedBack)
            val state = vm.state.first()
            assertEquals(9, state.filterMonth)
            assertEquals(2024, state.filterYear)
        }

    @Test
    fun `MonthNavigatedBack wraps January to December previous year`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L) // Oct 2024
            repeat(10) { vm.handleIntent(EventosIntent.MonthNavigatedBack) } // back to Dec 2023
            val state = vm.state.first()
            assertEquals(12, state.filterMonth)
            assertEquals(2023, state.filterYear)
        }

    @Test
    fun `month filter shows only events in the selected month`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L) // Oct 2024
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Oct Event", EventCategory.RESERVA, 20000L), // Oct 4, 2024
                        Event("e2", "Nov Event", EventCategory.RESERVA, 20040L), // Nov 13, 2024
                    ),
                ),
            )
            val filtered = vm.state.first().filteredEvents
            assertEquals(1, filtered.size)
            assertEquals("e1", filtered[0].id)
        }

    @Test
    fun `navigating to different month shows events of that month`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L) // Oct 2024
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Oct Event", EventCategory.RESERVA, 20000L), // Oct 4, 2024
                        Event("e2", "Nov Event", EventCategory.RESERVA, 20040L), // Nov 13, 2024
                    ),
                ),
            )
            vm.handleIntent(EventosIntent.MonthNavigatedForward) // → Nov 2024
            val filtered = vm.state.first().filteredEvents
            assertEquals(1, filtered.size)
            assertEquals("e2", filtered[0].id)
        }

    @Test
    fun `month filter applies to Realizados tab too`() =
        runTest {
            // Use todayEpochDay = 20050L so all events are in the past
            val vm = EventosViewModel(todayEpochDay = 20050L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Oct Event", EventCategory.RESERVA, 20000L), // Oct 4, 2024
                        Event("e2", "Nov Event", EventCategory.RESERVA, 20040L), // Nov 13, 2024
                    ),
                ),
            )
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            // filterMonth defaults to month of todayEpochDay (20050 → Nov 2024)
            val filtered = vm.state.first().filteredEvents
            assertEquals(1, filtered.size)
            assertEquals("e2", filtered[0].id)
        }

    @Test
    fun `month filter with no events returns empty`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L) // Oct 2024
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Oct Event", EventCategory.RESERVA, 20000L),
                    ),
                ),
            )
            // Navigate to a month with no events
            vm.handleIntent(EventosIntent.MonthNavigatedForward) // Nov
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .isEmpty(),
            )
        }

    // =========================================================================
    // 9. Filtros Combinados (Tab + Categoria + Mes + Busca)
    // =========================================================================

    @Test
    fun `category filter combined with search narrows results`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.ALUGUEL))
            vm.handleIntent(EventosIntent.SearchQueryChanged("Quadra"))
            val filtered = vm.state.first().filteredEvents
            assertTrue(filtered.isNotEmpty())
            assertTrue(filtered.all { it.category == EventCategory.ALUGUEL })
            assertTrue(filtered.all { it.name.contains("Quadra", ignoreCase = true) })
        }

    @Test
    fun `category filter combined with month filter`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L) // Oct 2024
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Oct Reserva", EventCategory.RESERVA, 20000L),
                        Event("e2", "Oct Aluguel", EventCategory.ALUGUEL, 20002L),
                        Event("e3", "Nov Reserva", EventCategory.RESERVA, 20040L),
                    ),
                ),
            )
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.RESERVA))
            // Month = Oct → only e1 (Oct Reserva)
            val filtered = vm.state.first().filteredEvents
            assertEquals(1, filtered.size)
            assertEquals("e1", filtered[0].id)
        }

    @Test
    fun `search plus category plus tab all intersect`() =
        runTest {
            val vm = createVmWithMixedEvents()
            // Tab = Eventos (future), Category = ALUGUEL, Search = "Quadra"
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.ALUGUEL))
            vm.handleIntent(EventosIntent.SearchQueryChanged("Quadra"))
            val filtered = vm.state.first().filteredEvents
            assertTrue(filtered.all { it.category == EventCategory.ALUGUEL })
            assertTrue(filtered.all { it.name.contains("Quadra", ignoreCase = true) })
            assertTrue(filtered.all { it.dateEpochDay >= 20000L })
        }

    @Test
    fun `all filters combined on Realizados tab`() =
        runTest {
            val vm = createVmWithMixedEvents()
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            vm.handleIntent(EventosIntent.CategoryFilterChanged(EventCategory.RESERVA))
            vm.handleIntent(EventosIntent.SearchQueryChanged("Passado"))
            val filtered = vm.state.first().filteredEvents
            assertTrue(filtered.all { it.dateEpochDay < 20000L })
            assertTrue(filtered.all { it.category == EventCategory.RESERVA })
            assertTrue(filtered.all { it.name.contains("Passado", ignoreCase = true) })
        }

    // =========================================================================
    // 10. Criacao de Evento
    // =========================================================================

    @Test
    fun `EventCreated adds event to events list`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.EventCreated(
                    name = "Festa de Natal",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 20010L,
                    contact = null,
                    description = null,
                ),
            )
            assertEquals(
                1,
                vm.state
                    .first()
                    .events.size,
            )
            assertEquals(
                "Festa de Natal",
                vm.state
                    .first()
                    .events[0]
                    .name,
            )
        }

    @Test
    fun `EventCreated assigns unique id`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.EventCreated(
                    name = "Evento 1",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 20010L,
                    contact = null,
                    description = null,
                ),
            )
            vm.handleIntent(
                EventosIntent.EventCreated(
                    name = "Evento 2",
                    category = EventCategory.ALUGUEL,
                    dateEpochDay = 20020L,
                    contact = null,
                    description = null,
                ),
            )
            val ids =
                vm.state
                    .first()
                    .events
                    .map { it.id }
                    .toSet()
            assertEquals(2, ids.size, "Each event should have a unique id")
        }

    @Test
    fun `EventCreated with all optional fields stores them`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.EventCreated(
                    name = "Aluguel Quadra",
                    category = EventCategory.ALUGUEL,
                    dateEpochDay = 20010L,
                    contact = "11999998888",
                    description = "Quadra poliesportiva",
                ),
            )
            val event =
                vm.state
                    .first()
                    .events
                    .first()
            assertEquals(EventCategory.ALUGUEL, event.category)
            assertEquals("11999998888", event.contact)
            assertEquals("Quadra poliesportiva", event.description)
        }

    @Test
    fun `future created event appears in Eventos tab filtered events`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.EventCreated(
                    name = "Day Use Piscina",
                    category = EventCategory.DAY_USE,
                    dateEpochDay = 20010L,
                    contact = null,
                    description = null,
                ),
            )
            val filtered = vm.state.first().filteredEvents
            assertEquals(1, filtered.size)
            assertEquals("Day Use Piscina", filtered[0].name)
        }

    @Test
    fun `past created event does NOT appear in Eventos tab`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20010L)
            vm.handleIntent(
                EventosIntent.EventCreated(
                    name = "Evento Passado",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 20005L,
                    contact = null,
                    description = null,
                ),
            )
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .isEmpty(),
            )
        }

    @Test
    fun `past created event appears in Eventos Realizados tab`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20010L)
            vm.handleIntent(
                EventosIntent.EventCreated(
                    name = "Evento Passado",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 20005L,
                    contact = null,
                    description = null,
                ),
            )
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            val realizados = vm.state.first().filteredEvents
            assertEquals(1, realizados.size)
            assertEquals("Evento Passado", realizados[0].name)
        }

    @Test
    fun `created event category is preserved (no automatic reclassification)`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20010L)
            vm.handleIntent(
                EventosIntent.EventCreated(
                    name = "Aluguel Passado",
                    category = EventCategory.ALUGUEL,
                    dateEpochDay = 20005L,
                    contact = null,
                    description = null,
                ),
            )
            val event =
                vm.state
                    .first()
                    .events
                    .first()
            assertEquals(EventCategory.ALUGUEL, event.category)
        }

    // =========================================================================
    // 11. Edicao de Evento
    // =========================================================================

    @Test
    fun `EventUpdated modifies existing event fields`() =
        runTest {
            val vm = createVmWithMixedEvents()
            val original =
                vm.state
                    .first()
                    .events
                    .first()
            vm.handleIntent(
                EventosIntent.EventUpdated(
                    eventId = original.id,
                    name = "Nome Atualizado",
                    category = original.category,
                    dateEpochDay = original.dateEpochDay,
                    contact = "11000001111",
                    description = "Descricao atualizada",
                ),
            )
            val updated =
                vm.state
                    .first()
                    .events
                    .find { it.id == original.id }
            assertNotNull(updated)
            assertEquals("Nome Atualizado", updated.name)
            assertEquals("11000001111", updated.contact)
            assertEquals("Descricao atualizada", updated.description)
        }

    @Test
    fun `EventUpdated can change category`() =
        runTest {
            val vm = createVmWithMixedEvents()
            val original =
                vm.state
                    .first()
                    .events
                    .first { it.category == EventCategory.RESERVA }
            vm.handleIntent(
                EventosIntent.EventUpdated(
                    eventId = original.id,
                    name = original.name,
                    category = EventCategory.DAY_USE,
                    dateEpochDay = original.dateEpochDay,
                    contact = original.contact,
                    description = original.description,
                ),
            )
            val updated =
                vm.state
                    .first()
                    .events
                    .find { it.id == original.id }
            assertNotNull(updated)
            assertEquals(EventCategory.DAY_USE, updated.category)
        }

    @Test
    fun `EventUpdated can change date causing tab switch`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Futuro", EventCategory.RESERVA, 20010L),
                    ),
                ),
            )
            // Event is visible in Eventos tab
            assertEquals(
                1,
                vm.state
                    .first()
                    .filteredEvents.size,
            )

            // Move to past date (kept in Oct 2024 to stay within filterMonth=10)
            vm.handleIntent(
                EventosIntent.EventUpdated(
                    eventId = "e1",
                    name = "Agora Passado",
                    category = EventCategory.RESERVA,
                    dateEpochDay = 19998L,
                    contact = null,
                    description = null,
                ),
            )
            // Should no longer be in Eventos tab
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .isEmpty(),
            )

            // But should be in Realizados tab
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            assertEquals(
                1,
                vm.state
                    .first()
                    .filteredEvents.size,
            )
        }

    @Test
    fun `EventUpdated does not affect other events`() =
        runTest {
            val vm = createVmWithMixedEvents()
            val events = vm.state.first().events
            val target = events.first()
            val other = events[1]

            vm.handleIntent(
                EventosIntent.EventUpdated(
                    eventId = target.id,
                    name = "Modificado",
                    category = target.category,
                    dateEpochDay = target.dateEpochDay,
                    contact = target.contact,
                    description = target.description,
                ),
            )
            val otherAfter =
                vm.state
                    .first()
                    .events
                    .find { it.id == other.id }
            assertEquals(other, otherAfter)
        }

    // =========================================================================
    // 12. Exclusao de Evento
    // =========================================================================

    @Test
    fun `EventDeleted removes event from list`() =
        runTest {
            val vm = createVmWithMixedEvents()
            val first =
                vm.state
                    .first()
                    .events
                    .first()
            vm.handleIntent(EventosIntent.EventDeleted(first.id))
            assertFalse(
                vm.state
                    .first()
                    .events
                    .any { it.id == first.id },
            )
        }

    @Test
    fun `EventDeleted updates filtered events`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Unico", EventCategory.RESERVA, 20010L),
                    ),
                ),
            )
            assertEquals(
                1,
                vm.state
                    .first()
                    .filteredEvents.size,
            )
            vm.handleIntent(EventosIntent.EventDeleted("e1"))
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .isEmpty(),
            )
        }

    @Test
    fun `EventDeleted with nonexistent id does nothing`() =
        runTest {
            val vm = createVmWithMixedEvents()
            val countBefore =
                vm.state
                    .first()
                    .events.size
            vm.handleIntent(EventosIntent.EventDeleted("nao_existe"))
            assertEquals(
                countBefore,
                vm.state
                    .first()
                    .events.size,
            )
        }

    // =========================================================================
    // 13. SetTodayEpochDay — reclassifica dinamicamente
    // =========================================================================

    @Test
    fun `SetTodayEpochDay moves future event to Realizados when time passes`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Soon", EventCategory.RESERVA, 20005L),
                    ),
                ),
            )
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .any { it.id == "e1" },
            )

            vm.handleIntent(EventosIntent.SetTodayEpochDay(20010L))
            // Gone from Eventos
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .isEmpty(),
            )

            // Appears in Realizados
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .any { it.id == "e1" },
            )
        }

    @Test
    fun `SetTodayEpochDay updates event count`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Near", EventCategory.RESERVA, 20005L),
                        Event("e2", "Far", EventCategory.RESERVA, 20020L),
                    ),
                ),
            )
            assertEquals(2, vm.state.first().eventCount)
            vm.handleIntent(EventosIntent.SetTodayEpochDay(20010L))
            assertEquals(1, vm.state.first().eventCount)
        }

    @Test
    fun `SetTodayEpochDay preserves event category`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Aluguel", EventCategory.ALUGUEL, 20005L),
                    ),
                ),
            )
            vm.handleIntent(EventosIntent.SetTodayEpochDay(20010L))
            val event =
                vm.state
                    .first()
                    .events
                    .first()
            assertEquals(EventCategory.ALUGUEL, event.category)
        }

    // =========================================================================
    // 14. Efeitos (Navigation + Error)
    // =========================================================================

    @Test
    fun `AddEventClicked emits NavigateToCreateEvent`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.effects.test {
                vm.handleIntent(EventosIntent.AddEventClicked)
                assertEquals(EventosEffect.NavigateToCreateEvent, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `EventClicked emits NavigateToEditEvent with correct event`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            val event = Event("e1", "Test", EventCategory.RESERVA, 20010L)
            vm.effects.test {
                vm.handleIntent(EventosIntent.EventClicked(event))
                val effect = awaitItem()
                assertTrue(effect is EventosEffect.NavigateToEditEvent)
                assertEquals(event, (effect as EventosEffect.NavigateToEditEvent).event)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ErrorOccurred emits ShowError effect`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.effects.test {
                vm.handleIntent(EventosIntent.ErrorOccurred("Erro de rede"))
                val effect = awaitItem()
                assertTrue(effect is EventosEffect.ShowError)
                assertEquals("Erro de rede", (effect as EventosEffect.ShowError).message)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // =========================================================================
    // 15. Edge Cases
    // =========================================================================

    @Test
    fun `empty events list shows empty filtered on both tabs`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .isEmpty(),
            )
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .isEmpty(),
            )
        }

    @Test
    fun `all events in past means Eventos tab is empty`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20050L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "A", EventCategory.RESERVA, 20000L),
                        Event("e2", "B", EventCategory.ALUGUEL, 20010L),
                    ),
                ),
            )
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .isEmpty(),
            )
        }

    @Test
    fun `all events in future means Realizados tab is empty`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 19990L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "A", EventCategory.RESERVA, 20000L),
                        Event("e2", "B", EventCategory.ALUGUEL, 20010L),
                    ),
                ),
            )
            vm.handleIntent(EventosIntent.TabSelected(EventTab.EVENTOS_REALIZADOS))
            assertTrue(
                vm.state
                    .first()
                    .filteredEvents
                    .isEmpty(),
            )
        }

    @Test
    fun `AddTestEvents replaces existing events`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(Event("e1", "First", EventCategory.RESERVA, 20010L)),
                ),
            )
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(Event("e2", "Second", EventCategory.ALUGUEL, 20020L)),
                ),
            )
            val events = vm.state.first().events
            assertEquals(1, events.size)
            assertEquals("e2", events[0].id)
        }

    @Test
    fun `multiple events on same date are both shown`() =
        runTest {
            val vm = EventosViewModel(todayEpochDay = 20000L)
            vm.handleIntent(
                EventosIntent.AddTestEvents(
                    listOf(
                        Event("e1", "Evento A", EventCategory.RESERVA, 20010L),
                        Event("e2", "Evento B", EventCategory.ALUGUEL, 20010L),
                    ),
                ),
            )
            assertEquals(
                2,
                vm.state
                    .first()
                    .filteredEvents.size,
            )
        }

    // =========================================================================
    // Helper — cria VM com eventos mistos (futuros e passados, 3 categorias)
    // =========================================================================

    /**
     * Creates a ViewModel with todayEpochDay = 20000 (Oct 4, 2024)
     * and 9 test events:
     * - 6 future: 2x RESERVA, 2x ALUGUEL, 2x DAY_USE (all in Oct 2024)
     * - 3 past: 1x RESERVA, 1x ALUGUEL, 1x DAY_USE (all in Oct 2024, before todayEpochDay=20000)
     */
    private fun createVmWithMixedEvents(): EventosViewModel {
        val vm = EventosViewModel(todayEpochDay = 20000L)
        vm.handleIntent(
            EventosIntent.AddTestEvents(
                listOf(
                    // Future events (Eventos tab) — all in Oct 2024
                    Event(
                        id = "e1",
                        name = "Aniversario do Pedro",
                        category = EventCategory.RESERVA,
                        dateEpochDay = 20010L,
                        contact = "11999998888",
                        description = "Festa surpresa",
                    ),
                    Event(
                        id = "e2",
                        name = "Casamento Maria e Joao",
                        category = EventCategory.RESERVA,
                        dateEpochDay = 20005L,
                        contact = null,
                        description = null,
                    ),
                    Event(
                        id = "e3",
                        name = "Aluguel Quadra Pedro",
                        category = EventCategory.ALUGUEL,
                        dateEpochDay = 20015L,
                        contact = "11888887777",
                        description = "Quadra esportiva",
                    ),
                    Event(
                        id = "e4",
                        name = "Aluguel Salao de Festas",
                        category = EventCategory.ALUGUEL,
                        dateEpochDay = 20020L,
                        contact = null,
                        description = null,
                    ),
                    Event(
                        id = "e5",
                        name = "Day Use Piscina Pedro",
                        category = EventCategory.DAY_USE,
                        dateEpochDay = 20003L,
                        contact = "11777776666",
                        description = null,
                    ),
                    Event(
                        id = "e6",
                        name = "Day Use Churrasqueira",
                        category = EventCategory.DAY_USE,
                        dateEpochDay = 20025L,
                        contact = null,
                        description = "Inclui area coberta",
                    ),
                    // Past events (Eventos Realizados tab) — all in Oct 2024 (matching todayEpochDay=20000 filter month)
                    Event(
                        id = "e7",
                        name = "Reserva Passado 1",
                        category = EventCategory.RESERVA,
                        dateEpochDay = 19997L,
                        contact = null,
                        description = null,
                    ),
                    Event(
                        id = "e8",
                        name = "Aluguel Passado Quadra",
                        category = EventCategory.ALUGUEL,
                        dateEpochDay = 19998L,
                        contact = null,
                        description = null,
                    ),
                    Event(
                        id = "e9",
                        name = "Day Use Passado",
                        category = EventCategory.DAY_USE,
                        dateEpochDay = 19999L,
                        contact = null,
                        description = null,
                    ),
                ),
            ),
        )
        return vm
    }
}
