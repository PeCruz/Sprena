package br.com.sprena.presentation.establishment

import br.com.sprena.shared.establishment.domain.model.Establishment
import br.com.sprena.shared.establishment.domain.model.MemberLinkResult
import br.com.sprena.shared.establishment.domain.model.MemberRole
import br.com.sprena.shared.establishment.domain.model.Membership
import br.com.sprena.shared.establishment.domain.repository.EstablishmentRepository
import br.com.sprena.shared.establishment.domain.repository.MemberMutationRepository
import br.com.sprena.shared.establishment.domain.repository.MembershipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** Fakes escritos à mão — MockK é JVM-only e proibido em `commonTest` (quebra o KMP). */
class FakeEstablishmentRepo(
    val all: MutableStateFlow<Result<List<Establishment>>> =
        MutableStateFlow(Result.success(emptyList())),
    var byId: Result<Establishment?> = Result.success(null),
    var cnpjTaken: Result<Boolean> = Result.success(false),
    var createResult: Result<String> = Result.success("est_novo"),
    var updateResult: Result<Unit> = Result.success(Unit),
    var setActiveResult: Result<Unit> = Result.success(Unit),
) : EstablishmentRepository {
    var created: Establishment? = null
    var updated: Establishment? = null
    var setActiveCalls: MutableList<Pair<String, Boolean>> = mutableListOf()
    var requestedId: String? = null

    override fun observeAll(): Flow<Result<List<Establishment>>> = all

    override fun observeById(id: String): Flow<Result<Establishment?>> = flowOf(byId)

    override suspend fun getById(id: String): Result<Establishment?> {
        requestedId = id
        return byId
    }

    override suspend fun isCnpjTaken(cnpjDigits: String): Result<Boolean> = cnpjTaken

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
    ): Result<Unit> {
        setActiveCalls += id to active
        return setActiveResult
    }
}

class FakeMembersRepo(
    private val byEstablishment: Map<String, Result<List<Membership>>> = emptyMap(),
) : MembershipRepository {
    var requestedEstablishments: MutableList<String> = mutableListOf()

    override fun observeMine(): Flow<Result<List<Membership>>> = flowOf(Result.success(emptyList()))

    override fun observeMembers(establishmentId: String): Flow<Result<List<Membership>>> {
        requestedEstablishments += establishmentId
        return flowOf(byEstablishment[establishmentId] ?: Result.success(emptyList()))
    }
}

fun establishment(
    id: String,
    name: String = "Bar $id",
    active: Boolean = true,
    cnpj: String = "11222333000181",
) = Establishment(
    id = id,
    name = name,
    cnpj = cnpj,
    phone = "11987654321",
    email = "contato@bar.com.br",
    active = active,
    razaoSocial = "Razao Social LTDA",
)

fun membership(
    uid: String,
    estId: String = "e1",
    role: MemberRole = MemberRole.CLIENT,
    active: Boolean = true,
    displayName: String? = null,
) = Membership(
    establishmentId = estId,
    uid = uid,
    role = role,
    active = active,
    displayName = displayName,
)

/** Mutações do grafo passam por callable; aqui só se registra o que foi pedido. */
class FakeMemberMutationRepo(
    var linkResult: MemberLinkResult = MemberLinkResult.Pending,
    var removeResult: Result<Unit> = Result.success(Unit),
) : MemberMutationRepository {
    var linkCalls: MutableList<Triple<String, String, MemberRole>> = mutableListOf()
    var removed: MutableList<String> = mutableListOf()

    override suspend fun linkByCpf(
        establishmentId: String,
        cpf: String,
        name: String,
        role: MemberRole,
    ): MemberLinkResult {
        linkCalls += Triple(establishmentId, cpf, role)
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
    ): Result<Unit> {
        removed += targetUid
        return removeResult
    }

    override suspend fun leave(establishmentId: String): Result<Unit> = Result.success(Unit)
}
