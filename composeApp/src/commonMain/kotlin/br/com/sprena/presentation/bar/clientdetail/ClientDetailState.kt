package br.com.sprena.presentation.bar.clientdetail

import br.com.sprena.presentation.bar.BarItem
import br.com.sprena.shared.core.mvi.UiState
import br.com.sprena.shared.core.privacy.formatCpf
import br.com.sprena.shared.core.privacy.maskCpf

/**
 * State da tela de detalhe do cliente — lista de itens consumidos.
 */
data class ClientDetailState(
    val clientId: String = "",
    val clientName: String = "",
    val clientNickname: String? = null,
    val clientPhone: String = "",
    val clientCpf: String = "",
    val clientEmail: String? = null,
    val items: List<BarItem> = emptyList(),
    val isPaid: Boolean = false,
    val totalCents: Long = 0L,
    val isAddItemVisible: Boolean = false,
    val newItemName: String = "",
    val newItemPriceCents: Long? = null,
    val newItemNameError: String? = null,
    val newItemPriceError: String? = null,
    val isDeleteConfirmVisible: Boolean = false,
    val itemsPage: Int = 0,
    val itemsPerPage: Int = 4,
    val itemToDeleteId: String? = null,
    val isCpfRevealed: Boolean = false,
    val canRevealCpf: Boolean = false,
) : UiState {
    /** Itens da página atual. */
    val paginatedItems: List<BarItem>
        get() = items.drop(itemsPage * itemsPerPage).take(itemsPerPage)

    /** Total de páginas. */
    val totalPages: Int
        get() = if (items.isEmpty()) 1 else ((items.size - 1) / itemsPerPage) + 1

    /**
     * CPF como deve aparecer na tela. Mascarado por padrão; só ADM/MOD conseguem
     * revelar — [isCpfRevealed] nunca é ligado sem [canRevealCpf].
     */
    val displayCpf: String
        get() = if (isCpfRevealed) formatCpf(clientCpf) else maskCpf(clientCpf)
}
