package br.com.sprena.presentation.kanban

import br.com.sprena.shared.core.mvi.UiEffect

sealed interface KanbanEffect : UiEffect {
    data object OpenAddTaskDialog : KanbanEffect

    data object NavigateToSettings : KanbanEffect

    data class ShowError(
        val message: String,
    ) : KanbanEffect
}
