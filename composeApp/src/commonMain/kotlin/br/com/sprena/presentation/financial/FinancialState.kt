package br.com.sprena.presentation.financial

import br.com.sprena.shared.core.mvi.UiState

data class FinancialTransactionSummary(
    val id: String,
    val description: String,
    val cents: Long,
    val type: TransactionType,
    val day: Int? = null,
    val month: Int = 1,
    val year: Int = 2026,
    val personName: String = "",
    val category: String = "",
    val notes: String = "",
)

enum class TransactionType { INCOME, EXPENSE }

enum class PeriodFilter(
    val label: String,
) {
    MONTHLY("Mensal"),
    QUARTERLY("Trimestral"),
    SEMI_ANNUAL("Semestral"),
    ANNUAL("Anual"),
}

data class FinancialState(
    val transactions: List<FinancialTransactionSummary> = emptyList(),
    val balanceCents: Long = 0L,
    val incomeCents: Long = 0L,
    val expenseCents: Long = 0L,
    val isLoading: Boolean = false,
    val isAddDialogVisible: Boolean = false,
    val isEditDialogVisible: Boolean = false,
    val editingTransactionId: String? = null,
    val periodFilter: PeriodFilter = PeriodFilter.MONTHLY,
    val periodOffset: Int = 0,
    val periodLabel: String = "",
    val filteredTransactions: List<FinancialTransactionSummary> = emptyList(),
    val filteredIncomeCents: Long = 0L,
    val filteredExpenseCents: Long = 0L,
    val filteredBalanceCents: Long = 0L,
    val visibleTransactionCount: Int = PAGE_SIZE,
) : UiState {
    val paginatedTransactions: List<FinancialTransactionSummary>
        get() = filteredTransactions.take(visibleTransactionCount)
    val hasMoreTransactions: Boolean
        get() = filteredTransactions.size > visibleTransactionCount

    companion object {
        const val PAGE_SIZE = 5
    }
}
