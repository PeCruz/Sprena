package br.com.sprena.presentation.kanban.addtask

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

class AddTaskViewModel(
    private val today: () -> Long = { 0L },
) : ViewModel(),
    MviViewModel<AddTaskState, AddTaskIntent, AddTaskEffect> {
    private val _state = MutableStateFlow(AddTaskState(startEpochDay = today()))
    override val state: StateFlow<AddTaskState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AddTaskEffect>()
    override val effects: SharedFlow<AddTaskEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: AddTaskIntent) {
        when (intent) {
            is AddTaskIntent.NameChanged -> {
                val v = TaskValidator.validateName(intent.value)
                updateState { copy(name = intent.value, nameError = v.errorMessage) }
            }

            is AddTaskIntent.PriorityChanged -> {
                val v = TaskValidator.validatePriority(intent.value)
                updateState { copy(priority = intent.value, priorityError = v.errorMessage) }
            }

            is AddTaskIntent.DescriptionChanged -> {
                val v = TaskValidator.validateDescription(intent.value)
                updateState { copy(description = intent.value, descriptionError = v.errorMessage) }
            }

            is AddTaskIntent.CommentChanged -> {
                val v = TaskValidator.validateComment(intent.value)
                updateState { copy(comment = intent.value, commentError = v.errorMessage) }
            }

            is AddTaskIntent.EndDateChanged -> {
                val v = TaskValidator.validateEndDate(intent.epochDay, today())
                updateState { copy(endEpochDay = intent.epochDay, endDateError = v.errorMessage) }
            }

            is AddTaskIntent.AttachmentSelected -> {
                val v = TaskValidator.validateAttachmentSize(intent.sizeBytes)
                updateState {
                    copy(
                        attachmentName = intent.name,
                        attachmentSizeBytes = intent.sizeBytes,
                        attachmentError = v.errorMessage,
                    )
                }
            }

            is AddTaskIntent.AttachmentCleared -> {
                updateState {
                    copy(attachmentName = null, attachmentSizeBytes = 0L, attachmentError = null)
                }
            }

            is AddTaskIntent.Submit -> {
                val s = _state.value
                if (s.canSubmit) {
                    viewModelScope.launch { _effects.emit(AddTaskEffect.TaskCreated) }
                } else {
                    viewModelScope.launch {
                        _effects.emit(AddTaskEffect.ShowError("Preencha todos os campos obrigatorios"))
                    }
                }
            }

            is AddTaskIntent.Dismiss -> {
                viewModelScope.launch { _effects.emit(AddTaskEffect.Dismissed) }
            }
        }
    }

    private fun updateState(transform: AddTaskState.() -> AddTaskState) {
        val newState = _state.value.transform()
        _state.value = newState.copy(canSubmit = computeCanSubmit(newState))
    }

    private fun computeCanSubmit(s: AddTaskState): Boolean =
        TaskValidator.validateName(s.name).isValid &&
            TaskValidator.validatePriority(s.priority).isValid &&
            TaskValidator.validateEndDate(s.endEpochDay, today()).isValid &&
            TaskValidator.validateDescription(s.description).isValid &&
            TaskValidator.validateComment(s.comment).isValid &&
            TaskValidator.validateAttachmentSize(s.attachmentSizeBytes).isValid &&
            s.endEpochDay != null
}
