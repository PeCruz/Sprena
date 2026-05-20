package br.com.sprena.presentation.kanban.addtask

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface AddTaskIntent : UiIntent {
    data class NameChanged(
        val value: String,
    ) : AddTaskIntent

    data class PriorityChanged(
        val value: Int?,
    ) : AddTaskIntent

    data class DescriptionChanged(
        val value: String,
    ) : AddTaskIntent

    data class CommentChanged(
        val value: String,
    ) : AddTaskIntent

    data class EndDateChanged(
        val epochDay: Long?,
    ) : AddTaskIntent

    data class AttachmentSelected(
        val name: String,
        val sizeBytes: Long,
    ) : AddTaskIntent

    data object AttachmentCleared : AddTaskIntent

    data object Submit : AddTaskIntent

    data object Dismiss : AddTaskIntent
}
