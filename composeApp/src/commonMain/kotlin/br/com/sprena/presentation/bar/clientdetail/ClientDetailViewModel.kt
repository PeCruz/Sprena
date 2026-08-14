package br.com.sprena.presentation.bar.clientdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.presentation.bar.BarClient
import br.com.sprena.presentation.bar.BarItem
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.bar.domain.validation.BarValidator
import br.com.sprena.shared.core.mvi.MviViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ViewModel do detalhe do cliente — gerencia itens consumidos,
 * toggle de pagamento, e exclusão com confirmação.
 */
@OptIn(ExperimentalUuidApi::class)
class ClientDetailViewModel(
    private val client: BarClient,
    private val sessionStore: SessionStore,
) : ViewModel(),
    MviViewModel<ClientDetailState, ClientDetailIntent, ClientDetailEffect> {
    private val _state =
        MutableStateFlow(
            ClientDetailState(
                clientId = client.id,
                clientName = client.name,
                clientNickname = client.nickname,
                clientPhone = client.phone,
                clientCpf = client.cpf,
                clientEmail = client.email,
                items = client.items,
                isPaid = client.isPaid,
                totalCents = client.items.sumOf { it.priceCents * it.quantity },
            ),
        )
    override val state: StateFlow<ClientDetailState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ClientDetailEffect>()
    override val effects: SharedFlow<ClientDetailEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            val role = sessionStore.load()?.role
            _state.value = _state.value.copy(canRevealCpf = role != null && role in STAFF_ROLES)
        }
    }

    override fun handleIntent(intent: ClientDetailIntent) {
        when (intent) {
            is ClientDetailIntent.AddItemClicked -> {
                _state.value = _state.value.copy(isAddItemVisible = true)
            }

            is ClientDetailIntent.DismissAddItem -> {
                _state.value =
                    _state.value.copy(
                        isAddItemVisible = false,
                        newItemName = "",
                        newItemPriceCents = null,
                        newItemNameError = null,
                        newItemPriceError = null,
                    )
            }

            is ClientDetailIntent.MenuItemSelected -> {
                val nameResult = BarValidator.validateItemName(intent.menuItem.name)
                val priceResult = BarValidator.validateItemPrice(intent.menuItem.priceCents)
                _state.value =
                    _state.value.copy(
                        newItemName = intent.menuItem.name,
                        newItemPriceCents = intent.menuItem.priceCents,
                        newItemNameError = nameResult.errorMessage,
                        newItemPriceError = priceResult.errorMessage,
                    )
            }

            is ClientDetailIntent.NewItemNameChanged -> {
                val result = BarValidator.validateItemName(intent.name)
                _state.value =
                    _state.value.copy(
                        newItemName = intent.name,
                        newItemNameError = result.errorMessage,
                    )
            }

            is ClientDetailIntent.NewItemPriceChanged -> {
                val result = BarValidator.validateItemPrice(intent.priceCents)
                _state.value =
                    _state.value.copy(
                        newItemPriceCents = intent.priceCents,
                        newItemPriceError = result.errorMessage,
                    )
            }

            is ClientDetailIntent.SaveItem -> {
                val s = _state.value
                val nameResult = BarValidator.validateItemName(s.newItemName)
                val priceResult = BarValidator.validateItemPrice(s.newItemPriceCents)

                if (!nameResult.isValid || !priceResult.isValid) {
                    _state.value =
                        s.copy(
                            newItemNameError = nameResult.errorMessage,
                            newItemPriceError = priceResult.errorMessage,
                        )
                    return
                }

                val trimmedName = s.newItemName.trim()
                val price = s.newItemPriceCents!!

                // Merge: se já existe item com mesmo nome e preço, incrementa quantity
                val existingIndex =
                    s.items.indexOfFirst {
                        it.name == trimmedName && it.priceCents == price
                    }
                val updatedItems =
                    if (existingIndex >= 0) {
                        s.items.mapIndexed { index, item ->
                            if (index == existingIndex) {
                                item.copy(quantity = item.quantity + 1)
                            } else {
                                item
                            }
                        }
                    } else {
                        val newItem =
                            BarItem(
                                id = "item_${Uuid.random()}",
                                name = trimmedName,
                                priceCents = price,
                            )
                        s.items + newItem
                    }

                _state.value =
                    s.copy(
                        items = updatedItems,
                        totalCents = updatedItems.sumOf { it.priceCents * it.quantity },
                        isAddItemVisible = false,
                        newItemName = "",
                        newItemPriceCents = null,
                        newItemNameError = null,
                        newItemPriceError = null,
                    )
                viewModelScope.launch {
                    _effects.emit(ClientDetailEffect.ClientUpdated(buildClient()))
                }
            }

            is ClientDetailIntent.RemoveItem -> {
                val s = _state.value
                val updatedItems = s.items.filter { it.id != intent.itemId }
                val newTotalPages =
                    if (updatedItems.isEmpty()) {
                        1
                    } else {
                        ((updatedItems.size - 1) / s.itemsPerPage) + 1
                    }
                val adjustedPage = s.itemsPage.coerceAtMost(newTotalPages - 1)
                _state.value =
                    s.copy(
                        items = updatedItems,
                        totalCents = updatedItems.sumOf { it.priceCents * it.quantity },
                        itemsPage = adjustedPage,
                    )
                viewModelScope.launch {
                    _effects.emit(ClientDetailEffect.ClientUpdated(buildClient()))
                }
            }

            is ClientDetailIntent.IncrementItem -> {
                val s = _state.value
                val updatedItems =
                    s.items.map { item ->
                        if (item.id == intent.itemId) item.copy(quantity = item.quantity + 1) else item
                    }
                _state.value =
                    s.copy(
                        items = updatedItems,
                        totalCents = updatedItems.sumOf { it.priceCents * it.quantity },
                    )
                viewModelScope.launch {
                    _effects.emit(ClientDetailEffect.ClientUpdated(buildClient()))
                }
            }

            is ClientDetailIntent.DecrementItem -> {
                val s = _state.value
                val item = s.items.firstOrNull { it.id == intent.itemId } ?: return
                if (item.quantity > 1) {
                    val updatedItems =
                        s.items.map {
                            if (it.id == intent.itemId) it.copy(quantity = it.quantity - 1) else it
                        }
                    _state.value =
                        s.copy(
                            items = updatedItems,
                            totalCents = updatedItems.sumOf { it.priceCents * it.quantity },
                        )
                    viewModelScope.launch {
                        _effects.emit(ClientDetailEffect.ClientUpdated(buildClient()))
                    }
                } else {
                    // quantity == 1 → show delete confirmation
                    _state.value = s.copy(itemToDeleteId = intent.itemId)
                }
            }

            is ClientDetailIntent.ConfirmDeleteItem -> {
                val s = _state.value
                val itemId = s.itemToDeleteId ?: return
                val updatedItems = s.items.filter { it.id != itemId }
                val newTotalPages =
                    if (updatedItems.isEmpty()) {
                        1
                    } else {
                        ((updatedItems.size - 1) / s.itemsPerPage) + 1
                    }
                val adjustedPage = s.itemsPage.coerceAtMost(newTotalPages - 1)
                _state.value =
                    s.copy(
                        items = updatedItems,
                        totalCents = updatedItems.sumOf { it.priceCents * it.quantity },
                        itemToDeleteId = null,
                        itemsPage = adjustedPage,
                    )
                viewModelScope.launch {
                    _effects.emit(ClientDetailEffect.ClientUpdated(buildClient()))
                }
            }

            is ClientDetailIntent.CancelDeleteItem -> {
                _state.value = _state.value.copy(itemToDeleteId = null)
            }

            is ClientDetailIntent.NextItemsPage -> {
                val s = _state.value
                val maxPage = s.totalPages - 1
                if (s.itemsPage < maxPage) {
                    _state.value = s.copy(itemsPage = s.itemsPage + 1)
                }
            }

            is ClientDetailIntent.PrevItemsPage -> {
                val s = _state.value
                if (s.itemsPage > 0) {
                    _state.value = s.copy(itemsPage = s.itemsPage - 1)
                }
            }

            is ClientDetailIntent.TogglePaid -> {
                _state.value = _state.value.copy(isPaid = !_state.value.isPaid)
                viewModelScope.launch {
                    _effects.emit(ClientDetailEffect.ClientUpdated(buildClient()))
                }
            }

            is ClientDetailIntent.DeleteClicked -> {
                _state.value = _state.value.copy(isDeleteConfirmVisible = true)
            }

            is ClientDetailIntent.DeleteCancelled -> {
                _state.value = _state.value.copy(isDeleteConfirmVisible = false)
            }

            is ClientDetailIntent.DeleteConfirmed -> {
                _state.value = _state.value.copy(isDeleteConfirmVisible = false)
                viewModelScope.launch {
                    _effects.emit(ClientDetailEffect.ClientDeleted(_state.value.clientId))
                }
            }

            is ClientDetailIntent.Dismiss -> {
                viewModelScope.launch {
                    _effects.emit(ClientDetailEffect.Dismissed)
                }
            }

            is ClientDetailIntent.ToggleCpfReveal -> {
                if (_state.value.canRevealCpf) {
                    _state.value = _state.value.copy(isCpfRevealed = !_state.value.isCpfRevealed)
                }
            }
        }
    }

    private fun buildClient(): BarClient {
        val s = _state.value
        return BarClient(
            id = s.clientId,
            name = s.clientName,
            nickname = s.clientNickname,
            phone = s.clientPhone,
            cpf = s.clientCpf,
            email = s.clientEmail,
            items = s.items,
            isPaid = s.isPaid,
        )
    }

    private companion object {
        val STAFF_ROLES = setOf(UserRole.ADM, UserRole.MOD)
    }
}
