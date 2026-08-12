package br.com.sprena.presentation.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.presentation.privacy.PolicyTextLoader
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.core.mvi.MviViewModel
import br.com.sprena.shared.privacy.domain.usecase.AcceptConsentUseCase
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
    private val sessionStore: SessionStore,
) : ViewModel(),
    MviViewModel<ConsentState, ConsentIntent, ConsentEffect> {
    private val _state = MutableStateFlow(ConsentState())
    override val state: StateFlow<ConsentState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ConsentEffect>()
    override val effects: SharedFlow<ConsentEffect> = _effects.asSharedFlow()

    init {
        loadPolicy()
    }

    override fun handleIntent(intent: ConsentIntent) {
        when (intent) {
            is ConsentIntent.ToggleRead ->
                _state.value = _state.value.copy(hasRead = !_state.value.hasRead)

            is ConsentIntent.Retry -> loadPolicy()

            is ConsentIntent.Accept -> accept()
        }
    }

    private fun loadPolicy() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { policyLoader.load() }
                .onSuccess { text ->
                    _state.value = _state.value.copy(policyText = text, isLoading = false, error = null)
                }.onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = LOAD_ERROR)
                }
        }
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
