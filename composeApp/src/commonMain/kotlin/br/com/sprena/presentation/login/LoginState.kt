package br.com.sprena.presentation.login

import br.com.sprena.shared.core.mvi.UiState

/**
 * Snapshot imutável da tela de Login.
 *
 * Campos principais:
 *  - [email] / [password] — inputs do usuário
 *  - [emailError] / [passwordError] — mensagens de validação inline
 *  - [isPasswordVisible] — toggle "olho" no campo senha
 *  - [isLoading] — indicador de autenticação em andamento
 *  - [canSubmit] — derivado: true quando email e senha passam na validação
 *  - [authError] — erro retornado pelo backend
 *
 * Fluxo "Esqueci a senha":
 *  - [passwordResetDialogOpen] — dialog visível?
 *  - [passwordResetEmail] — email digitado dentro do dialog
 *  - [passwordResetEmailError] — erro de validação do email no dialog
 *  - [passwordResetSending] — chamada em andamento
 */
data class LoginState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val canSubmit: Boolean = false,
    val authError: String? = null,
    val passwordResetDialogOpen: Boolean = false,
    val passwordResetEmail: String = "",
    val passwordResetEmailError: String? = null,
    val passwordResetSending: Boolean = false,
) : UiState
