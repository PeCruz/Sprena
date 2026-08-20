package br.com.sprena.presentation.establishment.moderators

import br.com.sprena.shared.core.mvi.UiEffect

sealed interface ModeratorsEffect : UiEffect {
    data class ShowMessage(
        val message: String,
    ) : ModeratorsEffect
}
