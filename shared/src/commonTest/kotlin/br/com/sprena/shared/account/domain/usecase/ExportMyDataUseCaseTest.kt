package br.com.sprena.shared.account.domain.usecase

import br.com.sprena.shared.account.domain.model.DataExport
import br.com.sprena.shared.account.domain.model.UserProfile
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.core.time.Clock
import br.com.sprena.shared.privacy.domain.model.ConsentRecord
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository
import br.com.sprena.shared.sportclient.domain.validation.SportModality
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * TDD — exportação dos próprios dados (F1.6a, LGPD art. 18 V — portabilidade).
 *
 * Duas invariantes que os testes existem para travar:
 *  1. **CPF e telefone vão completos.** Portabilidade com dado mascarado não é
 *     portabilidade — o destinatário é o próprio titular.
 *  2. **Nada de `sport_clients` entra.** São dados de terceiros sob responsabilidade do
 *     operador; exportá-los pela porta de "meus dados" seria vazamento com aparência
 *     de direito.
 */
class ExportMyDataUseCaseTest {
    private val session =
        SessionUser(
            uid = "uid-1",
            email = "pedro@example.com",
            role = UserRole.MOD,
            lastLoginEpochMillis = 1_000L,
        )

    private val profile =
        UserProfile(
            uid = "uid-1",
            email = "pedro@example.com",
            role = UserRole.MOD,
            name = "Pedro",
            apelido = "Pe",
            cpf = "12345678900",
            phone = "11987654321",
            modalities = listOf(SportModality.FUTEVOLEI, SportModality.VOLEI),
        )

    @Test
    fun `sem sessao nao exporta`() =
        runTest {
            val exporter = exporter(profileResult = Result.success(profile), session = null)

            assertTrue(exporter.invoke().isFailure)
        }

    @Test
    fun `falha de leitura do perfil nao gera arquivo pela metade`() =
        runTest {
            val exporter = exporter(profileResult = Result.failure(IllegalStateException("offline")))

            assertTrue(exporter.invoke().isFailure)
        }

    @Test
    fun `exporta CPF e telefone completos — mascarar quebraria a portabilidade`() =
        runTest {
            val export = assertIs<DataExport>(exporter().invoke().getOrNull())

            assertContains(export.json, "12345678900")
            assertContains(export.json, "11987654321")
            assertFalse(export.json.contains("***"))
        }

    @Test
    fun `exporta identidade, papel e modalidades`() =
        runTest {
            val json = assertIs<DataExport>(exporter().invoke().getOrNull()).json

            assertContains(json, "pedro@example.com")
            assertContains(json, "MOD")
            assertContains(json, "FUTEVOLEI")
            assertContains(json, "VOLEI")
        }

    @Test
    fun `exporta o historico de consentimento, nao so a versao vigente`() =
        runTest {
            val json = assertIs<DataExport>(exporter().invoke().getOrNull()).json

            assertContains(json, "2026-08-12")
            assertContains(json, "2026-08-14")
        }

    @Test
    fun `NAO exporta dados de clientes cadastrados — sao de terceiros`() =
        runTest {
            val json = assertIs<DataExport>(exporter().invoke().getOrNull()).json

            // O use case não tem sequer acesso a SportClientRepository; o teste trava a
            // regressão de alguém injetá-lo aqui achando que "meus dados" inclui a
            // carteira de clientes do estabelecimento.
            assertFalse(json.contains("sport_clients"))
            assertFalse(json.contains("clientes", ignoreCase = true) && json.contains("cpf\": \"000"))
        }

    @Test
    fun `carimba o instante da exportacao em ISO-8601`() =
        runTest {
            val json = assertIs<DataExport>(exporter().invoke().getOrNull()).json

            assertContains(json, "2026-08-14T18:22:05Z")
        }

    @Test
    fun `nome do arquivo identifica o conteudo e a data`() =
        runTest {
            val export = assertIs<DataExport>(exporter().invoke().getOrNull())

            assertEquals("sprena-meus-dados-2026-08-14.json", export.fileName)
        }

    @Test
    fun `falha no historico nao derruba a exportacao — o perfil e o essencial`() =
        runTest {
            val exporter =
                exporter(historyResult = Result.failure(IllegalStateException("offline")))

            val export = assertIs<DataExport>(exporter.invoke().getOrNull())
            assertContains(export.json, "12345678900")
        }

    private fun exporter(
        profileResult: Result<UserProfile?> = Result.success(profile),
        historyResult: Result<List<ConsentRecord>> = Result.success(defaultHistory),
        session: SessionUser? = this.session,
    ) = ExportMyDataUseCase(
        profileRepository = FakeProfileRepo(profileResult),
        consentRepository = FakeConsentHistoryRepo(historyResult),
        sessionStore = FakeSessionStore(session),
        clock = FixedClock(EXPORT_INSTANT),
        logger = NoOpLogger(),
    )

    private companion object {
        /** 2026-08-14T18:22:05Z */
        const val EXPORT_INSTANT = 1_786_731_725_000L

        val defaultHistory =
            listOf(
                ConsentRecord("uid-1", "2026-08-12", 1_786_000_000_000L),
                ConsentRecord("uid-1", "2026-08-14", 1_786_700_000_000L),
            )
    }
}

internal class FakeConsentHistoryRepo(
    private val historyResult: Result<List<ConsentRecord>>,
) : ConsentRepository {
    override suspend fun current(uid: String): Result<ConsentRecord?> =
        Result.success(historyResult.getOrNull()?.lastOrNull())

    override suspend fun accept(
        uid: String,
        policyVersion: String,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun history(uid: String): Result<List<ConsentRecord>> = historyResult
}

internal class FixedClock(
    private val instant: Long,
) : Clock {
    override fun nowEpochMillis(): Long = instant
}
