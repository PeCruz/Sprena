package br.com.sprena.presentation.profile

import br.com.sprena.shared.account.domain.model.UserProfile
import br.com.sprena.shared.core.mvi.UiState
import br.com.sprena.shared.core.privacy.formatCpf
import br.com.sprena.shared.core.privacy.formatPhone
import br.com.sprena.shared.core.privacy.maskCpf
import br.com.sprena.shared.core.privacy.maskPhone
import br.com.sprena.shared.sportclient.domain.validation.SportModality

const val NOT_INFORMED = "Não informado"
const val NONE_INFORMED = "Nenhuma informada"

/** Texto que o titular precisa digitar para liberar a exclusão. */
const val DELETE_KEYWORD = "EXCLUIR"

/**
 * Tela "Meus dados" (F1.6a).
 *
 * O rascunho de edição vive separado de [profile] de propósito: cancelar precisa
 * devolver os valores originais, e um formulário que escrevesse direto no perfil não
 * teria para onde voltar.
 */
data class ProfileState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isCpfRevealed: Boolean = false,
    val isPhoneRevealed: Boolean = false,
    val isEditing: Boolean = false,
    val draft: ProfileDraft = ProfileDraft(),
    val isSaving: Boolean = false,
    val cpfError: String? = null,
    val phoneError: String? = null,
    val isSendingPasswordReset: Boolean = false,
    val isExportDialogVisible: Boolean = false,
    val isExporting: Boolean = false,
    val isDeleteDialogVisible: Boolean = false,
    val deleteConfirmationText: String = "",
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
    val loggingOut: Boolean = false,
) : UiState {
    /**
     * Aqui a autorização é **propriedade**, não role: o dado é do próprio titular, então
     * revelar é sempre permitido. A máscara existe contra ombro e gravação de tela, não
     * contra o dono — por isso não há `canRevealCpf` como em `SportClientState`, onde a
     * pergunta é "este usuário pode ver o CPF de outra pessoa?".
     */
    val displayCpf: String
        get() =
            profile?.cpf?.let { if (isCpfRevealed) formatCpf(it) else maskCpf(it) } ?: NOT_INFORMED

    val displayPhone: String
        get() =
            profile?.phone?.let { if (isPhoneRevealed) formatPhone(it) else maskPhone(it) }
                ?: NOT_INFORMED

    val displayName: String get() = profile?.name ?: NOT_INFORMED

    val displayApelido: String get() = profile?.apelido ?: NOT_INFORMED

    val displayModalities: String
        get() =
            profile
                ?.modalities
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(", ") { it.label }
                ?: NONE_INFORMED

    val canSave: Boolean get() = !isSaving && isEditing

    val canConfirmDelete: Boolean
        get() = deleteConfirmationText.trim().equals(DELETE_KEYWORD, ignoreCase = true) && !isDeleting
}

/** Rascunho do formulário. Strings cruas — a normalização é do use case. */
data class ProfileDraft(
    val apelido: String = "",
    val cpf: String = "",
    val phone: String = "",
    val modalities: Set<SportModality> = emptySet(),
)
