package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.domain.validation.LoginValidator
import br.com.sprena.shared.core.logger.Logger

/**
 * Caso de uso de login.
 *
 * Responsabilidade única: validar inputs e delegar ao [AuthRepository].
 * Loga sucesso/falha SEM expor a senha (senha nunca entra na string de log).
 */
class LoginUseCase(
    private val authRepository: AuthRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(
        username: String,
        password: String,
    ): AuthResult {
        val usernameResult = LoginValidator.validateUsername(username)
        if (!usernameResult.isValid) {
            logger.warn(TAG, "login rejected invalid username")
            return AuthResult.Error(usernameResult.errorMessage ?: "Usuário inválido")
        }

        val passwordResult = LoginValidator.validatePassword(password)
        if (!passwordResult.isValid) {
            logger.warn(TAG, "login rejected invalid password for username=$username")
            return AuthResult.Error(passwordResult.errorMessage ?: "Senha inválida")
        }

        val result = authRepository.authenticate(username, password)
        when (result) {
            is AuthResult.Success -> logger.info(TAG, "login ok username=$username")
            is AuthResult.Error -> logger.warn(TAG, "login failed username=$username reason=${result.message}")
        }
        return result
    }

    private companion object {
        const val TAG = "LoginUseCase"
    }
}
