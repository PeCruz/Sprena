package br.com.sprena.presentation.sportclient

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.sprena.core.ui.components.ThemeToggleButton
import br.com.sprena.core.ui.mask.CpfMaskTransformation
import br.com.sprena.core.ui.mask.CurrencyMaskTransformation
import br.com.sprena.core.ui.mask.PhoneMaskTransformation
import br.com.sprena.core.ui.mask.filterDigitsOnly
import br.com.sprena.core.ui.mask.parseCurrencyDigits
import br.com.sprena.presentation.core.theme.ThemeViewModel
import br.com.sprena.presentation.financial.currentYearMonth
import br.com.sprena.shared.sportclient.domain.validation.PaymentMethod
import br.com.sprena.shared.sportclient.domain.validation.SportClientValidator
import br.com.sprena.shared.sportclient.domain.validation.SportModality

/**
 * Tela Home — gestão de clientes de esportes (futevôlei, beach tennis, vôlei).
 *
 * Layout: tabela com busca + botão add, linhas clicáveis para editar/deletar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportClientScreen(
    viewModel: SportClientViewModel,
    themeViewModel: ThemeViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Clientes") },
                actions = {
                    ThemeToggleButton(themeViewModel = themeViewModel)
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
                    .padding(horizontal = 16.dp),
        ) {
            // --- Search Input + Add Button ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = {
                        viewModel.handleIntent(SportClientIntent.SearchQueryChanged(it))
                    },
                    placeholder = { Text("Buscar cliente...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { viewModel.handleIntent(SportClientIntent.AddClientClicked) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adicionar cliente",
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Table Header ---
            SportClientTableHeader()

            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)

            // --- Table Body ---
            if (state.filteredClients.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text =
                            if (state.clients.isEmpty()) {
                                "Nenhum cliente registrado"
                            } else {
                                "Nenhum cliente encontrado"
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    items(state.filteredClients, key = { it.id }) { client ->
                        SportClientTableRow(
                            client = client,
                            onClick = {
                                viewModel.handleIntent(SportClientIntent.ClientClicked(client))
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    // --- Add Dialog ---
    if (state.isAddDialogVisible) {
        AddSportClientDialog(
            onDismiss = { viewModel.handleIntent(SportClientIntent.DismissAddDialog) },
            onConfirm = { client ->
                viewModel.handleIntent(SportClientIntent.ClientAdded(client))
            },
        )
    }

    // --- Read-only Detail Dialog ---
    if (state.selectedClient != null) {
        SportClientDetailDialog(
            state = state,
            onToggleCpfReveal = { viewModel.handleIntent(SportClientIntent.ToggleCpfReveal) },
            onDismiss = { viewModel.handleIntent(SportClientIntent.DismissClientDetail) },
            onEdit = { client ->
                viewModel.handleIntent(SportClientIntent.EditClientClicked(client))
            },
            onDelete = { clientId ->
                viewModel.handleIntent(SportClientIntent.ClientDeleted(clientId))
            },
        )
    }
}

@Composable
private fun SportClientTableHeader() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Coluna avatar (espaço fixo)
        Spacer(modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Nome",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = "Modalidade",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = "Mês Pgto",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.8f),
        )
    }
}

/**
 * Avatar circular com as iniciais do cliente.
 */
@Composable
private fun ClientAvatar(
    name: String,
    modifier: Modifier = Modifier,
) {
    val initials =
        name
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }

    Box(
        modifier =
            modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SportClientTableRow(
    client: SportClient,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClientAvatar(name = client.name)
        Spacer(modifier = Modifier.width(8.dp))

        // Nome + Apelido abaixo
        Column(modifier = Modifier.weight(1.2f)) {
            Text(
                text = client.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (client.apelido.isNotBlank()) {
                Text(
                    text = client.apelido,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = modalitiesLabel(client.modalities),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = client.lastPaymentMonth,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.8f),
        )
    }
}

// =========================================================================
// Labels
// =========================================================================

private fun paymentMethodLabel(method: PaymentMethod): String =
    when (method) {
        PaymentMethod.WELLHUB -> "Wellhub"
        PaymentMethod.TOTALPASS -> "TotalPass"
        PaymentMethod.CASH -> "Cash"
    }

private fun modalityLabel(modality: SportModality): String =
    when (modality) {
        SportModality.FUTEVOLEI -> "Futevôlei"
        SportModality.BEACH_TENNIS -> "Beach Tennis"
        SportModality.VOLEI -> "Vôlei"
    }

private fun modalitiesLabel(modalities: List<SportModality>): String =
    modalities.joinToString(", ") {
        modalityLabel(it)
    }

// =========================================================================
// Shared form fields composable
// =========================================================================

@Composable
private fun SportClientFormFields(
    name: String,
    onNameChange: (String) -> Unit,
    nameError: String?,
    apelido: String,
    onApelidoChange: (String) -> Unit,
    apelidoError: String?,
    cpfDigits: String,
    onCpfChange: (String) -> Unit,
    cpfError: String?,
    phoneDigits: String,
    onPhoneChange: (String) -> Unit,
    phoneError: String?,
    selectedModalities: Set<SportModality>,
    onModalityToggle: (SportModality) -> Unit,
    modalityError: String?,
    selectedAttendance: Int?,
    onAttendanceChange: (Int) -> Unit,
    attendanceError: String?,
    selectedPayment: PaymentMethod?,
    onPaymentChange: (PaymentMethod) -> Unit,
    paymentError: String?,
    cashDigits: String,
    onCashChange: (String) -> Unit,
    cashError: String?,
    lastPaymentMonthDisplay: String,
    onLastPaymentMonthClick: () -> Unit,
    lastPaymentMonthError: String?,
    paymentHistory: List<String> = emptyList(),
    onRemovePaymentMonth: (String) -> Unit = {},
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        // --- Nome ---
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Nome *") },
            isError = nameError != null,
            supportingText = nameError?.let { e -> { Text(e) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // --- Apelido (opcional) ---
        OutlinedTextField(
            value = apelido,
            onValueChange = onApelidoChange,
            label = { Text("Apelido") },
            isError = apelidoError != null,
            supportingText = apelidoError?.let { e -> { Text(e) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // --- CPF ---
        OutlinedTextField(
            value = cpfDigits,
            onValueChange = onCpfChange,
            label = { Text("CPF *") },
            isError = cpfError != null,
            supportingText = cpfError?.let { e -> { Text(e) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = CpfMaskTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // --- Telefone ---
        OutlinedTextField(
            value = phoneDigits,
            onValueChange = onPhoneChange,
            label = { Text("Telefone *") },
            isError = phoneError != null,
            supportingText = phoneError?.let { e -> { Text(e) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            visualTransformation = PhoneMaskTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // --- Modalidade ---
        Text(
            text = "Modalidade *",
            style = MaterialTheme.typography.bodySmall,
            color =
                if (modalityError != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            SportModality.entries.forEach { modality ->
                FilterChip(
                    selected = modality in selectedModalities,
                    onClick = { onModalityToggle(modality) },
                    label = { Text(modalityLabel(modality)) },
                )
            }
        }
        if (modalityError != null) {
            Text(
                text = modalityError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Frequência (1~4) ---
        Text(
            text = "Frequência *",
            style = MaterialTheme.typography.bodySmall,
            color =
                if (attendanceError != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..4).forEach { freq ->
                FilterChip(
                    selected = selectedAttendance == freq,
                    onClick = { onAttendanceChange(freq) },
                    label = { Text("${freq}x") },
                )
            }
        }
        if (attendanceError != null) {
            Text(
                text = attendanceError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Pagamento ---
        Text(
            text = "Pagamento *",
            style = MaterialTheme.typography.bodySmall,
            color =
                if (paymentError != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            PaymentMethod.entries.forEach { method ->
                FilterChip(
                    selected = selectedPayment == method,
                    onClick = { onPaymentChange(method) },
                    label = { Text(paymentMethodLabel(method)) },
                )
            }
        }
        if (paymentError != null) {
            Text(
                text = paymentError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // --- Valor em dinheiro (aparece para TODOS os métodos) ---
        if (selectedPayment != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = cashDigits,
                onValueChange = onCashChange,
                label = { Text("Valor (R$) *") },
                isError = cashError != null,
                supportingText = cashError?.let { e -> { Text(e) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = CurrencyMaskTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Mês Pagamento (adicionar ao histórico) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = lastPaymentMonthDisplay,
                onValueChange = {},
                label = { Text("Adicionar Mês Pgto *") },
                placeholder = { Text("MM/AAAA") },
                readOnly = true,
                isError = lastPaymentMonthError != null,
                supportingText = lastPaymentMonthError?.let { e -> { Text(e) } },
                singleLine = true,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Selecionar mês",
                    )
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable { onLastPaymentMonthClick() },
                interactionSource =
                    remember { MutableInteractionSource() }.also { source ->
                        LaunchedEffect(source) {
                            source.interactions.collect { interaction ->
                                if (interaction is PressInteraction.Release) {
                                    onLastPaymentMonthClick()
                                }
                            }
                        }
                    },
            )
        }

        // --- Histórico de pagamentos (chips removíveis) ---
        if (paymentHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Meses pagos:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                paymentHistory.forEach { month ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemovePaymentMonth(month) },
                        label = { Text(month) },
                        trailingIcon = {
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }
    }
}

// =========================================================================
// Month/Year Picker Dialog
// =========================================================================

private val MONTH_NAMES =
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
private fun MonthYearPickerDialog(
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
                                            text = MONTH_NAMES[monthIndex],
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
// Add Dialog
// =========================================================================

@Composable
private fun AddSportClientDialog(
    onDismiss: () -> Unit,
    onConfirm: (SportClient) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var apelido by remember { mutableStateOf("") }
    var cpfDigits by remember { mutableStateOf("") }
    var phoneDigits by remember { mutableStateOf("") }
    var selectedModalities by remember { mutableStateOf<Set<SportModality>>(emptySet()) }
    var selectedAttendance by remember { mutableStateOf<Int?>(null) }
    var selectedPayment by remember { mutableStateOf<PaymentMethod?>(null) }
    var cashDigits by remember { mutableStateOf("0") }
    var lastPaymentMonth by remember { mutableStateOf("") }
    var showMonthPicker by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var apelidoError by remember { mutableStateOf<String?>(null) }
    var cpfError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var modalityError by remember { mutableStateOf<String?>(null) }
    var attendanceError by remember { mutableStateOf<String?>(null) }
    var paymentError by remember { mutableStateOf<String?>(null) }
    var cashError by remember { mutableStateOf<String?>(null) }
    var lastPaymentMonthError by remember { mutableStateOf<String?>(null) }

    if (showMonthPicker) {
        val (defaultYear, defaultMonth) = currentYearMonth()
        val initMonth =
            if (lastPaymentMonth.isNotBlank()) {
                lastPaymentMonth.substringBefore("/").toIntOrNull() ?: defaultMonth
            } else {
                defaultMonth
            }
        val initYear =
            if (lastPaymentMonth.isNotBlank()) {
                lastPaymentMonth.substringAfter("/").toIntOrNull() ?: defaultYear
            } else {
                defaultYear
            }
        MonthYearPickerDialog(
            initialMonth = initMonth,
            initialYear = initYear,
            onDismiss = { showMonthPicker = false },
            onConfirm = { month, year ->
                lastPaymentMonth = "${month.toString().padStart(2, '0')}/${year.toString().padStart(4, '0')}"
                lastPaymentMonthError = null
                showMonthPicker = false
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Cliente") },
        text = {
            SportClientFormFields(
                name = name,
                onNameChange = {
                    name = it
                    nameError = null
                },
                nameError = nameError,
                apelido = apelido,
                onApelidoChange = {
                    apelido = it
                    apelidoError = null
                },
                apelidoError = apelidoError,
                cpfDigits = cpfDigits,
                onCpfChange = {
                    cpfDigits = filterDigitsOnly(it, 11)
                    cpfError = null
                },
                cpfError = cpfError,
                phoneDigits = phoneDigits,
                onPhoneChange = {
                    phoneDigits = filterDigitsOnly(it, 11)
                    phoneError = null
                },
                phoneError = phoneError,
                selectedModalities = selectedModalities,
                onModalityToggle = { modality ->
                    selectedModalities =
                        if (modality in selectedModalities) {
                            selectedModalities - modality
                        } else {
                            selectedModalities + modality
                        }
                    modalityError = null
                },
                modalityError = modalityError,
                selectedAttendance = selectedAttendance,
                onAttendanceChange = {
                    selectedAttendance = it
                    attendanceError = null
                },
                attendanceError = attendanceError,
                selectedPayment = selectedPayment,
                onPaymentChange = {
                    selectedPayment = it
                    paymentError = null
                    cashError = null
                },
                paymentError = paymentError,
                cashDigits = cashDigits,
                onCashChange = {
                    cashDigits = filterDigitsOnly(it, 10)
                    cashError = null
                },
                cashError = cashError,
                lastPaymentMonthDisplay = lastPaymentMonth,
                onLastPaymentMonthClick = { showMonthPicker = true },
                lastPaymentMonthError = lastPaymentMonthError,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val nameResult = SportClientValidator.validateName(name)
                val apelidoResult = SportClientValidator.validateApelido(apelido)
                val cpfResult = SportClientValidator.validateCpf(cpfDigits)
                val phoneResult = SportClientValidator.validatePhone(phoneDigits)
                val modalityResult = SportClientValidator.validateModalidade(selectedModalities.toList())
                val attendanceResult = SportClientValidator.validateAttendance(selectedAttendance)
                val paymentResult = SportClientValidator.validatePaymentMethod(selectedPayment)
                val lastMonthResult = SportClientValidator.validateLastPaymentMonth(lastPaymentMonth)

                nameError = nameResult.errorMessage
                apelidoError = apelidoResult.errorMessage
                cpfError = cpfResult.errorMessage
                phoneError = phoneResult.errorMessage
                modalityError = modalityResult.errorMessage
                attendanceError = attendanceResult.errorMessage
                paymentError = paymentResult.errorMessage
                lastPaymentMonthError = lastMonthResult.errorMessage

                val cashAmountCents = parseCurrencyDigits(cashDigits)
                if (selectedPayment != null) {
                    val cashResult =
                        SportClientValidator.validateCashAmount(
                            cashAmountCents,
                            selectedPayment!!,
                        )
                    cashError = cashResult.errorMessage
                    if (!cashResult.isValid) return@TextButton
                }

                if (!nameResult.isValid ||
                    !apelidoResult.isValid ||
                    !cpfResult.isValid ||
                    !phoneResult.isValid ||
                    !modalityResult.isValid ||
                    !attendanceResult.isValid ||
                    !paymentResult.isValid ||
                    !lastMonthResult.isValid
                ) {
                    return@TextButton
                }

                val client =
                    SportClient(
                        id = "sport_${System.currentTimeMillis()}",
                        name = name.trim(),
                        apelido = apelido.trim(),
                        cpf = cpfDigits,
                        phone = phoneDigits,
                        modalities = selectedModalities.toList(),
                        attendance = selectedAttendance!!,
                        paymentMethod = selectedPayment!!,
                        cashAmountCents = cashAmountCents ?: 0L,
                        paymentHistory =
                            if (lastPaymentMonth.isNotBlank()) {
                                listOf(lastPaymentMonth)
                            } else {
                                emptyList()
                            },
                    )
                onConfirm(client)
            }) {
                Text("Adicionar")
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
// Read-only Detail Dialog
// =========================================================================

@Composable
private fun SportClientDetailDialog(
    state: SportClientState,
    onToggleCpfReveal: () -> Unit,
    onDismiss: () -> Unit,
    onEdit: (SportClient) -> Unit,
    onDelete: (String) -> Unit,
) {
    val client = state.selectedClient ?: return
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        DeleteSportClientConfirmDialog(
            clientName = client.name,
            onConfirm = {
                showDeleteConfirmation = false
                onDelete(client.id)
            },
            onCancel = { showDeleteConfirmation = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            SportClientDetailTitle(
                clientName = client.name,
                onEdit = { onEdit(client) },
                onDelete = { showDeleteConfirmation = true },
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SportClientDetailFields(
                    client = client,
                    displayCpf = state.displayCpf,
                    canRevealCpf = state.canRevealCpf,
                    isCpfRevealed = state.isCpfRevealed,
                    onToggleCpfReveal = onToggleCpfReveal,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        },
    )
}

/** Confirmação de exclusão do cliente, disparada pelo ícone de lixeira do detalhe. */
@Composable
private fun DeleteSportClientConfirmDialog(
    clientName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Excluir Cliente") },
        text = { Text("Deseja realmente excluir \"$clientName\"?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    "Excluir",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancelar")
            }
        },
    )
}

/** Cabeçalho do detalhe: nome do cliente + ações de editar e excluir. */
@Composable
private fun SportClientDetailTitle(
    clientName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(clientName, modifier = Modifier.weight(1f))
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Editar",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Excluir",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Campos do detalhe read-only. O CPF vem pronto do state ([displayCpf]) — a tela
 * nunca formata o número completo por conta própria.
 */
@Composable
private fun SportClientDetailFields(
    client: SportClient,
    displayCpf: String,
    canRevealCpf: Boolean,
    isCpfRevealed: Boolean,
    onToggleCpfReveal: () -> Unit,
) {
    if (client.apelido.isNotBlank()) {
        DetailRow(label = "Apelido", value = client.apelido)
    }
    CpfDetailRow(
        displayCpf = displayCpf,
        canRevealCpf = canRevealCpf,
        isCpfRevealed = isCpfRevealed,
        onToggleCpfReveal = onToggleCpfReveal,
    )
    DetailRow(label = "Telefone", value = formatPhone(client.phone))
    DetailRow(
        label = "Modalidade",
        value = modalitiesLabel(client.modalities),
    )
    DetailRow(label = "Frequência", value = "${client.attendance}x por semana")
    DetailRow(
        label = "Pagamento",
        value = paymentMethodLabel(client.paymentMethod),
    )
    DetailRow(
        label = "Valor",
        value = formatCurrency(client.cashAmountCents),
    )
    if (client.paymentHistory.isNotEmpty()) {
        DetailRow(
            label = "Meses pagos",
            value = client.paymentHistory.joinToString(", "),
        )
    }
}

/**
 * Linha do CPF com o botão de revelar. O botão só existe para quem o state já
 * autorizou; sem [canRevealCpf] não há sequer o gesto disponível.
 */
@Composable
private fun CpfDetailRow(
    displayCpf: String,
    canRevealCpf: Boolean,
    isCpfRevealed: Boolean,
    onToggleCpfReveal: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DetailRow(label = "CPF", value = displayCpf)
        if (canRevealCpf) {
            IconButton(
                onClick = onToggleCpfReveal,
                modifier =
                    Modifier.semantics {
                        contentDescription = if (isCpfRevealed) "Ocultar CPF" else "Revelar CPF"
                    },
            ) {
                Text(
                    text = if (isCpfRevealed) "🙈" else "👁",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatPhone(digits: String): String {
    if (digits.length != 11) return digits
    return "(${digits.substring(0, 2)}) ${digits.substring(2, 7)}-${digits.substring(7)}"
}

private fun formatCurrency(cents: Long): String {
    val reais = cents / 100
    val centavos = cents % 100
    return "R$ $reais,${centavos.toString().padStart(2, '0')}"
}
