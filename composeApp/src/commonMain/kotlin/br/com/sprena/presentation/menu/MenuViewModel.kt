package br.com.sprena.presentation.menu

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

class MenuViewModel :
    ViewModel(),
    MviViewModel<MenuState, MenuIntent, MenuEffect> {
    private val _state = MutableStateFlow(MenuState())
    override val state: StateFlow<MenuState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MenuEffect>()
    override val effects: SharedFlow<MenuEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: MenuIntent) {
        when (intent) {
            is MenuIntent.SearchQueryChanged -> {
                _state.value = _state.value.copy(searchQuery = intent.query)
                recomputeFiltered()
            }

            is MenuIntent.AddItemClicked -> {
                _state.value = _state.value.copy(isAddDialogVisible = true)
            }

            is MenuIntent.DismissAddDialog -> {
                _state.value = _state.value.copy(isAddDialogVisible = false)
            }

            is MenuIntent.ItemAdded -> {
                val updatedItems = _state.value.items + intent.item
                _state.value =
                    _state.value.copy(
                        items = updatedItems,
                        isAddDialogVisible = false,
                    )
                recomputeFiltered()
            }

            is MenuIntent.ItemClicked -> {
                _state.value = _state.value.copy(selectedItem = intent.item)
            }

            is MenuIntent.DismissItemDetail -> {
                _state.value = _state.value.copy(selectedItem = null)
            }

            is MenuIntent.ItemUpdated -> {
                val updatedItems =
                    _state.value.items.map { existing ->
                        if (existing.id == intent.item.id) intent.item else existing
                    }
                _state.value =
                    _state.value.copy(
                        items = updatedItems,
                        selectedItem = null,
                    )
                recomputeFiltered()
            }

            is MenuIntent.ItemDeleted -> {
                val updatedItems = _state.value.items.filter { it.id != intent.itemId }
                _state.value =
                    _state.value.copy(
                        items = updatedItems,
                        selectedItem = null,
                    )
                recomputeFiltered()
            }

            is MenuIntent.NavigateBackClicked -> {
                viewModelScope.launch {
                    _effects.emit(MenuEffect.NavigateBack)
                }
            }
        }
    }

    private fun recomputeFiltered() {
        val query = _state.value.searchQuery
        val items = _state.value.items
        val filtered =
            if (query.isBlank()) {
                items
            } else {
                items.filter { it.name.contains(query, ignoreCase = true) }
            }
        _state.value = _state.value.copy(filteredItems = filtered)
    }
}
