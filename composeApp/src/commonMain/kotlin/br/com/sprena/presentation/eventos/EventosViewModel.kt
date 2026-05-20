package br.com.sprena.presentation.eventos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.core.mvi.MviViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EventosViewModel(
    todayEpochDay: Long = System.currentTimeMillis() / 86_400_000L,
) : ViewModel(), MviViewModel<EventosState, EventosIntent, EventosEffect> {

    private var eventIdCounter = 0L

    private val _state: MutableStateFlow<EventosState>

    init {
        val (year, month) = yearMonthFromEpochDay(todayEpochDay)
        _state = MutableStateFlow(
            EventosState(
                todayEpochDay = todayEpochDay,
                filterMonth = month,
                filterYear = year,
            ),
        )
    }
    override val state: StateFlow<EventosState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<EventosEffect>()
    override val effects: SharedFlow<EventosEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: EventosIntent) {
        when (intent) {
            is EventosIntent.TabSelected -> {
                _state.value = _state.value.copy(selectedTab = intent.tab)
                recomputeFiltered()
            }

            is EventosIntent.SearchQueryChanged -> {
                _state.value = _state.value.copy(searchQuery = intent.query)
                recomputeFiltered()
            }

            is EventosIntent.CategoryFilterChanged -> {
                _state.value = _state.value.copy(categoryFilter = intent.category)
                recomputeFiltered()
            }

            is EventosIntent.AddEventClicked -> {
                viewModelScope.launch {
                    _effects.emit(EventosEffect.NavigateToCreateEvent)
                }
            }

            is EventosIntent.EventClicked -> {
                viewModelScope.launch {
                    _effects.emit(EventosEffect.NavigateToEditEvent(intent.event))
                }
            }

            is EventosIntent.EventCreated -> {
                val newEvent = Event(
                    id = "event_${++eventIdCounter}",
                    name = intent.name,
                    category = intent.category,
                    dateEpochDay = intent.dateEpochDay,
                    contact = intent.contact,
                    description = intent.description,
                )
                _state.value = _state.value.copy(
                    events = _state.value.events + newEvent,
                )
                recomputeFiltered()
            }

            is EventosIntent.EventUpdated -> {
                val events = _state.value.events.map { event ->
                    if (event.id == intent.eventId) {
                        event.copy(
                            name = intent.name,
                            category = intent.category,
                            dateEpochDay = intent.dateEpochDay,
                            contact = intent.contact,
                            description = intent.description,
                        )
                    } else {
                        event
                    }
                }
                _state.value = _state.value.copy(events = events)
                recomputeFiltered()
            }

            is EventosIntent.EventDeleted -> {
                _state.value = _state.value.copy(
                    events = _state.value.events.filter { it.id != intent.eventId },
                )
                recomputeFiltered()
            }

            is EventosIntent.SetTodayEpochDay -> {
                _state.value = _state.value.copy(todayEpochDay = intent.todayEpochDay)
                recomputeFiltered()
            }

            is EventosIntent.ErrorOccurred -> {
                viewModelScope.launch {
                    _effects.emit(EventosEffect.ShowError(intent.message))
                }
            }

            is EventosIntent.MonthNavigatedForward -> {
                val s = _state.value
                val newMonth = if (s.filterMonth == 12) 1 else s.filterMonth + 1
                val newYear = if (s.filterMonth == 12) s.filterYear + 1 else s.filterYear
                _state.value = s.copy(filterMonth = newMonth, filterYear = newYear)
                recomputeFiltered()
            }

            is EventosIntent.MonthNavigatedBack -> {
                val s = _state.value
                val newMonth = if (s.filterMonth == 1) 12 else s.filterMonth - 1
                val newYear = if (s.filterMonth == 1) s.filterYear - 1 else s.filterYear
                _state.value = s.copy(filterMonth = newMonth, filterYear = newYear)
                recomputeFiltered()
            }

            is EventosIntent.AddTestEvents -> {
                _state.value = _state.value.copy(events = intent.events)
                recomputeFiltered()
            }
        }
    }

    /**
     * Recomputa [EventosState.filteredEvents] e [EventosState.eventCount].
     *
     * Logica:
     * 1. Separa eventos em futuros (>= hoje) e realizados (< hoje) conforme tab selecionada.
     * 2. Aplica filtro de categoria (se selecionado).
     * 3. Aplica filtro de mes (month navigator).
     * 4. Aplica busca por nome (dentro da tab ativa).
     * 5. Ordena: EVENTOS ascendente, EVENTOS_REALIZADOS descendente.
     */
    private fun recomputeFiltered() {
        val s = _state.value
        val query = s.searchQuery.trim()
        val isSearchActive = query.isNotEmpty()
        val today = s.todayEpochDay

        // Step 1: tab filter — separa futuros de realizados
        var filtered = when (s.selectedTab) {
            EventTab.EVENTOS -> s.events.filter { it.dateEpochDay >= today }
            EventTab.EVENTOS_REALIZADOS -> s.events.filter { it.dateEpochDay < today }
        }

        // Step 2: category filter
        val categoryFilter = s.categoryFilter
        if (categoryFilter != null) {
            filtered = filtered.filter { it.category == categoryFilter }
        }

        // Step 3: month filter (always active)
        if (s.filterMonth != 0 && s.filterYear != 0) {
            filtered = filtered.filter { event ->
                val (year, month) = yearMonthFromEpochDay(event.dateEpochDay)
                year == s.filterYear && month == s.filterMonth
            }
        }

        // Step 4: search by name (within the tab)
        if (isSearchActive) {
            filtered = filtered.filter { it.name.contains(query, ignoreCase = true) }
        }

        // Step 5: sort — EVENTOS ascending, EVENTOS_REALIZADOS descending
        val sorted = when (s.selectedTab) {
            EventTab.EVENTOS -> filtered.sortedBy { it.dateEpochDay }
            EventTab.EVENTOS_REALIZADOS -> filtered.sortedByDescending { it.dateEpochDay }
        }

        // Event count: total upcoming events (unfiltered, for the badge)
        val eventCount = s.events.count { it.dateEpochDay >= today }

        _state.value = s.copy(
            isSearchActive = isSearchActive,
            filteredEvents = sorted,
            eventCount = eventCount,
        )
    }
}
