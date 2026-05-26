package br.com.sprena.presentation.settings

/**
 * Callbacks de navegação da tela de Configurações, agrupados para evitar
 * lista de parâmetros longa.
 */
data class SettingsNavigation(
    val onNavigateBack: (() -> Unit)? = null,
    val onNavigateMenu: () -> Unit = {},
    val onNavigateCategory: () -> Unit = {},
    val onNavigateToLogin: () -> Unit = {},
)
