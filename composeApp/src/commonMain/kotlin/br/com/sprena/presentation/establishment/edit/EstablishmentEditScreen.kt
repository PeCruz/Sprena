package br.com.sprena.presentation.establishment.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.sprena.core.ui.components.SectionTitle
import br.com.sprena.core.ui.components.ThemeToggleButton
import br.com.sprena.core.ui.mask.CnpjMaskTransformation
import br.com.sprena.core.ui.mask.PhoneMaskTransformation
import br.com.sprena.core.ui.mask.filterDigitsOnly
import br.com.sprena.presentation.core.theme.ThemeViewModel

private const val CNPJ_DIGITS = 14
private const val PHONE_DIGITS = 11
private const val ZIP_DIGITS = 8

/**
 * Cadastro e edição de estabelecimento (ADM).
 *
 * CNPJ, telefone e CEP guardam **só dígitos** no estado; a pontuação é `VisualTransformation`.
 * É essa forma que a rule valida e que vira o id em `cnpj_index`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstablishmentEditScreen(
    themeViewModel: ThemeViewModel,
    viewModel: EstablishmentEditViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EstablishmentEditEffect.SavedAndClose -> onNavigateBack()
                is EstablishmentEditEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        modifier = modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isCreating) "Novo estabelecimento" else "Editar estabelecimento") },
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

                state.error != null ->
                    Text(
                        text = state.error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )

                else -> EditForm(state, viewModel)
            }
        }
    }
}

@Composable
private fun EditForm(
    state: EstablishmentEditState,
    viewModel: EstablishmentEditViewModel,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
        ) {
            IdentificationSection(state, viewModel)
            ContactSection(state, viewModel)
            AddressSection(state.draft, viewModel)
            ActiveToggle(state.draft.active) {
                viewModel.handleIntent(EstablishmentEditIntent.ActiveChanged(it))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = { viewModel.handleIntent(EstablishmentEditIntent.SaveClicked) },
            enabled = state.canSave,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        ) {
            Text(if (state.isSaving) "Salvando..." else "Salvar")
        }
    }
}

@Composable
private fun IdentificationSection(
    state: EstablishmentEditState,
    viewModel: EstablishmentEditViewModel,
) {
    SectionTitle("Identificação", modifier = Modifier.padding(horizontal = 0.dp))
    EditField(
        value = state.draft.name,
        onValueChange = { viewModel.handleIntent(EstablishmentEditIntent.NameChanged(it)) },
        label = "Nome *",
        error = state.nameError,
    )
    EditField(
        value = state.draft.cnpj,
        onValueChange = {
            viewModel.handleIntent(
                EstablishmentEditIntent.CnpjChanged(filterDigitsOnly(it, CNPJ_DIGITS)),
            )
        },
        label = "CNPJ *",
        error = state.cnpjError,
        keyboardType = KeyboardType.Number,
        visualTransformation = CnpjMaskTransformation(),
    )
    EditField(
        value = state.draft.razaoSocial,
        onValueChange = { viewModel.handleIntent(EstablishmentEditIntent.RazaoSocialChanged(it)) },
        label = "Razão social",
        error = state.razaoSocialError,
    )
}

@Composable
private fun ContactSection(
    state: EstablishmentEditState,
    viewModel: EstablishmentEditViewModel,
) {
    SectionTitle("Contato", modifier = Modifier.padding(horizontal = 0.dp))
    EditField(
        value = state.draft.phone,
        onValueChange = {
            viewModel.handleIntent(
                EstablishmentEditIntent.PhoneChanged(filterDigitsOnly(it, PHONE_DIGITS)),
            )
        },
        label = "Telefone *",
        error = state.phoneError,
        keyboardType = KeyboardType.Phone,
        visualTransformation = PhoneMaskTransformation(),
    )
    EditField(
        value = state.draft.email,
        onValueChange = { viewModel.handleIntent(EstablishmentEditIntent.EmailChanged(it)) },
        label = "E-mail *",
        error = state.emailError,
        keyboardType = KeyboardType.Email,
    )
}

/** Endereço é inteiro opcional: nenhum campo daqui tem erro para exibir. */
@Composable
private fun AddressSection(
    draft: EstablishmentDraft,
    viewModel: EstablishmentEditViewModel,
) {
    SectionTitle("Endereço", modifier = Modifier.padding(horizontal = 0.dp))
    EditField(
        value = draft.street,
        onValueChange = { viewModel.handleIntent(EstablishmentEditIntent.StreetChanged(it)) },
        label = "Rua",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            EditField(
                value = draft.number,
                onValueChange = { viewModel.handleIntent(EstablishmentEditIntent.NumberChanged(it)) },
                label = "Número",
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            EditField(
                value = draft.complement,
                onValueChange = { viewModel.handleIntent(EstablishmentEditIntent.ComplementChanged(it)) },
                label = "Complemento",
            )
        }
    }
    EditField(
        value = draft.district,
        onValueChange = { viewModel.handleIntent(EstablishmentEditIntent.DistrictChanged(it)) },
        label = "Bairro",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.weight(2f)) {
            EditField(
                value = draft.city,
                onValueChange = { viewModel.handleIntent(EstablishmentEditIntent.CityChanged(it)) },
                label = "Cidade",
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            EditField(
                value = draft.state,
                onValueChange = { viewModel.handleIntent(EstablishmentEditIntent.StateChanged(it)) },
                label = "UF",
            )
        }
    }
    EditField(
        value = draft.zipCode,
        onValueChange = {
            viewModel.handleIntent(
                EstablishmentEditIntent.ZipCodeChanged(filterDigitsOnly(it, ZIP_DIGITS)),
            )
        },
        label = "CEP",
        keyboardType = KeyboardType.Number,
    )
}

@Composable
private fun ActiveToggle(
    active: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Ativo", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Desativado, o estabelecimento some das abas de quem trabalha nele.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = active, onCheckedChange = onChange)
    }
}

/**
 * Campo do formulário. Espelha o `EditField` de `SportClientEditScreen` — mesmos cantos,
 * mesmo tratamento de erro em `supportingText` — para que os dois formulários do app não
 * pareçam vir de produtos diferentes.
 */
@Composable
private fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(4.dp))
}
