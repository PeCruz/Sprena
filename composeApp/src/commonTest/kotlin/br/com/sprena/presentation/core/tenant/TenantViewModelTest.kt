package br.com.sprena.presentation.core.tenant

import app.cash.turbine.test
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.auth.session.SessionUser
import br.com.sprena.shared.establishment.domain.model.MemberRole
import br.com.sprena.shared.establishment.domain.model.Membership
import br.com.sprena.shared.establishment.domain.repository.ActiveEstablishmentRepository
import br.com.sprena.shared.establishment.domain.repository.MembershipRepository
import br.com.sprena.test.MainDispatcherEnv
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeSession(
    private val user: SessionUser?,
) : SessionStore {
    override suspend fun save(user: SessionUser) = Unit

    override suspend fun load(): SessionUser? = user

    override suspend fun clear() = Unit
}

private class FakeMemberships(
    val flow: Flow<Result<List<Membership>>>,
) : MembershipRepository {
    override fun observeMine(): Flow<Result<List<Membership>>> = flow

    override fun observeMembers(establishmentId: String): Flow<Result<List<Membership>>> =
        flowOf(Result.success(emptyList()))
}

private class FakeActive(
    val flow: Flow<Result<String?>>,
) : ActiveEstablishmentRepository {
    override fun observe(): Flow<Result<String?>> = flow

    override suspend fun set(establishmentId: String?): Result<Unit> = Result.success(Unit)
}

private fun vinculo(
    estId: String,
    role: MemberRole = MemberRole.CLIENT,
) = Membership(establishmentId = estId, uid = "u1", role = role, active = true)

private fun sessao(role: UserRole) = SessionUser("u1", "a@b.com", role, 0L)

class TenantViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private fun vm(
        role: UserRole = UserRole.USER,
        memberships: Flow<Result<List<Membership>>> = flowOf(Result.success(emptyList())),
        active: Flow<Result<String?>> = flowOf(Result.success(null)),
    ) = TenantViewModel(
        memberships = FakeMemberships(memberships),
        activeEstablishment = FakeActive(active),
        sessionStore = FakeSession(sessao(role)),
    )

    @Test
    fun `combina sessao, vinculos e contexto ativo`() =
        runTest {
            val model =
                vm(
                    role = UserRole.USER,
                    memberships = flowOf(Result.success(listOf(vinculo("e1", MemberRole.MOD)))),
                    active = flowOf(Result.success("e1")),
                )

            model.context.test {
                assertNull(awaitItem())
                val ctx = awaitItem()!!
                assertEquals(UserRole.MOD, ctx.effectiveRole)
                assertEquals("e1", ctx.activeMembership?.establishmentId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `falha ao ler vinculos nao vira tela de sem estabelecimento`() =
        runTest {
            val model =
                vm(
                    memberships = flowOf(Result.failure(RuntimeException("offline"))),
                    active = flowOf(Result.success(null)),
                )

            model.context.test {
                // Continua `null` — que a barra trata como carregamento. Emitir um contexto
                // vazio aqui mostraria "contate um Moderador" para quem só está sem rede, e
                // mandaria a pessoa atrás de um problema que não existe.
                assertNull(awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `mantem o ultimo contexto bom quando a leitura falha depois`() =
        runTest {
            val fonte = MutableStateFlow<Result<List<Membership>>>(Result.success(listOf(vinculo("e1"))))
            val model = vm(memberships = fonte, active = flowOf(Result.success("e1")))

            model.context.test {
                assertNull(awaitItem())
                assertEquals(UserRole.CLIENT, awaitItem()!!.effectiveRole)

                fonte.value = Result.failure(RuntimeException("caiu a rede"))

                // A barra não pode piscar para a tela de erro por uma falha transitória.
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ADM sem vinculo tem contexto valido`() =
        runTest {
            val model = vm(role = UserRole.ADM)

            model.context.test {
                assertNull(awaitItem())
                val ctx = awaitItem()!!
                assertEquals(UserRole.ADM, ctx.effectiveRole)
                assertEquals(true, ctx.hasEstablishment)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
