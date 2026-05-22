package br.com.sprena.presentation.financial.addtransaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import br.com.sprena.presentation.financial.FinancialTransactionSummary
import br.com.sprena.presentation.financial.TransactionType
import br.com.sprena.presentation.financial.currentYearMonth

/**
 * Visual transformation that formats raw digit input as BRL currency.
 * E.g. "12345" → "R$ 123,45", "1" → "R$ 0,01", "" → "R$ 0,00"
 */
internal class BrlVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val cents = digits.toLongOrNull() ?: 0L
        val formatted = formatCentsForMask(cents)

        val offsetMapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = formatted.length

                override fun transformedToOriginal(offset: Int): Int = digits.length
            }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }

    private fun formatCentsForMask(cents: Long): String {
        val reais = cents / 100
        val centavos = (cents % 100).let { if (it < 0) -it else it }
        val reaisStr =
            reais
                .toString()
                .reversed()
                .chunked(3)
                .joinToString(".")
                .reversed()
        return "R$ $reaisStr,${centavos.toString().padStart(2, '0')}"
    }
}

/**
 * Diálogo para adicionar uma nova transação financeira.
 *
 * Campos: Nome, Amount (BRL), Person Name, Type (Income/Expense),
 * Category (dropdown), Description.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    viewModel: AddTransactionViewModel,
    categories: List<String> = emptyList(),
    isEditMode: Boolean = false,
    onDismiss: () -> Unit,
    onTransactionCreated: (FinancialTransactionSummary) -> Unit,
    onTransactionUpdated: (FinancialTransactionSummary) -> Unit = {},
    onTransactionDeleted: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AddTransactionEffect.TransactionCreated -> onTransactionCreated(effect.transaction)
                is AddTransactionEffect.TransactionUpdated -> onTransactionUpdated(effect.transaction)
                is AddTransactionEffect.Dismissed -> onDismiss()
                is AddTransactionEffect.ShowError -> { /* handled inline */ }
            }
        }
    }

    Dialog(onDismissRequest = { viewModel.handleIntent(AddTransactionIntent.Dismiss) }) {
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
                    text = if (isEditMode) "Editar Transação" else "Nova Transação",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- Type chips (Income / Expense) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.type == TransactionType.INCOME,
                        onClick = {
                            viewModel.handleIntent(
                                AddTransactionIntent.TypeChanged(TransactionType.INCOME),
                            )
                        },
                        label = {
                            Text(
                                "Entrada",
                                fontWeight =
                                    if (state.type == TransactionType.INCOME) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                            )
                        },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2E7D32),
                                selectedLabelColor = Color.White,
                            ),
                    )
                    FilterChip(
                        selected = state.type == TransactionType.EXPENSE,
                        onClick = {
                            viewModel.handleIntent(
                                AddTransactionIntent.TypeChanged(TransactionType.EXPENSE),
                            )
                        },
                        label = {
                            Text(
                                "Saída",
                                fontWeight =
                                    if (state.type == TransactionType.EXPENSE) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                            )
                        },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFC62828),
                                selectedLabelColor = Color.White,
                            ),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- Nome (required, max 50) ---
                OutlinedTextField(
                    value = state.name,
                    onValueChange = {
                        if (it.length <= 50) {
                            viewModel.handleIntent(AddTransactionIntent.NameChanged(it))
                        }
                    },
                    label = { Text("Nome *") },
                    singleLine = true,
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { e -> { Text(e) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- Amount (BRL) with live mask ---
                OutlinedTextField(
                    value = state.amountRaw,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }
                        viewModel.handleIntent(AddTransactionIntent.AmountChanged(digits))
                    },
                    label = { Text("Valor (R$) *") },
                    singleLine = true,
                    visualTransformation = remember { BrlVisualTransformation() },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.amountError != null,
                    supportingText = state.amountError?.let { e -> { Text(e) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- Person Name ---
                OutlinedTextField(
                    value = state.personName,
                    onValueChange = {
                        viewModel.handleIntent(AddTransactionIntent.PersonNameChanged(it))
                    },
                    label = { Text("Responsável *") },
                    singleLine = true,
                    isError = state.personNameError != null,
                    supportingText = state.personNameError?.let { e -> { Text(e) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- Category Dropdown ---
                var categoryExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                ) {
                    OutlinedTextField(
                        value = state.category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoria") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                        },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    viewModel.handleIntent(
                                        AddTransactionIntent.CategoryChanged(cat),
                                    )
                                    categoryExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- Description ---
                OutlinedTextField(
                    value = state.description,
                    onValueChange = {
                        viewModel.handleIntent(AddTransactionIntent.DescriptionChanged(it))
                    },
                    label = { Text("Descrição") },
                    minLines = 2,
                    maxLines = 4,
                    isError = state.descriptionError != null,
                    supportingText = state.descriptionError?.let { e -> { Text(e) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // --- Date Granularity chips ---
                Text(
                    text = "Data *",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.dateGranularity == DateGranularity.DAY,
                        onClick = {
                            viewModel.handleIntent(
                                AddTransactionIntent.DateGranularityChanged(DateGranularity.DAY),
                            )
                        },
                        label = { Text("Dia") },
                    )
                    FilterChip(
                        selected = state.dateGranularity == DateGranularity.MONTH,
                        onClick = {
                            viewModel.handleIntent(
                                AddTransactionIntent.DateGranularityChanged(DateGranularity.MONTH),
                            )
                        },
                        label = { Text("Mês") },
                    )
                    FilterChip(
                        selected = state.dateGranularity == DateGranularity.YEAR,
                        onClick = {
                            viewModel.handleIntent(
                                AddTransactionIntent.DateGranularityChanged(DateGranularity.YEAR),
                            )
                        },
                        label = { Text("Ano") },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- Date field (read-only, opens picker) ---
                var showDatePicker by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.dateDisplay,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = {
                            Text(
                                when (state.dateGranularity) {
                                    DateGranularity.DAY -> "DD/MM/AAAA"
                                    DateGranularity.MONTH -> "MM/AAAA"
                                    DateGranularity.YEAR -> "AAAA"
                                },
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Transparent overlay to capture clicks (OutlinedTextField
                    // with enabled=false ignores touch, so this Box receives it)
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .clickable { showDatePicker = true },
                    )
                }

                if (state.dateError != null) {
                    Text(
                        text = state.dateError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    )
                }

                // --- Date Picker Dialog (switches based on granularity) ---
                if (showDatePicker) {
                    when (state.dateGranularity) {
                        DateGranularity.DAY -> {
                            DatePickerDialog(
                                onDismissRequest = { showDatePicker = false },
                                confirmButton = {},
                            ) {
                                val datePickerState = rememberDatePickerState()
                                DatePicker(state = datePickerState)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(onClick = { showDatePicker = false }) {
                                        Text("Cancelar")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            datePickerState.selectedDateMillis?.let { millis ->
                                                viewModel.handleIntent(
                                                    AddTransactionIntent.DatePickerConfirmed(millis),
                                                )
                                            }
                                            showDatePicker = false
                                        },
                                    ) {
                                        Text("OK")
                                    }
                                }
                            }
                        }

                        DateGranularity.MONTH -> {
                            val (defaultYear, defaultMonth) = currentYearMonth()
                            val initMonth = state.selectedMonth ?: defaultMonth
                            val initYear = state.selectedYear ?: defaultYear
                            MonthPickerDialog(
                                initialMonth = initMonth,
                                initialYear = initYear,
                                onDismiss = { showDatePicker = false },
                                onConfirm = { month, year ->
                                    viewModel.handleIntent(
                                        AddTransactionIntent.DateSelected(
                                            day = null,
                                            month = month,
                                            year = year,
                                        ),
                                    )
                                    showDatePicker = false
                                },
                            )
                        }

                        DateGranularity.YEAR -> {
                            val (defaultYear, _) = currentYearMonth()
                            val initYear = state.selectedYear ?: defaultYear
                            YearPickerDialog(
                                initialYear = initYear,
                                onDismiss = { showDatePicker = false },
                                onConfirm = { year ->
                                    viewModel.handleIntent(
                                        AddTransactionIntent.DateSelected(
                                            day = null,
                                            month = null,
                                            year = year,
                                        ),
                                    )
                                    showDatePicker = false
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // --- Actions ---
                if (isEditMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = onTransactionDeleted,
                            colors =
                                androidx.compose.material3.ButtonDefaults.textButtonColors(
                                    contentColor = Color(0xFFE53935),
                                ),
                        ) {
                            Text("Excluir")
                        }
                        Row {
                            TextButton(
                                onClick = { viewModel.handleIntent(AddTransactionIntent.Dismiss) },
                            ) {
                                Text("Cancelar")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.handleIntent(AddTransactionIntent.Submit) },
                                enabled = state.canSubmit,
                            ) {
                                Text("Salvar")
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { viewModel.handleIntent(AddTransactionIntent.Dismiss) },
                        ) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.handleIntent(AddTransactionIntent.Submit) },
                            enabled = state.canSubmit,
                        ) {
                            Text("Adicionar")
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// Month Picker Dialog (for MONTH granularity)
// =========================================================================

private val MONTH_NAMES_PT =
    listOf(
        "Jan",
        "Fev",
        "Mar",
        "Abr",
        "Mai",
        "Jun",
        "Jul",
        "Ago",
        "Set",
        "Out",
        "Nov",
        "Dez",
    )

@Composable
private fun MonthPickerDialog(
    initialMonth: Int,
    initialYear: Int,
    onDismiss: () -> Unit,
    onConfirm: (month: Int, year: Int) -> Unit,
) {
    var selectedMonth by remember { mutableIntStateOf(initialMonth) }
    var selectedYear by remember { mutableIntStateOf(initialYear) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecionar Mês/Ano") },
        text = {
            Column {
                // Year navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selectedYear-- }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Ano anterior")
                    }
                    Text(
                        text = "$selectedYear",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    IconButton(onClick = { selectedYear++ }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo ano")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Month grid (3 columns x 4 rows)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0..3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (col in 0..2) {
                                val monthIndex = row * 3 + col
                                val monthNumber = monthIndex + 1
                                FilterChip(
                                    selected = selectedMonth == monthNumber,
                                    onClick = { selectedMonth = monthNumber },
                                    label = {
                                        Text(
                                            text = MONTH_NAMES_PT[monthIndex],
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedMonth, selectedYear) }) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

// =========================================================================
// Year Picker Dialog (for YEAR granularity)
// =========================================================================

@Composable
private fun YearPickerDialog(
    initialYear: Int,
    onDismiss: () -> Unit,
    onConfirm: (year: Int) -> Unit,
) {
    var selectedYear by remember { mutableIntStateOf(initialYear) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecionar Ano") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selectedYear-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Ano anterior")
                }
                Text(
                    text = "$selectedYear",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                IconButton(onClick = { selectedYear++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo ano")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedYear) }) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
