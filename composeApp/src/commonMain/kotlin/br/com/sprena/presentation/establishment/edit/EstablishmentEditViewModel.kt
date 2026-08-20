package br.com.sprena.presentation.establishment.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.core.mvi.MviViewModel
import br.com.sprena.shared.establishment.domain.model.EstablishmentSaveResult
import br.com.sprena.shared.establishment.domain.usecase.GetEstablishmentUseCase
import br.com.sprena.shared.establishment.domain.usecase.SaveEstablishmentUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Cadastro e edição de estabelecimento (ADM).
 *
 * [establishmentId] nulo significa criar. Ele vem como argumento de rota e o cadastro é lido
 * aqui — diferente de `SportClientEditScreen`, que é um Composable sem ViewModel e devolve o
 * resultado por uma dezena de chaves de `savedStateHandle` no `NavGraph`. Aquele caminho já é
 * difícil de seguir com um formulário; com dois seria pior.
 *
 * A validação inteira mora em `SaveEstablishmentUseCase`, que também normaliza CNPJ e telefone
 * para dígitos. Aqui só se traduz o desfecho para a tela.
 */
class EstablishmentEditViewModel(
    private val establishmentId: String?,
    private val getEstablishment: GetEstablishmentUseCase,
    private val saveEstablishment: SaveEstablishmentUseCase,
) : ViewModel(),
    MviViewModel<EstablishmentEditState, EstablishmentEditIntent, EstablishmentEditEffect> {
    private val _state =
        MutableStateFlow(
            EstablishmentEditState(
                isCreating = establishmentId == null,
                isLoading = establishmentId != null,
            ),
        )
    override val state: StateFlow<EstablishmentEditState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<EstablishmentEditEffect>()
    override val effects: SharedFlow<EstablishmentEditEffect> = _effects.asSharedFlow()

    init {
        if (establishmentId != null) load(establishmentId)
    }

    override fun handleIntent(intent: EstablishmentEditIntent) {
        when (intent) {
            is EstablishmentEditIntent.SaveClicked -> save()
            is EstablishmentEditIntent.Retry -> establishmentId?.let { load(it) }
            else -> handleFieldIntent(intent)
        }
    }

    /**
     * Separado de [handleIntent] para manter a complexidade ciclomática sob o limite do detekt
     * — mesma divisão que `ProfileViewModel` faz. A quebra em identificação x endereço existe
     * pelo mesmo motivo: um único `when` sobre catorze campos estoura o limite sozinho.
     *
     * Cada campo limpa o próprio erro ao ser editado: manter a marca vermelha enquanto a
     * pessoa corrige o valor é ruído.
     */
    private fun handleFieldIntent(intent: EstablishmentEditIntent) {
        val identity = identityField(intent)
        _state.value = identity ?: addressField(intent)
    }

    /** `null` quando o intent não é de identificação — aí quem responde é [addressField]. */
    private fun identityField(intent: EstablishmentEditIntent): EstablishmentEditState? {
        val current = _state.value
        val draft = current.draft
        return when (intent) {
            is EstablishmentEditIntent.NameChanged ->
                current.copy(draft = draft.copy(name = intent.value), nameError = null)
            is EstablishmentEditIntent.CnpjChanged ->
                current.copy(draft = draft.copy(cnpj = intent.value), cnpjError = null)
            is EstablishmentEditIntent.RazaoSocialChanged ->
                current.copy(draft = draft.copy(razaoSocial = intent.value), razaoSocialError = null)
            is EstablishmentEditIntent.PhoneChanged ->
                current.copy(draft = draft.copy(phone = intent.value), phoneError = null)
            is EstablishmentEditIntent.EmailChanged ->
                current.copy(draft = draft.copy(email = intent.value), emailError = null)
            is EstablishmentEditIntent.ActiveChanged ->
                current.copy(draft = draft.copy(active = intent.value))
            else -> null
        }
    }

    private fun addressField(intent: EstablishmentEditIntent): EstablishmentEditState {
        val current = _state.value
        val draft = current.draft
        return when (intent) {
            is EstablishmentEditIntent.StreetChanged ->
                current.copy(draft = draft.copy(street = intent.value))
            is EstablishmentEditIntent.NumberChanged ->
                current.copy(draft = draft.copy(number = intent.value))
            is EstablishmentEditIntent.ComplementChanged ->
                current.copy(draft = draft.copy(complement = intent.value))
            is EstablishmentEditIntent.DistrictChanged ->
                current.copy(draft = draft.copy(district = intent.value))
            is EstablishmentEditIntent.CityChanged ->
                current.copy(draft = draft.copy(city = intent.value))
            is EstablishmentEditIntent.StateChanged ->
                current.copy(draft = draft.copy(state = intent.value))
            is EstablishmentEditIntent.ZipCodeChanged ->
                current.copy(draft = draft.copy(zipCode = intent.value))
            else -> current
        }
    }

    private fun load(id: String) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            getEstablishment(id).fold(
                onSuccess = { found ->
                    _state.value =
                        if (found == null) {
                            // Formulário em branco aqui salvaria como novo e criaria um
                            // duplicado do cadastro que o ADM pensou estar editando.
                            _state.value.copy(isLoading = false, error = NOT_FOUND)
                        } else {
                            _state.value.copy(
                                draft = EstablishmentDraft.from(found),
                                isLoading = false,
                                error = null,
                            )
                        }
                },
                onFailure = { _state.value = _state.value.copy(isLoading = false, error = LOAD_FAILED) },
            )
        }
    }

    private fun save() {
        if (_state.value.isSaving) return
        _state.value = _state.value.copy(isSaving = true)

        viewModelScope.launch {
            when (val result = saveEstablishment(_state.value.draft.toEstablishment())) {
                is EstablishmentSaveResult.Saved -> {
                    _state.value = _state.value.copy(isSaving = false)
                    _effects.emit(EstablishmentEditEffect.SavedAndClose)
                }

                is EstablishmentSaveResult.Invalid ->
                    _state.value =
                        _state.value.copy(
                            isSaving = false,
                            nameError = result.name.errorMessage,
                            cnpjError = result.cnpj.errorMessage,
                            razaoSocialError = result.razaoSocial.errorMessage,
                            phoneError = result.phone.errorMessage,
                            emailError = result.email.errorMessage,
                        )

                // Marca o campo do CNPJ em vez de virar Snackbar: o número está certo, o
                // problema é que já existe outro estabelecimento com ele. Sem a distinção o
                // ADM ficaria procurando um erro de digitação que não existe.
                is EstablishmentSaveResult.DuplicateCnpj ->
                    _state.value = _state.value.copy(isSaving = false, cnpjError = DUPLICATE_CNPJ)

                is EstablishmentSaveResult.Failed -> {
                    // Não fecha a tela: fechar perderia tudo que foi digitado.
                    _state.value = _state.value.copy(isSaving = false)
                    _effects.emit(EstablishmentEditEffect.ShowMessage(result.message))
                }
            }
        }
    }

    private companion object {
        const val NOT_FOUND = "Este estabelecimento não existe mais."
        const val LOAD_FAILED = "Não foi possível carregar o estabelecimento."
        const val DUPLICATE_CNPJ = "Já existe um estabelecimento com este CNPJ."
    }
}
