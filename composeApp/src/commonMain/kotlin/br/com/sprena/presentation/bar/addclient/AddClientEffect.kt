package br.com.sprena.presentation.bar.addclient

import br.com.sprena.presentation.bar.BarClient
import br.com.sprena.shared.core.mvi.UiEffect

sealed interface AddClientEffect : UiEffect {
    data class ClientCreated(
        val client: BarClient,
    ) : AddClientEffect

    data class ShowError(
        val message: String,
    ) : AddClientEffect

    data object Dismissed : AddClientEffect
}
