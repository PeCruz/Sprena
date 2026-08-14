package br.com.sprena.presentation.consent

import br.com.sprena.shared.core.mvi.UiState

/**
 * State do gate de consentimento LGPD.
 *
 * [canAccept] é derivado: não se aceita política que não carregou nem se aceita
 * duas vezes em paralelo.
 */
data class ConsentState(
    val policyText: String = "",
    val isLoading: Boolean = true,
    val isAccepting: Boolean = false,
    val hasRead: Boolean = false,
    val error: String? = null,
) : UiState {
    val canAccept: Boolean
        get() = hasRead && !isLoading && !isAccepting && policyText.isNotBlank()
}
