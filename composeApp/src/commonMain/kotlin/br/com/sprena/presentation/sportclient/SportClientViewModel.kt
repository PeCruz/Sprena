package br.com.sprena.presentation.sportclient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.session.SessionStore
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
 *
 * A permissão de revelar o CPF é resolvida aqui, a partir da role da sessão: a UI
 * só renderiza o que o state já decidiu (F1.5).
 */
class SportClientViewModel(
    private val sessionStore: SessionStore,
) : ViewModel(),
    MviViewModel<SportClientState, SportClientIntent, SportClientEffect> {
    private val _state = MutableStateFlow(SportClientState())
    override val state: StateFlow<SportClientState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<SportClientEffect>()
    override val effects: SharedFlow<SportClientEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            val role = sessionStore.load()?.role
            _state.value = _state.value.copy(canRevealCpf = role != null && role in STAFF_ROLES)
        }
    }

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
                // Abrir o detalhe sempre parte do CPF mascarado, mesmo para ADM/MOD.
                _state.value = _state.value.copy(selectedClient = intent.client, isCpfRevealed = false)
            }

            is SportClientIntent.DismissClientDetail -> {
                _state.value = _state.value.copy(selectedClient = null, isCpfRevealed = false)
            }

            is SportClientIntent.EditClientClicked -> {
                _state.value = _state.value.copy(selectedClient = null, isCpfRevealed = false)
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
                        isCpfRevealed = false,
                    )
                recomputeFiltered()
            }

            is SportClientIntent.ClientDeleted -> deleteClient(intent.clientId)

            is SportClientIntent.ToggleCpfReveal -> toggleCpfReveal()
        }
    }

    /** Remove o cliente e, se ele estava aberto no detalhe, fecha e remascara o CPF. */
    private fun deleteClient(clientId: String) {
        val current = _state.value
        val keepsSelection = current.selectedClient?.id != clientId
        _state.value =
            current.copy(
                clients = current.clients.filter { it.id != clientId },
                selectedClient = if (keepsSelection) current.selectedClient else null,
                isCpfRevealed = keepsSelection && current.isCpfRevealed,
            )
        recomputeFiltered()
    }

    /** Toggle ignorado sem permissão: [SportClientState.isCpfRevealed] nunca liga sozinho. */
    private fun toggleCpfReveal() {
        val current = _state.value
        if (current.canRevealCpf) {
            _state.value = current.copy(isCpfRevealed = !current.isCpfRevealed)
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

    private companion object {
        val STAFF_ROLES = setOf(UserRole.ADM, UserRole.MOD)
    }
}
