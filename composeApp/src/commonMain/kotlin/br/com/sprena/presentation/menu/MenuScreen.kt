package br.com.sprena.presentation.menu

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.sprena.core.ui.components.ThemeToggleButton
import br.com.sprena.core.ui.mask.CurrencyMaskTransformation
import br.com.sprena.core.ui.mask.centsToDigitString
import br.com.sprena.core.ui.mask.filterDigitsOnly
import br.com.sprena.core.ui.mask.parseCurrencyDigits
import br.com.sprena.presentation.core.theme.ThemeViewModel
import br.com.sprena.shared.menu.domain.validation.MenuValidator
import kotlinx.coroutines.launch

/**
 * Tela de configuração do Cardápio — lista de itens com busca,
 * botão de adicionar, e clique para editar/deletar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    viewModel: MenuViewModel,
    themeViewModel: ThemeViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MenuEffect.NavigateBack -> onNavigateBack()
                is MenuEffect.ShowError -> {
                    scope.launch { snackbarHostState.showSnackbar(effect.message) }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Cardápio") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.handleIntent(MenuIntent.NavigateBackClicked)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                        )
                    }
                },
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
                        viewModel.handleIntent(MenuIntent.SearchQueryChanged(it))
                    },
                    placeholder = { Text("Buscar item...") },
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
                    onClick = { viewModel.handleIntent(MenuIntent.AddItemClicked) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adicionar item",
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Item List ---
            if (state.filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text =
                            if (state.items.isEmpty()) {
                                "Nenhum item no cardápio"
                            } else {
                                "Nenhum item encontrado"
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    items(state.filteredItems, key = { it.id }) { item ->
                        MenuItemRow(
                            item = item,
                            onClick = {
                                viewModel.handleIntent(MenuIntent.ItemClicked(item))
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
        AddMenuItemDialog(
            onDismiss = { viewModel.handleIntent(MenuIntent.DismissAddDialog) },
            onConfirm = { item ->
                viewModel.handleIntent(MenuIntent.ItemAdded(item))
            },
        )
    }

    // --- Edit / Detail Dialog ---
    val selectedItem = state.selectedItem
    if (selectedItem != null) {
        EditMenuItemDialog(
            item = selectedItem,
            onDismiss = { viewModel.handleIntent(MenuIntent.DismissItemDetail) },
            onUpdate = { updated ->
                viewModel.handleIntent(MenuIntent.ItemUpdated(updated))
            },
            onDelete = { itemId ->
                viewModel.handleIntent(MenuIntent.ItemDeleted(itemId))
            },
        )
    }
}

@Composable
private fun MenuItemRow(
    item: MenuItem,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.description.isNullOrBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatCents(item.priceCents),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// =========================================================================
// Add Dialog
// =========================================================================

@Composable
private fun AddMenuItemDialog(
    onDismiss: () -> Unit,
    onConfirm: (MenuItem) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var priceDigits by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var priceError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Nome *") },
                    isError = nameError != null,
                    supportingText = nameError?.let { error -> { Text(error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = priceDigits,
                    onValueChange = {
                        priceDigits = filterDigitsOnly(it, 10)
                        priceError = null
                    },
                    label = { Text("Preço *") },
                    placeholder = { Text("0,00") },
                    isError = priceError != null,
                    supportingText = priceError?.let { error -> { Text(error) } },
                    singleLine = true,
                    visualTransformation = CurrencyMaskTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val nameResult = MenuValidator.validateMenuItemName(name)
                val priceCents = parseCurrencyDigits(priceDigits)
                val priceResult = MenuValidator.validateMenuItemPrice(priceCents)

                nameError = nameResult.errorMessage
                priceError = priceResult.errorMessage

                if (nameResult.isValid && priceResult.isValid && priceCents != null) {
                    val item =
                        MenuItem(
                            id = generateId(),
                            name = name.trim(),
                            priceCents = priceCents,
                            description = description.trim().ifBlank { null },
                        )
                    onConfirm(item)
                }
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
// Edit / Detail Dialog
// =========================================================================

@Composable
private fun EditMenuItemDialog(
    item: MenuItem,
    onDismiss: () -> Unit,
    onUpdate: (MenuItem) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var priceDigits by remember(item.id) { mutableStateOf(centsToDigitString(item.priceCents)) }
    var description by remember(item.id) { mutableStateOf(item.description ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var priceError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Excluir Item") },
            text = { Text("Deseja realmente excluir \"${item.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDelete(item.id)
                }) {
                    Text(
                        "Excluir",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancelar")
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Editar Item")
                IconButton(onClick = { showDeleteConfirmation = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Nome *") },
                    isError = nameError != null,
                    supportingText = nameError?.let { error -> { Text(error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = priceDigits,
                    onValueChange = {
                        priceDigits = filterDigitsOnly(it, 10)
                        priceError = null
                    },
                    label = { Text("Preço *") },
                    placeholder = { Text("0,00") },
                    isError = priceError != null,
                    supportingText = priceError?.let { error -> { Text(error) } },
                    singleLine = true,
                    visualTransformation = CurrencyMaskTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val nameResult = MenuValidator.validateMenuItemName(name)
                val priceCents = parseCurrencyDigits(priceDigits)
                val priceResult = MenuValidator.validateMenuItemPrice(priceCents)

                nameError = nameResult.errorMessage
                priceError = priceResult.errorMessage

                if (nameResult.isValid && priceResult.isValid && priceCents != null) {
                    val updated =
                        item.copy(
                            name = name.trim(),
                            priceCents = priceCents,
                            description = description.trim().ifBlank { null },
                        )
                    onUpdate(updated)
                }
            }) {
                Text("Salvar")
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
// Helpers
// =========================================================================

/**
 * Formata centavos para R$ X,XX.
 */
private fun formatCents(cents: Long): String {
    val reais = cents / 100
    val centavos = cents % 100
    return "R\$ $reais,${centavos.toString().padStart(2, '0')}"
}

/**
 * Gera um ID simples baseado em timestamp.
 */
private fun generateId(): String = "menu_${System.currentTimeMillis()}"
