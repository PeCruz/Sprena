package br.com.sprena.presentation.kanban.createtask

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface CreateTaskIntent : UiIntent {
    data class NameChanged(
        val value: String,
    ) : CreateTaskIntent

    data class PriorityChanged(
        val value: Int?,
    ) : CreateTaskIntent

    data class DescriptionChanged(
        val value: String,
    ) : CreateTaskIntent

    data class CommentChanged(
        val value: String,
    ) : CreateTaskIntent

    data class EndDateChanged(
        val epochDay: Long?,
    ) : CreateTaskIntent

    data class AttachmentSelected(
        val name: String,
        val sizeBytes: Long,
    ) : CreateTaskIntent

    data object AttachmentCleared : CreateTaskIntent

    data object Submit : CreateTaskIntent

    data object NavigateBack : CreateTaskIntent
}
