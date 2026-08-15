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
 * - Conta não existe mais no servidor (F1.6a) → signOut + clear → NotAuthenticated
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
        return uidMismatch(stored) ?: accountStillExists(stored)
    }

    private suspend fun uidMismatch(stored: SessionUser): String? {
        val currentUid = authRepository.currentUid()
        return if (currentUid == null || currentUid != stored.uid) {
            sessionStore.clear()
            "session uid mismatch stored=${stored.uid} firebase=$currentUid"
        } else {
            null
        }
    }

    /**
     * `currentUid()` responde do cache local do SDK, então uma conta excluída (F1.6a) ou
     * apagada pelo Console continua parecendo válida até o token ser renovado. O refresh
     * é o que fecha essa janela.
     *
     * Falha aqui significa **conta inexistente ou desabilitada** — falha de rede devolve
     * sucesso por contrato de [AuthRepository.refreshToken], senão abrir o app offline
     * deslogaria todo mundo.
     */
    private suspend fun accountStillExists(stored: SessionUser): String? =
        authRepository.refreshToken().fold(
            onSuccess = { null },
            onFailure = {
                authRepository.signOut()
                sessionStore.clear()
                "account no longer exists uid=${stored.uid}"
            },
        )

    private fun notAuthenticated(reason: String): RestoreResult.NotAuthenticated {
        logger.info(TAG, reason)
        return RestoreResult.NotAuthenticated
    }

    private companion object {
        const val TAG = "RestoreSession"
    }
}
