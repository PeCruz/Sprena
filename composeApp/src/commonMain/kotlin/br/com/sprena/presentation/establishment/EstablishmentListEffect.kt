package br.com.sprena.presentation.establishment

import br.com.sprena.shared.core.mvi.UiEffect

sealed interface EstablishmentListEffect : UiEffect {
    /** [id] nulo abre o formulário em branco — é o mesmo destino para criar e editar. */
    data class NavigateToEdit(
        val id: String?,
    ) : EstablishmentListEffect

    data class ShowMessage(
        val message: String,
    ) : EstablishmentListEffect
}
