package br.com.sprena.presentation.establishment.moderators

import br.com.sprena.shared.core.mvi.UiIntent
import br.com.sprena.shared.establishment.domain.model.MemberRole

sealed interface ModeratorsIntent : UiIntent {
    data class EstablishmentSelected(
        val id: String,
    ) : ModeratorsIntent

    data object LinkClicked : ModeratorsIntent

    data object LinkDismissed : ModeratorsIntent

    data class LinkCpfChanged(
        val value: String,
    ) : ModeratorsIntent

    data class LinkNameChanged(
        val value: String,
    ) : ModeratorsIntent

    data class LinkRoleChanged(
        val value: MemberRole,
    ) : ModeratorsIntent

    data object LinkConfirmed : ModeratorsIntent

    data class RemoveMember(
        val uid: String,
    ) : ModeratorsIntent
}
