package br.com.sprena.shared.privacy.domain.usecase

import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.privacy.domain.model.ConsentRecord
import br.com.sprena.shared.privacy.domain.model.ConsentStatus
import br.com.sprena.shared.privacy.domain.model.PrivacyPolicy
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD — decisão do gate de consentimento (F1.5).
 *
 * O gate é fail-closed: falha de leitura vira `Unavailable`, nunca `Granted`.
 */
class CheckConsentUseCaseTest {
    private class FakeRepo(
        var currentResult: Result<ConsentRecord?> = Result.success(null),
    ) : ConsentRepository {
        override suspend fun current(uid: String): Result<ConsentRecord?> = currentResult

        override suspend fun accept(
            uid: String,
            policyVersion: String,
        ): Result<Unit> = Result.success(Unit)

        // Só a exportação (F1.6a) usa o histórico; o gate de consentimento não.
        override suspend fun history(uid: String): Result<List<ConsentRecord>> = Result.success(emptyList())
    }

    private fun useCase(repo: FakeRepo) = CheckConsentUseCase(repository = repo, logger = NoOpLogger())

    @Test
    fun `sem registro de aceite exige consentimento por ausencia`() =
        runTest {
            val repo = FakeRepo(currentResult = Result.success(null))

            val status = useCase(repo)("uid_1")

            assertEquals(ConsentStatus.Required(ConsentStatus.Reason.MISSING), status)
        }

    @Test
    fun `aceite de versao antiga exige novo consentimento`() =
        runTest {
            val repo =
                FakeRepo(
                    currentResult =
                        Result.success(
                            ConsentRecord(uid = "uid_1", policyVersion = "2020-01-01", acceptedAtEpochMillis = 1L),
                        ),
                )

            val status = useCase(repo)("uid_1")

            assertEquals(ConsentStatus.Required(ConsentStatus.Reason.OUTDATED), status)
        }

    @Test
    fun `aceite da versao atual libera o acesso`() =
        runTest {
            val repo =
                FakeRepo(
                    currentResult =
                        Result.success(
                            ConsentRecord(
                                uid = "uid_1",
                                policyVersion = PrivacyPolicy.VERSION,
                                acceptedAtEpochMillis = 1L,
                            ),
                        ),
                )

            val status = useCase(repo)("uid_1")

            assertEquals(ConsentStatus.Granted, status)
        }

    @Test
    fun `falha de leitura nao libera acesso — vira Unavailable`() =
        runTest {
            val repo = FakeRepo(currentResult = Result.failure(RuntimeException("offline")))

            val status = useCase(repo)("uid_1")

            assertTrue(status is ConsentStatus.Unavailable)
            assertTrue(status.message.isNotBlank())
        }
}
