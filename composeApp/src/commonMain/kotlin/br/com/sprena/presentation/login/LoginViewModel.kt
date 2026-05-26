package br.com.sprena.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.usecase.LoginUseCase
import br.com.sprena.shared.auth.domain.validation.LoginValidator
import br.com.sprena.shared.core.mvi.MviViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val loginUseCase: LoginUseCase,
) : ViewModel(),
    MviViewModel<LoginState, LoginIntent, LoginEffect> {
    private val _state = MutableStateFlow(LoginState())
    override val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<LoginEffect>()
    override val effects: SharedFlow<LoginEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.UsernameChanged -> {
                val clamped = intent.value.take(LoginValidator.EMAIL_MAX_LENGTH)
                val validation = LoginValidator.validateEmail(clamped)
                _state.value =
                    _state.value.copy(
                        username = clamped,
                        usernameError = if (clamped.isEmpty()) null else validation.errorMessage,
                        canSubmit = canSubmit(clamped, _state.value.password),
                        authError = null,
                    )
            }

            is LoginIntent.PasswordChanged -> {
                val newPassword = intent.value
                val validation = LoginValidator.validatePassword(newPassword)
                _state.value =
                    _state.value.copy(
                        password = newPassword,
                        passwordError = if (newPassword.isEmpty()) null else validation.errorMessage,
                        canSubmit = canSubmit(_state.value.username, newPassword),
                        authError = null,
                    )
            }

            is LoginIntent.TogglePasswordVisibility -> {
                _state.value =
                    _state.value.copy(
                        isPasswordVisible = !_state.value.isPasswordVisible,
                    )
            }

            is LoginIntent.Submit -> handleSubmit()
        }
    }

    private fun handleSubmit() {
        val currentState = _state.value
        val uResult = LoginValidator.validateEmail(currentState.username)
        val pResult = LoginValidator.validatePassword(currentState.password)

        if (!uResult.isValid || !pResult.isValid) {
            _state.value =
                currentState.copy(
                    usernameError = uResult.errorMessage,
                    passwordError = pResult.errorMessage,
                    authError = null,
                )
            viewModelScope.launch {
                _effects.emit(LoginEffect.ShowError("Dados inválidos"))
            }
            return
        }

        viewModelScope.launch {
            _state.value = currentState.copy(isLoading = true, authError = null)

            when (val result = loginUseCase(currentState.username, currentState.password)) {
                is AuthResult.Success -> {
                    _state.value = _state.value.copy(isLoading = false)
                    _effects.emit(LoginEffect.NavigateHome(result.user))
                }
                is AuthResult.Error -> {
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            authError = result.message,
                        )
                    _effects.emit(LoginEffect.ShowError(result.message))
                }
            }
        }
    }

    private fun canSubmit(
        username: String,
        password: String,
    ): Boolean =
        LoginValidator.validateEmail(username).isValid &&
            LoginValidator.validatePassword(password).isValid
}
