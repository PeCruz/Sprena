package br.com.sprena.shared.account.domain.usecase

import br.com.sprena.shared.account.domain.model.AccountDeletionResult
import br.com.sprena.shared.account.domain.repository.AccountDeletionRepository
import br.com.sprena.shared.auth.domain.model.AuthResult
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.logger.NoOpLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * TDD — exclusão da própria conta (F1.6a, LGPD art. 18 VI).
 *
 * A invariante mais importante está no teste de ordem: o callable roda **antes** do
 * `signOut()`. Invertido, o ID token morreria antes de o backend poder validá-lo, e a
 * exclusão falharia sempre — com a sessão já destruída e o titular sem caminho de volta.
 */
class DeleteMyAccountUseCaseTest {
    private val session =
        SessionUser(
            uid = "uid-1",
            email = "pedro@example.com",
            role = UserRole.MOD,
            lastLoginEpochMillis = 1_000L,
        )

    @Test
    fun `sucesso limpa a sessao e desloga`() =
        runTest {
            val auth = FakeAuthRepository()
            val store = FakeSessionStore(session)
            val useCase = useCase(AccountDeletionResult.Deleted, auth, store)

            assertIs<AccountDeletionResult.Deleted>(useCase())
            assertTrue(auth.signedOut)
            assertTrue(store.cleared)
        }

    @Test
    fun `falha mantem a sessao intacta para o titular tentar de novo`() =
        runTest {
            val auth = FakeAuthRepository()
            val store = FakeSessionStore(session)
            val useCase = useCase(AccountDeletionResult.Failed("offline"), auth, store)

            assertIs<AccountDeletionResult.Failed>(useCase())
            assertFalse(auth.signedOut)
            assertFalse(store.cleared)
        }

    @Test
    fun `sessao expirada tambem limpa a sessao — o token ja nao vale`() =
        runTest {
            val auth = FakeAuthRepository()
            val store = FakeSessionStore(session)
            val useCase = useCase(AccountDeletionResult.SessionExpired, auth, store)

            assertIs<AccountDeletionResult.SessionExpired>(useCase())
            assertTrue(store.cleared)
        }

    @Test
    fun `NAO chama signOut antes do callable — inverter derrubaria o token`() =
        runTest {
            val auth = FakeAuthRepository()
            val store = FakeSessionStore(session)
            val repo = FakeDeletionRepo(AccountDeletionResult.Deleted, auth)

            DeleteMyAccountUseCase(repo, auth, store, NoOpLogger()).invoke()

            assertEquals(false, repo.signedOutWhenCalled)
        }

    private fun useCase(
        result: AccountDeletionResult,
        auth: FakeAuthRepository,
        store: FakeSessionStore,
    ) = DeleteMyAccountUseCase(FakeDeletionRepo(result, auth), auth, store, NoOpLogger())
}

internal class FakeDeletionRepo(
    private val result: AccountDeletionResult,
    private val auth: FakeAuthRepository,
) : AccountDeletionRepository {
    /** Fotografa o estado do Auth no instante da chamada — é assim que a ordem é provada. */
    var signedOutWhenCalled: Boolean? = null
        private set

    override suspend fun deleteMyAccount(): AccountDeletionResult {
        signedOutWhenCalled = auth.signedOut
        return result
    }
}

internal class FakeAuthRepository : AuthRepository {
    var signedOut = false
        private set

    override suspend fun authenticate(
        email: String,
        password: String,
    ): AuthResult = AuthResult.Error("não usado")

    override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun signOut() {
        signedOut = true
    }

    override fun currentUid(): String? = if (signedOut) null else "uid-1"

    override suspend fun refreshToken(): Result<Unit> = Result.success(Unit)
}
