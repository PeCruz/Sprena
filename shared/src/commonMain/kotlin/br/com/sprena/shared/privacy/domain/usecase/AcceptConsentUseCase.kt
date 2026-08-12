package br.com.sprena.shared.privacy.domain.usecase

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.privacy.domain.model.PrivacyPolicy
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository

/** Registra o aceite da versão vigente da política. */
class AcceptConsentUseCase(
    private val repository: ConsentRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(uid: String): Result<Unit> =
        repository
            .accept(uid, PrivacyPolicy.VERSION)
            .onSuccess { logger.info(TAG, "consent accepted uid=$uid version=${PrivacyPolicy.VERSION}") }
            .onFailure { logger.warn(TAG, "consent write failed uid=$uid", it) }

    private companion object {
        const val TAG = "AcceptConsent"
    }
}
