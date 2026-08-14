package br.com.sprena.shared.privacy.domain.usecase

import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.privacy.domain.model.ConsentRecord
import br.com.sprena.shared.privacy.domain.model.PrivacyPolicy
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TDD — gravação do aceite (F1.5). */
class AcceptConsentUseCaseTest {
    private class FakeRepo(
        var acceptResult: Result<Unit> = Result.success(Unit),
    ) : ConsentRepository {
        var lastUid: String? = null
        var lastVersion: String? = null

        override suspend fun current(uid: String): Result<ConsentRecord?> = Result.success(null)

        override suspend fun accept(
            uid: String,
            policyVersion: String,
        ): Result<Unit> {
            lastUid = uid
            lastVersion = policyVersion
            return acceptResult
        }
    }

    @Test
    fun `grava o aceite com a versao atual da politica`() =
        runTest {
            val repo = FakeRepo()

            val result = AcceptConsentUseCase(repository = repo, logger = NoOpLogger())("uid_1")

            assertTrue(result.isSuccess)
            assertEquals("uid_1", repo.lastUid)
            assertEquals(PrivacyPolicy.VERSION, repo.lastVersion)
        }

    @Test
    fun `propaga falha de gravacao`() =
        runTest {
            val repo = FakeRepo(acceptResult = Result.failure(RuntimeException("offline")))

            val result = AcceptConsentUseCase(repository = repo, logger = NoOpLogger())("uid_1")

            assertTrue(result.isFailure)
        }
}
