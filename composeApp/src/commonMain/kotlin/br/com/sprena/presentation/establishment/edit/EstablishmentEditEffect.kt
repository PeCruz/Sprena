package br.com.sprena.presentation.establishment.edit

import br.com.sprena.shared.core.mvi.UiEffect

sealed interface EstablishmentEditEffect : UiEffect {
    /** Gravou: a tela fecha e a lista se atualiza sozinha pelo snapshot do Firestore. */
    data object SavedAndClose : EstablishmentEditEffect

    data class ShowMessage(
        val message: String,
    ) : EstablishmentEditEffect
}
