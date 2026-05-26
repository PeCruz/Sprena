package br.com.sprena.shared.auth.data.repository

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.UserModel
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.domain.repository.AuthRepository

/**
 * Mock — será DELETADO em F1.3 Task 13 quando FirebaseAuthRepositoryImpl entrar.
 * Mantido aqui apenas para compilação intermediária.
 */
class MockAuthRepository : AuthRepository {
    override suspend fun authenticate(email: String, password: String): AuthResult =
        AuthResult.Success(
            UserModel(id = "mock", email = email, name = "Mock User", role = UserRole.ADM),
        )

    override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun signOut() = Unit

    override fun currentUid(): String? = null
}
