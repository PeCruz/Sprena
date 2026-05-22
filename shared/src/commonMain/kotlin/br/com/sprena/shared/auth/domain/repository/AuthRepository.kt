package br.com.sprena.shared.auth.domain.repository

import br.com.sprena.shared.auth.domain.model.AuthResult

/**
 * Contrato do repositório de autenticação.
 *
 * Interface em `shared/commonMain` — implementação concreta
 * pode ser mockada (in-memory) ou Firebase Auth.
 */
interface AuthRepository {
    /**
     * Autentica o usuário com [username] e [password].
     *
     * @return [AuthResult.Success] com dados do usuário ou [AuthResult.Error] com mensagem.
     */
    suspend fun authenticate(
        username: String,
        password: String,
    ): AuthResult
}
