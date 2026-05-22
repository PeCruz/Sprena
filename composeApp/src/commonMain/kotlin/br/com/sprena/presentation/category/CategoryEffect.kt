package br.com.sprena.presentation.category

import br.com.sprena.shared.core.mvi.UiEffect

sealed interface CategoryEffect : UiEffect {
    data object NavigateBack : CategoryEffect

    data class ShowError(
        val message: String,
    ) : CategoryEffect
}
