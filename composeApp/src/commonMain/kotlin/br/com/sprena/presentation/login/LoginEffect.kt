package br.com.sprena.presentation.login

import br.com.sprena.shared.auth.domain.model.UserModel
import br.com.sprena.shared.core.mvi.UiEffect

sealed interface LoginEffect : UiEffect {
    data class NavigateHome(val user: UserModel) : LoginEffect
    data class ShowError(val message: String) : LoginEffect
}
