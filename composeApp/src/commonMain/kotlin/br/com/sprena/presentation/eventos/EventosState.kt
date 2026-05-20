package br.com.sprena.presentation.eventos

import br.com.sprena.shared.core.mvi.UiState

/**
 * State da tela Eventos.
 *
 * @property tabs lista ordenada das tabs disponiveis (Eventos, Eventos Realizados).
 * @property selectedTab tab atualmente selecionada.
 * @property events todos os eventos (fonte da verdade).
 * @property searchQuery texto digitado na busca (filtra dentro da tab ativa).
 * @property isSearchActive true quando searchQuery nao esta vazia.
 * @property categoryFilter filtro por categoria (null = Todos).
 * @property filteredEvents eventos filtrados e ordenados conforme tab/busca/categoria/mes.
 * @property eventCount contagem total de eventos na tab Eventos (futuros).
 * @property todayEpochDay dia atual em epoch days (para separar futuros de realizados).
 * @property filterMonth mes do navegador de meses.
 * @property filterYear ano do navegador de meses.
 * @property isLoading indica carregamento de dados.
 * @property error mensagem de erro.
 */
data class EventosState(
    val tabs: List<EventTab> = listOf(
        EventTab.EVENTOS,
        EventTab.EVENTOS_REALIZADOS,
    ),
    val selectedTab: EventTab = EventTab.EVENTOS,
    val events: List<Event> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val categoryFilter: EventCategory? = null,
    val filteredEvents: List<Event> = emptyList(),
    val eventCount: Int = 0,
    val todayEpochDay: Long = 0L,
    val filterMonth: Int = 0,
    val filterYear: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
) : UiState
