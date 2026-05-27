package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.PasswordResetResult
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.domain.validation.LoginValidator
import br.com.sprena.shared.core.logger.Logger

/**
 * Solicita reset de senha por email.
 *
 * 1. Valida o email com [LoginValidator] (não bate na rede se inválido)
 * 2. Chama [AuthRepository.sendPasswordReset]
 * 3. Mapeia falhas para [PasswordResetResult]
 */
class RequestPasswordResetUseCase(
    private val authRepository: AuthRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(email: String): PasswordResetResult {
        val emailResult = LoginValidator.validateEmail(email)
        if (!emailResult.isValid) {
            return PasswordResetResult.InvalidEmail(emailResult.errorMessage ?: "Email inválido")
        }

        return authRepository.sendPasswordReset(email.trim()).fold(
            onSuccess = {
                logger.info(TAG, "password reset email sent to email=$email")
                PasswordResetResult.Sent
            },
            onFailure = { e ->
                logger.warn(TAG, "password reset failed for email=$email", e)
                if (isNetworkError(e)) {
                    PasswordResetResult.NetworkError
                } else {
                    PasswordResetResult.UnknownError(e.message ?: "erro desconhecido")
                }
            },
        )
    }

    private fun isNetworkError(e: Throwable): Boolean {
        val name = e::class.simpleName ?: ""
        return name.contains("UnknownHostException") ||
            name.contains("FirebaseNetworkException") ||
            name.contains("IOException")
    }

    private companion object {
        const val TAG = "PasswordResetUseCase"
    }
}
