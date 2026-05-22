package br.com.sprena.presentation.core.theme

import br.com.sprena.shared.core.mvi.UiEffect

sealed interface ThemeEffect : UiEffect {
    data class ThemeChanged(
        val mode: ThemeMode,
    ) : ThemeEffect
}
