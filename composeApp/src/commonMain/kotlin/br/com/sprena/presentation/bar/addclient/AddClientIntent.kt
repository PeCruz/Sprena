package br.com.sprena.presentation.bar.addclient

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface AddClientIntent : UiIntent {
    data class NameChanged(
        val name: String,
    ) : AddClientIntent

    data class NicknameChanged(
        val nickname: String,
    ) : AddClientIntent

    data class PhoneChanged(
        val phone: String,
    ) : AddClientIntent

    data class CpfChanged(
        val cpf: String,
    ) : AddClientIntent

    data class EmailChanged(
        val email: String,
    ) : AddClientIntent

    data object Save : AddClientIntent

    data object Dismiss : AddClientIntent
}
