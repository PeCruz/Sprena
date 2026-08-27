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
            is BottomNavIntent.TabsResolved -> resolve(intent.tabs)
            is BottomNavIntent.TabSelected -> select(intent.tab)
        }
    }

    private fun resolve(tabs: List<BottomTab>) {
        _state.value =
            _state.value.copy(
                tabs = tabs,
                // Mantém a aba atual se ela sobreviveu à mudança de papel; senão vai para a
                // primeira. Sem isso, quem estivesse no Financeiro e fosse rebaixado de MOD
                // para CLIENT ficaria numa aba que não existe mais na barra.
                current = _state.value.current?.takeIf { it in tabs } ?: tabs.firstOrNull(),
                resolved = true,
            )
    }

    private fun select(tab: BottomTab) {
        // Aba fora da lista do papel é ignorada. Não é barreira de segurança — as rules é que
        // negam o dado — mas evita deixar a barra num estado sem item selecionado.
        if (tab !in _state.value.tabs) return

        _state.value = _state.value.copy(current = tab)
        viewModelScope.launch {
            _effects.emit(BottomNavEffect.NavigateTo(tab))
        }
    }
}
