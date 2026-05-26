package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.PasswordResetResult
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.core.logger.NoOpLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestPasswordResetUseCaseTest {
    private class FakeAuthRepo(
        var nextResult: Result<Unit> = Result.success(Unit),
    ) : AuthRepository {
        var lastEmail: String? = null
        override suspend fun authenticate(email: String, password: String) =
            AuthResult.Error("not used")
        override suspend fun sendPasswordReset(email: String): Result<Unit> {
            lastEmail = email
            return nextResult
        }
        override suspend fun signOut() = Unit
        override fun currentUid(): String? = null
    }

    @Test
    fun `returns InvalidEmail without calling repo when email is malformed`() = runTest {
        val repo = FakeAuthRepo()
        val useCase = RequestPasswordResetUseCase(repo, NoOpLogger())

        val result = useCase("no-arroba")

        assertTrue(result is PasswordResetResult.InvalidEmail)
        assertNull(repo.lastEmail)
    }

    @Test
    fun `returns Sent when repo succeeds`() = runTest {
        val repo = FakeAuthRepo()
        val useCase = RequestPasswordResetUseCase(repo, NoOpLogger())

        val result = useCase("pedro@gmail.com")

        assertEquals(PasswordResetResult.Sent, result)
        assertEquals("pedro@gmail.com", repo.lastEmail)
    }

    @Test
    fun `returns NetworkError when repo throws network exception`() = runTest {
        val repo =
            FakeAuthRepo(
                nextResult = Result.failure(java.net.UnknownHostException("offline")),
            )
        val useCase = RequestPasswordResetUseCase(repo, NoOpLogger())

        val result = useCase("pedro@gmail.com")

        assertEquals(PasswordResetResult.NetworkError, result)
    }

    @Test
    fun `returns UnknownError for other failures`() = runTest {
        val repo = FakeAuthRepo(nextResult = Result.failure(RuntimeException("boom")))
        val useCase = RequestPasswordResetUseCase(repo, NoOpLogger())

        val result = useCase("pedro@gmail.com")

        assertTrue(result is PasswordResetResult.UnknownError)
    }
}
