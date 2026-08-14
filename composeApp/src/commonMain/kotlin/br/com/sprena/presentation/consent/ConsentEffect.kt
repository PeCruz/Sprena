package br.com.sprena.presentation.consent

import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.mvi.UiEffect

sealed interface ConsentEffect : UiEffect {
    /** Carrega a sessão junto porque a rota da Home precisa dos dados do usuário. */
    data class NavigateHome(
        val session: SessionUser,
    ) : ConsentEffect

    /** Sessão sumiu no meio do fluxo — estado inconsistente, volta para o login. */
    data object NavigateLogin : ConsentEffect
}
