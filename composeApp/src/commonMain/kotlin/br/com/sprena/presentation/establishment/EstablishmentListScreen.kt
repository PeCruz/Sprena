package br.com.sprena.presentation.establishment

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.sprena.core.ui.components.ThemeToggleButton
import br.com.sprena.core.ui.mask.formatCnpjDigits
import br.com.sprena.presentation.core.theme.ThemeViewModel
import br.com.sprena.shared.establishment.domain.model.Establishment
import org.koin.compose.viewmodel.koinViewModel

/** Opacidade dos cartões desativados — legíveis, mas visivelmente fora de operação. */
private const val INACTIVE_ALPHA = 0.5f

/**
 * Lista de estabelecimentos (ADM).
 *
 * Inativos ficam na lista, esmaecidos e etiquetados: desativar é o "excluir" do produto, e o
 * ADM precisa continuar enxergando para poder reativar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstablishmentListScreen(
    themeViewModel: ThemeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EstablishmentListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EstablishmentListEffect.NavigateToEdit -> onNavigateToEdit(effect.id)
                is EstablishmentListEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Estabelecimentos") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = { ThemeToggleButton(themeViewModel) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.handleIntent(EstablishmentListIntent.CreateClicked) },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListBody(state, viewModel)
        }
    }
}

@Composable
private fun BoxScope.ListBody(
    state: EstablishmentListState,
    viewModel: EstablishmentListViewModel,
) {
    when {
        state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

        state.error != null ->
            CenteredMessage(
                message = state.error,
                // Sem botão de repetir: o fluxo do Firestore continua vivo e uma leitura
                // bem-sucedida limpa o erro sozinha.
                hint = "A lista volta assim que a conexão se restabelecer.",
            )

        state.isEmpty ->
            CenteredMessage(
                message = "Nenhum estabelecimento cadastrado",
                hint = "Toque em + para criar o primeiro.",
            )

        else ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.establishments, key = { it.id }) { establishment ->
                    EstablishmentCard(
                        establishment = establishment,
                        onClick = {
                            viewModel.handleIntent(
                                EstablishmentListIntent.EstablishmentClicked(establishment.id),
                            )
                        },
                        onActiveChange = { active ->
                            viewModel.handleIntent(
                                EstablishmentListIntent.ToggleActive(establishment.id, active),
                            )
                        },
                    )
                }
            }
    }
}

@Composable
private fun BoxScope.CenteredMessage(
    message: String,
    hint: String,
) {
    Column(
        modifier = Modifier.align(Alignment.Center).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun EstablishmentCard(
    establishment: Establishment,
    onClick: () -> Unit,
    onActiveChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .alpha(if (establishment.active) 1f else INACTIVE_ALPHA),
            ) {
                Text(establishment.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = formatCnpjDigits(establishment.cnpj),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!establishment.active) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text("Inativo") },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Switch(checked = establishment.active, onCheckedChange = onActiveChange)
        }
    }
}
