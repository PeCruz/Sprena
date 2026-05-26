package br.com.sprena.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.auth.domain.usecase.LogoutUseCase
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.core.mvi.MviViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel da tela de Configurações — responsável apenas pela seção "Conta".
 *
 * Carrega a sessão atual no init e expõe a ação de Logout.
 */
class SettingsViewModel(
    private val sessionStore: SessionStore,
    private val logout: LogoutUseCase,
) : ViewModel(),
    MviViewModel<SettingsState, SettingsIntent, SettingsEffect> {
    private val _state = MutableStateFlow(SettingsState())
    override val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>()
    override val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(user = sessionStore.load())
        }
    }

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.Logout -> doLogout()
        }
    }

    private fun doLogout() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loggingOut = true)
            logout()
            _effects.emit(SettingsEffect.NavigateToLogin)
        }
    }
}
