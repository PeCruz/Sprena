package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.RestoreResult
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
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
        val stored = sessionStore.load() ?: return notAuthenticated("no local session")
        val invalidReason = invalidateIfNeeded(stored)
        return if (invalidReason != null) {
            notAuthenticated(invalidReason)
        } else {
            logger.info(TAG, "session restored uid=${stored.uid}")
            RestoreResult.Authenticated(stored)
        }
    }

    private suspend fun invalidateIfNeeded(stored: SessionUser): String? {
        if (SessionValidator.isExpired(stored.lastLoginEpochMillis, clock.nowEpochMillis())) {
            authRepository.signOut()
            sessionStore.clear()
            return "session expired uid=${stored.uid}"
        }
        val currentUid = authRepository.currentUid()
        return if (currentUid == null || currentUid != stored.uid) {
            sessionStore.clear()
            "session uid mismatch stored=${stored.uid} firebase=$currentUid"
        } else {
            null
        }
    }

    private fun notAuthenticated(reason: String): RestoreResult.NotAuthenticated {
        logger.info(TAG, reason)
        return RestoreResult.NotAuthenticated
    }

    private companion object {
        const val TAG = "RestoreSession"
    }
}
