package br.com.sprena.presentation.establishment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.core.mvi.MviViewModel
import br.com.sprena.shared.establishment.domain.usecase.ObserveEstablishmentsUseCase
import br.com.sprena.shared.establishment.domain.usecase.SetEstablishmentActiveUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Administração dos estabelecimentos — só o ADM chega aqui, porque só ele tem a aba Config e
 * só ele tem `list` na rule de `establishments`.
 *
 * Não há "excluir": desativar é o que o produto oferece, porque apagar o documento deixaria
 * membros, comandas e lançamentos órfãos sob um path ilegível.
 */
class EstablishmentListViewModel(
    observeEstablishments: ObserveEstablishmentsUseCase,
    private val setActive: SetEstablishmentActiveUseCase,
) : ViewModel(),
    MviViewModel<EstablishmentListState, EstablishmentListIntent, EstablishmentListEffect> {
    private val _state = MutableStateFlow(EstablishmentListState())
    override val state: StateFlow<EstablishmentListState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<EstablishmentListEffect>()
    override val effects: SharedFlow<EstablishmentListEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            observeEstablishments().collect { result ->
                _state.value =
                    result.fold(
                        onSuccess = { list ->
                            _state.value.copy(establishments = list, isLoading = false, error = null)
                        },
                        // A causa técnica fica no log do repositório; aqui vai uma frase que o
                        // ADM consegue agir sobre. O fluxo continua vivo, então uma leitura
                        // bem-sucedida depois limpa o erro sozinha.
                        onFailure = { _state.value.copy(isLoading = false, error = LOAD_FAILED) },
                    )
            }
        }
    }

    override fun handleIntent(intent: EstablishmentListIntent) {
        when (intent) {
            is EstablishmentListIntent.CreateClicked -> emit(EstablishmentListEffect.NavigateToEdit(null))
            is EstablishmentListIntent.EstablishmentClicked ->
                emit(EstablishmentListEffect.NavigateToEdit(intent.id))
            is EstablishmentListIntent.ToggleActive -> toggle(intent.id, intent.active)
        }
    }

    private fun toggle(
        id: String,
        active: Boolean,
    ) {
        viewModelScope.launch {
            // Sem atualização otimista: quem manda na lista é o snapshot do Firestore, que
            // chega sozinho quando a escrita passa. Antecipar aqui faria a linha piscar de
            // volta ao estado anterior se a rule recusasse.
            setActive(id, active).onFailure {
                _effects.emit(EstablishmentListEffect.ShowMessage(TOGGLE_FAILED))
            }
        }
    }

    private fun emit(effect: EstablishmentListEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private companion object {
        const val LOAD_FAILED = "Não foi possível carregar os estabelecimentos."
        const val TOGGLE_FAILED = "Não foi possível alterar o estabelecimento. Tente de novo."
    }
}
