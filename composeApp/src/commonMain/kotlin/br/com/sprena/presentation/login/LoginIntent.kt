package br.com.sprena.presentation.login

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface LoginIntent : UiIntent {
    data class EmailChanged(
        val value: String,
    ) : LoginIntent

    data class PasswordChanged(
        val value: String,
    ) : LoginIntent

    data object TogglePasswordVisibility : LoginIntent

    data object Submit : LoginIntent

    // --- Esqueci a senha ---
    data object OpenPasswordResetDialog : LoginIntent

    data class UpdatePasswordResetEmail(
        val value: String,
    ) : LoginIntent

    data object SubmitPasswordReset : LoginIntent

    data object DismissPasswordResetDialog : LoginIntent
}
