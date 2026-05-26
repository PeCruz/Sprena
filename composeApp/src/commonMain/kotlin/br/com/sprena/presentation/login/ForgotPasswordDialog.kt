package br.com.sprena.presentation.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Dialog de "Esqueci a senha" — coleta o email e dispara o envio do link de reset.
 *
 * Apenas renderiza state e dispara callbacks (MVI puro). O ViewModel é dono
 * do estado de envio e validação.
 */
@Composable
fun ForgotPasswordDialog(
    email: String,
    emailError: String?,
    sending: Boolean,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recuperar senha") },
        text = {
            Column {
                Text(
                    "Informe seu email cadastrado. Enviaremos um link para criar uma nova senha.",
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    isError = emailError != null,
                    supportingText = emailError?.let { error -> { Text(error) } },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = !sending) {
                Text(if (sending) "Enviando..." else "Enviar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
