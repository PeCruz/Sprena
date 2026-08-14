package br.com.sprena.presentation.profile

import br.com.sprena.shared.core.mvi.UiIntent
import br.com.sprena.shared.sportclient.domain.validation.SportModality

/** Tudo que o titular pode fazer na tela "Meus dados" (F1.6a). */
sealed interface ProfileIntent : UiIntent {
    data object Retry : ProfileIntent

    data object ToggleCpfReveal : ProfileIntent

    data object TogglePhoneReveal : ProfileIntent

    data object EditClicked : ProfileIntent

    data object EditCancelled : ProfileIntent

    data class ApelidoChanged(
        val value: String,
    ) : ProfileIntent

    data class CpfChanged(
        val value: String,
    ) : ProfileIntent

    data class PhoneChanged(
        val value: String,
    ) : ProfileIntent

    data class ModalityToggled(
        val modality: SportModality,
    ) : ProfileIntent

    data object SaveClicked : ProfileIntent

    data object PasswordResetRequested : ProfileIntent

    data object ExportClicked : ProfileIntent

    data object ExportDismissed : ProfileIntent

    data object ExportConfirmed : ProfileIntent

    data object DeleteClicked : ProfileIntent

    data object DeleteDismissed : ProfileIntent

    data class DeleteConfirmationChanged(
        val text: String,
    ) : ProfileIntent

    data object DeleteConfirmed : ProfileIntent

    data object Logout : ProfileIntent
}
