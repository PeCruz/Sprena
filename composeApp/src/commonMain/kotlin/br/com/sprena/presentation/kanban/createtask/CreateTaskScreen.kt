package br.com.sprena.presentation.kanban.createtask

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import br.com.sprena.core.ui.components.ThemeToggleButton
import br.com.sprena.presentation.core.theme.ThemeViewModel

/**
 * Tela "Criando Tarefa" — tela completa (não dialog) para criar uma task.
 *
 * Header fixo: ← Voltar | "Criando Tarefa" | ☀/🌙 | ⚙
 *
 * Campos: Name, Priority (dropdown), Description, Comment,
 * StartDate (auto), EndDate (DatePicker com datas passadas bloqueadas),
 * Attachment (file picker do sistema).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    viewModel: CreateTaskViewModel,
    themeViewModel: ThemeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateSettings: () -> Unit,
    onTaskCreated: (name: String, priority: Int) -> Unit = { _, _ -> },
    onPickFile: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    var showExitDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CreateTaskEffect.TaskCreated -> {
                    val currentState = viewModel.state.value
                    onTaskCreated(currentState.name, currentState.priority ?: 3)
                }
                is CreateTaskEffect.GoBack -> onNavigateBack()
                is CreateTaskEffect.ShowError -> { /* errors shown inline on fields */ }
            }
        }
    }

    val handleBack: () -> Unit = {
        if (state.hasUnsavedChanges) {
            showExitDialog = true
        } else {
            viewModel.handleIntent(CreateTaskIntent.NavigateBack)
        }
    }

    // DatePicker state: bloqueia datas antes de hoje
    val todayMillis =
        remember {
            // epoch day 0 → usar o valor correto do dia atual em millis
            val now = System.currentTimeMillis()
            // Início do dia UTC de hoje
            (now / 86_400_000L) * 86_400_000L
        }

    val datePickerState =
        rememberDatePickerState(
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayMillis

                    override fun isSelectableYear(year: Int): Boolean = year >= 2024
                },
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Criando Tarefa",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                        )
                    }
                },
                actions = {
                    ThemeToggleButton(themeViewModel = themeViewModel)
                    IconButton(onClick = onNavigateSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configurações",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // --- Name ---
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.handleIntent(CreateTaskIntent.NameChanged(it)) },
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
                    CreateTaskState.PRIORITY_OPTIONS.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(priorityLabel(p)) },
                            onClick = {
                                viewModel.handleIntent(CreateTaskIntent.PriorityChanged(p))
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
                onValueChange = { viewModel.handleIntent(CreateTaskIntent.DescriptionChanged(it)) },
                label = { Text("Descrição") },
                minLines = 3,
                maxLines = 6,
                isError = state.descriptionError != null,
                supportingText = state.descriptionError?.let { e -> { Text(e) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- Comment ---
            OutlinedTextField(
                value = state.comment,
                onValueChange = { viewModel.handleIntent(CreateTaskIntent.CommentChanged(it)) },
                label = { Text("Comentário") },
                minLines = 2,
                maxLines = 4,
                isError = state.commentError != null,
                supportingText = state.commentError?.let { e -> { Text(e) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- Start Date (read-only) ---
            OutlinedTextField(
                value = "Hoje (automático)",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Data de Início") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- End Date (DatePicker) ---
            // Clicking anywhere on the field opens the date picker immediately
            OutlinedTextField(
                value = state.endEpochDay?.let { epochDayToDateString(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Data de Término *") },
                singleLine = true,
                isError = state.endDateError != null,
                supportingText = state.endDateError?.let { e -> { Text(e) } },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Selecionar data",
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                enabled = false, // Disable typing; click opens picker
            )

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val epochDay = millis / 86_400_000L
                                    viewModel.handleIntent(CreateTaskIntent.EndDateChanged(epochDay))
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

            Spacer(modifier = Modifier.height(12.dp))

            // --- Attachment (uses device file picker) ---
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        if (onPickFile != null) {
                            onPickFile()
                        }
                    },
                ) {
                    Text("Anexar Arquivo")
                }

                if (state.attachmentName != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { viewModel.handleIntent(CreateTaskIntent.AttachmentCleared) },
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
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (state.attachmentError != null) {
                Text(
                    text = state.attachmentError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Submit Button ---
            Button(
                onClick = { viewModel.handleIntent(CreateTaskIntent.Submit) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Criar Tarefa")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- Exit confirmation dialog ---
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Sair da criação") },
            text = { Text("Você tem certeza que deseja sair?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        viewModel.handleIntent(CreateTaskIntent.NavigateBack)
                    },
                ) {
                    Text("Sim, sair")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
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

/**
 * Converte epoch day (dias desde 1970-01-01) para uma string dd/MM/yyyy.
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
    val dd = d.toString().padStart(2, '0')
    val mm = m.toString().padStart(2, '0')
    return "$dd/$mm/$year"
}
