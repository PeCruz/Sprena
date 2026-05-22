package br.com.sprena.presentation.financial

import br.com.sprena.shared.core.mvi.UiEffect

sealed interface FinancialEffect : UiEffect {
    data object OpenAddTransactionDialog : FinancialEffect

    data class ShowError(
        val message: String,
    ) : FinancialEffect
}
