package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.core.logger.Logger

/**
 * Encerra a sessão: Firebase signOut + limpa sessão local.
 */
class LogoutUseCase(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
    private val logger: Logger,
) {
    suspend operator fun invoke() {
        authRepository.signOut()
        sessionStore.clear()
        logger.info(TAG, "user logged out")
    }

    private companion object {
        const val TAG = "LogoutUseCase"
    }
}
