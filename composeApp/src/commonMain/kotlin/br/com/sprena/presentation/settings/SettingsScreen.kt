package br.com.sprena.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.sprena.core.ui.components.ThemeToggleButton
import br.com.sprena.presentation.core.theme.ThemeViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Tela de Configurações — acessível via BottomNav (aba "Config").
 * Lista de opções como "Cardápio" que navegam para sub-telas.
 *
 * Overload sem onNavigateBack é usado quando é aba do BottomNav.
 * Overload com onNavigateBack é usado quando navegado via rota direta.
 *
 * Inclui seção "Conta" no topo com email + role do usuário e botão Sair.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeViewModel: ThemeViewModel,
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = koinViewModel(),
    navigation: SettingsNavigation = SettingsNavigation(),
) {
    val settingsState by settingsViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        settingsViewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.NavigateToLogin -> navigation.onNavigateToLogin()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            SettingsTopBar(
                themeViewModel = themeViewModel,
                onNavigateBack = navigation.onNavigateBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            AccountSection(
                state = settingsState,
                onLogout = { settingsViewModel.handleIntent(SettingsIntent.Logout) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            ComandasSection(onNavigateMenu = navigation.onNavigateMenu)
            Spacer(modifier = Modifier.height(8.dp))
            FinanceiroSection(onNavigateCategory = navigation.onNavigateCategory)
            Spacer(modifier = Modifier.height(8.dp))
            PrivacidadeSection(onNavigatePrivacyPolicy = navigation.onNavigatePrivacyPolicy)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    themeViewModel: ThemeViewModel,
    onNavigateBack: (() -> Unit)?,
) {
    TopAppBar(
        title = { Text("Configurações") },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                    )
                }
            }
        },
        actions = {
            ThemeToggleButton(themeViewModel = themeViewModel)
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@Composable
private fun AccountSection(
    state: SettingsState,
    onLogout: () -> Unit,
) {
    SectionTitle(title = "Conta")
    val user = state.user
    if (user != null) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Perfil: ${user.role.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    TextButton(
        onClick = onLogout,
        enabled = !state.loggingOut,
        modifier = Modifier.padding(horizontal = 16.dp),
        colors =
            ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
    ) {
        Text(if (state.loggingOut) "Saindo..." else "Sair")
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ComandasSection(onNavigateMenu: () -> Unit) {
    SectionTitle(title = "Comandas")
    SettingsItem(
        icon = {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = "Cardápio",
        subtitle = "Configurar itens do cardápio",
        onClick = onNavigateMenu,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun FinanceiroSection(onNavigateCategory: () -> Unit) {
    SectionTitle(title = "Categorias")
    SettingsItem(
        icon = {
            Text(
                text = "R$",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        title = "Categorias",
        subtitle = "Gerenciar categorias de transações",
        onClick = onNavigateCategory,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun PrivacidadeSection(onNavigatePrivacyPolicy: () -> Unit) {
    SectionTitle(title = "Privacidade")
    SettingsItem(
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = "Política de Privacidade",
        subtitle = "Como seus dados são tratados",
        onClick = onNavigatePrivacyPolicy,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingsItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
