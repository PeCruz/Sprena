package br.com.sprena.shared.auth.domain.repository

import br.com.sprena.shared.auth.domain.model.AuthResult

/**
 * Contrato do repositório de autenticação.
 *
 * Implementação concreta em `shared/androidMain`: `FirebaseAuthRepositoryImpl`.
 */
interface AuthRepository {
    /**
     * Autentica com [email] e [password].
     * Em sucesso, retorna [AuthResult.Success] com `UserModel` populado a partir
     * do Firebase Auth + doc `users/{uid}` no Firestore.
     */
    suspend fun authenticate(
        email: String,
        password: String,
    ): AuthResult

    /**
     * Envia email de reset de senha. `Result.failure` se rede/Firebase falhar.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /**
     * Encerra a sessão Firebase Auth local (não invalida no servidor).
     */
    suspend fun signOut()

    /**
     * Retorna o uid do usuário atualmente autenticado no Firebase Auth, ou null.
     * Não depende de cache local — consulta `FirebaseAuth.currentUser`.
     */
    fun currentUid(): String?
}
