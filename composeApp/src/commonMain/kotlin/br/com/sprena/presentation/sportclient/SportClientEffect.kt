package br.com.sprena.presentation.sportclient

import br.com.sprena.shared.core.mvi.UiEffect

sealed interface SportClientEffect : UiEffect {
    data class ShowError(
        val message: String,
    ) : SportClientEffect

    data class NavigateToEdit(
        val client: SportClient,
    ) : SportClientEffect
}
