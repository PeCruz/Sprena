package br.com.sprena.shared.establishment.domain.usecase

import br.com.sprena.shared.establishment.domain.model.MemberLinkResult
import br.com.sprena.shared.establishment.domain.model.MemberRole
import br.com.sprena.shared.establishment.domain.repository.MemberMutationRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeMutationRepo(
    var linkResult: MemberLinkResult = MemberLinkResult.Pending,
) : MemberMutationRepository {
    var lastCpf: String? = null
    var lastName: String? = null
    var lastRole: MemberRole? = null
    var calls = 0

    override suspend fun linkByCpf(
        establishmentId: String,
        cpf: String,
        name: String,
        role: MemberRole,
    ): MemberLinkResult {
        calls++
        lastCpf = cpf
        lastName = name
        lastRole = role
        return linkResult
    }

    override suspend fun setRole(
        establishmentId: String,
        targetUid: String,
        role: MemberRole,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun remove(
        establishmentId: String,
        targetUid: String,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun leave(establishmentId: String): Result<Unit> = Result.success(Unit)
}

class LinkMemberByCpfUseCaseTest {
    private val validCpf = "111.444.777-35"

    @Test
    fun `encaminha o CPF como digitado e o papel escolhido`() =
        runTest {
            val repo = FakeMutationRepo()

            val result = LinkMemberByCpfUseCase(repo)("e1", validCpf, "Maria", MemberRole.MOD)

            assertEquals(MemberLinkResult.Pending, result)
            // Normalizar aqui não adiantaria: o servidor normaliza de novo, porque não confia
            // no cliente. Mandar como veio evita duas regras de normalização divergindo.
            assertEquals(validCpf, repo.lastCpf)
            assertEquals(MemberRole.MOD, repo.lastRole)
        }

    @Test
    fun `recusa CPF com digito verificador errado sem chamar o servidor`() =
        runTest {
            val repo = FakeMutationRepo()

            val result = LinkMemberByCpfUseCase(repo)("e1", "111.444.777-00", "Maria", MemberRole.USER)

            // Conveniência de UI, não segurança — o servidor valida de novo. O ganho é não
            // gastar rede nem rate limit com um número que nunca passaria.
            assertIs<MemberLinkResult.Invalid>(result)
            assertEquals(0, repo.calls)
        }

    @Test
    fun `recusa nome vazio`() =
        runTest {
            val repo = FakeMutationRepo()

            val result = LinkMemberByCpfUseCase(repo)("e1", validCpf, "   ", MemberRole.USER)

            // Sem nome o vínculo nasceria sem displayName, e a lista de membros mostraria um
            // identificador opaco — o problema que a denormalização daquele campo resolve.
            assertIs<MemberLinkResult.Invalid>(result)
            assertEquals(0, repo.calls)
            assertNull(repo.lastName)
        }

    @Test
    fun `apara e limita o nome`() =
        runTest {
            val repo = FakeMutationRepo()

            LinkMemberByCpfUseCase(repo)("e1", validCpf, "  ${"a".repeat(80)}  ", MemberRole.USER)

            assertEquals(60, repo.lastName?.length)
        }

    @Test
    fun `propaga os desfechos do servidor sem reinterpretar`() =
        runTest {
            for (
            esperado in
            listOf(
                MemberLinkResult.Linked,
                MemberLinkResult.AlreadyLinked,
                MemberLinkResult.Denied("sem permissão"),
                MemberLinkResult.Failed("caiu"),
            )
            ) {
                val repo = FakeMutationRepo(linkResult = esperado)

                val result = LinkMemberByCpfUseCase(repo)("e1", validCpf, "Maria", MemberRole.USER)

                // `Linked` e `Pending` são sucessos diferentes e a tela precisa dos dois:
                // no primeiro a pessoa já tem acesso, no segundo precisa entrar no app.
                assertEquals(esperado, result)
            }
        }

    @Test
    fun `CPF vazio e recusado como invalido`() =
        runTest {
            val repo = FakeMutationRepo()

            val result = LinkMemberByCpfUseCase(repo)("e1", "", "Maria", MemberRole.USER)

            assertIs<MemberLinkResult.Invalid>(result)
            assertTrue(result.message.isNotBlank())
        }
}
