package br.com.sprena.presentation.consent

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface ConsentIntent : UiIntent {
    /** Marca/desmarca "li e concordo". */
    data object ToggleRead : ConsentIntent

    /** Grava o aceite da versão vigente. */
    data object Accept : ConsentIntent

    /** Recarrega o texto após falha. */
    data object Retry : ConsentIntent
}
