package br.com.sprena.shared.establishment.domain.usecase

import app.cash.turbine.test
import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.establishment.domain.model.Establishment
import br.com.sprena.shared.establishment.domain.model.MemberRole
import br.com.sprena.shared.establishment.domain.model.Membership
import br.com.sprena.shared.establishment.domain.repository.EstablishmentRepository
import br.com.sprena.shared.establishment.domain.repository.MembershipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeMembershipRepo(
    private val mine: Result<List<Membership>>,
) : MembershipRepository {
    override fun observeMine(): Flow<Result<List<Membership>>> = flowOf(mine)

    override fun observeMembers(establishmentId: String): Flow<Result<List<Membership>>> =
        flowOf(Result.success(emptyList()))
}

/** Só o que este use case consome do repositório de estabelecimentos: leitura por id. */
private class FakeCatalog(
    private val catalog: Map<String, Establishment?>,
    private val failures: Set<String> = emptySet(),
) : EstablishmentRepository {
    override suspend fun getById(id: String): Result<Establishment?> =
        if (id in failures) Result.failure(RuntimeException("offline")) else Result.success(catalog[id])

    override fun observeAll(): Flow<Result<List<Establishment>>> = flowOf(Result.success(emptyList()))

    override fun observeById(id: String): Flow<Result<Establishment?>> = flowOf(Result.success(catalog[id]))

    override suspend fun isCnpjTaken(cnpjDigits: String): Result<Boolean> = Result.success(false)

    override suspend fun create(establishment: Establishment): Result<String> = Result.success(establishment.id)

    override suspend fun update(establishment: Establishment): Result<Unit> = Result.success(Unit)

    override suspend fun setActive(
        id: String,
        active: Boolean,
    ): Result<Unit> = Result.success(Unit)
}

private fun est(
    id: String,
    name: String,
    active: Boolean = true,
) = Establishment(
    id = id,
    name = name,
    cnpj = "11222333000181",
    phone = "11987654321",
    email = "c@e.com",
    active = active,
)

private fun vinculo(
    estId: String,
    role: MemberRole = MemberRole.CLIENT,
    active: Boolean = true,
) = Membership(establishmentId = estId, uid = "uid_1", role = role, active = active)

class ObserveMyEstablishmentsUseCaseTest {
    private fun useCase(
        memberships: Result<List<Membership>>,
        catalog: Map<String, Establishment?>,
        failures: Set<String> = emptySet(),
    ) = ObserveMyEstablishmentsUseCase(
        memberships = FakeMembershipRepo(memberships),
        establishments = FakeCatalog(catalog, failures),
        logger = NoOpLogger(),
    )

    @Test
    fun `junta o vinculo com o estabelecimento e devolve o papel de cada um`() =
        runTest {
            val uc =
                useCase(
                    memberships =
                        Result.success(
                            listOf(vinculo("e2", MemberRole.MOD), vinculo("e1", MemberRole.CLIENT)),
                        ),
                    catalog = mapOf("e1" to est("e1", "Arena Um"), "e2" to est("e2", "Bar Dois")),
                )

            uc().test {
                val lista = awaitItem().getOrThrow()
                assertEquals(2, lista.size)
                // Ordenado por nome: a ordem em que os vinculos chegam do collection group
                // nao tem significado nenhum para quem le a lista.
                assertEquals(listOf("Arena Um", "Bar Dois"), lista.map { it.establishment.name })
                assertEquals(MemberRole.CLIENT, lista.first().role)
                assertEquals(MemberRole.MOD, lista.last().role)
                awaitComplete()
            }
        }

    @Test
    fun `ignora vinculo desligado`() =
        runTest {
            val uc =
                useCase(
                    memberships = Result.success(listOf(vinculo("e1"), vinculo("e2", active = false))),
                    catalog = mapOf("e1" to est("e1", "Arena"), "e2" to est("e2", "Bar")),
                )

            uc().test {
                assertEquals(listOf("e1"), awaitItem().getOrThrow().map { it.establishment.id })
                awaitComplete()
            }
        }

    @Test
    fun `ignora estabelecimento desativado`() =
        runTest {
            val uc =
                useCase(
                    memberships = Result.success(listOf(vinculo("e1"), vinculo("e2"))),
                    catalog = mapOf("e1" to est("e1", "Arena"), "e2" to est("e2", "Bar", active = false)),
                )

            uc().test {
                assertEquals(listOf("e1"), awaitItem().getOrThrow().map { it.establishment.id })
                awaitComplete()
            }
        }

    @Test
    fun `ignora vinculo cujo estabelecimento nao existe mais`() =
        runTest {
            val uc =
                useCase(
                    memberships = Result.success(listOf(vinculo("e1"), vinculo("fantasma"))),
                    catalog = mapOf("e1" to est("e1", "Arena"), "fantasma" to null),
                )

            uc().test {
                // Um vinculo orfao nao pode derrubar a lista inteira: o usuario ficaria
                // sem acesso aos estabelecimentos validos por causa de um resto de dado.
                assertEquals(listOf("e1"), awaitItem().getOrThrow().map { it.establishment.id })
                awaitComplete()
            }
        }

    @Test
    fun `falha ao ler um estabelecimento nao derruba os demais`() =
        runTest {
            val uc =
                useCase(
                    memberships = Result.success(listOf(vinculo("e1"), vinculo("e2"))),
                    catalog = mapOf("e1" to est("e1", "Arena"), "e2" to est("e2", "Bar")),
                    failures = setOf("e2"),
                )

            uc().test {
                assertEquals(listOf("e1"), awaitItem().getOrThrow().map { it.establishment.id })
                awaitComplete()
            }
        }

    @Test
    fun `propaga falha na leitura dos vinculos`() =
        runTest {
            val uc =
                useCase(
                    memberships = Result.failure(RuntimeException("sem indice")),
                    catalog = emptyMap(),
                )

            uc().test {
                // Aqui a falha e total: sem os vinculos nao da para saber se a lista esta
                // vazia ou se a leitura quebrou, e mostrar "nenhum estabelecimento" seria
                // mentira que leva o usuario a procurar um ADM sem motivo.
                assertTrue(awaitItem().isFailure)
                awaitComplete()
            }
        }
}
