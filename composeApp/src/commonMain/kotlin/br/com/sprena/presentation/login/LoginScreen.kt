package br.com.sprena.presentation.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.com.sprena.core.ui.components.SprenaLogo
import br.com.sprena.core.ui.components.ThemeToggleButton
import br.com.sprena.presentation.core.theme.ThemeViewModel
import br.com.sprena.shared.auth.domain.model.UserModel

/**
 * Tela de Login — simple sign-in com logo Sprena,
 * campos de username (max 8 chars) / password (6 dígitos numéricos)
 * e botão de entrar.
 *
 * Apenas renderiza state e dispara intents (MVI).
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    themeViewModel: ThemeViewModel,
    onNavigateHome: (UserModel) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    // Observa efeitos one-shot
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LoginEffect.NavigateHome -> onNavigateHome(effect.user)
                is LoginEffect.ShowError -> { /* Handled inline via authError state */ }
            }
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            // --- Theme toggle no canto superior direito ---
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
            ) {
                ThemeToggleButton(themeViewModel = themeViewModel)
            }

            // --- Content central ---
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp)
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // --- Logo ---
                SprenaLogo(size = 96.dp)

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Sprena",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(32.dp))

                // --- Username (max 8 caracteres) ---
                OutlinedTextField(
                    value = state.username,
                    onValueChange = { viewModel.handleIntent(LoginIntent.UsernameChanged(it)) },
                    label = { Text("Usuário") },
                    singleLine = true,
                    isError = state.usernameError != null,
                    supportingText = state.usernameError?.let { error -> { Text(error) } },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // --- Password (6 dígitos numéricos) ---
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { viewModel.handleIntent(LoginIntent.PasswordChanged(it)) },
                    label = { Text("Senha (6 dígitos)") },
                    singleLine = true,
                    isError = state.passwordError != null,
                    supportingText = state.passwordError?.let { error -> { Text(error) } },
                    visualTransformation =
                        if (state.isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    trailingIcon = {
                        IconButton(
                            onClick = { viewModel.handleIntent(LoginIntent.TogglePasswordVisibility) },
                        ) {
                            Text(
                                text = if (state.isPasswordVisible) "🙈" else "👁",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = { viewModel.handleIntent(LoginIntent.Submit) },
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // --- Auth error (inline) ---
                if (state.authError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.authError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- Login Button ---
                Button(
                    onClick = { viewModel.handleIntent(LoginIntent.Submit) },
                    enabled = state.canSubmit && !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.height(24.dp).width(24.dp),
                        )
                    } else {
                        Text("Entrar")
                    }
                }
            }
        }
    }
}
