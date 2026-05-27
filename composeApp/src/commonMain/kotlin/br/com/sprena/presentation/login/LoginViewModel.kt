package br.com.sprena.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.PasswordResetResult
import br.com.sprena.shared.auth.domain.usecase.LoginUseCase
import br.com.sprena.shared.auth.domain.usecase.RequestPasswordResetUseCase
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
    private val requestPasswordReset: RequestPasswordResetUseCase,
) : ViewModel(),
    MviViewModel<LoginState, LoginIntent, LoginEffect> {
    private val _state = MutableStateFlow(LoginState())
    override val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<LoginEffect>()
    override val effects: SharedFlow<LoginEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.EmailChanged -> handleEmailChanged(intent.value)
            is LoginIntent.PasswordChanged -> handlePasswordChanged(intent.value)
            is LoginIntent.TogglePasswordVisibility ->
                _state.value =
                    _state.value.copy(
                        isPasswordVisible = !_state.value.isPasswordVisible,
                    )
            is LoginIntent.Submit -> handleSubmit()
            is LoginIntent.OpenPasswordResetDialog ->
                _state.value =
                    _state.value.copy(
                        passwordResetDialogOpen = true,
                        passwordResetEmail = _state.value.email,
                        passwordResetEmailError = null,
                    )
            is LoginIntent.UpdatePasswordResetEmail ->
                _state.value =
                    _state.value.copy(
                        passwordResetEmail = intent.value,
                        passwordResetEmailError = null,
                    )
            is LoginIntent.SubmitPasswordReset -> handleSubmitPasswordReset()
            is LoginIntent.DismissPasswordResetDialog ->
                _state.value =
                    _state.value.copy(
                        passwordResetDialogOpen = false,
                        passwordResetEmail = "",
                        passwordResetEmailError = null,
                        passwordResetSending = false,
                    )
        }
    }

    private fun handleEmailChanged(value: String) {
        val clamped = value.take(LoginValidator.EMAIL_MAX_LENGTH)
        val validation = LoginValidator.validateEmail(clamped)
        _state.value =
            _state.value.copy(
                email = clamped,
                emailError = if (clamped.isEmpty()) null else validation.errorMessage,
                canSubmit = canSubmit(clamped, _state.value.password),
                authError = null,
            )
    }

    private fun handlePasswordChanged(value: String) {
        val validation = LoginValidator.validatePassword(value)
        _state.value =
            _state.value.copy(
                password = value,
                passwordError = if (value.isEmpty()) null else validation.errorMessage,
                canSubmit = canSubmit(_state.value.email, value),
                authError = null,
            )
    }

    private fun handleSubmit() {
        val currentState = _state.value
        val emailResult = LoginValidator.validateEmail(currentState.email)
        val passwordResult = LoginValidator.validatePassword(currentState.password)

        if (!emailResult.isValid || !passwordResult.isValid) {
            _state.value =
                currentState.copy(
                    emailError = emailResult.errorMessage,
                    passwordError = passwordResult.errorMessage,
                    authError = null,
                )
            viewModelScope.launch {
                _effects.emit(LoginEffect.ShowError("Dados inválidos"))
            }
            return
        }

        viewModelScope.launch {
            _state.value = currentState.copy(isLoading = true, authError = null)

            when (val result = loginUseCase(currentState.email, currentState.password)) {
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

    private fun handleSubmitPasswordReset() {
        viewModelScope.launch {
            _state.value = _state.value.copy(passwordResetSending = true)
            val result = requestPasswordReset(_state.value.passwordResetEmail)
            _state.value = _state.value.copy(passwordResetSending = false)
            when (result) {
                is PasswordResetResult.Sent -> {
                    _state.value =
                        _state.value.copy(
                            passwordResetDialogOpen = false,
                            passwordResetEmail = "",
                            passwordResetEmailError = null,
                        )
                    _effects.emit(LoginEffect.ShowPasswordResetSent)
                }
                is PasswordResetResult.InvalidEmail ->
                    _state.value =
                        _state.value.copy(
                            passwordResetEmailError = result.message,
                        )
                is PasswordResetResult.NetworkError ->
                    _effects.emit(
                        LoginEffect.ShowPasswordResetError("Sem conexão. Verifique a internet"),
                    )
                is PasswordResetResult.UnknownError ->
                    _effects.emit(
                        LoginEffect.ShowPasswordResetError("Não foi possível enviar agora. Tente novamente"),
                    )
            }
        }
    }

    private fun canSubmit(
        email: String,
        password: String,
    ): Boolean =
        LoginValidator.validateEmail(email).isValid &&
            LoginValidator.validatePassword(password).isValid
}
