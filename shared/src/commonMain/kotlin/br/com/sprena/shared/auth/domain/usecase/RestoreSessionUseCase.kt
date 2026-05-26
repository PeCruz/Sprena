package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.RestoreResult
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionValidator
import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.core.time.Clock

/**
 * Restaura a sessão no cold start. Decide se navega para Home ou Login.
 *
 * - Sem sessão local → NotAuthenticated
 * - Sessão expirada (>= TTL) → signOut + clear → NotAuthenticated
 * - Firebase já sem currentUser (uid null) → clear → NotAuthenticated
 * - uid local != uid do Firebase → clear → NotAuthenticated
 * - Tudo OK → Authenticated(sessionUser)
 */
class RestoreSessionUseCase(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
    private val clock: Clock,
    private val logger: Logger,
) {
    suspend operator fun invoke(): RestoreResult {
        val stored = sessionStore.load()
        if (stored == null) {
            logger.info(TAG, "no local session")
            return RestoreResult.NotAuthenticated
        }

        if (SessionValidator.isExpired(stored.lastLoginEpochMillis, clock.nowEpochMillis())) {
            logger.info(TAG, "session expired uid=${stored.uid}")
            authRepository.signOut()
            sessionStore.clear()
            return RestoreResult.NotAuthenticated
        }

        val currentUid = authRepository.currentUid()
        if (currentUid == null || currentUid != stored.uid) {
            logger.warn(TAG, "session uid mismatch stored=${stored.uid} firebase=$currentUid")
            sessionStore.clear()
            return RestoreResult.NotAuthenticated
        }

        logger.info(TAG, "session restored uid=${stored.uid}")
        return RestoreResult.Authenticated(stored)
    }

    private companion object {
        const val TAG = "RestoreSession"
    }
}
