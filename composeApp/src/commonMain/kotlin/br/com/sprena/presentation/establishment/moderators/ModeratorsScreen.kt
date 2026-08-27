package br.com.sprena.presentation.establishment.moderators

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.sprena.core.ui.components.ThemeToggleButton
import br.com.sprena.core.ui.mask.CpfMaskTransformation
import br.com.sprena.core.ui.mask.filterDigitsOnly
import br.com.sprena.presentation.core.theme.ThemeViewModel
import br.com.sprena.shared.establishment.domain.model.MemberRole
import org.koin.compose.viewmodel.koinViewModel

private const val INACTIVE_ALPHA = 0.5f
private const val CPF_DIGITS = 11

/**
 * Membros de um estabelecimento (ADM).
 *
 * Somente leitura nesta fatia. Vincular alguém passa pela callable `linkMemberByCpf`, que roda
 * com Admin SDK — `members` é `write: if false` nas rules, então um botão que gravasse daqui
 * seria negado pelo servidor de qualquer forma.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeratorsScreen(
    themeViewModel: ThemeViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModeratorsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ModeratorsEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    state.linkDraft?.let { draft ->
        LinkDialog(
            draft = draft,
            onIntent = viewModel::handleIntent,
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Moderadores") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = { ThemeToggleButton(themeViewModel) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.error != null -> Centered(state.error!!)

                state.hasNoEstablishments ->
                    Centered("Cadastre um estabelecimento antes de vincular pessoas a ele.")

                else -> MembersContent(state, viewModel)
            }
        }
    }
}

@Composable
private fun MembersContent(
    state: ModeratorsState,
    viewModel: ModeratorsViewModel,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        EstablishmentPicker(
            state = state,
            onSelect = { viewModel.handleIntent(ModeratorsIntent.EstablishmentSelected(it)) },
        )

        OutlinedButton(
            onClick = { viewModel.handleIntent(ModeratorsIntent.LinkClicked) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
        ) {
            Text("Vincular por CPF")
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoadingMembers ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.membersError != null ->
                    Text(
                        text = state.membersError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )

                state.members.isEmpty() ->
                    Text(
                        text = "Ninguém vinculado a este estabelecimento",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )

                else ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.members, key = { it.uid }) { member ->
                            MemberLine(member) {
                                viewModel.handleIntent(ModeratorsIntent.RemoveMember(member.uid))
                            }
                        }
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EstablishmentPicker(
    state: ModeratorsState,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = state.selectedEstablishment?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Estabelecimento") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.establishments.forEach { establishment ->
                DropdownMenuItem(
                    text = { Text(establishment.name) },
                    onClick = {
                        onSelect(establishment.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MemberLine(
    member: MemberRow,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .alpha(if (member.active) 1f else INACTIVE_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(member.label, style = MaterialTheme.typography.bodyLarge)
            if (!member.active) {
                Text(
                    text = "Desligado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AssistChip(onClick = {}, enabled = false, label = { Text(member.role.displayName) })
        if (member.active) {
            TextButton(onClick = onRemove) { Text("Desligar") }
        }
    }
}

@Composable
private fun BoxScope.Centered(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center).padding(32.dp),
    )
}

/**
 * Formulário de vinculação.
 *
 * O CPF é o único campo obrigatório de verdade — mas o nome também é exigido, porque sem ele o
 * vínculo nasceria sem `displayName` e a lista mostraria um identificador opaco.
 *
 * O texto de rodapé é deliberado: quem vincula precisa saber que não descobre nada sobre o CPF
 * digitado. Sem isso, a tela pareceria uma busca que não achou ninguém.
 */
@Composable
private fun LinkDialog(
    draft: LinkDraft,
    onIntent: (ModeratorsIntent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onIntent(ModeratorsIntent.LinkDismissed) },
        title = { Text("Vincular por CPF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.cpf,
                    onValueChange = {
                        onIntent(ModeratorsIntent.LinkCpfChanged(filterDigitsOnly(it, CPF_DIGITS)))
                    },
                    label = { Text("CPF") },
                    isError = draft.cpfError != null,
                    supportingText = draft.cpfError?.let { message -> { Text(message) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = CpfMaskTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onIntent(ModeratorsIntent.LinkNameChanged(it)) },
                    label = { Text("Nome") },
                    isError = draft.nameError != null,
                    supportingText = draft.nameError?.let { message -> { Text(message) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MemberRole.entries.forEach { role ->
                        FilterChip(
                            selected = draft.role == role,
                            onClick = { onIntent(ModeratorsIntent.LinkRoleChanged(role)) },
                            label = { Text(role.displayName) },
                        )
                    }
                }
                Text(
                    text =
                        "Se a pessoa ainda não usa o app, o vínculo vale assim que ela " +
                            "entrar e informar este CPF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onIntent(ModeratorsIntent.LinkConfirmed) },
                enabled = draft.canSubmit,
            ) {
                Text(if (draft.isSubmitting) "Vinculando..." else "Vincular")
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(ModeratorsIntent.LinkDismissed) }) { Text("Cancelar") }
        },
    )
}
