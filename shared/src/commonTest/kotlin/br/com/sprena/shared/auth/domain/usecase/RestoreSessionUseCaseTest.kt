package br.com.sprena.shared.auth.domain.usecase

import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.RestoreResult
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.auth.session.SessionValidator
import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.core.time.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestoreSessionUseCaseTest {
    private class FakeRepo(var uid: String? = null) : AuthRepository {
        var signOutCalled = false
        override suspend fun authenticate(email: String, password: String) =
            AuthResult.Error("not used")
        override suspend fun sendPasswordReset(email: String) = Result.success(Unit)
        override suspend fun signOut() { signOutCalled = true }
        override fun currentUid(): String? = uid
    }

    private class FakeStore(var current: SessionUser? = null) : SessionStore {
        var cleared = false
        override suspend fun save(user: SessionUser) { current = user }
        override suspend fun load(): SessionUser? = current
        override suspend fun clear() { current = null; cleared = true }
    }

    private class FixedClock(private val now: Long) : Clock {
        override fun nowEpochMillis(): Long = now
    }

    @Test
    fun `returns NotAuthenticated when store is empty`() = runTest {
        val repo = FakeRepo()
        val store = FakeStore(current = null)
        val useCase = RestoreSessionUseCase(repo, store, FixedClock(1_000L), NoOpLogger())

        val result = useCase()

        assertEquals(RestoreResult.NotAuthenticated, result)
    }

    @Test
    fun `returns NotAuthenticated and clears when session is expired`() = runTest {
        val last = 1_000L
        val now = last + SessionValidator.DEFAULT_TTL_MILLIS + 1L
        val repo = FakeRepo(uid = "u1")
        val store =
            FakeStore(
                current = SessionUser("u1", "a@b.com", UserRole.ADM, last),
            )
        val useCase = RestoreSessionUseCase(repo, store, FixedClock(now), NoOpLogger())

        val result = useCase()

        assertEquals(RestoreResult.NotAuthenticated, result)
        assertTrue(repo.signOutCalled)
        assertTrue(store.cleared)
        assertNull(store.current)
    }

    @Test
    fun `returns NotAuthenticated and clears when uid mismatch`() = runTest {
        val now = 1_000L
        val repo = FakeRepo(uid = "outro_uid")
        val store =
            FakeStore(
                current = SessionUser("u1", "a@b.com", UserRole.ADM, now - 1000L),
            )
        val useCase = RestoreSessionUseCase(repo, store, FixedClock(now), NoOpLogger())

        val result = useCase()

        assertEquals(RestoreResult.NotAuthenticated, result)
        assertTrue(store.cleared)
    }

    @Test
    fun `returns Authenticated when session valid and uid matches`() = runTest {
        val now = 1_000_000L
        val last = now - 5_000L
        val repo = FakeRepo(uid = "u1")
        val user = SessionUser("u1", "a@b.com", UserRole.MOD, last)
        val store = FakeStore(current = user)
        val useCase = RestoreSessionUseCase(repo, store, FixedClock(now), NoOpLogger())

        val result = useCase()

        assertEquals(RestoreResult.Authenticated(user), result)
        assertEquals(false, store.cleared)
        assertEquals(false, repo.signOutCalled)
    }

    @Test
    fun `returns NotAuthenticated and clears when currentUid is null (firebase signed out)`() = runTest {
        val now = 1_000_000L
        val last = now - 5_000L
        val repo = FakeRepo(uid = null)
        val store = FakeStore(current = SessionUser("u1", "a@b.com", UserRole.ADM, last))
        val useCase = RestoreSessionUseCase(repo, store, FixedClock(now), NoOpLogger())

        val result = useCase()

        assertEquals(RestoreResult.NotAuthenticated, result)
        assertTrue(store.cleared)
    }
}
