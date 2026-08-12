package br.com.sprena.presentation.bar.clientdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.sprena.core.ui.mask.CurrencyMaskTransformation
import br.com.sprena.core.ui.mask.centsToDigitString
import br.com.sprena.core.ui.mask.filterDigitsOnly
import br.com.sprena.core.ui.mask.parseCurrencyDigits
import br.com.sprena.presentation.bar.BarClient
import br.com.sprena.presentation.bar.BarItem
import br.com.sprena.presentation.menu.MenuItem as CardapioItem

/**
 * Bottom sheet de detalhe do cliente — exibe dados do cliente,
 * lista de itens consumidos com preço, botão adicionar item,
 * checkbox de pagamento, total e botão deletar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailSheet(
    viewModel: ClientDetailViewModel,
    sheetState: SheetState,
    menuItems: List<CardapioItem> = emptyList(),
    onDismiss: () -> Unit,
    onClientUpdated: (BarClient) -> Unit,
    onClientDeleted: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ClientDetailEffect.ClientUpdated -> onClientUpdated(effect.client)
                is ClientDetailEffect.ClientDeleted -> onClientDeleted(effect.clientId)
                is ClientDetailEffect.ShowError -> { /* errors shown inline */ }
                is ClientDetailEffect.Dismissed -> onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.handleIntent(ClientDetailIntent.Dismiss) },
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp),
        ) {
            // --- Header: name + delete icon ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.clientNickname ?: state.clientName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (state.clientNickname != null) {
                        Text(
                            text = state.clientName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = { viewModel.handleIntent(ClientDetailIntent.DeleteClicked) },
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Deletar cliente",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- Client info ---
            Text(
                text = "Tel: ${state.clientPhone}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CPF: ${state.displayCpf}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.canRevealCpf) {
                    IconButton(
                        onClick = { viewModel.handleIntent(ClientDetailIntent.ToggleCpfReveal) },
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    if (state.isCpfRevealed) "Ocultar CPF" else "Revelar CPF"
                            },
                    ) {
                        Text(
                            text = if (state.isCpfRevealed) "🙈" else "👁",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            state.clientEmail?.let { email ->
                Text(
                    text = "Email: $email",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // --- Items header + Add button ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Itens Consumidos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = { viewModel.handleIntent(ClientDetailIntent.AddItemClicked) },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adicionar item",
                    )
                }
            }

            // --- Add Item Form ---
            if (state.isAddItemVisible) {
                AddItemForm(
                    state = state,
                    menuItems = menuItems,
                    onMenuItemSelected = { menuItem ->
                        viewModel.handleIntent(ClientDetailIntent.MenuItemSelected(menuItem))
                    },
                    onNameChanged = { name ->
                        viewModel.handleIntent(ClientDetailIntent.NewItemNameChanged(name))
                    },
                    onPriceChanged = { priceCents ->
                        viewModel.handleIntent(ClientDetailIntent.NewItemPriceChanged(priceCents))
                    },
                    onSave = { viewModel.handleIntent(ClientDetailIntent.SaveItem) },
                    onCancel = { viewModel.handleIntent(ClientDetailIntent.DismissAddItem) },
                )
            }

            // --- Item List (paginated, 4 per page) ---
            if (state.items.isEmpty()) {
                Text(
                    text = "Nenhum item adicionado",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(state.paginatedItems, key = { it.id }) { item ->
                        ItemRow(
                            item = item,
                            onIncrement = {
                                viewModel.handleIntent(ClientDetailIntent.IncrementItem(item.id))
                            },
                            onDecrement = {
                                viewModel.handleIntent(ClientDetailIntent.DecrementItem(item.id))
                            },
                        )
                    }
                }

                // --- Pagination controls ---
                if (state.totalPages > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { viewModel.handleIntent(ClientDetailIntent.PrevItemsPage) },
                            enabled = state.itemsPage > 0,
                        ) {
                            Text("Anterior")
                        }
                        Text(
                            text = "${state.itemsPage + 1} / ${state.totalPages}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        TextButton(
                            onClick = { viewModel.handleIntent(ClientDetailIntent.NextItemsPage) },
                            enabled = state.itemsPage < state.totalPages - 1,
                        ) {
                            Text("Próximo")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // --- Total + Paid ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Total: ${formatCents(state.totalCents)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Pago",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Checkbox(
                        checked = state.isPaid,
                        onCheckedChange = {
                            viewModel.handleIntent(ClientDetailIntent.TogglePaid)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- Delete confirmation dialog ---
    if (state.isDeleteConfirmVisible) {
        AlertDialog(
            onDismissRequest = {
                viewModel.handleIntent(ClientDetailIntent.DeleteCancelled)
            },
            title = { Text("Deletar cliente") },
            text = { Text("Você tem certeza que deseja deletar este cliente e todos os seus itens?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.handleIntent(ClientDetailIntent.DeleteConfirmed) },
                ) {
                    Text("Sim, deletar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.handleIntent(ClientDetailIntent.DeleteCancelled) },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }

    // --- Item delete confirmation dialog (decrement from quantity 1) ---
    if (state.itemToDeleteId != null) {
        val itemName = state.items.firstOrNull { it.id == state.itemToDeleteId }?.name ?: ""
        AlertDialog(
            onDismissRequest = {
                viewModel.handleIntent(ClientDetailIntent.CancelDeleteItem)
            },
            title = { Text("Remover item") },
            text = { Text("Deseja remover \"$itemName\" da lista?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.handleIntent(ClientDetailIntent.ConfirmDeleteItem) },
                ) {
                    Text("Sim, remover", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.handleIntent(ClientDetailIntent.CancelDeleteItem) },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
}

/**
 * Formulário de adicionar item com Menu dropdown, Nome e Preço (com máscara de centavos).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemForm(
    state: ClientDetailState,
    menuItems: List<CardapioItem> = emptyList(),
    onMenuItemSelected: (MenuItem) -> Unit,
    onNameChanged: (String) -> Unit,
    onPriceChanged: (Long?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    // Controla o estado local da string de dígitos do preço
    // para a máscara de centavos (separado do priceCents do state)
    var priceDigits by remember(state.newItemPriceCents) {
        mutableStateOf(
            state.newItemPriceCents?.let { centsToDigitString(it) } ?: "",
        )
    }

    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // --- Menu Dropdown ---
            ExposedDropdownMenuBox(
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it },
            ) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Menu") },
                    placeholder = { Text("Selecione do cardápio") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded)
                    },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (menuItems.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Nenhum item no cardápio",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = { menuExpanded = false },
                        )
                    } else {
                        menuItems.forEach { cardapioItem ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(cardapioItem.name)
                                        Text(
                                            text = formatCents(cardapioItem.priceCents),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    val selected =
                                        MenuItem(
                                            name = cardapioItem.name,
                                            priceCents = cardapioItem.priceCents,
                                        )
                                    onMenuItemSelected(selected)
                                    priceDigits = centsToDigitString(cardapioItem.priceCents)
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- Nome do item ---
            OutlinedTextField(
                value = state.newItemName,
                onValueChange = onNameChanged,
                label = { Text("Nome do item *") },
                singleLine = true,
                isError = state.newItemNameError != null,
                supportingText = state.newItemNameError?.let { e -> { Text(e) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(4.dp))

            // --- Preço com máscara de centavos ---
            OutlinedTextField(
                value = priceDigits,
                onValueChange = { input ->
                    val digits = filterDigitsOnly(input, 9)
                    priceDigits = digits
                    onPriceChanged(parseCurrencyDigits(digits))
                },
                label = { Text("Preço (R$) *") },
                singleLine = true,
                isError = state.newItemPriceError != null,
                supportingText = state.newItemPriceError?.let { e -> { Text(e) } },
                visualTransformation = CurrencyMaskTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) {
                    Text("Cancelar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSave) {
                    Text("Adicionar")
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: BarItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatCents(item.priceCents * item.quantity),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.width(8.dp))
        // --- Quantity controls: - [qty] + ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Diminuir quantidade",
                )
            }
            Text(
                text = item.quantity.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onIncrement) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Aumentar quantidade",
                )
            }
        }
    }
}

private fun formatCents(cents: Long): String {
    val reais = cents / 100
    val centavos = cents % 100
    return "R\$ $reais,${centavos.toString().padStart(2, '0')}"
}
