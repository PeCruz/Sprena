package br.com.sprena.presentation.home

import androidx.lifecycle.ViewModel
import br.com.sprena.shared.auth.domain.model.UserModel
import br.com.sprena.shared.auth.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado da tela Home — imutável, seguindo UDF.
 * Adapta conteúdo conforme o role do usuário logado.
 */
data class HomeUiState(
    val title: String = "Sprena",
    val subtitle: String = "Bem-vindo ao Sprena",
    val userName: String = "",
    val userRole: UserRole = UserRole.CLIENT,
    val isLoading: Boolean = false,
)

/**
 * Eventos que a UI pode disparar.
 */
sealed interface HomeUiEvent {
    data class UserLoaded(
        val user: UserModel,
    ) : HomeUiEvent

    data object OnRefresh : HomeUiEvent
}

/**
 * ViewModel da Home — gerencia estado via StateFlow.
 *
 * Segue o padrão UDF (Unidirectional Data Flow):
 * UI observa [uiState] e emite eventos via [onEvent].
 */
class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.UserLoaded -> handleUserLoaded(event.user)
            is HomeUiEvent.OnRefresh -> handleRefresh()
        }
    }

    private fun handleUserLoaded(user: UserModel) {
        _uiState.value =
            _uiState.value.copy(
                userName = user.name,
                userRole = user.role,
                title = buildTitle(user.role),
                subtitle = buildSubtitle(user.name, user.role),
            )
    }

    private fun buildTitle(role: UserRole): String =
        when (role) {
            UserRole.ADM -> "Painel Admin"
            UserRole.MOD -> "Painel Moderador"
            UserRole.CLIENT -> "Sprena"
        }

    private fun buildSubtitle(
        name: String,
        role: UserRole,
    ): String =
        when (role) {
            UserRole.ADM -> "Olá, $name! Acesso total ao sistema."
            UserRole.MOD -> "Olá, $name! Gestão de eventos e visualizações."
            UserRole.CLIENT -> "Olá, $name! Acompanhe suas atividades."
        }

    private fun handleRefresh() {
        _uiState.value = _uiState.value.copy(isLoading = false)
    }
}
