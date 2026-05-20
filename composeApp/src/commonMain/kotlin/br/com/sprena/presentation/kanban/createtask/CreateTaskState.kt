package br.com.sprena.presentation.kanban.createtask

import br.com.sprena.shared.core.mvi.UiState

/**
 * State da tela "Criando Tarefa".
 *
 * Campos obrigatórios: name, priority, endEpochDay.
 * Campos opcionais: description, comment, attachment.
 *
 * [hasUnsavedChanges] — true quando qualquer campo textual foi preenchido.
 * Usado para exibir confirmação de saída.
 */
data class CreateTaskState(
    val name: String = "",
    val priority: Int? = null,
    val description: String = "",
    val comment: String = "",
    val startEpochDay: Long = 0L,
    val endEpochDay: Long? = null,
    val attachmentName: String? = null,
    val attachmentSizeBytes: Long = 0L,
    val nameError: String? = null,
    val priorityError: String? = null,
    val descriptionError: String? = null,
    val commentError: String? = null,
    val endDateError: String? = null,
    val attachmentError: String? = null,
    val canSubmit: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
) : UiState {
    companion object {
        val PRIORITY_OPTIONS: List<Int> = listOf(1, 2, 3, 4, 5)
    }
}
