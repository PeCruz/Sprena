package br.com.sprena.presentation.financial

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.sprena.core.ui.components.ThemeToggleButton
import br.com.sprena.presentation.core.theme.ThemeViewModel
import br.com.sprena.presentation.financial.addtransaction.dateComponentsFromMillis
import br.com.sprena.shared.financial.domain.validation.BrlMonetaryFormatter

/**
 * Tela Financeira — exibe dashboard com Entrada (esquerda) e Saída (direita),
 * filtro por período navegável, totais horizontais e lista de transações.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialScreen(
    viewModel: FinancialViewModel,
    themeViewModel: ThemeViewModel,
    modifier: Modifier = Modifier,
    onAddTransactionDialog: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.handleIntent(FinancialIntent.Load)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FinancialEffect.OpenAddTransactionDialog -> onAddTransactionDialog()
                is FinancialEffect.ShowError -> { /* TODO: snackbar */ }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Financeiro") },
                actions = {
                    ThemeToggleButton(themeViewModel = themeViewModel)
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.handleIntent(FinancialIntent.AddTransactionClicked) },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
            ) {
                // --- Period Filter Chips ---
                PeriodFilterRow(
                    selected = state.periodFilter,
                    onFilterChanged = {
                        viewModel.handleIntent(FinancialIntent.PeriodFilterChanged(it))
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- Jump-to-date picker ---
                var showJumpDatePicker by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = { showJumpDatePicker = true }) {
                        Text(
                            text = "📅 Ir para data",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                if (showJumpDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showJumpDatePicker = false },
                        confirmButton = {},
                    ) {
                        val datePickerState = rememberDatePickerState()
                        DatePicker(state = datePickerState)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { showJumpDatePicker = false }) {
                                Text("Cancelar")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    datePickerState.selectedDateMillis?.let { millis ->
                                        val components = dateComponentsFromMillis(millis)
                                        viewModel.handleIntent(
                                            FinancialIntent.JumpToDate(
                                                month = components.month,
                                                year = components.year,
                                            ),
                                        )
                                    }
                                    showJumpDatePicker = false
                                },
                            ) {
                                Text("OK")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // --- Period Navigator (← label →) ---
                PeriodNavigator(
                    label = state.periodLabel,
                    onPrevious = { viewModel.handleIntent(FinancialIntent.PreviousPeriod) },
                    onNext = { viewModel.handleIntent(FinancialIntent.NextPeriod) },
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- Dashboard: Entrada (left) | Saída (right) — filtered ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DashboardCard(
                        label = "Entrada",
                        valueCents = state.filteredIncomeCents,
                        color = Color(0xFF4CAF50),
                        backgroundColor = Color(0xFFE6FFD5),
                        modifier = Modifier.weight(1f),
                    )
                    DashboardCard(
                        label = "Saída",
                        valueCents = state.filteredExpenseCents,
                        color = Color(0xFFE53935),
                        backgroundColor = Color(0xFFF4946E).copy(alpha = 0.3f),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- Totals Row — filtered ---
                TotalsRow(
                    incomeCents = state.filteredIncomeCents,
                    expenseCents = state.filteredExpenseCents,
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Transações",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (state.filteredTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Nenhuma transação neste período",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.paginatedTransactions, key = { it.id }) { tx ->
                            TransactionCard(
                                tx = tx,
                                onClick = {
                                    viewModel.handleIntent(
                                        FinancialIntent.EditTransactionClicked(tx.id),
                                    )
                                },
                            )
                        }
                        if (state.hasMoreTransactions) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TextButton(
                                        onClick = {
                                            viewModel.handleIntent(
                                                FinancialIntent.LoadMoreTransactions,
                                            )
                                        },
                                    ) {
                                        Text("Carregar mais")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Period Filter Chips ---

@Composable
private fun PeriodFilterRow(
    selected: PeriodFilter,
    onFilterChanged: (PeriodFilter) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(PeriodFilter.entries.toList()) { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onFilterChanged(filter) },
                label = { Text(filter.label) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
            )
        }
    }
}

// --- Period Navigator (← Abr 2026 →) ---

@Composable
private fun PeriodNavigator(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
            Text("◀", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(140.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
            Text("▶", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// --- Dashboard Cards (Entrada / Saída) ---

@Composable
private fun DashboardCard(
    label: String,
    valueCents: Long,
    color: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = BrlMonetaryFormatter.formatCents(valueCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// --- Totals Row ---

@Composable
private fun TotalsRow(
    incomeCents: Long,
    expenseCents: Long,
) {
    val balanceCents = incomeCents - expenseCents
    val balanceColor = if (balanceCents >= 0) Color(0xFF4CAF50) else Color(0xFFE53935)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TotalItem(
            label = "Total Entrada",
            valueCents = incomeCents,
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f),
        )
        TotalItem(
            label = "Total Saída",
            valueCents = expenseCents,
            color = Color(0xFFE53935),
            modifier = Modifier.weight(1f),
        )
        TotalItem(
            label = "Saldo",
            valueCents = balanceCents,
            color = balanceColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TotalItem(
    label: String,
    valueCents: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = BrlMonetaryFormatter.formatCents(valueCents),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// --- Transaction Card ---

@Composable
private fun TransactionCard(
    tx: FinancialTransactionSummary,
    onClick: () -> Unit = {},
) {
    val isIncome = tx.type == TransactionType.INCOME
    val valueColor = if (isIncome) Color(0xFF4CAF50) else Color(0xFFE53935)
    val prefix = if (isIncome) "+" else "-"

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tx.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$prefix ${BrlMonetaryFormatter.formatCents(tx.cents)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
            )
        }
    }
}
