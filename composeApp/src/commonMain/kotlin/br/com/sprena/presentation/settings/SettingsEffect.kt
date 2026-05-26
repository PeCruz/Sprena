package br.com.sprena.presentation.settings

import br.com.sprena.shared.core.mvi.UiEffect

sealed interface SettingsEffect : UiEffect {
    data object NavigateToLogin : SettingsEffect
}
