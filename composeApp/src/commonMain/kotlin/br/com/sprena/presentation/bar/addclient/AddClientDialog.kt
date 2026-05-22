package br.com.sprena.presentation.bar.addclient

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.sprena.core.ui.mask.CpfMaskTransformation
import br.com.sprena.core.ui.mask.PhoneMaskTransformation
import br.com.sprena.core.ui.mask.filterDigitsOnly
import br.com.sprena.presentation.bar.BarClient

/**
 * Diálogo para adicionar um novo cliente ao Bar.
 * Campos: Nome (obrigatório), Apelido (opcional), Telefone (obrigatório),
 * CPF (obrigatório), Email (opcional).
 *
 * Telefone usa máscara (XX) XXXXX-XXXX.
 * CPF usa máscara XXX.XXX.XXX-XX.
 */
@Composable
fun AddClientDialog(
    viewModel: AddClientViewModel,
    onDismiss: () -> Unit,
    onClientCreated: (BarClient) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AddClientEffect.ClientCreated -> onClientCreated(effect.client)
                is AddClientEffect.ShowError -> { /* errors shown inline */ }
                is AddClientEffect.Dismissed -> onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { viewModel.handleIntent(AddClientIntent.Dismiss) },
        title = { Text("Novo Cliente") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                // Name (obrigatório)
                OutlinedTextField(
                    value = state.name,
                    onValueChange = {
                        viewModel.handleIntent(AddClientIntent.NameChanged(it))
                    },
                    label = { Text("Nome *") },
                    singleLine = true,
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { e -> { Text(e) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Nickname (opcional)
                OutlinedTextField(
                    value = state.nickname,
                    onValueChange = {
                        viewModel.handleIntent(AddClientIntent.NicknameChanged(it))
                    },
                    label = { Text("Apelido") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Phone (obrigatório) — máscara (XX) XXXXX-XXXX
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = { input ->
                        val digits = filterDigitsOnly(input, 11)
                        viewModel.handleIntent(AddClientIntent.PhoneChanged(digits))
                    },
                    label = { Text("Telefone *") },
                    singleLine = true,
                    isError = state.phoneError != null,
                    supportingText = state.phoneError?.let { e -> { Text(e) } },
                    visualTransformation = PhoneMaskTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(4.dp))

                // CPF (obrigatório) — máscara XXX.XXX.XXX-XX
                OutlinedTextField(
                    value = state.cpf,
                    onValueChange = { input ->
                        val digits = filterDigitsOnly(input, 11)
                        viewModel.handleIntent(AddClientIntent.CpfChanged(digits))
                    },
                    label = { Text("CPF *") },
                    singleLine = true,
                    isError = state.cpfError != null,
                    supportingText = state.cpfError?.let { e -> { Text(e) } },
                    visualTransformation = CpfMaskTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Email (opcional)
                OutlinedTextField(
                    value = state.email,
                    onValueChange = {
                        viewModel.handleIntent(AddClientIntent.EmailChanged(it))
                    },
                    label = { Text("Email") },
                    singleLine = true,
                    isError = state.emailError != null,
                    supportingText = state.emailError?.let { e -> { Text(e) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.handleIntent(AddClientIntent.Save) },
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.handleIntent(AddClientIntent.Dismiss) },
            ) {
                Text("Cancelar")
            }
        },
    )
}
