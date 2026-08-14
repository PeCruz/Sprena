package br.com.sprena.presentation.profile

/**
 * Callbacks de navegação da tela "Meus dados".
 *
 * Mesmo padrão de `SettingsNavigation` — o ponto de extensão fica no data class, e não
 * em parâmetros soltos do Composable.
 */
data class ProfileNavigation(
    val onNavigateToLogin: () -> Unit = {},
    val onNavigateSettings: () -> Unit = {},
    val onNavigatePrivacyPolicy: () -> Unit = {},
)
