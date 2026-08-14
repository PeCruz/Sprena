package br.com.sprena.shared.privacy.domain.usecase

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.privacy.domain.model.ConsentStatus
import br.com.sprena.shared.privacy.domain.model.PrivacyPolicy
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository

/**
 * Decide se o usuário pode entrar no app ou precisa aceitar a política.
 *
 * Fail-closed: qualquer falha de leitura vira [ConsentStatus.Unavailable], que a
 * UI trata como bloqueio com retry.
 */
class CheckConsentUseCase(
    private val repository: ConsentRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(uid: String): ConsentStatus {
        val record =
            repository.current(uid).getOrElse { error ->
                logger.warn(TAG, "consent read failed uid=$uid", error)
                return ConsentStatus.Unavailable(READ_FAILED_MESSAGE)
            }

        return when {
            record == null -> ConsentStatus.Required(ConsentStatus.Reason.MISSING)
            record.policyVersion != PrivacyPolicy.VERSION -> ConsentStatus.Required(ConsentStatus.Reason.OUTDATED)
            else -> ConsentStatus.Granted
        }
    }

    private companion object {
        const val TAG = "CheckConsent"
        const val READ_FAILED_MESSAGE = "Não foi possível verificar seu consentimento. Verifique a conexão."
    }
}
