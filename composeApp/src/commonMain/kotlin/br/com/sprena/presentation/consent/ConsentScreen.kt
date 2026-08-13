package br.com.sprena.presentation.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.sprena.shared.auth.session.SessionUser

/**
 * Gate de consentimento — primeira tela após login/restore enquanto o aceite da
 * versão vigente não estiver registrado.
 *
 * Não há botão de voltar nem de recusar: recusar é fechar o app. Isso é
 * deliberado — sem aceite não há base legal para operar os dados.
 */
@Composable
fun ConsentScreen(
    viewModel: ConsentViewModel,
    onNavigateHome: (SessionUser) -> Unit,
    onNavigateLogin: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ConsentEffect.NavigateHome -> onNavigateHome(effect.session)
                is ConsentEffect.NavigateLogin -> onNavigateLogin()
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Antes de continuar",
                style = MaterialTheme.typography.headlineSmall,
            )

            when {
                state.isLoading -> LoadingSection()
                state.policyText.isBlank() ->
                    PolicyLoadErrorSection(
                        error = state.error,
                        onRetry = { viewModel.handleIntent(ConsentIntent.Retry) },
                    )
                else ->
                    ConsentAcceptanceSection(
                        state = state,
                        onToggleRead = { viewModel.handleIntent(ConsentIntent.ToggleRead) },
                        onAccept = { viewModel.handleIntent(ConsentIntent.Accept) },
                        onRetry = { viewModel.handleIntent(ConsentIntent.Retry) },
                    )
            }
        }
    }
}

/**
 * Indicador de carregamento exibido enquanto a política de privacidade é buscada.
 */
@Composable
private fun LoadingSection() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Mensagem exibida quando a política não pôde ser carregada, com botão para tentar de novo.
 */
@Composable
private fun PolicyLoadErrorSection(
    error: String?,
    onRetry: () -> Unit,
) {
    Text(
        text = error ?: "Não foi possível carregar a política.",
        color = MaterialTheme.colorScheme.error,
    )
    TextButton(onClick = onRetry) {
        Text("Tentar de novo")
    }
}

/**
 * Texto da política, checkbox de leitura e botão de aceite — fluxo principal do gate.
 *
 * Extensão de [ColumnScope] porque o texto da política usa `weight(1f)` para ocupar
 * o espaço restante da coluna, empurrando o botão de aceite para o fim da tela.
 */
@Composable
private fun ColumnScope.ConsentAcceptanceSection(
    state: ConsentState,
    onToggleRead: () -> Unit,
    onAccept: () -> Unit,
    onRetry: () -> Unit,
) {
    Text(
        text = state.policyText,
        style = MaterialTheme.typography.bodyMedium,
        modifier =
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = state.hasRead,
            onCheckedChange = { onToggleRead() },
        )
        Text("Li e concordo com a Política de Privacidade")
    }

    // Falha de gravação do aceite ou de leitura do consentimento (Unavailable):
    // nos dois casos o "tentar de novo" é a única ação que faz sentido oferecer.
    state.error?.let { message ->
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onRetry) {
            Text("Tentar de novo")
        }
    }

    Button(
        onClick = onAccept,
        enabled = state.canAccept,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.isAccepting) "Registrando..." else "Aceitar e continuar")
    }
}
