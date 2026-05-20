package br.com.sprena.presentation.financial.addtransaction

import br.com.sprena.presentation.financial.TransactionType
import br.com.sprena.shared.core.mvi.UiState

/**
 * Granularidade da data da transação.
 * DAY = DD/MM/YYYY, MONTH = MM/YYYY, YEAR = YYYY.
 */
enum class DateGranularity {
    DAY,
    MONTH,
    YEAR,
}

/**
 * State do diálogo "Nova Transação Financeira".
 *
 * Campos:
 *  - [amountRaw]       — texto BRL como o usuário digita ("R$ 1.234,56")
 *  - [amountCents]     — valor parseado em centavos (fonte da verdade)
 *  - [personName]      — nome de quem adicionou (obrigatório, máx 50)
 *  - [type]            — INCOME (entrada) ou EXPENSE (saída)
 *  - [category]        — categoria livre (ex: "Mercado", "Salário")
 *  - [description]     — breve descrição (opcional, máx 3000)
 *  - [inputEpochDay]   — data de entrada (= dia atual, auto)
 *  - [*Error]          — mensagens de validação por campo
 *  - [canSubmit]       — derivado: todos os campos obrigatórios válidos
 */
data class AddTransactionState(
    val editingId: String? = null,
    val name: String = "",
    val amountRaw: String = "",
    val amountCents: Long = 0L,
    val personName: String = "",
    val type: TransactionType = TransactionType.INCOME,
    val category: String = "",
    val description: String = "",
    val inputEpochDay: Long = 0L,
    val nameError: String? = null,
    val amountError: String? = null,
    val personNameError: String? = null,
    val descriptionError: String? = null,
    val dateError: String? = null,
    val canSubmit: Boolean = false,
    val dateGranularity: DateGranularity = DateGranularity.MONTH,
    val selectedDay: Int? = null,
    val selectedMonth: Int? = null,
    val selectedYear: Int? = null,
    val dateDisplay: String = "",
) : UiState
