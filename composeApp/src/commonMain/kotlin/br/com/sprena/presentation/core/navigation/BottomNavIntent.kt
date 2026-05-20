package br.com.sprena.presentation.core.navigation

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface BottomNavIntent : UiIntent {
    data class TabSelected(
        val tab: BottomTab,
    ) : BottomNavIntent
}
