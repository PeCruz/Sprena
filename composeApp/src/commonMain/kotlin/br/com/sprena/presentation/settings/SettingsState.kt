package br.com.sprena.presentation.settings

import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.mvi.UiState

/**
 * Snapshot imutável da tela de Configurações.
 *
 * @property user Sessão atual (email + role) — null antes de carregar.
 * @property loggingOut true enquanto a operação de logout está em andamento.
 */
data class SettingsState(
    val user: SessionUser? = null,
    val loggingOut: Boolean = false,
) : UiState
