package br.com.sprena.presentation.sportclient

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface SportClientIntent : UiIntent {
    data class SearchQueryChanged(
        val query: String,
    ) : SportClientIntent

    data object AddClientClicked : SportClientIntent

    data object DismissAddDialog : SportClientIntent

    data class ClientAdded(
        val client: SportClient,
    ) : SportClientIntent

    data class ClientClicked(
        val client: SportClient,
    ) : SportClientIntent

    data object DismissClientDetail : SportClientIntent

    data class EditClientClicked(
        val client: SportClient,
    ) : SportClientIntent

    data class ClientUpdated(
        val client: SportClient,
    ) : SportClientIntent

    data class ClientDeleted(
        val clientId: String,
    ) : SportClientIntent
}
