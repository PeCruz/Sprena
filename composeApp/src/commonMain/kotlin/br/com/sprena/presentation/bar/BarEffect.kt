package br.com.sprena.presentation.bar

import br.com.sprena.shared.core.mvi.UiEffect

sealed interface BarEffect : UiEffect {
    data object OpenAddClientDialog : BarEffect

    data class ShowError(
        val message: String,
    ) : BarEffect
}
