package br.com.sprena.presentation.eventos

import br.com.sprena.shared.core.mvi.UiEffect

sealed interface EventosEffect : UiEffect {
    data object NavigateToCreateEvent : EventosEffect

    data class NavigateToEditEvent(
        val event: Event,
    ) : EventosEffect

    data class ShowError(
        val message: String,
    ) : EventosEffect
}
