package br.com.sprena.presentation.sportclient

import br.com.sprena.shared.core.mvi.UiState
import br.com.sprena.shared.sportclient.domain.validation.PaymentMethod
import br.com.sprena.shared.sportclient.domain.validation.SportModality

/**
 * Cliente de esportes (futevôlei, beach tennis, vôlei).
 *
 * @param cashAmountCents valor em dinheiro (centavos) — obrigatório para todos os métodos.
 * @param paymentHistory lista de meses pagos, formato MM/YYYY.
 */
data class SportClient(
    val id: String,
    val name: String,
    val apelido: String = "",
    val cpf: String,
    val phone: String,
    val modalities: List<SportModality>,
    val attendance: Int,
    val paymentMethod: PaymentMethod,
    val cashAmountCents: Long,
    val paymentHistory: List<String> = emptyList(),
) {
    /** Último mês pago (derivado do histórico). */
    val lastPaymentMonth: String
        get() = paymentHistory.lastOrNull() ?: ""
}

/**
 * State da tela Home — gestão de clientes de esportes.
 */
data class SportClientState(
    val clients: List<SportClient> = emptyList(),
    val searchQuery: String = "",
    val filteredClients: List<SportClient> = emptyList(),
    val isLoading: Boolean = false,
    val isAddDialogVisible: Boolean = false,
    val selectedClient: SportClient? = null,
    val error: String? = null,
) : UiState
