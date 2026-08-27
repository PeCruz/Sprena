package br.com.sprena.presentation.establishment.moderators

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface ModeratorsIntent : UiIntent {
    data class EstablishmentSelected(
        val id: String,
    ) : ModeratorsIntent
}
