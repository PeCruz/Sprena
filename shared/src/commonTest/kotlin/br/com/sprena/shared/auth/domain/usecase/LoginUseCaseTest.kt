package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.UserModel
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.core.time.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginUseCaseTest {
    private class FakeAuthRepository(
        var nextResult: AuthResult =
            AuthResult.Success(
                UserModel(id = "u1", email = "a@b.com", name = "A", role = UserRole.ADM),
            ),
    ) : AuthRepository {
        var lastEmail: String? = null
        var lastPassword: String? = null

        override suspend fun authenticate(
            email: String,
            password: String,
        ): AuthResult {
            lastEmail = email
            lastPassword = password
            return nextResult
        }

        override suspend fun sendPasswordReset(email: String) = Result.success(Unit)

        override suspend fun signOut() = Unit

        override fun currentUid(): String? = null

        // Só RestoreSessionUseCase consulta o refresh; estes fluxos não.
        override suspend fun refreshToken(): Result<Unit> = Result.success(Unit)
    }

    private class FakeSessionStore : SessionStore {
        var saved: SessionUser? = null
        var cleared = false

        override suspend fun save(user: SessionUser) {
            saved = user
        }

        override suspend fun load(): SessionUser? = saved

        override suspend fun clear() {
            saved = null
            cleared = true
        }
    }

    private class FixedClock(
        private val now: Long,
    ) : Clock {
        override fun nowEpochMillis(): Long = now
    }

    @Test
    fun `returns Error and does not persist when email invalid`() =
        runTest {
            val repo = FakeAuthRepository()
            val store = FakeSessionStore()
            val useCase = LoginUseCase(repo, store, FixedClock(1_000L), NoOpLogger())

            val result = useCase("nao-eh-email", "abc123")

            assertTrue(result is AuthResult.Error)
            assertNull(store.saved)
            assertNull(repo.lastEmail)
        }

    @Test
    fun `returns Error and does not persist when password invalid`() =
        runTest {
            val repo = FakeAuthRepository()
            val store = FakeSessionStore()
            val useCase = LoginUseCase(repo, store, FixedClock(1_000L), NoOpLogger())

            val result = useCase("ok@ex.com", "12345") // < 6

            assertTrue(result is AuthResult.Error)
            assertNull(store.saved)
        }

    @Test
    fun `delegates to repository when validation passes`() =
        runTest {
            val repo = FakeAuthRepository()
            val store = FakeSessionStore()
            val useCase = LoginUseCase(repo, store, FixedClock(1_000L), NoOpLogger())

            useCase("ok@ex.com", "abc123")

            assertEquals("ok@ex.com", repo.lastEmail)
            assertEquals("abc123", repo.lastPassword)
        }

    @Test
    fun `persists session with clock timestamp on success`() =
        runTest {
            val repo =
                FakeAuthRepository(
                    nextResult =
                        AuthResult.Success(
                            UserModel(id = "u42", email = "ok@ex.com", name = "P", role = UserRole.MOD),
                        ),
                )
            val store = FakeSessionStore()
            val useCase = LoginUseCase(repo, store, FixedClock(now = 9_999L), NoOpLogger())

            useCase("ok@ex.com", "abc123")

            assertEquals(
                SessionUser(uid = "u42", email = "ok@ex.com", role = UserRole.MOD, lastLoginEpochMillis = 9_999L),
                store.saved,
            )
        }

    @Test
    fun `does not persist when repository returns Error`() =
        runTest {
            val repo = FakeAuthRepository(nextResult = AuthResult.Error("falhou"))
            val store = FakeSessionStore()
            val useCase = LoginUseCase(repo, store, FixedClock(1_000L), NoOpLogger())

            val result = useCase("ok@ex.com", "abc123")

            assertTrue(result is AuthResult.Error)
            assertNull(store.saved)
        }
}
