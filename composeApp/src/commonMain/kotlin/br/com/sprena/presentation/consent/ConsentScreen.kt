package br.com.sprena.presentation.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
                state.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.policyText.isBlank() -> {
                    Text(
                        text = state.error ?: "Não foi possível carregar a política.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { viewModel.handleIntent(ConsentIntent.Retry) }) {
                        Text("Tentar de novo")
                    }
                }

                else -> {
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
                            onCheckedChange = { viewModel.handleIntent(ConsentIntent.ToggleRead) },
                        )
                        Text("Li e concordo com a Política de Privacidade")
                    }

                    if (state.error != null) {
                        Text(
                            text = state.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Button(
                        onClick = { viewModel.handleIntent(ConsentIntent.Accept) },
                        enabled = state.canAccept,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isAccepting) "Registrando..." else "Aceitar e continuar")
                    }
                }
            }
        }
    }
}
