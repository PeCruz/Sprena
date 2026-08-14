package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.logger.NoOpLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogoutUseCaseTest {
    private class FakeAuthRepo : AuthRepository {
        var signOutCalled = false

        override suspend fun authenticate(
            email: String,
            password: String,
        ) = AuthResult.Error("not used")

        override suspend fun sendPasswordReset(email: String) = Result.success(Unit)

        override suspend fun signOut() {
            signOutCalled = true
        }

        override fun currentUid(): String? = null

        // Só RestoreSessionUseCase consulta o refresh; estes fluxos não.
        override suspend fun refreshToken(): Result<Unit> = Result.success(Unit)
    }

    private class FakeStore(
        var current: SessionUser? = null,
    ) : SessionStore {
        var cleared = false

        override suspend fun save(user: SessionUser) {
            current = user
        }

        override suspend fun load(): SessionUser? = current

        override suspend fun clear() {
            current = null
            cleared = true
        }
    }

    @Test
    fun `signs out and clears session`() =
        runTest {
            val repo = FakeAuthRepo()
            val store = FakeStore()
            val useCase = LogoutUseCase(repo, store, NoOpLogger())

            useCase()

            assertTrue(repo.signOutCalled)
            assertTrue(store.cleared)
            assertNull(store.current)
        }
}
