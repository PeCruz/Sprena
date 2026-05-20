package br.com.sprena.presentation.eventos

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface EventosIntent : UiIntent {

    /** Seleciona uma tab (Eventos ou Eventos Realizados). */
    data class TabSelected(val tab: EventTab) : EventosIntent

    /** Texto de busca alterado (filtra dentro da tab ativa). */
    data class SearchQueryChanged(val query: String) : EventosIntent

    /** Filtro por categoria alterado (null = Todos). */
    data class CategoryFilterChanged(val category: EventCategory?) : EventosIntent

    /** FAB clicado — navegar para tela de criacao. */
    data object AddEventClicked : EventosIntent

    /** Evento clicado — navegar para tela de edicao. */
    data class EventClicked(val event: Event) : EventosIntent

    /** Evento criado (vindo do CreateEventScreen). */
    data class EventCreated(
        val name: String,
        val category: EventCategory,
        val dateEpochDay: Long,
        val contact: String?,
        val description: String?,
    ) : EventosIntent

    /** Evento atualizado (vindo do CreateEventScreen em modo edicao). */
    data class EventUpdated(
        val eventId: String,
        val name: String,
        val category: EventCategory,
        val dateEpochDay: Long,
        val contact: String?,
        val description: String?,
    ) : EventosIntent

    /** Evento excluido. */
    data class EventDeleted(val eventId: String) : EventosIntent

    /** Define a data de hoje (epoch days) para calculo de expirados. */
    data class SetTodayEpochDay(val todayEpochDay: Long) : EventosIntent

    /** Erro ocorrido (para emissao de efeito). */
    data class ErrorOccurred(val message: String) : EventosIntent

    /** Avanca o mes do navegador de meses. */
    data object MonthNavigatedForward : EventosIntent

    /** Retrocede o mes do navegador de meses. */
    data object MonthNavigatedBack : EventosIntent

    /** Intent auxiliar para injetar eventos em testes. Nao usar em producao. */
    data class AddTestEvents(val events: List<Event>) : EventosIntent
}
