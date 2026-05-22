package br.com.sprena.presentation.core.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.core.mvi.MviViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThemeViewModel :
    ViewModel(),
    MviViewModel<ThemeState, ThemeIntent, ThemeEffect> {
    private val _state = MutableStateFlow(ThemeState())
    override val state: StateFlow<ThemeState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ThemeEffect>()
    override val effects: SharedFlow<ThemeEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: ThemeIntent) {
        when (intent) {
            is ThemeIntent.Set -> {
                _state.value = _state.value.copy(mode = intent.mode)
                viewModelScope.launch {
                    _effects.emit(ThemeEffect.ThemeChanged(intent.mode))
                }
            }

            is ThemeIntent.Toggle -> {
                val newMode =
                    when (_state.value.mode) {
                        ThemeMode.LIGHT -> ThemeMode.DARK
                        ThemeMode.DARK -> ThemeMode.LIGHT
                        ThemeMode.SYSTEM -> ThemeMode.DARK
                    }
                _state.value = _state.value.copy(mode = newMode)
                viewModelScope.launch {
                    _effects.emit(ThemeEffect.ThemeChanged(newMode))
                }
            }
        }
    }
}
