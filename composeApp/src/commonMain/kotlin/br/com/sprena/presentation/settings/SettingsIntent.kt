package br.com.sprena.presentation.settings

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface SettingsIntent : UiIntent {
    data object Logout : SettingsIntent
}
