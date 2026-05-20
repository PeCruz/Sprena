package br.com.sprena.presentation.kanban.taskdetail

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface TaskDetailIntent : UiIntent {
    data class NameChanged(
        val value: String,
    ) : TaskDetailIntent

    data class PriorityChanged(
        val value: Int?,
    ) : TaskDetailIntent

    data class DescriptionChanged(
        val value: String,
    ) : TaskDetailIntent

    data class CommentChanged(
        val value: String,
    ) : TaskDetailIntent

    data class EndDateChanged(
        val epochDay: Long?,
    ) : TaskDetailIntent

    data class ColumnChanged(
        val columnId: String,
    ) : TaskDetailIntent

    data object DeleteClicked : TaskDetailIntent

    data object DeleteConfirmed : TaskDetailIntent

    data object DeleteCancelled : TaskDetailIntent

    data object Save : TaskDetailIntent

    data object Dismiss : TaskDetailIntent
}
