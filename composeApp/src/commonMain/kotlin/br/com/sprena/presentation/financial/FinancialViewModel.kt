package br.com.sprena.presentation.financial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.core.mvi.MviViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Minimal year+month pair to avoid adding kotlinx-datetime dependency.
 */
data class YearMonth(
    val year: Int,
    val month: Int,
)

expect fun currentYearMonth(): YearMonth

class FinancialViewModel(
    private val clock: () -> YearMonth = { currentYearMonth() },
) : ViewModel(),
    MviViewModel<FinancialState, FinancialIntent, FinancialEffect> {
    private val _state = MutableStateFlow(FinancialState())
    override val state: StateFlow<FinancialState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<FinancialEffect>()
    override val effects: SharedFlow<FinancialEffect> = _effects.asSharedFlow()

    init {
        _state.value =
            _state.value.copy(
                periodLabel = computePeriodLabel(PeriodFilter.MONTHLY, 0),
            )
    }

    override fun handleIntent(intent: FinancialIntent) {
        when (intent) {
            is FinancialIntent.Load -> {
                _state.value = _state.value.copy(isLoading = true)
                // TODO: integrar com Firebase Firestore para carregar transações reais
                _state.value = _state.value.copy(isLoading = false)
            }

            is FinancialIntent.AddTransactionClicked -> {
                _state.value = _state.value.copy(isAddDialogVisible = true)
                viewModelScope.launch {
                    _effects.emit(FinancialEffect.OpenAddTransactionDialog)
                }
            }

            is FinancialIntent.DismissAddDialog -> {
                _state.value = _state.value.copy(isAddDialogVisible = false)
            }

            is FinancialIntent.TransactionAdded -> {
                val tx = intent.transaction
                val s = _state.value
                val newTransactions = listOf(tx) + s.transactions
                recalculateAndUpdate(
                    s.copy(
                        transactions = newTransactions,
                        isAddDialogVisible = false,
                    ),
                )
            }

            is FinancialIntent.EditTransactionClicked -> {
                _state.value =
                    _state.value.copy(
                        isEditDialogVisible = true,
                        editingTransactionId = intent.transactionId,
                    )
            }

            is FinancialIntent.DismissEditDialog -> {
                _state.value =
                    _state.value.copy(
                        isEditDialogVisible = false,
                        editingTransactionId = null,
                    )
            }

            is FinancialIntent.TransactionUpdated -> {
                val updated = intent.transaction
                val s = _state.value
                val newTransactions =
                    s.transactions.map { tx ->
                        if (tx.id == updated.id) updated else tx
                    }
                recalculateAndUpdate(
                    s.copy(
                        transactions = newTransactions,
                        isEditDialogVisible = false,
                        editingTransactionId = null,
                    ),
                )
            }

            is FinancialIntent.TransactionDeleted -> {
                val s = _state.value
                val newTransactions = s.transactions.filter { it.id != intent.transactionId }
                recalculateAndUpdate(
                    s.copy(
                        transactions = newTransactions,
                        isEditDialogVisible = false,
                        editingTransactionId = null,
                    ),
                )
            }

            is FinancialIntent.PeriodFilterChanged -> {
                val newFilter = intent.filter
                val s =
                    _state.value.copy(
                        periodFilter = newFilter,
                        periodOffset = 0,
                        periodLabel = computePeriodLabel(newFilter, 0),
                    )
                recalculateAndUpdate(s)
            }

            is FinancialIntent.PreviousPeriod -> {
                val s = _state.value
                val newOffset = s.periodOffset - 1
                val updated =
                    s.copy(
                        periodOffset = newOffset,
                        periodLabel = computePeriodLabel(s.periodFilter, newOffset),
                    )
                recalculateAndUpdate(updated)
            }

            is FinancialIntent.NextPeriod -> {
                val s = _state.value
                val newOffset = s.periodOffset + 1
                val updated =
                    s.copy(
                        periodOffset = newOffset,
                        periodLabel = computePeriodLabel(s.periodFilter, newOffset),
                    )
                recalculateAndUpdate(updated)
            }

            is FinancialIntent.JumpToDate -> {
                val s = _state.value
                val offset = computeOffsetForDate(s.periodFilter, intent.month, intent.year)
                val updated =
                    s.copy(
                        periodOffset = offset,
                        periodLabel = computePeriodLabel(s.periodFilter, offset),
                    )
                recalculateAndUpdate(updated)
            }

            is FinancialIntent.LoadMoreTransactions -> {
                val s = _state.value
                _state.value =
                    s.copy(
                        visibleTransactionCount = s.visibleTransactionCount + FinancialState.PAGE_SIZE,
                    )
            }
        }
    }

    /**
     * Recalculates global totals + filtered data and emits new state.
     */
    private fun recalculateAndUpdate(s: FinancialState) {
        val allIncome = s.transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.cents }
        val allExpense = s.transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.cents }

        val filtered =
            s.transactions.filter { tx ->
                transactionMatchesPeriod(tx, s.periodFilter, s.periodOffset)
            }
        val filteredIncome = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.cents }
        val filteredExpense = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.cents }

        _state.value =
            s.copy(
                incomeCents = allIncome,
                expenseCents = allExpense,
                balanceCents = allIncome - allExpense,
                filteredTransactions = filtered,
                filteredIncomeCents = filteredIncome,
                filteredExpenseCents = filteredExpense,
                filteredBalanceCents = filteredIncome - filteredExpense,
                visibleTransactionCount = FinancialState.PAGE_SIZE,
            )
    }

    /**
     * Returns true if the transaction's month/year falls within the specified period.
     */
    private fun transactionMatchesPeriod(
        tx: FinancialTransactionSummary,
        filter: PeriodFilter,
        offset: Int,
    ): Boolean {
        val ym = clock()
        return when (filter) {
            PeriodFilter.MONTHLY -> {
                val totalMonths = ym.year * 12 + (ym.month - 1) + offset
                val periodYear = totalMonths / 12
                val periodMonth = totalMonths % 12 + 1
                tx.year == periodYear && tx.month == periodMonth
            }
            PeriodFilter.QUARTERLY -> {
                val currentQ = (ym.month - 1) / 3
                val totalQ = ym.year * 4 + currentQ + offset
                val periodYear = totalQ / 4
                val periodQ = totalQ % 4 // 0-based quarter
                val txQ = (tx.month - 1) / 3
                tx.year == periodYear && txQ == periodQ
            }
            PeriodFilter.SEMI_ANNUAL -> {
                val currentS = (ym.month - 1) / 6
                val totalS = ym.year * 2 + currentS + offset
                val periodYear = totalS / 2
                val periodS = totalS % 2 // 0-based semester
                val txS = (tx.month - 1) / 6
                tx.year == periodYear && txS == periodS
            }
            PeriodFilter.ANNUAL -> {
                val periodYear = ym.year + offset
                tx.year == periodYear
            }
        }
    }

    /**
     * Computes the period offset needed to navigate to the given month/year.
     */
    private fun computeOffsetForDate(
        filter: PeriodFilter,
        targetMonth: Int,
        targetYear: Int,
    ): Int {
        val ym = clock()
        return when (filter) {
            PeriodFilter.MONTHLY -> {
                val currentTotal = ym.year * 12 + (ym.month - 1)
                val targetTotal = targetYear * 12 + (targetMonth - 1)
                targetTotal - currentTotal
            }
            PeriodFilter.QUARTERLY -> {
                val currentQ = ym.year * 4 + (ym.month - 1) / 3
                val targetQ = targetYear * 4 + (targetMonth - 1) / 3
                targetQ - currentQ
            }
            PeriodFilter.SEMI_ANNUAL -> {
                val currentS = ym.year * 2 + (ym.month - 1) / 6
                val targetS = targetYear * 2 + (targetMonth - 1) / 6
                targetS - currentS
            }
            PeriodFilter.ANNUAL -> {
                targetYear - ym.year
            }
        }
    }

    internal fun computePeriodLabel(
        filter: PeriodFilter,
        offset: Int,
    ): String {
        val ym = clock()
        val monthNames =
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
        return when (filter) {
            PeriodFilter.MONTHLY -> {
                val totalMonths = ym.year * 12 + (ym.month - 1) + offset
                val year = totalMonths / 12
                val month = totalMonths % 12
                "${monthNames[month]} $year"
            }
            PeriodFilter.QUARTERLY -> {
                val currentQ = (ym.month - 1) / 3
                val totalQ = ym.year * 4 + currentQ + offset
                val year = totalQ / 4
                val q = totalQ % 4 + 1
                "${q}º Tri $year"
            }
            PeriodFilter.SEMI_ANNUAL -> {
                val currentS = (ym.month - 1) / 6
                val totalS = ym.year * 2 + currentS + offset
                val year = totalS / 2
                val s = totalS % 2 + 1
                "${s}º Sem $year"
            }
            PeriodFilter.ANNUAL -> {
                val year = ym.year + offset
                "$year"
            }
        }
    }
}
