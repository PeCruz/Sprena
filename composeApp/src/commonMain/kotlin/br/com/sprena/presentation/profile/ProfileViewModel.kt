package br.com.sprena.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.sprena.shared.account.domain.model.AccountDeletionResult
import br.com.sprena.shared.account.domain.model.ProfilePatch
import br.com.sprena.shared.account.domain.model.ProfileResult
import br.com.sprena.shared.account.domain.model.ProfileSaveResult
import br.com.sprena.shared.account.domain.model.UserProfile
import br.com.sprena.shared.account.domain.usecase.DeleteMyAccountUseCase
import br.com.sprena.shared.account.domain.usecase.ExportMyDataUseCase
import br.com.sprena.shared.account.domain.usecase.GetMyProfileUseCase
import br.com.sprena.shared.account.domain.usecase.SaveMyProfileUseCase
import br.com.sprena.shared.auth.domain.model.PasswordResetResult
import br.com.sprena.shared.auth.domain.usecase.LogoutUseCase
import br.com.sprena.shared.auth.domain.usecase.RequestPasswordResetUseCase
import br.com.sprena.shared.core.mvi.MviViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tela "Meus dados" — direitos do titular sobre a própria conta (F1.6a).
 *
 * **Sem `SessionStore`**: os use cases resolvem a sessão internamente. Isso diverge do
 * `SettingsViewModel`, que chama `sessionStore.load()` direto, e a divergência é
 * intencional — acesso a sessão não precisa estar na camada de apresentação.
 */
class ProfileViewModel(
    private val getProfile: GetMyProfileUseCase,
    private val saveProfile: SaveMyProfileUseCase,
    private val exportMyData: ExportMyDataUseCase,
    private val deleteMyAccount: DeleteMyAccountUseCase,
    private val requestPasswordReset: RequestPasswordResetUseCase,
    private val logout: LogoutUseCase,
) : ViewModel(),
    MviViewModel<ProfileState, ProfileIntent, ProfileEffect> {
    private val _state = MutableStateFlow(ProfileState())
    override val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ProfileEffect>()
    override val effects: SharedFlow<ProfileEffect> = _effects.asSharedFlow()

    init {
        refresh()
    }

    override fun handleIntent(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.Retry -> refresh()
            is ProfileIntent.ToggleCpfReveal ->
                _state.value = _state.value.copy(isCpfRevealed = !_state.value.isCpfRevealed)

            is ProfileIntent.TogglePhoneReveal ->
                _state.value = _state.value.copy(isPhoneRevealed = !_state.value.isPhoneRevealed)

            is ProfileIntent.Logout -> logoutAndLeave()
            is ProfileIntent.PasswordResetRequested -> sendPasswordReset()
            else -> handleFormIntent(intent)
        }
    }

    /** Separado de [handleIntent] para manter a complexidade ciclomática sob o limite. */
    private fun handleFormIntent(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.EditClicked -> startEditing()
            is ProfileIntent.EditCancelled ->
                _state.value =
                    _state.value.copy(isEditing = false, cpfError = null, phoneError = null)

            is ProfileIntent.ApelidoChanged -> updateDraft { it.copy(apelido = intent.value) }
            is ProfileIntent.CpfChanged -> updateDraft { it.copy(cpf = intent.value) }
            is ProfileIntent.PhoneChanged -> updateDraft { it.copy(phone = intent.value) }
            is ProfileIntent.ModalityToggled -> toggleModality(intent)
            is ProfileIntent.SaveClicked -> save()
            else -> handleDangerIntent(intent)
        }
    }

    /** Exportação e exclusão — as duas ações que saem do app ou destroem dados. */
    private fun handleDangerIntent(intent: ProfileIntent) {
        when (intent) {
            is ProfileIntent.ExportClicked ->
                _state.value = _state.value.copy(isExportDialogVisible = true)

            is ProfileIntent.ExportDismissed ->
                _state.value = _state.value.copy(isExportDialogVisible = false)

            is ProfileIntent.ExportConfirmed -> export()
            is ProfileIntent.DeleteClicked ->
                _state.value =
                    _state.value.copy(
                        isDeleteDialogVisible = true,
                        deleteConfirmationText = "",
                        deleteError = null,
                    )

            is ProfileIntent.DeleteDismissed ->
                _state.value = _state.value.copy(isDeleteDialogVisible = false)

            is ProfileIntent.DeleteConfirmationChanged ->
                _state.value = _state.value.copy(deleteConfirmationText = intent.text)

            is ProfileIntent.DeleteConfirmed -> delete()
            else -> Unit
        }
    }

    private fun refresh() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val result = getProfile()) {
                is ProfileResult.Loaded ->
                    _state.value =
                        _state.value.copy(
                            profile = result.profile,
                            draft = result.profile.toDraft(),
                            isLoading = false,
                            error = null,
                            isEditing = false,
                        )

                is ProfileResult.Unavailable ->
                    _state.value =
                        _state.value.copy(profile = null, isLoading = false, error = result.message)
            }
        }
    }

    /** Recarrega o rascunho do perfil salvo — cancelar precisa ter para onde voltar. */
    private fun startEditing() {
        val profile = _state.value.profile ?: return
        _state.value =
            _state.value.copy(
                isEditing = true,
                draft = profile.toDraft(),
                cpfError = null,
                phoneError = null,
            )
    }

    private fun updateDraft(transform: (ProfileDraft) -> ProfileDraft) {
        _state.value = _state.value.copy(draft = transform(_state.value.draft))
    }

    private fun toggleModality(intent: ProfileIntent.ModalityToggled) {
        updateDraft { draft ->
            val next =
                if (intent.modality in draft.modalities) {
                    draft.modalities - intent.modality
                } else {
                    draft.modalities + intent.modality
                }
            draft.copy(modalities = next)
        }
    }

    private fun save() {
        if (!_state.value.canSave) return
        val draft = _state.value.draft
        _state.value = _state.value.copy(isSaving = true, cpfError = null, phoneError = null)

        viewModelScope.launch {
            val patch =
                ProfilePatch(
                    apelido = draft.apelido,
                    cpf = draft.cpf,
                    phone = draft.phone,
                    modalities = draft.modalities.toList(),
                )

            when (val result = saveProfile(patch)) {
                is ProfileSaveResult.Saved -> {
                    _state.value = _state.value.copy(isSaving = false)
                    _effects.emit(ProfileEffect.ShowMessage(SAVED_MESSAGE))
                    refresh()
                }

                is ProfileSaveResult.Invalid ->
                    _state.value =
                        _state.value.copy(
                            isSaving = false,
                            cpfError = result.cpf.errorMessage,
                            phoneError = result.phone.errorMessage,
                        )

                is ProfileSaveResult.Failed -> {
                    _state.value = _state.value.copy(isSaving = false)
                    _effects.emit(ProfileEffect.ShowMessage(result.message))
                }
            }
        }
    }

    private fun sendPasswordReset() {
        val email = _state.value.profile?.email ?: return
        _state.value = _state.value.copy(isSendingPasswordReset = true)
        viewModelScope.launch {
            val message =
                when (val result = requestPasswordReset(email)) {
                    is PasswordResetResult.Sent -> RESET_SENT_MESSAGE
                    is PasswordResetResult.NetworkError -> NETWORK_MESSAGE
                    // As mensagens já vêm mapeadas para PT-BR pelo AuthErrorMapper.
                    is PasswordResetResult.InvalidEmail -> result.message
                    is PasswordResetResult.UnknownError -> result.message
                }
            _state.value = _state.value.copy(isSendingPasswordReset = false)
            _effects.emit(ProfileEffect.ShowMessage(message))
        }
    }

    private fun export() {
        _state.value = _state.value.copy(isExporting = true, isExportDialogVisible = false)
        viewModelScope.launch {
            exportMyData()
                .onSuccess { export ->
                    _state.value = _state.value.copy(isExporting = false)
                    _effects.emit(ProfileEffect.ShareExport(export.fileName, export.json))
                }.onFailure {
                    _state.value = _state.value.copy(isExporting = false)
                    _effects.emit(ProfileEffect.ShowMessage(EXPORT_FAILED_MESSAGE))
                }
        }
    }

    private fun delete() {
        if (!_state.value.canConfirmDelete) return
        _state.value = _state.value.copy(isDeleting = true, deleteError = null)

        viewModelScope.launch {
            when (val result = deleteMyAccount()) {
                is AccountDeletionResult.Deleted,
                is AccountDeletionResult.SessionExpired,
                -> {
                    _state.value = _state.value.copy(isDeleting = false, isDeleteDialogVisible = false)
                    _effects.emit(ProfileEffect.NavigateToLogin)
                }

                // O diálogo PERMANECE aberto: nada foi apagado e a sessão está intacta,
                // então o titular precisa ver o erro onde tomou a decisão.
                is AccountDeletionResult.Failed ->
                    _state.value = _state.value.copy(isDeleting = false, deleteError = result.message)
            }
        }
    }

    private fun logoutAndLeave() {
        _state.value = _state.value.copy(loggingOut = true)
        viewModelScope.launch {
            runCatching { logout() }
            _effects.emit(ProfileEffect.NavigateToLogin)
        }
    }

    private companion object {
        const val SAVED_MESSAGE = "Dados salvos."
        const val RESET_SENT_MESSAGE = "Enviamos um link de redefinição para o seu e-mail."
        const val NETWORK_MESSAGE = "Sem conexão. Verifique a internet."
        const val EXPORT_FAILED_MESSAGE = "Não foi possível gerar a exportação. Tente novamente."
    }
}

private fun UserProfile.toDraft() =
    ProfileDraft(
        apelido = apelido.orEmpty(),
        cpf = cpf.orEmpty(),
        phone = phone.orEmpty(),
        modalities = modalities.toSet(),
    )
