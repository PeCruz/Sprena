package br.com.sprena.presentation.establishment

import br.com.sprena.shared.core.mvi.UiState
import br.com.sprena.shared.establishment.domain.model.Establishment

/**
 * Lista de estabelecimentos (ADM).
 *
 * [error] e lista vazia são estados diferentes e a tela precisa distingui-los: vazio significa
 * "ainda não há nenhum" e convida a cadastrar; erro significa "não consegui ler" e oferece
 * tentar de novo. Mostrar o convite quando a leitura falhou levaria o ADM a cadastrar de novo
 * um estabelecimento que já existe.
 */
data class EstablishmentListState(
    val establishments: List<Establishment> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
) : UiState {
    val isEmpty: Boolean
        get() = !isLoading && error == null && establishments.isEmpty()
}
