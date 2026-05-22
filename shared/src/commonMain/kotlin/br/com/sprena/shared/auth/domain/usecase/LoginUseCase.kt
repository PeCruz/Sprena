package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.domain.validation.LoginValidator

/**
 * Caso de uso de login.
 *
 * Responsabilidade única: validar inputs e delegar ao [AuthRepository].
 * Não conhece detalhes de implementação (mock, Firebase, etc.).
 */
class LoginUseCase(
    private val authRepository: AuthRepository,
) {
    /**
     * Executa o login com [username] e [password].
     *
     * 1. Valida username (3–8 chars) e password (exatamente 6 dígitos numéricos)
     * 2. Se válido, delega ao repositório
     * 3. Se inválido, retorna [AuthResult.Error] imediatamente
     */
    suspend operator fun invoke(
        username: String,
        password: String,
    ): AuthResult {
        val usernameResult = LoginValidator.validateUsername(username)
        if (!usernameResult.isValid) {
            return AuthResult.Error(usernameResult.errorMessage ?: "Usuário inválido")
        }

        val passwordResult = LoginValidator.validatePassword(password)
        if (!passwordResult.isValid) {
            return AuthResult.Error(passwordResult.errorMessage ?: "Senha inválida")
        }

        return authRepository.authenticate(username, password)
    }
}
