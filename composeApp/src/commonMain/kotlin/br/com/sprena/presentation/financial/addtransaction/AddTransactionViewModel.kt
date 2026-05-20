package br.com.sprena.presentation.financial.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.presentation.financial.FinancialTransactionSummary
import br.com.sprena.shared.core.mvi.MviViewModel
import br.com.sprena.shared.financial.domain.validation.TransactionValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddTransactionViewModel(
    private val today: () -> Long = { 0L },
) : ViewModel(),
    MviViewModel<AddTransactionState, AddTransactionIntent, AddTransactionEffect> {
    private val _state = MutableStateFlow(AddTransactionState(inputEpochDay = today()))
    override val state: StateFlow<AddTransactionState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AddTransactionEffect>()
    override val effects: SharedFlow<AddTransactionEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: AddTransactionIntent) {
        when (intent) {
            is AddTransactionIntent.LoadForEdit -> {
                val tx = intent.transaction
                val amountRaw = tx.cents.toString()
                val granularity = _state.value.dateGranularity
                val display = formatDateDisplay(granularity, tx.day, tx.month, tx.year)
                updateState {
                    copy(
                        editingId = tx.id,
                        name = tx.description,
                        amountRaw = amountRaw,
                        amountCents = tx.cents,
                        type = tx.type,
                        selectedDay = tx.day,
                        selectedMonth = tx.month,
                        selectedYear = tx.year,
                        dateDisplay = display,
                        personName = tx.personName,
                        category = tx.category,
                        description = tx.notes,
                    )
                }
            }

            is AddTransactionIntent.NameChanged -> {
                val v = TransactionValidator.validateName(intent.value)
                updateState {
                    copy(name = intent.value, nameError = v.errorMessage)
                }
            }

            is AddTransactionIntent.AmountChanged -> {
                val digits = intent.raw.filter { it.isDigit() }
                val cents = digits.toLongOrNull() ?: 0L
                val error =
                    when {
                        digits.isEmpty() -> "Valor é obrigatório"
                        cents <= 0L -> "Valor deve ser maior que zero"
                        else -> null
                    }
                updateState {
                    copy(
                        amountRaw = digits,
                        amountCents = cents,
                        amountError = error,
                    )
                }
            }

            is AddTransactionIntent.PersonNameChanged -> {
                val v = TransactionValidator.validatePersonName(intent.value)
                updateState {
                    copy(personName = intent.value, personNameError = v.errorMessage)
                }
            }

            is AddTransactionIntent.TypeChanged -> {
                updateState { copy(type = intent.value) }
            }

            is AddTransactionIntent.CategoryChanged -> {
                updateState { copy(category = intent.value) }
            }

            is AddTransactionIntent.DescriptionChanged -> {
                val v = TransactionValidator.validateDescription(intent.value)
                updateState {
                    copy(description = intent.value, descriptionError = v.errorMessage)
                }
            }

            is AddTransactionIntent.DateGranularityChanged -> {
                updateState {
                    copy(
                        dateGranularity = intent.value,
                        selectedDay = null,
                        selectedMonth = null,
                        selectedYear = null,
                        dateDisplay = "",
                    )
                }
            }

            is AddTransactionIntent.DateSelected -> {
                updateState {
                    copy(
                        selectedDay = intent.day,
                        selectedMonth = intent.month,
                        selectedYear = intent.year,
                        dateDisplay = formatDateDisplay(dateGranularity, intent.day, intent.month, intent.year),
                    )
                }
            }

            is AddTransactionIntent.DatePickerConfirmed -> {
                val components = dateComponentsFromMillis(intent.millis)
                val g = _state.value.dateGranularity
                val dateIntent =
                    when (g) {
                        DateGranularity.DAY ->
                            AddTransactionIntent.DateSelected(
                                day = components.day,
                                month = components.month,
                                year = components.year,
                            )
                        DateGranularity.MONTH ->
                            AddTransactionIntent.DateSelected(
                                day = null,
                                month = components.month,
                                year = components.year,
                            )
                        DateGranularity.YEAR ->
                            AddTransactionIntent.DateSelected(
                                day = null,
                                month = null,
                                year = components.year,
                            )
                    }
                handleIntent(dateIntent)
            }

            is AddTransactionIntent.Submit -> {
                val s = _state.value
                if (s.canSubmit) {
                    val tx =
                        FinancialTransactionSummary(
                            id = s.editingId ?: generateId(),
                            description = s.name,
                            cents = s.amountCents,
                            type = s.type,
                            day = s.selectedDay,
                            month = s.selectedMonth ?: 1,
                            year = s.selectedYear ?: 2026,
                            personName = s.personName,
                            category = s.category,
                            notes = s.description,
                        )
                    viewModelScope.launch {
                        if (s.editingId != null) {
                            _effects.emit(AddTransactionEffect.TransactionUpdated(tx))
                        } else {
                            _effects.emit(AddTransactionEffect.TransactionCreated(tx))
                        }
                    }
                } else {
                    viewModelScope.launch {
                        _effects.emit(AddTransactionEffect.ShowError("Preencha todos os campos obrigatórios"))
                    }
                }
            }

            is AddTransactionIntent.Dismiss -> {
                viewModelScope.launch { _effects.emit(AddTransactionEffect.Dismissed) }
            }
        }
    }

    private fun updateState(transform: AddTransactionState.() -> AddTransactionState) {
        val newState = _state.value.transform()
        _state.value = newState.copy(canSubmit = computeCanSubmit(newState))
    }

    private fun generateId(): String = "tx-${kotlin.random.Random.nextLong(0, Long.MAX_VALUE)}"

    private fun formatDateDisplay(
        granularity: DateGranularity,
        day: Int?,
        month: Int?,
        year: Int?,
    ): String =
        when (granularity) {
            DateGranularity.DAY -> {
                val d = (day ?: 0).toString().padStart(2, '0')
                val m = (month ?: 0).toString().padStart(2, '0')
                "$d/$m/$year"
            }
            DateGranularity.MONTH -> {
                val m = (month ?: 0).toString().padStart(2, '0')
                "$m/$year"
            }
            DateGranularity.YEAR -> "$year"
        }

    private fun computeCanSubmit(s: AddTransactionState): Boolean {
        val cents = s.amountRaw.toLongOrNull() ?: 0L
        val hasDate = s.dateDisplay.isNotEmpty()
        return TransactionValidator.validateName(s.name).isValid &&
            cents > 0L &&
            TransactionValidator.validatePersonName(s.personName).isValid &&
            TransactionValidator.validateDescription(s.description).isValid &&
            hasDate
    }
}
