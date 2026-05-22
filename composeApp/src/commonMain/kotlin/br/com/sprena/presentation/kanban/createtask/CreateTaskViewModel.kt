package br.com.sprena.presentation.kanban.createtask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.core.mvi.MviViewModel
import br.com.sprena.shared.kanban.domain.validation.TaskValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CreateTaskViewModel(
    private val today: () -> Long = { 0L },
) : ViewModel(),
    MviViewModel<CreateTaskState, CreateTaskIntent, CreateTaskEffect> {
    private val _state = MutableStateFlow(CreateTaskState(startEpochDay = today()))
    override val state: StateFlow<CreateTaskState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<CreateTaskEffect>()
    override val effects: SharedFlow<CreateTaskEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: CreateTaskIntent) {
        when (intent) {
            is CreateTaskIntent.NameChanged -> {
                val v = TaskValidator.validateName(intent.value)
                updateState { copy(name = intent.value, nameError = v.errorMessage) }
            }

            is CreateTaskIntent.PriorityChanged -> {
                val v = TaskValidator.validatePriority(intent.value)
                updateState { copy(priority = intent.value, priorityError = v.errorMessage) }
            }

            is CreateTaskIntent.DescriptionChanged -> {
                val v = TaskValidator.validateDescription(intent.value)
                updateState { copy(description = intent.value, descriptionError = v.errorMessage) }
            }

            is CreateTaskIntent.CommentChanged -> {
                val v = TaskValidator.validateComment(intent.value)
                updateState { copy(comment = intent.value, commentError = v.errorMessage) }
            }

            is CreateTaskIntent.EndDateChanged -> {
                val v = TaskValidator.validateEndDate(intent.epochDay, today())
                updateState { copy(endEpochDay = intent.epochDay, endDateError = v.errorMessage) }
            }

            is CreateTaskIntent.AttachmentSelected -> {
                val v = TaskValidator.validateAttachmentSize(intent.sizeBytes)
                updateState {
                    copy(
                        attachmentName = intent.name,
                        attachmentSizeBytes = intent.sizeBytes,
                        attachmentError = v.errorMessage,
                    )
                }
            }

            is CreateTaskIntent.AttachmentCleared -> {
                updateState {
                    copy(
                        attachmentName = null,
                        attachmentSizeBytes = 0L,
                        attachmentError = null,
                    )
                }
            }

            is CreateTaskIntent.Submit -> {
                if (_state.value.canSubmit) {
                    viewModelScope.launch { _effects.emit(CreateTaskEffect.TaskCreated) }
                } else {
                    viewModelScope.launch {
                        _effects.emit(
                            CreateTaskEffect.ShowError("Preencha todos os campos obrigatórios"),
                        )
                    }
                }
            }

            is CreateTaskIntent.NavigateBack -> {
                viewModelScope.launch { _effects.emit(CreateTaskEffect.GoBack) }
            }
        }
    }

    private fun updateState(transform: CreateTaskState.() -> CreateTaskState) {
        val newState = _state.value.transform()
        _state.value =
            newState.copy(
                canSubmit = computeCanSubmit(newState),
                hasUnsavedChanges = computeHasUnsavedChanges(newState),
            )
    }

    private fun computeCanSubmit(s: CreateTaskState): Boolean =
        TaskValidator.validateName(s.name).isValid &&
            TaskValidator.validatePriority(s.priority).isValid &&
            TaskValidator.validateEndDate(s.endEpochDay, today()).isValid &&
            TaskValidator.validateDescription(s.description).isValid &&
            TaskValidator.validateComment(s.comment).isValid &&
            TaskValidator.validateAttachmentSize(s.attachmentSizeBytes).isValid

    private fun computeHasUnsavedChanges(s: CreateTaskState): Boolean =
        s.name.isNotBlank() || s.description.isNotBlank() || s.comment.isNotBlank()
}
