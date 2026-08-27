package br.com.sprena.presentation.establishment

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface EstablishmentListIntent : UiIntent {
    data object CreateClicked : EstablishmentListIntent

    data class EstablishmentClicked(
        val id: String,
    ) : EstablishmentListIntent

    /** [active] é o valor desejado, não o atual — evita o ViewModel reler a lista para inverter. */
    data class ToggleActive(
        val id: String,
        val active: Boolean,
    ) : EstablishmentListIntent
}
