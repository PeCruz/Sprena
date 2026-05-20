package br.com.sprena.shared.auth.data.repository

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.UserModel
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import kotlinx.coroutines.delay

/**
 * Implementação mockada do [AuthRepository] com usuários hardcoded in-memory.
 *
 * Usuários disponíveis:
 * - admin / 123456 → Administrador
 * - mod / 654321 → Moderador
 * - func / 111111 → Funcionário
 *
 * Simula um pequeno delay de rede (300ms) para testar loading states.
 */
class MockAuthRepository : AuthRepository {
    private val users =
        listOf(
            MockUser(
                user =
                    UserModel(
                        id = "1",
                        username = "admin",
                        name = "Pedro Admin",
                        role = UserRole.ADM,
                    ),
                password = "123456",
            ),
            MockUser(
                user =
                    UserModel(
                        id = "2",
                        username = "mod",
                        name = "Maria Moderadora",
                        role = UserRole.MOD,
                    ),
                password = "654321",
            ),
            MockUser(
                user =
                    UserModel(
                        id = "3",
                        username = "func",
                        name = "João Funcionário",
                        role = UserRole.CLIENT,
                    ),
                password = "111111",
            ),
        )

    override suspend fun authenticate(
        username: String,
        password: String,
    ): AuthResult {
        delay(FAKE_NETWORK_DELAY_MS)

        val trimmedUsername = username.trim().lowercase()
        val trimmedPassword = password.trim()

        val found =
            users.find { it.user.username == trimmedUsername }
                ?: return AuthResult.Error("Usuário não encontrado")

        return if (found.password == trimmedPassword) {
            AuthResult.Success(found.user)
        } else {
            AuthResult.Error("Senha incorreta")
        }
    }

    private data class MockUser(
        val user: UserModel,
        val password: String,
    )

    companion object {
        const val FAKE_NETWORK_DELAY_MS = 300L
    }
}
