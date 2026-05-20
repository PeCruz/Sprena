package br.com.sprena.presentation.category

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface CategoryIntent : UiIntent {
    data object AddCategoryClicked : CategoryIntent

    data object DismissAddDialog : CategoryIntent

    data class CategoryAdded(
        val name: String,
    ) : CategoryIntent

    data class CategoryClicked(
        val name: String,
    ) : CategoryIntent

    data object DismissEditDialog : CategoryIntent

    data class CategoryRenamed(
        val oldName: String,
        val newName: String,
    ) : CategoryIntent

    data class CategoryDeleted(
        val name: String,
    ) : CategoryIntent
}
