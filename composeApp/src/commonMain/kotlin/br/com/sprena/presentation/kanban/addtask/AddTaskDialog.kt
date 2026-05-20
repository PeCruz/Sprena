package br.com.sprena.presentation.kanban.addtask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Diálogo para adicionar uma nova tarefa ao Kanban.
 *
 * Campos: Name, Priority (dropdown), Description, Comment,
 * StartDate (auto, não editável), EndDate, Attachment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    viewModel: AddTaskViewModel,
    onDismiss: () -> Unit,
    onTaskCreated: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AddTaskEffect.TaskCreated -> onTaskCreated()
                is AddTaskEffect.Dismissed -> onDismiss()
                is AddTaskEffect.ShowError -> { /* handled inline via field errors */ }
            }
        }
    }

    Dialog(onDismissRequest = { viewModel.handleIntent(AddTaskIntent.Dismiss) }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Nova Tarefa",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- Name ---
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.handleIntent(AddTaskIntent.NameChanged(it)) },
                    label = { Text("Nome da tarefa *") },
                    singleLine = true,
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { e -> { Text(e) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- Priority Dropdown ---
                var priorityExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = priorityExpanded,
                    onExpandedChange = { priorityExpanded = it },
                ) {
                    OutlinedTextField(
                        value = state.priority?.let { priorityLabel(it) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Prioridade *") },
                        isError = state.priorityError != null,
                        supportingText = state.priorityError?.let { e -> { Text(e) } },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = priorityExpanded,
                        onDismissRequest = { priorityExpanded = false },
                    ) {
                        AddTaskState.PRIORITY_OPTIONS.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(priorityLabel(p)) },
                                onClick = {
                                    viewModel.handleIntent(AddTaskIntent.PriorityChanged(p))
                                    priorityExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- Description ---
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { viewModel.handleIntent(AddTaskIntent.DescriptionChanged(it)) },
                    label = { Text("Descrição") },
                    minLines = 3,
                    maxLines = 5,
                    isError = state.descriptionError != null,
                    supportingText = state.descriptionError?.let { e -> { Text(e) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- Comment ---
                OutlinedTextField(
                    value = state.comment,
                    onValueChange = { viewModel.handleIntent(AddTaskIntent.CommentChanged(it)) },
                    label = { Text("Comentário") },
                    minLines = 2,
                    maxLines = 4,
                    isError = state.commentError != null,
                    supportingText = state.commentError?.let { e -> { Text(e) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- Start Date (read-only, auto = today) ---
                OutlinedTextField(
                    value = "Hoje (automático)",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Data de Início") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- End Date ---
                // TODO: integrar com DatePicker nativo (platform-specific)
                OutlinedTextField(
                    value = state.endEpochDay?.toString() ?: "",
                    onValueChange = { raw ->
                        raw.toLongOrNull()?.let {
                            viewModel.handleIntent(AddTaskIntent.EndDateChanged(it))
                        }
                    },
                    label = { Text("Data de Término *") },
                    singleLine = true,
                    isError = state.endDateError != null,
                    supportingText = state.endDateError?.let { e -> { Text(e) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- Attachment ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(
                        onClick = {
                            // TODO: integrar com file picker nativo
                            viewModel.handleIntent(
                                AddTaskIntent.AttachmentSelected(
                                    name = "arquivo.pdf",
                                    sizeBytes = 0L,
                                ),
                            )
                        },
                    ) {
                        Text("Anexar Arquivo")
                    }

                    if (state.attachmentName != null) {
                        TextButton(
                            onClick = { viewModel.handleIntent(AddTaskIntent.AttachmentCleared) },
                        ) {
                            Text("Remover")
                        }
                    }
                }

                if (state.attachmentName != null) {
                    Text(
                        text = state.attachmentName ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.attachmentError != null) {
                    Text(
                        text = state.attachmentError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // --- Actions ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { viewModel.handleIntent(AddTaskIntent.Dismiss) },
                    ) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.handleIntent(AddTaskIntent.Submit) },
                        enabled = state.canSubmit,
                    ) {
                        Text("Criar Tarefa")
                    }
                }
            }
        }
    }
}

private fun priorityLabel(priority: Int): String =
    when (priority) {
        1 -> "1 — Muito Baixa"
        2 -> "2 — Baixa"
        3 -> "3 — Média"
        4 -> "4 — Alta"
        5 -> "5 — Urgente"
        else -> "—"
    }
