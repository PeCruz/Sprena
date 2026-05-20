package br.com.sprena.presentation.eventos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.sprena.core.ui.components.ThemeToggleButton
import br.com.sprena.presentation.core.theme.ThemeViewModel
import kotlinx.coroutines.launch

/**
 * Cores por categoria de evento.
 */
private val AluguelColor = Color(0xFF6959CD)
private val ReservaColor = Color(0xFF9370DB)
private val DayUseColor = Color(0xFFEE82EE)
private val CompletedColor = Color(0xFFFF0000)

/**
 * Tela principal de Eventos — duas tabs (Eventos, Eventos Realizados),
 * search input no topo, filtro de categoria dropdown, navegador de mes,
 * lista flat de cards com cores por categoria.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventosScreen(
    viewModel: EventosViewModel,
    themeViewModel: ThemeViewModel,
    modifier: Modifier = Modifier,
    onNavigateCreateEvent: () -> Unit = {},
    onNavigateEditEvent: (Event) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EventosEffect.NavigateToCreateEvent -> onNavigateCreateEvent()
                is EventosEffect.NavigateToEditEvent -> onNavigateEditEvent(effect.event)
                is EventosEffect.ShowError -> {
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
                title = {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = {
                            viewModel.handleIntent(EventosIntent.SearchQueryChanged(it))
                        },
                        placeholder = { Text("Buscar eventos...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                actions = {
                    ThemeToggleButton(themeViewModel = themeViewModel)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.handleIntent(EventosIntent.AddEventClicked)
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Criar evento",
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // --- Filter Row: Category Dropdown + Month Navigator ---
            FilterRow(
                categoryFilter = state.categoryFilter,
                filterMonth = state.filterMonth,
                filterYear = state.filterYear,
                onCategoryFilterChanged = { category ->
                    viewModel.handleIntent(EventosIntent.CategoryFilterChanged(category))
                },
                onMonthBack = {
                    viewModel.handleIntent(EventosIntent.MonthNavigatedBack)
                },
                onMonthForward = {
                    viewModel.handleIntent(EventosIntent.MonthNavigatedForward)
                },
            )

            // --- Two Tabs: Eventos (with counter) | Eventos Realizados ---
            val tabIndex = state.tabs.indexOf(state.selectedTab).coerceAtLeast(0)

            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                state.tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = {
                            viewModel.handleIntent(EventosIntent.TabSelected(tab))
                        },
                        text = {
                            if (tab == EventTab.EVENTOS && state.eventCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(state.eventCount.toString())
                                        }
                                    },
                                ) {
                                    Text(tab.label)
                                }
                            } else {
                                Text(tab.label)
                            }
                        },
                    )
                }
            }

            // --- Event List ---
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (state.filteredEvents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.isSearchActive) {
                            "Nenhum evento encontrado"
                        } else {
                            "Nenhum evento"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.filteredEvents, key = { it.id }) { event ->
                        EventCard(
                            event = event,
                            isCompleted = state.selectedTab == EventTab.EVENTOS_REALIZADOS,
                            onClick = {
                                viewModel.handleIntent(EventosIntent.EventClicked(event))
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Linha de filtros: dropdown de categoria (esquerda) + navegador de mes (direita).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    categoryFilter: EventCategory?,
    filterMonth: Int,
    filterYear: Int,
    onCategoryFilterChanged: (EventCategory?) -> Unit,
    onMonthBack: () -> Unit,
    onMonthForward: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // --- Category Dropdown (left) ---
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = categoryFilter?.label ?: "Todos",
                onValueChange = {},
                readOnly = true,
                label = { Text("Categoria") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Todos") },
                    onClick = {
                        onCategoryFilterChanged(null)
                        expanded = false
                    },
                )
                EventCategory.entries.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.label) },
                        onClick = {
                            onCategoryFilterChanged(category)
                            expanded = false
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // --- Month Navigator (right) — ◀ Mes/Ano ▶ ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMonthBack, modifier = Modifier.size(32.dp)) {
                Text("◀", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "${monthName(filterMonth)}/$filterYear",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(90.dp),
            )
            IconButton(onClick = onMonthForward, modifier = Modifier.size(32.dp)) {
                Text("▶", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Retorna nome abreviado do mes em portugues.
 */
private fun monthName(month: Int): String = when (month) {
    1 -> "Jan"
    2 -> "Fev"
    3 -> "Mar"
    4 -> "Abr"
    5 -> "Mai"
    6 -> "Jun"
    7 -> "Jul"
    8 -> "Ago"
    9 -> "Set"
    10 -> "Out"
    11 -> "Nov"
    12 -> "Dez"
    else -> "---"
}

/**
 * Card de evento — exibe nome, data e badge de categoria.
 * Cores definidas por categoria. Eventos realizados usam cor vermelha.
 */
@Composable
private fun EventCard(
    event: Event,
    isCompleted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryColor = categoryColor(event.category)
    val cardAlpha = if (isCompleted) 0.55f else 1f
    val borderColor = if (isCompleted) CompletedColor else categoryColor
    val containerColor = if (isCompleted) {
        CompletedColor.copy(alpha = 0.08f)
    } else {
        categoryColor.copy(alpha = 0.08f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = borderColor.copy(alpha = cardAlpha),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompleted) 0.dp else 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = if (isCompleted) {
                        CompletedColor.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                CategoryBadge(
                    category = event.category,
                    isCompleted = isCompleted,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = formatEpochDay(event.dateEpochDay),
                style = MaterialTheme.typography.bodySmall,
                color = if (isCompleted) {
                    CompletedColor.copy(alpha = 0.6f)
                } else {
                    categoryColor.copy(alpha = 0.8f)
                },
            )

            if (event.contact != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = event.contact,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (isCompleted) 0.5f else 0.8f,
                    ),
                )
            }
        }
    }
}

/**
 * Badge com o nome da categoria do evento.
 * Usa cores definidas por categoria; eventos realizados usam cor vermelha.
 */
@Composable
private fun CategoryBadge(
    category: EventCategory,
    isCompleted: Boolean = false,
) {
    val color = if (isCompleted) CompletedColor else categoryColor(category)
    val alpha = if (isCompleted) 0.5f else 1f
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = alpha),
    ) {
        Text(
            text = category.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = alpha),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * Retorna a cor correspondente a categoria do evento.
 */
private fun categoryColor(category: EventCategory): Color = when (category) {
    EventCategory.ALUGUEL -> AluguelColor
    EventCategory.RESERVA -> ReservaColor
    EventCategory.DAY_USE -> DayUseColor
}

/**
 * Formata epoch day (dias desde 1970-01-01) para "dd/MM/yyyy".
 * Usa algoritmo civil-from-days para evitar dependencia de kotlinx-datetime.
 */
internal fun formatEpochDay(epochDay: Long): String {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = (z - era * 146097)
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + (if (mp < 10) 3 else -9)
    val year = y + (if (m <= 2) 1 else 0)

    val day = d.toString().padStart(2, '0')
    val month = m.toString().padStart(2, '0')
    return "$day/$month/$year"
}

/**
 * Retorna (year, month) de um epoch day usando o mesmo algoritmo civil-from-days.
 */
internal fun yearMonthFromEpochDay(epochDay: Long): Pair<Int, Int> {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = (z - era * 146097)
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val m = mp + (if (mp < 10) 3 else -9)
    val year = (y + (if (m <= 2) 1 else 0)).toInt()
    return year to m.toInt()
}
