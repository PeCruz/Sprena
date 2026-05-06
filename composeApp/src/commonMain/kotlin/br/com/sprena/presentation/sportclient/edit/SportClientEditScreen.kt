package br.com.sprena.presentation.sportclient.edit

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.sprena.core.ui.mask.CpfMaskTransformation
import br.com.sprena.core.ui.mask.CurrencyMaskTransformation
import br.com.sprena.core.ui.mask.PhoneMaskTransformation
import br.com.sprena.core.ui.mask.centsToDigitString
import br.com.sprena.core.ui.mask.filterDigitsOnly
import br.com.sprena.core.ui.mask.parseCurrencyDigits
import br.com.sprena.presentation.financial.currentYearMonth
import br.com.sprena.presentation.sportclient.SportClient
import br.com.sprena.shared.sportclient.domain.validation.PaymentMethod
import br.com.sprena.shared.sportclient.domain.validation.SportClientValidator
import br.com.sprena.shared.sportclient.domain.validation.SportModality

// =========================================================================
// Color constants for the gradient header
// =========================================================================

private val GradientStart = Color(0xFF0077B6) // Primary
private val GradientEnd = Color(0xFF0353A4)   // Secondary

// =========================================================================
// Label helpers
// =========================================================================

private fun paymentMethodLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.WELLHUB -> "Wellhub"
    PaymentMethod.TOTALPASS -> "TotalPass"
    PaymentMethod.CASH -> "Cash"
}

private fun modalityLabel(modality: SportModality): String = when (modality) {
    SportModality.FUTEVOLEI -> "Futevôlei"
    SportModality.BEACH_TENNIS -> "Beach Tennis"
    SportModality.VOLEI -> "Vôlei"
}

private val MONTH_NAMES = listOf(
    "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
    "Jul", "Ago", "Set", "Out", "Nov", "Dez",
)

// =========================================================================
// Edit Screen
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportClientEditScreen(
    client: SportClient,
    onNavigateBack: () -> Unit,
    onSave: (SportClient) -> Unit,
) {
    // --- Editable state ---
    var name by remember(client.id) { mutableStateOf(client.name) }
    var apelido by remember(client.id) { mutableStateOf(client.apelido) }
    var cpfDigits by remember(client.id) { mutableStateOf(client.cpf) }
    var phoneDigits by remember(client.id) { mutableStateOf(client.phone) }
    var selectedModalities by remember(client.id) { mutableStateOf(client.modalities.toSet()) }
    var selectedAttendance by remember(client.id) { mutableStateOf<Int?>(client.attendance) }
    var selectedPayment by remember(client.id) { mutableStateOf<PaymentMethod?>(client.paymentMethod) }
    var cashDigits by remember(client.id) { mutableStateOf(centsToDigitString(client.cashAmountCents)) }
    var paymentHistory by remember(client.id) { mutableStateOf(client.paymentHistory) }
    var lastPaymentMonth by remember(client.id) { mutableStateOf("") }
    var showMonthPicker by remember { mutableStateOf(false) }

    // --- Validation errors ---
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
        val initMonth = if (lastPaymentMonth.isNotBlank()) {
            lastPaymentMonth.substringBefore("/").toIntOrNull() ?: defaultMonth
        } else {
            defaultMonth
        }
        val initYear = if (lastPaymentMonth.isNotBlank()) {
            lastPaymentMonth.substringAfter("/").toIntOrNull() ?: defaultYear
        } else {
            defaultYear
        }
        MonthYearPickerDialog(
            initialMonth = initMonth,
            initialYear = initYear,
            onDismiss = { showMonthPicker = false },
            onConfirm = { month, year ->
                val newMonth = "${month.toString().padStart(2, '0')}/${year.toString().padStart(4, '0')}"
                if (newMonth !in paymentHistory) {
                    paymentHistory = paymentHistory + newMonth
                }
                lastPaymentMonth = newMonth
                lastPaymentMonthError = null
                showMonthPicker = false
            },
        )
    }

    val onSaveClick = {
        val nameResult = SportClientValidator.validateName(name)
        val apelidoResult = SportClientValidator.validateApelido(apelido)
        val cpfResult = SportClientValidator.validateCpf(cpfDigits)
        val phoneResult = SportClientValidator.validatePhone(phoneDigits)
        val modalityResult = SportClientValidator.validateModalidade(selectedModalities.toList())
        val attendanceResult = SportClientValidator.validateAttendance(selectedAttendance)
        val paymentResult = SportClientValidator.validatePaymentMethod(selectedPayment)

        nameError = nameResult.errorMessage
        apelidoError = apelidoResult.errorMessage
        cpfError = cpfResult.errorMessage
        phoneError = phoneResult.errorMessage
        modalityError = modalityResult.errorMessage
        attendanceError = attendanceResult.errorMessage
        paymentError = paymentResult.errorMessage

        if (paymentHistory.isEmpty()) {
            lastPaymentMonthError = "Adicione ao menos um mês de pagamento"
        } else {
            lastPaymentMonthError = null
        }

        val cashAmountCents = parseCurrencyDigits(cashDigits)
        var cashValid = true
        if (selectedPayment != null) {
            val cashResult = SportClientValidator.validateCashAmount(
                cashAmountCents,
                selectedPayment!!,
            )
            cashError = cashResult.errorMessage
            cashValid = cashResult.isValid
        }

        if (nameResult.isValid && apelidoResult.isValid && cpfResult.isValid &&
            phoneResult.isValid && modalityResult.isValid && attendanceResult.isValid &&
            paymentResult.isValid && paymentHistory.isNotEmpty() && cashValid
        ) {
            onSave(
                client.copy(
                    name = name.trim(),
                    apelido = apelido.trim(),
                    cpf = cpfDigits,
                    phone = phoneDigits,
                    modalities = selectedModalities.toList(),
                    attendance = selectedAttendance!!,
                    paymentMethod = selectedPayment!!,
                    cashAmountCents = cashAmountCents ?: 0L,
                    paymentHistory = paymentHistory,
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Cliente", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            // --- Gradient header with avatar ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(GradientStart, GradientEnd),
                        ),
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .offset(y = 36.dp)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    val initials = name.trim()
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("") { it.first().uppercase() }
                        .ifEmpty { "?" }
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(44.dp))

            // --- Form content ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                // --- Nome ---
                EditField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = "Nome completo *",
                    icon = Icons.Default.Person,
                    error = nameError,
                )

                // --- Apelido ---
                EditField(
                    value = apelido,
                    onValueChange = { apelido = it; apelidoError = null },
                    label = "Apelido",
                    icon = Icons.Default.Person,
                    error = apelidoError,
                )

                // --- CPF ---
                EditField(
                    value = cpfDigits,
                    onValueChange = { cpfDigits = filterDigitsOnly(it, 11); cpfError = null },
                    label = "CPF *",
                    icon = Icons.Default.Person,
                    error = cpfError,
                    keyboardType = KeyboardType.Number,
                    visualTransformation = CpfMaskTransformation(),
                )

                // --- Telefone ---
                EditField(
                    value = phoneDigits,
                    onValueChange = { phoneDigits = filterDigitsOnly(it, 11); phoneError = null },
                    label = "Telefone *",
                    icon = Icons.Default.Phone,
                    error = phoneError,
                    keyboardType = KeyboardType.Phone,
                    visualTransformation = PhoneMaskTransformation(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // --- Modalidade ---
                ChipSection(
                    label = "Modalidade *",
                    error = modalityError,
                ) {
                    SportModality.entries.forEach { modality ->
                        FilterChip(
                            selected = modality in selectedModalities,
                            onClick = {
                                selectedModalities = if (modality in selectedModalities) {
                                    selectedModalities - modality
                                } else {
                                    selectedModalities + modality
                                }
                                modalityError = null
                            },
                            label = { Text(modalityLabel(modality)) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- Frequência ---
                ChipSection(
                    label = "Frequência *",
                    error = attendanceError,
                ) {
                    (1..4).forEach { freq ->
                        FilterChip(
                            selected = selectedAttendance == freq,
                            onClick = { selectedAttendance = freq; attendanceError = null },
                            label = { Text("${freq}x") },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- Pagamento ---
                ChipSection(
                    label = "Pagamento *",
                    error = paymentError,
                ) {
                    PaymentMethod.entries.forEach { method ->
                        FilterChip(
                            selected = selectedPayment == method,
                            onClick = {
                                selectedPayment = method
                                paymentError = null
                                cashError = null
                            },
                            label = { Text(paymentMethodLabel(method)) },
                        )
                    }
                }

                // --- Valor ---
                if (selectedPayment != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cashDigits,
                        onValueChange = { cashDigits = filterDigitsOnly(it, 10); cashError = null },
                        label = { Text("Valor (R$) *") },
                        leadingIcon = {
                            Text(
                                text = "R$",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        isError = cashError != null,
                        supportingText = cashError?.let { e -> { Text(e) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = CurrencyMaskTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- Mês Pagamento ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = lastPaymentMonth,
                        onValueChange = {},
                        label = { Text("Adicionar Mês Pgto *") },
                        placeholder = { Text("MM/AAAA") },
                        readOnly = true,
                        isError = lastPaymentMonthError != null,
                        supportingText = lastPaymentMonthError?.let { e -> { Text(e) } },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showMonthPicker = true },
                        interactionSource = remember { MutableInteractionSource() }.also { source ->
                            LaunchedEffect(source) {
                                source.interactions.collect { interaction ->
                                    if (interaction is PressInteraction.Release) {
                                        showMonthPicker = true
                                    }
                                }
                            }
                        },
                    )
                }

                // --- Histórico de pagamentos ---
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
                                onClick = { paymentHistory = paymentHistory - month },
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

                Spacer(modifier = Modifier.height(24.dp))
            }

            // --- Save button (fixed at bottom) ---
            Button(
                onClick = onSaveClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GradientStart,
                ),
            ) {
                Text(
                    text = "Salvar",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// =========================================================================
// Reusable edit field with icon + rounded shape
// =========================================================================

@Composable
private fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    error: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        isError = error != null,
        supportingText = error?.let { e -> { Text(e) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(4.dp))
}

// =========================================================================
// Chip section (label + row of chips)
// =========================================================================

@Composable
private fun ChipSection(
    label: String,
    error: String?,
    content: @Composable () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = if (error != null) {
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
        content()
    }
    if (error != null) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

// =========================================================================
// Month/Year Picker Dialog
// =========================================================================

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
