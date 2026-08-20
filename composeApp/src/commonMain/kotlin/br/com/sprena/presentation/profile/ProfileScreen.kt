package br.com.sprena.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.sprena.core.platform.ExportPayload
import br.com.sprena.core.platform.rememberDataExportSharer
import br.com.sprena.core.ui.components.SectionTitle
import br.com.sprena.core.ui.components.SettingsRow
import br.com.sprena.core.ui.components.ThemeToggleButton
import br.com.sprena.presentation.core.theme.ThemeViewModel
import br.com.sprena.shared.sportclient.domain.validation.SportModality
import org.koin.compose.viewmodel.koinViewModel

/**
 * Tela "Meus dados" — direitos do titular sobre a própria conta (F1.6a).
 *
 * Substitui `SettingsScreen` na aba do BottomNav; Configurações vira uma linha aqui
 * dentro apontando para a rota que já existe. Isso também elimina o render duplicado de
 * `SettingsScreen`, que antes existia como aba **e** como rota.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    themeViewModel: ThemeViewModel,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
    navigation: ProfileNavigation = ProfileNavigation(),
    onNavigateBack: (() -> Unit)? = null,
    /**
     * Liga a seção "Administração" (Estabelecimentos e Moderadores).
     *
     * A aba Config do ADM reaproveita esta tela em vez de ter uma própria: o ADM também é
     * titular, e duplicar a tela deixaria a exportação e a exclusão de conta (F1.6a) só de um
     * lado. `TabMatrixTest` tem um caso que quebra se algum papel perder essas portas.
     */
    showAdminSection: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val shareExport = rememberDataExportSharer()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProfileEffect.NavigateToLogin -> navigation.onNavigateToLogin()
                is ProfileEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                is ProfileEffect.ShareExport ->
                    shareExport(ExportPayload(fileName = effect.fileName, content = effect.json))
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { ProfileTopBar(themeViewModel, onNavigateBack) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
        ) {
            when {
                state.isLoading -> LoadingSection()
                state.error != null ->
                    ErrorSection(
                        message = state.error.orEmpty(),
                        onRetry = { viewModel.handleIntent(ProfileIntent.Retry) },
                    )

                else -> ProfileContent(state, viewModel, navigation, showAdminSection)
            }
        }
    }

    if (state.isExportDialogVisible) {
        ExportConfirmationDialog(
            onConfirm = { viewModel.handleIntent(ProfileIntent.ExportConfirmed) },
            onDismiss = { viewModel.handleIntent(ProfileIntent.ExportDismissed) },
        )
    }

    if (state.isDeleteDialogVisible) {
        DeleteAccountDialog(state = state, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTopBar(
    themeViewModel: ThemeViewModel,
    onNavigateBack: (() -> Unit)?,
) {
    TopAppBar(
        title = { Text("Perfil") },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            }
        },
        actions = { ThemeToggleButton(themeViewModel = themeViewModel) },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@Composable
private fun ProfileContent(
    state: ProfileState,
    viewModel: ProfileViewModel,
    navigation: ProfileNavigation,
    showAdminSection: Boolean,
) {
    if (showAdminSection) {
        SectionTitle("Administração")
        SettingsRow(
            title = "Estabelecimentos",
            subtitle = "Cadastrar, editar e desativar",
            onClick = navigation.onNavigateEstablishments,
        )
        SettingsRow(
            title = "Moderadores",
            subtitle = "Quem gere cada estabelecimento",
            onClick = navigation.onNavigateModerators,
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
    }

    SectionTitle("Meus dados")
    if (state.isEditing) {
        ProfileForm(state, viewModel)
    } else {
        ProfileFields(state, viewModel)
    }

    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))

    PrivacySection(state, viewModel, navigation)

    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))

    AccountActions(state, viewModel)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun PrivacySection(
    state: ProfileState,
    viewModel: ProfileViewModel,
    navigation: ProfileNavigation,
) {
    SectionTitle("Segurança e privacidade")
    SettingsRow(
        title = "Redefinir senha",
        subtitle =
            if (state.isSendingPasswordReset) {
                "Enviando..."
            } else {
                "Enviamos um link para o seu e-mail"
            },
        onClick = { viewModel.handleIntent(ProfileIntent.PasswordResetRequested) },
    )
    SettingsRow(
        title = "Política de privacidade",
        subtitle = "Como seus dados são tratados",
        onClick = navigation.onNavigatePrivacyPolicy,
    )
    SettingsRow(
        title = "Exportar meus dados",
        subtitle = if (state.isExporting) "Gerando arquivo..." else "Baixe uma cópia em JSON",
        onClick = { viewModel.handleIntent(ProfileIntent.ExportClicked) },
    )
    SettingsRow(
        title = "Configurações",
        subtitle = "Cardápio, categorias e preferências",
        onClick = navigation.onNavigateSettings,
    )
}

@Composable
private fun AccountActions(
    state: ProfileState,
    viewModel: ProfileViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = { viewModel.handleIntent(ProfileIntent.Logout) },
            modifier = Modifier.weight(1f),
            enabled = !state.loggingOut,
        ) {
            Text(if (state.loggingOut) "Saindo..." else "Sair")
        }
        Button(
            onClick = { viewModel.handleIntent(ProfileIntent.DeleteClicked) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Excluir conta")
        }
    }
}

@Composable
private fun ProfileFields(
    state: ProfileState,
    viewModel: ProfileViewModel,
) {
    ReadOnlyField(
        "Papel",
        state.profile
            ?.role
            ?.displayName
            .orEmpty(),
    )
    ReadOnlyField("Nome", state.displayName)
    ReadOnlyField("Apelido", state.displayApelido)
    ReadOnlyField("E-mail", state.profile?.email.orEmpty())
    RevealableField(
        label = "CPF",
        value = state.displayCpf,
        isRevealed = state.isCpfRevealed,
        hasValue = state.profile?.cpf != null,
        onToggle = { viewModel.handleIntent(ProfileIntent.ToggleCpfReveal) },
    )
    RevealableField(
        label = "Telefone",
        value = state.displayPhone,
        isRevealed = state.isPhoneRevealed,
        hasValue = state.profile?.phone != null,
        onToggle = { viewModel.handleIntent(ProfileIntent.TogglePhoneReveal) },
    )
    ReadOnlyField("Modalidades", state.displayModalities)

    Spacer(Modifier.height(8.dp))
    TextButton(
        onClick = { viewModel.handleIntent(ProfileIntent.EditClicked) },
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Text("Editar meus dados")
    }
}

@Composable
private fun ProfileForm(
    state: ProfileState,
    viewModel: ProfileViewModel,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.draft.apelido,
            onValueChange = { viewModel.handleIntent(ProfileIntent.ApelidoChanged(it)) },
            label = { Text("Apelido") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.draft.cpf,
            onValueChange = { viewModel.handleIntent(ProfileIntent.CpfChanged(it)) },
            label = { Text("CPF") },
            singleLine = true,
            isError = state.cpfError != null,
            supportingText = state.cpfError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.draft.phone,
            onValueChange = { viewModel.handleIntent(ProfileIntent.PhoneChanged(it)) },
            label = { Text("Telefone") },
            singleLine = true,
            isError = state.phoneError != null,
            supportingText = state.phoneError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        ModalityChips(state, viewModel)
        Spacer(Modifier.height(16.dp))
        FormActions(state, viewModel)
    }
}

@Composable
private fun ModalityChips(
    state: ProfileState,
    viewModel: ProfileViewModel,
) {
    Text("Modalidades que você joga", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SportModality.entries.forEach { modality ->
            FilterChip(
                selected = modality in state.draft.modalities,
                onClick = { viewModel.handleIntent(ProfileIntent.ModalityToggled(modality)) },
                label = { Text(modality.label) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun FormActions(
    state: ProfileState,
    viewModel: ProfileViewModel,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { viewModel.handleIntent(ProfileIntent.EditCancelled) },
            modifier = Modifier.weight(1f),
            enabled = !state.isSaving,
        ) {
            Text("Cancelar")
        }
        Button(
            onClick = { viewModel.handleIntent(ProfileIntent.SaveClicked) },
            modifier = Modifier.weight(1f),
            enabled = state.canSave,
        ) {
            Text(if (state.isSaving) "Salvando..." else "Salvar")
        }
    }
}

@Composable
private fun ExportConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar meus dados") },
        text = {
            Text(
                "O arquivo contém seus dados pessoais completos, incluindo CPF e telefone " +
                    "sem máscara. Escolha com cuidado para onde enviá-lo.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Exportar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Diálogo de exclusão. Exigir a digitação de `EXCLUIR` não é requisito da Play Store —
 * é a única barreira contra o pior acidente possível no app, e custa uma linha de State.
 *
 * Em falha ele **permanece aberto** com o erro: nada foi apagado, e o titular precisa
 * ver o resultado onde tomou a decisão.
 */
@Composable
private fun DeleteAccountDialog(
    state: ProfileState,
    viewModel: ProfileViewModel,
) {
    AlertDialog(
        onDismissRequest = { viewModel.handleIntent(ProfileIntent.DeleteDismissed) },
        title = { Text("Excluir conta") },
        text = {
            Column {
                Text("Esta ação é irreversível.")
                Spacer(Modifier.height(8.dp))
                Text("• Seu perfil, seu acesso e o registro do aceite da política são apagados.")
                Text(
                    "• Lançamentos financeiros históricos são mantidos de forma anonimizada, " +
                        "por obrigação legal (LGPD art. 16, I).",
                )
                Text(
                    "• Os clientes cadastrados por você continuam existindo: são dados do " +
                        "estabelecimento, não seus.",
                )
                Spacer(Modifier.height(16.dp))
                Text("Digite $DELETE_KEYWORD para confirmar:")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.deleteConfirmationText,
                    onValueChange = {
                        viewModel.handleIntent(ProfileIntent.DeleteConfirmationChanged(it))
                    },
                    singleLine = true,
                    isError = state.deleteError != null,
                    supportingText = state.deleteError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.handleIntent(ProfileIntent.DeleteConfirmed) },
                enabled = state.canConfirmDelete,
            ) {
                Text(
                    text = if (state.isDeleting) "Excluindo..." else "Excluir definitivamente",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.handleIntent(ProfileIntent.DeleteDismissed) },
                enabled = !state.isDeleting,
            ) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun LoadingSection() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorSection(
    message: String,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("Tentar de novo") }
    }
}

@Composable
private fun ReadOnlyField(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Campo mascarado com botão de revelar.
 *
 * Aqui `canReveal` é sempre verdadeiro — o dado é do próprio titular. A máscara existe
 * contra ombro e gravação de tela, não contra o dono, ao contrário do CPF de clientes
 * em `SportClientScreen`, onde a revelação depende da role.
 */
@Composable
private fun RevealableField(
    label: String,
    value: String,
    isRevealed: Boolean,
    hasValue: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        if (hasValue) {
            TextButton(onClick = onToggle) {
                Text(
                    text = if (isRevealed) "🙈" else "👁",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
