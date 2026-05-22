package br.com.sprena.presentation.financial

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface FinancialIntent : UiIntent {
    data object Load : FinancialIntent

    data object AddTransactionClicked : FinancialIntent

    data object DismissAddDialog : FinancialIntent

    data class TransactionAdded(
        val transaction: FinancialTransactionSummary,
    ) : FinancialIntent

    data class EditTransactionClicked(
        val transactionId: String,
    ) : FinancialIntent

    data object DismissEditDialog : FinancialIntent

    data class TransactionUpdated(
        val transaction: FinancialTransactionSummary,
    ) : FinancialIntent

    data class TransactionDeleted(
        val transactionId: String,
    ) : FinancialIntent

    data class PeriodFilterChanged(
        val filter: PeriodFilter,
    ) : FinancialIntent

    data object PreviousPeriod : FinancialIntent

    data object NextPeriod : FinancialIntent

    data class JumpToDate(
        val month: Int,
        val year: Int,
    ) : FinancialIntent

    data object LoadMoreTransactions : FinancialIntent
}
