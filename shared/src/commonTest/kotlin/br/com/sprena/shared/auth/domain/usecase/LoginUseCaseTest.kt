package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.data.repository.MockAuthRepository
import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.core.logger.NoOpLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes do LoginUseCase.
 *
 * Cobre:
 *  - Validação de inputs antes de chamar o repository
 *  - Delegação ao repository com inputs válidos
 *  - Retorno correto de Success/Error
 */
class LoginUseCaseTest {
    private val useCase = LoginUseCase(MockAuthRepository(), NoOpLogger())

    // ── Validação de inputs ──────────────────────────────

    @Test
    fun `empty username returns Error without calling repository`() =
        runTest {
            val result = useCase("", "123456")
            assertTrue(result is AuthResult.Error)
        }

    @Test
    fun `username too short returns Error`() =
        runTest {
            val result = useCase("ab", "123456")
            assertTrue(result is AuthResult.Error)
        }

    @Test
    fun `username too long returns Error`() =
        runTest {
            val result = useCase("123456789", "123456")
            assertTrue(result is AuthResult.Error)
        }

    @Test
    fun `password with letters returns Error`() =
        runTest {
            val result = useCase("admin", "abc123")
            assertTrue(result is AuthResult.Error)
        }

    @Test
    fun `password too short returns Error`() =
        runTest {
            val result = useCase("admin", "12345")
            assertTrue(result is AuthResult.Error)
        }

    @Test
    fun `password too long returns Error`() =
        runTest {
            val result = useCase("admin", "1234567")
            assertTrue(result is AuthResult.Error)
        }

    // ── Autenticação ─────────────────────────────────────

    @Test
    fun `valid admin credentials return Success with ADM role`() =
        runTest {
            val result = useCase("admin", "123456")
            assertTrue(result is AuthResult.Success)
            assertEquals(UserRole.ADM, (result as AuthResult.Success).user.role)
        }

    @Test
    fun `valid mod credentials return Success with MOD role`() =
        runTest {
            val result = useCase("mod", "654321")
            assertTrue(result is AuthResult.Success)
            assertEquals(UserRole.MOD, (result as AuthResult.Success).user.role)
        }

    @Test
    fun `valid client credentials return Success with CLIENT role`() =
        runTest {
            val result = useCase("func", "111111")
            assertTrue(result is AuthResult.Success)
            assertEquals(UserRole.CLIENT, (result as AuthResult.Success).user.role)
        }

    @Test
    fun `wrong password returns Error`() =
        runTest {
            val result = useCase("admin", "999999")
            assertTrue(result is AuthResult.Error)
            assertEquals("Senha incorreta", (result as AuthResult.Error).message)
        }

    @Test
    fun `unknown user returns Error`() =
        runTest {
            val result = useCase("unknown", "123456")
            assertTrue(result is AuthResult.Error)
            assertEquals("Usuário não encontrado", (result as AuthResult.Error).message)
        }
}
