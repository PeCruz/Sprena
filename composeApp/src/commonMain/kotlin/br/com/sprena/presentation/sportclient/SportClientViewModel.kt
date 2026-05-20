package br.com.sprena.presentation.sportclient

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

/**
 * ViewModel da tela Home — gestão de clientes de esportes.
 * Placeholder: campos do cliente serão definidos posteriormente.
 */
class SportClientViewModel :
    ViewModel(),
    MviViewModel<SportClientState, SportClientIntent, SportClientEffect> {
    private val _state = MutableStateFlow(SportClientState())
    override val state: StateFlow<SportClientState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<SportClientEffect>()
    override val effects: SharedFlow<SportClientEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: SportClientIntent) {
        when (intent) {
            is SportClientIntent.SearchQueryChanged -> {
                _state.value = _state.value.copy(searchQuery = intent.query)
                recomputeFiltered()
            }

            is SportClientIntent.AddClientClicked -> {
                _state.value = _state.value.copy(isAddDialogVisible = true)
            }

            is SportClientIntent.DismissAddDialog -> {
                _state.value = _state.value.copy(isAddDialogVisible = false)
            }

            is SportClientIntent.ClientAdded -> {
                val updated = _state.value.clients + intent.client
                _state.value =
                    _state.value.copy(
                        clients = updated,
                        isAddDialogVisible = false,
                    )
                recomputeFiltered()
            }

            is SportClientIntent.ClientClicked -> {
                _state.value = _state.value.copy(selectedClient = intent.client)
            }

            is SportClientIntent.DismissClientDetail -> {
                _state.value = _state.value.copy(selectedClient = null)
            }

            is SportClientIntent.EditClientClicked -> {
                _state.value = _state.value.copy(selectedClient = null)
                viewModelScope.launch {
                    _effects.emit(SportClientEffect.NavigateToEdit(intent.client))
                }
            }

            is SportClientIntent.ClientUpdated -> {
                val updated =
                    _state.value.clients.map { c ->
                        if (c.id == intent.client.id) intent.client else c
                    }
                _state.value =
                    _state.value.copy(
                        clients = updated,
                        selectedClient = null,
                    )
                recomputeFiltered()
            }

            is SportClientIntent.ClientDeleted -> {
                val updated = _state.value.clients.filter { it.id != intent.clientId }
                _state.value =
                    _state.value.copy(
                        clients = updated,
                        selectedClient =
                            if (_state.value.selectedClient?.id == intent.clientId) {
                                null
                            } else {
                                _state.value.selectedClient
                            },
                    )
                recomputeFiltered()
            }
        }
    }

    private fun recomputeFiltered() {
        val query = _state.value.searchQuery
        val clients = _state.value.clients
        val filtered =
            if (query.isBlank()) {
                clients
            } else {
                val lowerQuery = query.lowercase()
                clients.filter {
                    it.name.lowercase().contains(lowerQuery) ||
                        it.apelido.lowercase().contains(lowerQuery)
                }
            }
        _state.value = _state.value.copy(filteredClients = filtered)
    }
}
