package br.com.sprena.shared.account.domain.usecase

import br.com.sprena.shared.account.domain.model.ProfileResult
import br.com.sprena.shared.account.domain.repository.UserProfileRepository
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.core.logger.Logger

/**
 * Lê o perfil do titular logado (LGPD art. 18, II — direito de acesso).
 *
 * Fail-closed no mesmo molde de [br.com.sprena.shared.privacy.domain.usecase.CheckConsentUseCase]:
 * falha de leitura vira [ProfileResult.Unavailable] com retry na UI, nunca um perfil
 * com campos em branco que o titular leria como "vocês não têm esse dado".
 */
class GetMyProfileUseCase(
    private val repository: UserProfileRepository,
    private val sessionStore: SessionStore,
    private val logger: Logger,
) {
    suspend operator fun invoke(): ProfileResult {
        val session = sessionStore.load() ?: return ProfileResult.Unavailable(NO_SESSION_MESSAGE)

        return repository.current(session.uid).fold(
            // `null` distingue "doc de users não existe" de "falha de leitura": só o
            // primeiro significa que a conta não está autorizada.
            onSuccess = { profile ->
                profile?.let(ProfileResult::Loaded) ?: ProfileResult.Unavailable(NOT_AUTHORIZED_MESSAGE)
            },
            onFailure = { error ->
                logger.warn(TAG, "profile read failed", error)
                ProfileResult.Unavailable(READ_FAILED_MESSAGE)
            },
        )
    }

    private companion object {
        const val TAG = "GetMyProfile"
        const val NO_SESSION_MESSAGE = "Sessão expirada. Entre novamente."
        const val READ_FAILED_MESSAGE = "Não foi possível carregar seus dados. Verifique a conexão."
        const val NOT_AUTHORIZED_MESSAGE = "Conta não autorizada. Contate o administrador."
    }
}
