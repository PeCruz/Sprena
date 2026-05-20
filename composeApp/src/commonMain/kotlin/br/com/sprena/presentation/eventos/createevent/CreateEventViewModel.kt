package br.com.sprena.presentation.eventos.createevent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.core.mvi.MviViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CreateEventViewModel :
    ViewModel(),
    MviViewModel<CreateEventState, CreateEventIntent, CreateEventEffect> {
    private val _state = MutableStateFlow(CreateEventState())
    override val state: StateFlow<CreateEventState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<CreateEventEffect>()
    override val effects: SharedFlow<CreateEventEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: CreateEventIntent) {
        when (intent) {
            is CreateEventIntent.NameChanged -> {
                _state.value = _state.value.copy(name = intent.name, nameError = null)
                recomputeCanSubmit()
                markUnsaved()
            }

            is CreateEventIntent.CategoryChanged -> {
                _state.value = _state.value.copy(category = intent.category, categoryError = null)
                recomputeCanSubmit()
                markUnsaved()
            }

            is CreateEventIntent.DateChanged -> {
                _state.value = _state.value.copy(dateEpochDay = intent.dateEpochDay, dateError = null)
                recomputeCanSubmit()
                markUnsaved()
            }

            is CreateEventIntent.ContactChanged -> {
                _state.value = _state.value.copy(contact = intent.contact)
                markUnsaved()
            }

            is CreateEventIntent.DescriptionChanged -> {
                _state.value = _state.value.copy(description = intent.description)
                markUnsaved()
            }

            is CreateEventIntent.LoadForEdit -> {
                _state.value =
                    _state.value.copy(
                        editingEventId = intent.eventId,
                        isEditMode = true,
                        name = intent.name,
                        category = intent.category,
                        dateEpochDay = intent.dateEpochDay,
                        contact = intent.contact?.filter { it.isDigit() } ?: "",
                        description = intent.description ?: "",
                        nameError = null,
                        categoryError = null,
                        dateError = null,
                    )
                recomputeCanSubmit()
            }

            is CreateEventIntent.Submit -> {
                val s = _state.value
                val nameError = if (s.name.isBlank()) "Nome do evento e obrigatorio" else null
                val categoryError = if (s.category == null) "Categoria e obrigatoria" else null
                val dateError = if (s.dateEpochDay == null) "Data do evento e obrigatoria" else null

                if (nameError != null || categoryError != null || dateError != null) {
                    _state.value =
                        s.copy(
                            nameError = nameError,
                            categoryError = categoryError,
                            dateError = dateError,
                        )
                    return
                }

                _state.value =
                    s.copy(
                        nameError = null,
                        categoryError = null,
                        dateError = null,
                    )

                viewModelScope.launch {
                    _effects.emit(
                        CreateEventEffect.EventSaved(
                            eventId = s.editingEventId,
                            name = s.name.trim(),
                            category = s.category!!,
                            dateEpochDay = s.dateEpochDay!!,
                            contact = s.contact.ifBlank { null }?.let { applyPhoneMask(it) },
                            description = s.description.ifBlank { null },
                        ),
                    )
                }
            }

            is CreateEventIntent.BackClicked -> {
                viewModelScope.launch {
                    _effects.emit(CreateEventEffect.NavigateBack)
                }
            }
        }
    }

    private fun recomputeCanSubmit() {
        val s = _state.value
        val canSubmit = s.name.isNotBlank() && s.category != null && s.dateEpochDay != null
        _state.value = _state.value.copy(canSubmit = canSubmit)
    }

    private fun markUnsaved() {
        val s = _state.value
        if (!s.hasUnsavedChanges) {
            val hasChanges =
                s.name.isNotEmpty() ||
                    s.category != null ||
                    s.dateEpochDay != null ||
                    s.contact.isNotEmpty() ||
                    s.description.isNotEmpty()
            if (hasChanges) {
                _state.value = _state.value.copy(hasUnsavedChanges = true)
            }
        }
    }
}
