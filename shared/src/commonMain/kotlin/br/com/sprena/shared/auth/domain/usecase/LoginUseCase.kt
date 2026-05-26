package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.domain.validation.LoginValidator
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.core.time.Clock

/**
 * Caso de uso de login.
 *
 * 1. Valida email e senha (ver [LoginValidator])
 * 2. Delega ao [AuthRepository]
 * 3. Em sucesso, persiste [SessionUser] em [SessionStore] com timestamp do [Clock]
 *
 * Loga warn em rejeições, info em sucesso — sempre via [Logger] com email mascarado.
 */
class LoginUseCase(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
    private val clock: Clock,
    private val logger: Logger,
) {
    suspend operator fun invoke(email: String, password: String): AuthResult {
        val emailResult = LoginValidator.validateEmail(email)
        if (!emailResult.isValid) {
            logger.warn(TAG, "login rejected invalid email")
            return AuthResult.Error(emailResult.errorMessage ?: "Email inválido")
        }

        val passwordResult = LoginValidator.validatePassword(password)
        if (!passwordResult.isValid) {
            logger.warn(TAG, "login rejected invalid password for email=$email")
            return AuthResult.Error(passwordResult.errorMessage ?: "Senha inválida")
        }

        val result = authRepository.authenticate(email.trim(), password)
        if (result is AuthResult.Success) {
            sessionStore.save(
                SessionUser(
                    uid = result.user.id,
                    email = result.user.email,
                    role = result.user.role,
                    lastLoginEpochMillis = clock.nowEpochMillis(),
                ),
            )
            logger.info(TAG, "login ok email=$email")
        } else {
            logger.warn(TAG, "login failed email=$email")
        }
        return result
    }

    private companion object {
        const val TAG = "LoginUseCase"
    }
}
