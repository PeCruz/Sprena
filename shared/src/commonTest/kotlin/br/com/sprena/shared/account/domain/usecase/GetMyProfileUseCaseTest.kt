package br.com.sprena.shared.account.domain.usecase

import br.com.sprena.shared.account.domain.model.ProfilePatch
import br.com.sprena.shared.account.domain.model.ProfileResult
import br.com.sprena.shared.account.domain.model.UserProfile
import br.com.sprena.shared.account.domain.repository.UserProfileRepository
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.sportclient.domain.validation.SportModality
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD — leitura do próprio perfil (F1.6a, LGPD art. 18 II).
 *
 * Invariante central: **nunca existe perfil parcial**. Falha de leitura vira
 * [ProfileResult.Unavailable], não um perfil com campos em branco — a tela afirma
 * "estes são os dados que temos sobre você" e não pode mentir por omissão.
 */
class GetMyProfileUseCaseTest {
    private val session =
        SessionUser(
            uid = "uid-1",
            email = "pedro@example.com",
            role = UserRole.MOD,
            lastLoginEpochMillis = 1_000L,
        )

    private val fullProfile =
        UserProfile(
            uid = "uid-1",
            email = "pedro@example.com",
            role = UserRole.MOD,
            name = "Pedro",
            apelido = "Pe",
            cpf = "12345678900",
            phone = "11987654321",
            modalities = listOf(SportModality.FUTEVOLEI),
        )

    @Test
    fun `sem sessao devolve Unavailable e nao consulta o repositorio`() =
        runTest {
            val repo = FakeProfileRepo(result = Result.success(fullProfile))
            val useCase = GetMyProfileUseCase(repo, FakeSessionStore(null), NoOpLogger())

            val result = useCase()

            assertIs<ProfileResult.Unavailable>(result)
            assertNull(repo.lastRequestedUid)
        }

    @Test
    fun `falha de leitura vira Unavailable, nunca perfil parcial`() =
        runTest {
            val repo = FakeProfileRepo(result = Result.failure(IllegalStateException("offline")))
            val useCase = GetMyProfileUseCase(repo, FakeSessionStore(session), NoOpLogger())

            val result = useCase()

            assertIs<ProfileResult.Unavailable>(result)
            assertTrue(result.message.isNotBlank())
        }

    @Test
    fun `conta sem doc em users vira Unavailable — nao autorizada`() =
        runTest {
            val repo = FakeProfileRepo(result = Result.success(null))
            val useCase = GetMyProfileUseCase(repo, FakeSessionStore(session), NoOpLogger())

            assertIs<ProfileResult.Unavailable>(useCase())
        }

    @Test
    fun `perfil completo vira Loaded e usa o uid da sessao`() =
        runTest {
            val repo = FakeProfileRepo(result = Result.success(fullProfile))
            val useCase = GetMyProfileUseCase(repo, FakeSessionStore(session), NoOpLogger())

            val result = useCase()

            assertIs<ProfileResult.Loaded>(result)
            assertEquals(fullProfile, result.profile)
            assertEquals("uid-1", repo.lastRequestedUid)
        }

    @Test
    fun `sidecar ausente vira Loaded com campos autodeclarados nulos`() =
        runTest {
            val semSidecar =
                fullProfile.copy(apelido = null, cpf = null, phone = null, modalities = emptyList())
            val repo = FakeProfileRepo(result = Result.success(semSidecar))
            val useCase = GetMyProfileUseCase(repo, FakeSessionStore(session), NoOpLogger())

            val result = useCase()

            assertIs<ProfileResult.Loaded>(result)
            assertNull(result.profile.cpf)
            assertTrue(result.profile.modalities.isEmpty())
            // A identidade operacional continua vindo de users/{uid}.
            assertEquals(UserRole.MOD, result.profile.role)
        }
}

internal class FakeProfileRepo(
    private val result: Result<UserProfile?>,
    private val saveResult: Result<Unit> = Result.success(Unit),
) : UserProfileRepository {
    var lastRequestedUid: String? = null
        private set
    var lastSavedUid: String? = null
        private set
    var lastSavedPatch: ProfilePatch? = null
        private set

    override suspend fun current(uid: String): Result<UserProfile?> {
        lastRequestedUid = uid
        return result
    }

    override suspend fun save(
        uid: String,
        patch: ProfilePatch,
    ): Result<Unit> {
        lastSavedUid = uid
        lastSavedPatch = patch
        return saveResult
    }
}

internal class FakeSessionStore(
    private var current: SessionUser?,
) : SessionStore {
    var cleared = false
        private set

    override suspend fun save(user: SessionUser) {
        current = user
    }

    override suspend fun load(): SessionUser? = current

    override suspend fun clear() {
        cleared = true
        current = null
    }
}
