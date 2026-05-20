package br.com.sprena.presentation.menu

import br.com.sprena.shared.core.mvi.UiEffect

sealed interface MenuEffect : UiEffect {
    data object NavigateBack : MenuEffect

    data class ShowError(
        val message: String,
    ) : MenuEffect
}
