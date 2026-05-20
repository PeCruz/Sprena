package br.com.sprena.presentation.kanban.taskdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.presentation.kanban.KanbanColumn
import br.com.sprena.presentation.kanban.KanbanTask
import br.com.sprena.shared.core.mvi.MviViewModel
import br.com.sprena.shared.kanban.domain.validation.TaskValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TaskDetailViewModel(
    task: KanbanTask,
    columns: List<KanbanColumn>,
) : ViewModel(),
    MviViewModel<TaskDetailState, TaskDetailIntent, TaskDetailEffect> {
    private val _state =
        MutableStateFlow(
            TaskDetailState(
                taskId = task.id,
                name = task.name,
                priority = task.priority,
                columnId = task.columnId,
                availableColumns = columns,
                // Snapshot originais
                originalName = task.name,
                originalPriority = task.priority,
                originalColumnId = task.columnId,
            ),
        )
    override val state: StateFlow<TaskDetailState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TaskDetailEffect>()
    override val effects: SharedFlow<TaskDetailEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: TaskDetailIntent) {
        when (intent) {
            is TaskDetailIntent.NameChanged -> {
                val v = TaskValidator.validateName(intent.value)
                updateState { copy(name = intent.value, nameError = v.errorMessage) }
            }

            is TaskDetailIntent.PriorityChanged -> {
                val v = TaskValidator.validatePriority(intent.value)
                updateState { copy(priority = intent.value, priorityError = v.errorMessage) }
            }

            is TaskDetailIntent.DescriptionChanged -> {
                val v = TaskValidator.validateDescription(intent.value)
                updateState { copy(description = intent.value, descriptionError = v.errorMessage) }
            }

            is TaskDetailIntent.CommentChanged -> {
                val v = TaskValidator.validateComment(intent.value)
                updateState { copy(comment = intent.value, commentError = v.errorMessage) }
            }

            is TaskDetailIntent.EndDateChanged -> {
                updateState { copy(endEpochDay = intent.epochDay, endDateError = null) }
            }

            is TaskDetailIntent.ColumnChanged -> {
                updateState { copy(columnId = intent.columnId) }
            }

            is TaskDetailIntent.DeleteClicked -> {
                updateState { copy(isDeleteConfirmVisible = true) }
            }

            is TaskDetailIntent.DeleteCancelled -> {
                updateState { copy(isDeleteConfirmVisible = false) }
            }

            is TaskDetailIntent.DeleteConfirmed -> {
                updateState { copy(isDeleteConfirmVisible = false) }
                viewModelScope.launch {
                    _effects.emit(TaskDetailEffect.TaskDeleted(_state.value.taskId))
                }
            }

            is TaskDetailIntent.Save -> {
                val s = _state.value
                val nameValid = TaskValidator.validateName(s.name).isValid
                val priorityValid = TaskValidator.validatePriority(s.priority).isValid
                val descValid = TaskValidator.validateDescription(s.description).isValid
                val commentValid = TaskValidator.validateComment(s.comment).isValid

                if (nameValid && priorityValid && descValid && commentValid) {
                    viewModelScope.launch {
                        _effects.emit(
                            TaskDetailEffect.TaskUpdated(
                                taskId = s.taskId,
                                name = s.name,
                                priority = s.priority ?: 3,
                                description = s.description,
                                comment = s.comment,
                                endEpochDay = s.endEpochDay,
                                columnId = s.columnId,
                            ),
                        )
                    }
                } else {
                    viewModelScope.launch {
                        _effects.emit(
                            TaskDetailEffect.ShowError("Corrija os campos inválidos"),
                        )
                    }
                }
            }

            is TaskDetailIntent.Dismiss -> {
                viewModelScope.launch {
                    _effects.emit(TaskDetailEffect.Dismissed)
                }
            }
        }
    }

    private fun updateState(transform: TaskDetailState.() -> TaskDetailState) {
        val newState = _state.value.transform()
        _state.value = newState.copy(hasChanges = computeHasChanges(newState))
    }

    private fun computeHasChanges(s: TaskDetailState): Boolean =
        s.name != s.originalName ||
            s.priority != s.originalPriority ||
            s.description != s.originalDescription ||
            s.comment != s.originalComment ||
            s.endEpochDay != s.originalEndEpochDay ||
            s.columnId != s.originalColumnId
}
