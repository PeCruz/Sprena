package br.com.sprena.shared.establishment.domain.usecase

import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.establishment.domain.model.MemberRole
import br.com.sprena.shared.establishment.domain.model.Membership
import br.com.sprena.shared.establishment.domain.repository.ActiveEstablishmentRepository
import br.com.sprena.shared.establishment.domain.repository.MembershipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MembershipsStub(
    private val mine: Result<List<Membership>>,
) : MembershipRepository {
    override fun observeMine(): Flow<Result<List<Membership>>> = flowOf(mine)

    override fun observeMembers(establishmentId: String): Flow<Result<List<Membership>>> =
        flowOf(Result.success(emptyList()))
}

private class FakeActiveRepo(
    var setResult: Result<Unit> = Result.success(Unit),
) : ActiveEstablishmentRepository {
    var written: String? = null
    var writeCount = 0

    override fun observe(): Flow<Result<String?>> = flowOf(Result.success(written))

    override suspend fun set(establishmentId: String?): Result<Unit> {
        written = establishmentId
        writeCount++
        return setResult
    }
}

private fun vinculo(
    estId: String,
    active: Boolean = true,
) = Membership(establishmentId = estId, uid = "uid_1", role = MemberRole.CLIENT, active = active)

class SelectActiveEstablishmentUseCaseTest {
    private fun useCase(
        memberships: Result<List<Membership>>,
        active: FakeActiveRepo,
    ) = SelectActiveEstablishmentUseCase(
        memberships = MembershipsStub(memberships),
        activeEstablishment = active,
        logger = NoOpLogger(),
    )

    @Test
    fun `grava o estabelecimento quando ha vinculo ativo`() =
        runTest {
            val active = FakeActiveRepo()

            val result = useCase(Result.success(listOf(vinculo("e1"))), active).invoke("e1")

            assertTrue(result.isSuccess)
            assertEquals("e1", active.written)
        }

    @Test
    fun `recusa estabelecimento onde o usuario nao tem vinculo`() =
        runTest {
            val active = FakeActiveRepo()

            val result = useCase(Result.success(listOf(vinculo("e1"))), active).invoke("e2")

            // Não é barreira de segurança — as rules já negam tudo em `e2`, e escrever o
            // id alheio em user_settings é inofensivo. É para o app não entrar num estado
            // em que toda aba mostra "sem permissão" sem explicar o porquê.
            assertTrue(result.isFailure)
            assertEquals(0, active.writeCount)
        }

    @Test
    fun `recusa estabelecimento cujo vinculo foi desligado`() =
        runTest {
            val active = FakeActiveRepo()

            val result = useCase(Result.success(listOf(vinculo("e1", active = false))), active).invoke("e1")

            assertTrue(result.isFailure)
            assertEquals(0, active.writeCount)
        }

    @Test
    fun `permite limpar o contexto com null sem consultar vinculos`() =
        runTest {
            val active = FakeActiveRepo()

            val result = useCase(Result.failure(RuntimeException("offline")), active).invoke(null)

            assertTrue(result.isSuccess)
            assertNull(active.written)
            assertEquals(1, active.writeCount)
        }

    @Test
    fun `falha ao ler vinculos impede a troca de contexto`() =
        runTest {
            val active = FakeActiveRepo()

            val result = useCase(Result.failure(RuntimeException("offline")), active).invoke("e1")

            assertTrue(result.isFailure)
            assertEquals(0, active.writeCount)
        }

    @Test
    fun `propaga falha de gravacao`() =
        runTest {
            val active = FakeActiveRepo(setResult = Result.failure(RuntimeException("boom")))

            val result = useCase(Result.success(listOf(vinculo("e1"))), active).invoke("e1")

            assertTrue(result.isFailure)
        }
}
