package br.com.sprena.presentation.core.navigation

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

class BottomNavViewModel :
    ViewModel(),
    MviViewModel<BottomNavState, BottomNavIntent, BottomNavEffect> {
    private val _state = MutableStateFlow(BottomNavState())
    override val state: StateFlow<BottomNavState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<BottomNavEffect>()
    override val effects: SharedFlow<BottomNavEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: BottomNavIntent) {
        when (intent) {
            is BottomNavIntent.TabSelected -> {
                _state.value = _state.value.copy(current = intent.tab)
                viewModelScope.launch {
                    _effects.emit(BottomNavEffect.NavigateTo(intent.tab))
                }
            }
        }
    }
}
