package br.com.sprena.presentation.bar.addclient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.presentation.bar.BarClient
import br.com.sprena.shared.bar.domain.validation.BarValidator
import br.com.sprena.shared.core.mvi.MviViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ViewModel do diálogo de adicionar novo cliente ao Bar.
 * Valida campos obrigatórios (Name, Phone, CPF) e opcionais (Nickname, Email).
 */
@OptIn(ExperimentalUuidApi::class)
class AddClientViewModel :
    ViewModel(),
    MviViewModel<AddClientState, AddClientIntent, AddClientEffect> {
    private val _state = MutableStateFlow(AddClientState())
    override val state: StateFlow<AddClientState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AddClientEffect>()
    override val effects: SharedFlow<AddClientEffect> = _effects.asSharedFlow()

    override fun handleIntent(intent: AddClientIntent) {
        when (intent) {
            is AddClientIntent.NameChanged -> {
                val result = BarValidator.validateClientName(intent.name)
                _state.value =
                    _state.value.copy(
                        name = intent.name,
                        nameError = result.errorMessage,
                    )
            }

            is AddClientIntent.NicknameChanged -> {
                _state.value = _state.value.copy(nickname = intent.nickname)
            }

            is AddClientIntent.PhoneChanged -> {
                val result = BarValidator.validatePhone(intent.phone)
                _state.value =
                    _state.value.copy(
                        phone = intent.phone,
                        phoneError = result.errorMessage,
                    )
            }

            is AddClientIntent.CpfChanged -> {
                val result = BarValidator.validateCpf(intent.cpf)
                _state.value =
                    _state.value.copy(
                        cpf = intent.cpf,
                        cpfError = result.errorMessage,
                    )
            }

            is AddClientIntent.EmailChanged -> {
                val result =
                    BarValidator.validateEmail(
                        intent.email.ifEmpty { null },
                    )
                _state.value =
                    _state.value.copy(
                        email = intent.email,
                        emailError = result.errorMessage,
                    )
            }

            is AddClientIntent.Save -> {
                val s = _state.value
                val nameResult = BarValidator.validateClientName(s.name)
                val phoneResult = BarValidator.validatePhone(s.phone)
                val cpfResult = BarValidator.validateCpf(s.cpf)
                val emailResult = BarValidator.validateEmail(s.email.ifEmpty { null })

                _state.value =
                    s.copy(
                        nameError = nameResult.errorMessage,
                        phoneError = phoneResult.errorMessage,
                        cpfError = cpfResult.errorMessage,
                        emailError = emailResult.errorMessage,
                    )

                val allValid =
                    nameResult.isValid &&
                        phoneResult.isValid &&
                        cpfResult.isValid &&
                        emailResult.isValid

                viewModelScope.launch {
                    if (allValid) {
                        val client =
                            BarClient(
                                id = "bar_client_${Uuid.random()}",
                                name = s.name.trim(),
                                nickname = s.nickname.ifBlank { null },
                                phone = s.phone,
                                cpf = s.cpf,
                                email = s.email.ifBlank { null },
                            )
                        _effects.emit(AddClientEffect.ClientCreated(client))
                    } else {
                        _effects.emit(
                            AddClientEffect.ShowError("Preencha os campos obrigatórios corretamente"),
                        )
                    }
                }
            }

            is AddClientIntent.Dismiss -> {
                viewModelScope.launch {
                    _effects.emit(AddClientEffect.Dismissed)
                }
            }
        }
    }
}
