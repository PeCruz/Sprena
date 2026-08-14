package br.com.sprena.shared.account.domain.usecase

import br.com.sprena.shared.account.domain.model.ProfilePatch
import br.com.sprena.shared.account.domain.model.ProfileSaveResult
import br.com.sprena.shared.account.domain.repository.UserProfileRepository
import br.com.sprena.shared.account.domain.validation.ProfileValidator
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.core.logger.Logger

/**
 * Grava os campos autodeclarados do titular (LGPD art. 18, III — correção, no que o app
 * consegue oferecer sem intermediação do controlador).
 *
 * Normaliza CPF e telefone para só dígitos **antes** de persistir: a formatação é
 * decisão de exibição ([br.com.sprena.shared.core.privacy.formatCpf]), e guardar o valor
 * pontuado faria a leitura depender de como o titular digitou.
 */
class SaveMyProfileUseCase(
    private val repository: UserProfileRepository,
    private val sessionStore: SessionStore,
    private val logger: Logger,
) {
    suspend operator fun invoke(patch: ProfilePatch): ProfileSaveResult {
        val session = sessionStore.load() ?: return ProfileSaveResult.Failed(NO_SESSION_MESSAGE)

        val cpfCheck = ProfileValidator.validateCpf(patch.cpf.orEmpty())
        val phoneCheck = ProfileValidator.validatePhone(patch.phone.orEmpty())

        return when {
            !cpfCheck.isValid || !phoneCheck.isValid ->
                ProfileSaveResult.Invalid(cpf = cpfCheck, phone = phoneCheck)

            else -> persist(session.uid, patch)
        }
    }

    private suspend fun persist(
        uid: String,
        patch: ProfilePatch,
    ): ProfileSaveResult {
        val normalized =
            patch.copy(
                apelido = patch.apelido?.trim()?.takeIf { it.isNotEmpty() },
                cpf = patch.cpf?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() },
                phone = patch.phone?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() },
            )

        return repository.save(uid, normalized).fold(
            onSuccess = { ProfileSaveResult.Saved },
            onFailure = { error ->
                logger.warn(TAG, "profile save failed", error)
                ProfileSaveResult.Failed(SAVE_FAILED_MESSAGE)
            },
        )
    }

    private companion object {
        const val TAG = "SaveMyProfile"
        const val NO_SESSION_MESSAGE = "Sessão expirada. Entre novamente."
        const val SAVE_FAILED_MESSAGE = "Não foi possível salvar. Verifique a conexão."
    }
}
