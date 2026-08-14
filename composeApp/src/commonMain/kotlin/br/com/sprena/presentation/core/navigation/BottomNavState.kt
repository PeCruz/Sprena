package br.com.sprena.presentation.core.navigation

import br.com.sprena.shared.core.mvi.UiState

/**
 * Abas da barra inferior de navegação.
 * Ordem: Home (clientes esportes), Eventos, Comandas (bar),
 * Financeiro, Perfil.
 *
 * [PROFILE] era `SETTINGS` até F1.6a. A aba passou a ser "Meus dados" — direitos do
 * titular — e Configurações virou uma linha dentro dela, na rota standalone que já
 * existia. O enum é estado em memória, sem persistência, então o rename não migra nada.
 */
enum class BottomTab { HOME, EVENTOS, BAR, FINANCIAL, PROFILE }

data class BottomNavState(
    val current: BottomTab = BottomTab.HOME,
) : UiState
