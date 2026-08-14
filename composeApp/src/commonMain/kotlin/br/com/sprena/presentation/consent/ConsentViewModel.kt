package br.com.sprena.presentation.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.presentation.privacy.PolicyTextLoader
import br.com.sprena.shared.auth.domain.usecase.LogoutUseCase
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.core.mvi.MviViewModel
import br.com.sprena.shared.privacy.domain.model.ConsentStatus
import br.com.sprena.shared.privacy.domain.usecase.AcceptConsentUseCase
import br.com.sprena.shared.privacy.domain.usecase.CheckConsentUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Gate de consentimento LGPD (F1.5).
 *
 * Fail-closed: enquanto o aceite não for gravado com sucesso, nenhum efeito de
 * navegação para a Home é emitido.
 */
class ConsentViewModel(
    private val policyLoader: PolicyTextLoader,
    private val acceptConsent: AcceptConsentUseCase,
    private val checkConsent: CheckConsentUseCase,
    private val sessionStore: SessionStore,
    private val logout: LogoutUseCase,
) : ViewModel(),
    MviViewModel<ConsentState, ConsentIntent, ConsentEffect> {
    private val _state = MutableStateFlow(ConsentState())
    override val state: StateFlow<ConsentState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ConsentEffect>()
    override val effects: SharedFlow<ConsentEffect> = _effects.asSharedFlow()

    init {
        refresh()
    }

    override fun handleIntent(intent: ConsentIntent) {
        when (intent) {
            is ConsentIntent.ToggleRead ->
                _state.value = _state.value.copy(hasRead = !_state.value.hasRead)

            is ConsentIntent.Retry -> refresh()

            is ConsentIntent.Accept -> accept()

            is ConsentIntent.Logout -> logoutAndLeave()
        }
    }

    /**
     * Saída de emergência do gate. A navegação para o login acontece mesmo se o
     * `signOut` falhar: prender no consentimento quem pediu para sair é exatamente
     * o beco sem saída que este botão existe para eliminar. A sessão local é limpa
     * pelo próprio [LogoutUseCase].
     */
    private fun logoutAndLeave() {
        viewModelScope.launch {
            runCatching { logout() }
            _effects.emit(ConsentEffect.NavigateLogin)
        }
    }

    /**
     * Carrega o texto da política **e** reconsulta o consentimento.
     *
     * A reconsulta existe porque `Unavailable` (falha de leitura) manda o usuário
     * para cá mesmo quando ele já aceitou: sem ela, o único caminho oferecido seria
     * "Aceitar", e um aceite não é a correção de uma falha de rede. Voltando
     * `Granted`, o gate se resolve sozinho e navega para a Home.
     */
    private fun refresh() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val policyText = runCatching { policyLoader.load() }.getOrNull()
            val session = sessionStore.load()
            val status = session?.let { checkConsent(it.uid) }

            if (session != null && status is ConsentStatus.Granted) {
                _state.value =
                    _state.value.copy(
                        policyText = policyText ?: _state.value.policyText,
                        isLoading = false,
                        error = null,
                    )
                _effects.emit(ConsentEffect.NavigateHome(session))
                return@launch
            }

            _state.value =
                _state.value.copy(
                    policyText = policyText ?: _state.value.policyText,
                    isLoading = false,
                    error = resolveError(policyText, status),
                )
        }
    }

    /**
     * Erro visível na tela. O texto ausente vem primeiro: sem política não há o que
     * aceitar, e a mensagem de leitura do consentimento seria ruído nesse estado.
     */
    private fun resolveError(
        policyText: String?,
        status: ConsentStatus?,
    ): String? =
        when {
            policyText == null -> LOAD_ERROR
            status is ConsentStatus.Unavailable -> status.message
            else -> null
        }

    private fun accept() {
        if (!_state.value.canAccept) return
        _state.value = _state.value.copy(isAccepting = true, error = null)
        viewModelScope.launch {
            val session = sessionStore.load()
            if (session == null) {
                _state.value = _state.value.copy(isAccepting = false)
                _effects.emit(ConsentEffect.NavigateLogin)
                return@launch
            }

            acceptConsent(session.uid)
                .onSuccess {
                    _state.value = _state.value.copy(isAccepting = false)
                    _effects.emit(ConsentEffect.NavigateHome(session))
                }.onFailure {
                    _state.value = _state.value.copy(isAccepting = false, error = SAVE_ERROR)
                }
        }
    }

    private companion object {
        const val LOAD_ERROR = "Não foi possível carregar a política. Tente novamente."
        const val SAVE_ERROR = "Não foi possível registrar seu aceite. Verifique a conexão."
    }
}
