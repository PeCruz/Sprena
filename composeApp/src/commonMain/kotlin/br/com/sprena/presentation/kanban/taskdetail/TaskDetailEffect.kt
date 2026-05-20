package br.com.sprena.presentation.kanban.taskdetail

import br.com.sprena.shared.core.mvi.UiEffect

sealed interface TaskDetailEffect : UiEffect {
    data class TaskUpdated(
        val taskId: String,
        val name: String,
        val priority: Int,
        val description: String,
        val comment: String,
        val endEpochDay: Long?,
        val columnId: String,
    ) : TaskDetailEffect

    data class TaskDeleted(
        val taskId: String,
    ) : TaskDetailEffect

    data class ShowError(
        val message: String,
    ) : TaskDetailEffect

    data object Dismissed : TaskDetailEffect
}
