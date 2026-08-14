package br.com.sprena.presentation.consent

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface ConsentIntent : UiIntent {
    /** Marca/desmarca "li e concordo". */
    data object ToggleRead : ConsentIntent

    /** Grava o aceite da versão vigente. */
    data object Accept : ConsentIntent

    /** Recarrega o texto e reconsulta o consentimento após falha. */
    data object Retry : ConsentIntent

    /**
     * Encerra a sessão e volta ao login.
     *
     * Não é "recusar" — recusar continua sendo fechar o app, por decisão da fase.
     * É a saída de emergência de quem ficou preso no gate e precisa ao menos
     * trocar de conta.
     */
    data object Logout : ConsentIntent
}
