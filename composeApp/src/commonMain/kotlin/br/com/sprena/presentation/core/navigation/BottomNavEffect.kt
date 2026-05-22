package br.com.sprena.presentation.core.navigation

import br.com.sprena.shared.core.mvi.UiEffect

sealed interface BottomNavEffect : UiEffect {
    data class NavigateTo(
        val tab: BottomTab,
    ) : BottomNavEffect
}
