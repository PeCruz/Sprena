package br.com.sprena.presentation.login

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface LoginIntent : UiIntent {
    data class UsernameChanged(
        val value: String,
    ) : LoginIntent

    data class PasswordChanged(
        val value: String,
    ) : LoginIntent

    data object TogglePasswordVisibility : LoginIntent

    data object Submit : LoginIntent
}
