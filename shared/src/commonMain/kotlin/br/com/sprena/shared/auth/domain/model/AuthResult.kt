package br.com.sprena.shared.auth.domain.model

/**
 * Resultado de uma tentativa de autenticação.
 */
sealed interface AuthResult {
    /** Login bem-sucedido com dados do usuário. */
    data class Success(val user: UserModel) : AuthResult

    /** Falha na autenticação. */
    data class Error(val message: String) : AuthResult
}
