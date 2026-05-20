package br.com.sprena.presentation.eventos.createevent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.com.sprena.presentation.eventos.EventCategory
import br.com.sprena.presentation.eventos.formatEpochDay

/**
 * Tela de criacao/edicao de evento — formulario full-screen.
 *
 * Campos:
 *  - Nome do Evento (obrigatorio)
 *  - Categoria (obrigatorio) — dropdown com Eventos, Aluguel, Day Use
 *  - Data do Evento (obrigatorio) — date picker (abre ao clicar no campo)
 *  - Contato (opcional) — com mascara de telefone
 *  - Descricao (opcional)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    viewModel: CreateEventViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onEventSaved: (
        eventId: String?,
        name: String,
        category: EventCategory,
        dateEpochDay: Long,
        contact: String?,
        description: String?,
    ) -> Unit = { _, _, _, _, _, _ -> },
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CreateEventEffect.EventSaved -> {
                    onEventSaved(
                        effect.eventId,
                        effect.name,
                        effect.category,
                        effect.dateEpochDay,
                        effect.contact,
                        effect.description,
                    )
                    onNavigateBack()
                }
                is CreateEventEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    val screenTitle = if (state.isEditMode) "Editar Evento" else "Criar Evento"
    val buttonLabel = if (state.isEditMode) "Salvar Alteracoes" else "Salvar Evento"

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.handleIntent(CreateEventIntent.BackClicked)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
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
                    .padding(horizontal = 16.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // --- Nome do Evento (obrigatorio) ---
            OutlinedTextField(
                value = state.name,
                onValueChange = {
                    viewModel.handleIntent(CreateEventIntent.NameChanged(it))
                },
                label = { Text("Nome do Evento *") },
                isError = state.nameError != null,
                supportingText =
                    state.nameError?.let { error ->
                        { Text(error, color = MaterialTheme.colorScheme.error) }
                    },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // --- Categoria (obrigatorio) — exclui REALIZADOS do dropdown ---
            CategoryDropdown(
                selected = state.category,
                isError = state.categoryError != null,
                errorText = state.categoryError,
                onCategorySelected = {
                    viewModel.handleIntent(CreateEventIntent.CategoryChanged(it))
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // --- Data do Evento (obrigatorio) — clique no campo inteiro abre picker ---
            EventDateField(
                dateEpochDay = state.dateEpochDay,
                isError = state.dateError != null,
                errorText = state.dateError,
                onDateSelected = {
                    viewModel.handleIntent(CreateEventIntent.DateChanged(it))
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // --- Contato (opcional) — com mascara de telefone ---
            // State stores raw digits only; mask is applied visually.
            OutlinedTextField(
                value = state.contact,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(11)
                    viewModel.handleIntent(CreateEventIntent.ContactChanged(digits))
                },
                label = { Text("Contato") },
                placeholder = { Text("(00) 00000-0000") },
                singleLine = true,
                visualTransformation = PhoneMaskTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // --- Descricao (opcional) ---
            OutlinedTextField(
                value = state.description,
                onValueChange = {
                    viewModel.handleIntent(CreateEventIntent.DescriptionChanged(it))
                },
                label = { Text("Descricao") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- Botao Salvar ---
            Button(
                onClick = { viewModel.handleIntent(CreateEventIntent.Submit) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(buttonLabel)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Aplica mascara de telefone brasileiro como VisualTransformation.
 * Entrada: somente digitos (max 11). Saida: (XX) XXXXX-XXXX ou (XX) XXXX-XXXX.
 *
 * Isso evita problemas de cursor — o TextField armazena somente digitos
 * e a mascara e aplicada apenas na camada visual.
 */
internal class PhoneMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val masked = applyPhoneMask(digits)
        return TransformedText(
            AnnotatedString(masked),
            PhoneOffsetMapping(digits, masked),
        )
    }
}

/**
 * Mapeia posicoes de cursor entre digitos puros e texto mascarado.
 */
private class PhoneOffsetMapping(
    private val original: String,
    private val masked: String,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        // Map original digit index → masked string index
        var digitsSeen = 0
        for (i in masked.indices) {
            if (digitsSeen == offset) return i
            if (masked[i].isDigit()) digitsSeen++
        }
        return masked.length
    }

    override fun transformedToOriginal(offset: Int): Int {
        // Map masked string index → original digit index
        var digitsSeen = 0
        for (i in 0 until offset.coerceAtMost(masked.length)) {
            if (masked[i].isDigit()) digitsSeen++
        }
        return digitsSeen.coerceAtMost(original.length)
    }
}

/**
 * Aplica mascara de telefone brasileiro: (XX) XXXXX-XXXX ou (XX) XXXX-XXXX.
 */
internal fun applyPhoneMask(digits: String): String =
    buildString {
        digits.forEachIndexed { i, c ->
            when (i) {
                0 -> append("($c")
                1 -> append("$c) ")
                6 -> if (digits.length <= 10) append("-$c") else append(c)
                7 -> if (digits.length > 10) append("-$c") else append(c)
                else -> append(c)
            }
        }
    }

/**
 * Dropdown para selecao de categoria (exclui REALIZADOS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selected: EventCategory?,
    isError: Boolean,
    errorText: String?,
    onCategorySelected: (EventCategory) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectableCategories = EventCategory.entries.toList()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected?.label ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Categoria *") },
            isError = isError,
            supportingText =
                errorText?.let { error ->
                    { Text(error, color = MaterialTheme.colorScheme.error) }
                },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            selectableCategories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.label) },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Campo de data — clicar em qualquer lugar do campo abre o DatePicker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDateField(
    dateEpochDay: Long?,
    isError: Boolean,
    errorText: String?,
    onDateSelected: (Long) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    // Box with clickable overlay so tapping anywhere opens the picker
    androidx.compose.foundation.layout.Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true },
    ) {
        OutlinedTextField(
            value = dateEpochDay?.let { formatEpochDay(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text("Data do Evento *") },
            isError = isError,
            supportingText =
                errorText?.let { error ->
                    { Text(error, color = MaterialTheme.colorScheme.error) }
                },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = "Selecionar data",
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors =
                androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledBorderColor =
                        if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        )
    }

    if (showDatePicker) {
        val datePickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = dateEpochDay?.let { it * 86_400_000L },
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean = true
                    },
            )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val epochDay = millis / 86_400_000L
                            onDateSelected(epochDay)
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
}
