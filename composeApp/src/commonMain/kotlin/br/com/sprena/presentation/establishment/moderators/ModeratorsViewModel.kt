package br.com.sprena.presentation.establishment.moderators

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.core.mvi.MviViewModel
import br.com.sprena.shared.establishment.domain.usecase.ObserveEstablishmentMembersUseCase
import br.com.sprena.shared.establishment.domain.usecase.ObserveEstablishmentsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Quem está vinculado a cada estabelecimento (ADM).
 *
 * Somente leitura, e não por simplificação: `members` é `write: if false` nas rules, e
 * `MembershipRepository` não expõe escrita por contrato. Vincular alguém passa por
 * `linkMemberByCpf`, uma callable que roda com Admin SDK — assim a única aresta que concede
 * acesso no sistema tem um ponto de decisão só, com auditoria garantida.
 *
 * Duas leituras separadas em vez de uma: a lista de estabelecimentos vem de uma query, e os
 * membros de cada um vêm da subcoleção dele. Carregar os membros de todos de uma vez custaria
 * uma query por estabelecimento a cada abertura da tela, para mostrar só um deles.
 */
class ModeratorsViewModel(
    observeEstablishments: ObserveEstablishmentsUseCase,
    private val observeMembers: ObserveEstablishmentMembersUseCase,
) : ViewModel(),
    MviViewModel<ModeratorsState, ModeratorsIntent, ModeratorsEffect> {
    private val _state = MutableStateFlow(ModeratorsState())
    override val state: StateFlow<ModeratorsState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ModeratorsEffect>()
    override val effects: SharedFlow<ModeratorsEffect> = _effects.asSharedFlow()

    /** Cancelado a cada troca: sem isso, o snapshot do estabelecimento anterior continuaria
     *  chegando e sobrescreveria a lista do que acabou de ser escolhido. */
    private var membersJob: Job? = null

    init {
        viewModelScope.launch {
            observeEstablishments().collect { result ->
                result.fold(
                    onSuccess = { list ->
                        _state.value =
                            _state.value.copy(establishments = list, isLoading = false, error = null)
                        // Abrir a tela sem nada selecionado obrigaria um toque extra para ver
                        // qualquer coisa. Só escolhe sozinho se ainda não houver escolha válida.
                        val current = _state.value.selectedEstablishmentId
                        if (current == null || list.none { it.id == current }) {
                            list.firstOrNull()?.let { select(it.id) }
                        }
                    },
                    onFailure = {
                        _state.value = _state.value.copy(isLoading = false, error = LOAD_FAILED)
                    },
                )
            }
        }
    }

    override fun handleIntent(intent: ModeratorsIntent) {
        when (intent) {
            is ModeratorsIntent.EstablishmentSelected -> select(intent.id)
        }
    }

    private fun select(establishmentId: String) {
        _state.value =
            _state.value.copy(
                selectedEstablishmentId = establishmentId,
                members = emptyList(),
                isLoadingMembers = true,
                membersError = null,
            )

        membersJob?.cancel()
        membersJob =
            viewModelScope.launch {
                observeMembers(establishmentId).collect { result ->
                    _state.value =
                        result.fold(
                            onSuccess = { members ->
                                _state.value.copy(
                                    members = members.map(MemberRow::from),
                                    isLoadingMembers = false,
                                    membersError = null,
                                )
                            },
                            // Não zera `establishments`: o seletor precisa continuar utilizável
                            // para o ADM tentar outro estabelecimento.
                            onFailure = {
                                _state.value.copy(isLoadingMembers = false, membersError = MEMBERS_FAILED)
                            },
                        )
                }
            }
    }

    private companion object {
        const val LOAD_FAILED = "Não foi possível carregar os estabelecimentos."
        const val MEMBERS_FAILED = "Não foi possível carregar os membros deste estabelecimento."
    }
}
