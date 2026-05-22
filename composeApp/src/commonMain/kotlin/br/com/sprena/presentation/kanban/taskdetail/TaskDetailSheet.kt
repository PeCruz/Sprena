package br.com.sprena.presentation.kanban.taskdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Bottom sheet exibido sobre a tela Kanban quando o usuário clica em um card de tarefa.
 *
 * Permite editar: Nome, Prioridade, Descrição, Comentário, Data de Fim, Coluna.
 * Botão de deletar com confirmação: "Você tem certeza que deseja deletar a tarefa?"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(
    viewModel: TaskDetailViewModel,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onTaskUpdated: (TaskDetailEffect.TaskUpdated) -> Unit,
    onTaskDeleted: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TaskDetailEffect.TaskUpdated -> {
                    onTaskUpdated(effect)
                }
                is TaskDetailEffect.TaskDeleted -> {
                    onTaskDeleted(effect.taskId)
                }
                is TaskDetailEffect.ShowError -> { /* errors shown inline */ }
                is TaskDetailEffect.Dismissed -> onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.handleIntent(TaskDetailIntent.Dismiss) },
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            // --- Header: title + delete icon ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Detalhes da Tarefa",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { viewModel.handleIntent(TaskDetailIntent.DeleteClicked) },
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Deletar tarefa",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- Name ---
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.handleIntent(TaskDetailIntent.NameChanged(it)) },
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
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = priorityExpanded)
                    },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = priorityExpanded,
                    onDismissRequest = { priorityExpanded = false },
                ) {
                    PRIORITY_OPTIONS.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(priorityLabel(p)) },
                            onClick = {
                                viewModel.handleIntent(TaskDetailIntent.PriorityChanged(p))
                                priorityExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Column Dropdown (mover tarefa) ---
            var columnExpanded by remember { mutableStateOf(false) }
            val currentColumn = state.availableColumns.find { it.id == state.columnId }

            ExposedDropdownMenuBox(
                expanded = columnExpanded,
                onExpandedChange = { columnExpanded = it },
            ) {
                OutlinedTextField(
                    value = currentColumn?.title ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Coluna") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = columnExpanded)
                    },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = columnExpanded,
                    onDismissRequest = { columnExpanded = false },
                ) {
                    state.availableColumns.forEach { col ->
                        DropdownMenuItem(
                            text = { Text(col.title) },
                            onClick = {
                                viewModel.handleIntent(TaskDetailIntent.ColumnChanged(col.id))
                                columnExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Description ---
            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.handleIntent(TaskDetailIntent.DescriptionChanged(it)) },
                label = { Text("Descrição") },
                minLines = 2,
                maxLines = 4,
                isError = state.descriptionError != null,
                supportingText = state.descriptionError?.let { e -> { Text(e) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- Comment ---
            OutlinedTextField(
                value = state.comment,
                onValueChange = { viewModel.handleIntent(TaskDetailIntent.CommentChanged(it)) },
                label = { Text("Comentário") },
                minLines = 2,
                maxLines = 4,
                isError = state.commentError != null,
                supportingText = state.commentError?.let { e -> { Text(e) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- End Date (DatePicker) ---
            var showDatePicker by remember { mutableStateOf(false) }
            val todayMillis = remember { (System.currentTimeMillis() / 86_400_000L) * 86_400_000L }
            val datePickerState =
                rememberDatePickerState(
                    selectableDates =
                        object : SelectableDates {
                            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayMillis

                            override fun isSelectableYear(year: Int): Boolean = year >= 2024
                        },
                )

            OutlinedTextField(
                value = state.endEpochDay?.let { epochDayToDateString(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Data de Término") },
                singleLine = true,
                isError = state.endDateError != null,
                supportingText = state.endDateError?.let { e -> { Text(e) } },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Selecionar data",
                    )
                },
                enabled = false,
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
            )

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val epochDay = millis / 86_400_000L
                                    viewModel.handleIntent(TaskDetailIntent.EndDateChanged(epochDay))
                                }
                                showDatePicker = false
                            },
                        ) {
                            Text("Confirmar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancelar")
                        }
                    },
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Save Button ---
            Button(
                onClick = { viewModel.handleIntent(TaskDetailIntent.Save) },
                enabled = state.hasChanges && state.nameError == null && state.priorityError == null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Salvar Alterações")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- Delete confirmation dialog ---
    if (state.isDeleteConfirmVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.handleIntent(TaskDetailIntent.DeleteCancelled) },
            title = { Text("Deletar tarefa") },
            text = { Text("Você tem certeza que deseja deletar a tarefa?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.handleIntent(TaskDetailIntent.DeleteConfirmed) },
                ) {
                    Text("Sim, deletar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.handleIntent(TaskDetailIntent.DeleteCancelled) },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
}

private val PRIORITY_OPTIONS = listOf(1, 2, 3, 4, 5)

private fun priorityLabel(priority: Int): String =
    when (priority) {
        1 -> "1 — Muito Baixa"
        2 -> "2 — Baixa"
        3 -> "3 — Média"
        4 -> "4 — Alta"
        5 -> "5 — Urgente"
        else -> "—"
    }

/**
 * Converte epoch day (dias desde 1970-01-01) para string dd/MM/yyyy.
 */
private fun epochDayToDateString(epochDay: Long): String {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + (if (mp < 10) 3 else -9)
    val year = y + (if (m <= 2) 1 else 0)
    return "${d.toString().padStart(2, '0')}/${m.toString().padStart(2, '0')}/$year"
}
