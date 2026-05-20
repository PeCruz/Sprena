package br.com.sprena.presentation.bar

import androidx.lifecycle.ViewModel
import br.com.sprena.shared.core.mvi.MviViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel da tela principal do Bar — gerencia lista de clientes,
 * busca, e estado de diálogos.
 */
class BarViewModel :
    ViewModel(),
    MviViewModel<BarState, BarIntent, BarEffect> {
    private val _state = MutableStateFlow(BarState())
    override val state: StateFlow<BarState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<BarEffect>()
    override val effects: SharedFlow<BarEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: BarIntent) {
        when (intent) {
            is BarIntent.Load -> { /* future: load from repository */ }

            is BarIntent.SearchQueryChanged -> {
                val query = intent.query
                _state.value = _state.value.copy(searchQuery = query)
                recomputeFiltered()
            }

            is BarIntent.PaymentFilterChanged -> {
                _state.value = _state.value.copy(paymentFilter = intent.filter)
                recomputeFiltered()
            }

            is BarIntent.AddClientClicked -> {
                _state.value = _state.value.copy(isAddClientDialogVisible = true)
            }

            is BarIntent.DismissAddClientDialog -> {
                _state.value = _state.value.copy(isAddClientDialogVisible = false)
            }

            is BarIntent.ClientClicked -> {
                _state.value = _state.value.copy(selectedClient = intent.client)
            }

            is BarIntent.DismissClientDetail -> {
                _state.value = _state.value.copy(selectedClient = null)
            }

            is BarIntent.ClientAdded -> {
                val updated = _state.value.clients + intent.client
                _state.value = _state.value.copy(clients = updated)
                recomputeFiltered()
            }

            is BarIntent.ClientUpdated -> {
                val updated =
                    _state.value.clients.map { c ->
                        if (c.id == intent.client.id) intent.client else c
                    }
                _state.value = _state.value.copy(clients = updated)
                recomputeFiltered()
            }

            is BarIntent.TogglePaid -> {
                val updated =
                    _state.value.clients.map { c ->
                        if (c.id == intent.clientId) c.copy(isPaid = !c.isPaid) else c
                    }
                _state.value = _state.value.copy(clients = updated)
                recomputeFiltered()
            }

            is BarIntent.ClientDeleted -> {
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
        val s = _state.value
        var result = s.clients

        // Payment filter
        result =
            when (s.paymentFilter) {
                PaymentFilter.ALL -> result
                PaymentFilter.PAID ->
                    result.filter {
                        it.isPaid ||
                            it.items.sumOf { i -> i.priceCents * i.quantity } == 0L
                    }
                PaymentFilter.UNPAID ->
                    result.filter {
                        !it.isPaid &&
                            it.items.sumOf { i -> i.priceCents * i.quantity } > 0L
                    }
            }

        // Search filter
        if (s.searchQuery.isNotBlank()) {
            val lowerQuery = s.searchQuery.lowercase()
            result =
                result.filter { client ->
                    client.name.lowercase().contains(lowerQuery) ||
                        (client.nickname?.lowercase()?.contains(lowerQuery) == true)
                }
        }

        _state.value = s.copy(filteredClients = result)
    }
}
