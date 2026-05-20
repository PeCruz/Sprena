package br.com.sprena.presentation.financial.addtransaction

import br.com.sprena.presentation.financial.FinancialTransactionSummary
import br.com.sprena.shared.core.mvi.UiEffect

sealed interface AddTransactionEffect : UiEffect {
    data class TransactionCreated(
        val transaction: FinancialTransactionSummary,
    ) : AddTransactionEffect

    data class TransactionUpdated(
        val transaction: FinancialTransactionSummary,
    ) : AddTransactionEffect

    data object Dismissed : AddTransactionEffect

    data class ShowError(
        val message: String,
    ) : AddTransactionEffect
}
