package br.com.sprena.shared.establishment.domain.usecase

import br.com.sprena.shared.core.logger.NoOpLogger
import br.com.sprena.shared.establishment.domain.model.Establishment
import br.com.sprena.shared.establishment.domain.model.EstablishmentSaveResult
import br.com.sprena.shared.establishment.domain.repository.EstablishmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeEstablishmentRepo(
    var cnpjTaken: Result<Boolean> = Result.success(false),
    var createResult: Result<String> = Result.success("est_novo"),
    var updateResult: Result<Unit> = Result.success(Unit),
) : EstablishmentRepository {
    var created: Establishment? = null
    var updated: Establishment? = null
    var cnpjChecked: String? = null

    override fun observeAll(): Flow<Result<List<Establishment>>> = flowOf(Result.success(emptyList()))

    override fun observeById(id: String): Flow<Result<Establishment?>> = flowOf(Result.success(null))

    override suspend fun getById(id: String): Result<Establishment?> = Result.success(null)

    override suspend fun isCnpjTaken(cnpjDigits: String): Result<Boolean> {
        cnpjChecked = cnpjDigits
        return cnpjTaken
    }

    override suspend fun create(establishment: Establishment): Result<String> {
        created = establishment
        return createResult
    }

    override suspend fun update(establishment: Establishment): Result<Unit> {
        updated = establishment
        return updateResult
    }

    override suspend fun setActive(
        id: String,
        active: Boolean,
    ): Result<Unit> = Result.success(Unit)
}

private fun valido(extra: Establishment.() -> Establishment = { this }) =
    Establishment(
        id = "",
        name = "Bar do Ze",
        cnpj = "11.222.333/0001-81",
        phone = "(11) 98765-4321",
        email = "contato@bar.com.br",
        razaoSocial = "Ze Bebidas LTDA",
    ).extra()

class SaveEstablishmentUseCaseTest {
    private fun useCase(repo: EstablishmentRepository) = SaveEstablishmentUseCase(repo, NoOpLogger())

    @Test
    fun `cria e normaliza CNPJ e telefone para so digitos`() =
        runTest {
            val repo = FakeEstablishmentRepo()

            val result = useCase(repo).invoke(valido())

            assertEquals(EstablishmentSaveResult.Saved("est_novo"), result)
            // O que persiste precisa ser a forma normalizada: e ela que a rule valida com
            // `^[0-9]{14}$` e que vira o id em cnpj_index. Guardar o valor pontuado faria
            // dois cadastros do mesmo CNPJ escaparem da unicidade.
            assertEquals("11222333000181", repo.created?.cnpj)
            assertEquals("11987654321", repo.created?.phone)
        }

    @Test
    fun `apara espacos do nome, razao social e email`() =
        runTest {
            val repo = FakeEstablishmentRepo()

            useCase(repo).invoke(
                valido {
                    copy(name = "  Bar do Ze  ", razaoSocial = " Ze Bebidas LTDA ", email = " contato@bar.com.br ")
                },
            )

            assertEquals("Bar do Ze", repo.created?.name)
            assertEquals("Ze Bebidas LTDA", repo.created?.razaoSocial)
            assertEquals("contato@bar.com.br", repo.created?.email)
        }

    @Test
    fun `razao social em branco vira nulo em vez de string vazia`() =
        runTest {
            val repo = FakeEstablishmentRepo()

            useCase(repo).invoke(valido { copy(razaoSocial = "   ") })

            assertNull(repo.created?.razaoSocial)
        }

    @Test
    fun `recusa nome vazio sem chamar o repositorio`() =
        runTest {
            val repo = FakeEstablishmentRepo()

            val result = useCase(repo).invoke(valido { copy(name = "") })

            val invalid = assertIs<EstablishmentSaveResult.Invalid>(result)
            assertTrue(invalid.hasError)
            assertEquals("Nome é obrigatório", invalid.name.errorMessage)
            assertNull(repo.created)
            assertNull(repo.cnpjChecked)
        }

    @Test
    fun `recusa CNPJ com digito verificador errado`() =
        runTest {
            val repo = FakeEstablishmentRepo()

            val result = useCase(repo).invoke(valido { copy(cnpj = "11222333000182") })

            val invalid = assertIs<EstablishmentSaveResult.Invalid>(result)
            assertEquals("CNPJ inválido", invalid.cnpj.errorMessage)
            assertNull(repo.created)
        }

    @Test
    fun `recusa telefone e email malformados de uma vez so`() =
        runTest {
            val repo = FakeEstablishmentRepo()

            val result = useCase(repo).invoke(valido { copy(phone = "119876", email = "contato") })

            val invalid = assertIs<EstablishmentSaveResult.Invalid>(result)
            // Os dois campos voltam marcados no mesmo resultado: obrigar o usuario a
            // descobrir um erro por vez seria uma ida e volta a cada tentativa.
            assertTrue(!invalid.phone.isValid && !invalid.email.isValid)
        }

    @Test
    fun `recusa CNPJ ja cadastrado sem tentar gravar`() =
        runTest {
            val repo = FakeEstablishmentRepo(cnpjTaken = Result.success(true))

            val result = useCase(repo).invoke(valido())

            assertEquals(EstablishmentSaveResult.DuplicateCnpj, result)
            assertEquals("11222333000181", repo.cnpjChecked)
            assertNull(repo.created)
        }

    @Test
    fun `falha do repositorio vira Failed com mensagem para o usuario`() =
        runTest {
            val repo = FakeEstablishmentRepo(createResult = Result.failure(RuntimeException("boom")))

            val result = useCase(repo).invoke(valido())

            val falha = assertIs<EstablishmentSaveResult.Failed>(result)
            assertTrue(falha.message.isNotBlank())
            // A causa tecnica fica no log, nunca na tela.
            assertTrue(!falha.message.contains("boom"))
        }

    @Test
    fun `com id preenchido atualiza e nao consulta o indice de CNPJ`() =
        runTest {
            val repo = FakeEstablishmentRepo()

            val result = useCase(repo).invoke(valido { copy(id = "est_a") })

            assertEquals(EstablishmentSaveResult.Saved("est_a"), result)
            assertEquals("est_a", repo.updated?.id)
            assertNull(repo.created)
            // O CNPJ nao muda em edicao — mover a chave de unicidade exigiria remover a
            // entrada antiga do indice, e `cnpj_index` nega delete.
            assertNull(repo.cnpjChecked)
        }

    @Test
    fun `falha ao consultar o indice nao passa por cima da checagem`() =
        runTest {
            val repo = FakeEstablishmentRepo(cnpjTaken = Result.failure(RuntimeException("offline")))

            val result = useCase(repo).invoke(valido())

            // Fail-closed: sem saber se o CNPJ ja existe, nao grava. Seguir em frente
            // criaria o estabelecimento duplicado justamente quando a rede esta instavel.
            assertIs<EstablishmentSaveResult.Failed>(result)
            assertNull(repo.created)
        }
}
