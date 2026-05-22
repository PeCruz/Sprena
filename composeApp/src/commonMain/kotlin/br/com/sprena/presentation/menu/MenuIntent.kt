package br.com.sprena.presentation.menu

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface MenuIntent : UiIntent {
    data class SearchQueryChanged(
        val query: String,
    ) : MenuIntent

    data object AddItemClicked : MenuIntent

    data object DismissAddDialog : MenuIntent

    data class ItemAdded(
        val item: MenuItem,
    ) : MenuIntent

    data class ItemClicked(
        val item: MenuItem,
    ) : MenuIntent

    data object DismissItemDetail : MenuIntent

    data class ItemUpdated(
        val item: MenuItem,
    ) : MenuIntent

    data class ItemDeleted(
        val itemId: String,
    ) : MenuIntent

    data object NavigateBackClicked : MenuIntent
}
