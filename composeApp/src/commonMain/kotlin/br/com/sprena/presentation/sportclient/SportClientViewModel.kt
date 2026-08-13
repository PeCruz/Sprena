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
 * A permissão de revelar o CPF ([SportClientState.canRevealCpf]) e a permissão de
 * escrever (adicionar/editar/excluir cliente, [SportClientState.canManageClients]) são
 * resolvidas aqui, a partir da role da sessão: a UI só renderiza o que o state já
 * decidiu (F1.5). São duas autorizações independentes — cada uma com seu próprio
 * conjunto de roles — mesmo hoje tendo o mesmo resultado para ADM/MOD/CLIENT.
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
            _state.value =
                _state.value.copy(
                    canRevealCpf = role != null && role in CPF_REVEAL_ROLES,
                    canManageClients = role != null && role in CLIENT_MANAGEMENT_ROLES,
                )
        }
    }

    override fun handleIntent(intent: SportClientIntent) {
        when (intent) {
            is SportClientIntent.SearchQueryChanged -> {
                _state.value = _state.value.copy(searchQuery = intent.query)
                recomputeFiltered()
            }

            is SportClientIntent.AddClientClicked -> onAddClientClicked()

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

            is SportClientIntent.EditClientClicked -> onEditClientClicked(intent.client)

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

            is SportClientIntent.ClientDeleted -> onClientDeleted(intent.clientId)

            is SportClientIntent.ToggleCpfReveal -> toggleCpfReveal()
        }
    }

    /** Ignorado sem permissão: esconder o FAB na UI não pode ser a única barreira. */
    private fun onAddClientClicked() {
        if (_state.value.canManageClients) {
            _state.value = _state.value.copy(isAddDialogVisible = true)
        }
    }

    /**
     * Ignorado sem permissão: as Firestore Rules já recusam a escrita para quem não é
     * ADM/MOD, mas o formulário de edição não pode nem abrir.
     */
    private fun onEditClientClicked(client: SportClient) {
        if (_state.value.canManageClients) {
            _state.value = _state.value.copy(selectedClient = null, isCpfRevealed = false)
            viewModelScope.launch {
                _effects.emit(SportClientEffect.NavigateToEdit(client))
            }
        }
    }

    /** Ignorado sem permissão, pelo mesmo motivo dos demais intents de escrita. */
    private fun onClientDeleted(clientId: String) {
        if (_state.value.canManageClients) deleteClient(clientId)
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
        /** Quem pode revelar o CPF completo no diálogo de detalhe. */
        val CPF_REVEAL_ROLES = setOf(UserRole.ADM, UserRole.MOD)

        /**
         * Quem pode adicionar, editar ou excluir clientes. Hoje coincide com
         * [CPF_REVEAL_ROLES], mas é uma autorização separada — não reaproveitar uma
         * lista para a outra.
         */
        val CLIENT_MANAGEMENT_ROLES = setOf(UserRole.ADM, UserRole.MOD)
    }
}
