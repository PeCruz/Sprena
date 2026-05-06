package br.com.sprena.presentation.login

import br.com.sprena.shared.core.mvi.UiState

/**
 * Snapshot imutável da tela de Login.
 *
 * Campos sugeridos (Day 3 TDD):
 *  - [username] / [password] — inputs do usuário
 *  - [usernameError] / [passwordError] — mensagens de validação
 *  - [isPasswordVisible] — toggle "olho" no campo senha
 *  - [isLoading] — indicador de autenticação em andamento
 *  - [canSubmit] — derivado: true quando usuário e senha passam na validação
 */
data class LoginState(
    val username: String = "",
    val password: String = "",
    val usernameError: String? = null,
    val passwordError: String? = null,
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val canSubmit: Boolean = false,
    val authError: String? = null,
) : UiState
