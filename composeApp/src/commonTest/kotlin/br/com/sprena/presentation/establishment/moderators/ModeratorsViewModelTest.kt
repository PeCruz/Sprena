package br.com.sprena.presentation.establishment.moderators

import br.com.sprena.presentation.establishment.FakeEstablishmentRepo
import br.com.sprena.presentation.establishment.FakeMembersRepo
import br.com.sprena.presentation.establishment.establishment
import br.com.sprena.presentation.establishment.membership
import br.com.sprena.shared.establishment.domain.model.MemberRole
import br.com.sprena.shared.establishment.domain.usecase.ObserveEstablishmentMembersUseCase
import br.com.sprena.shared.establishment.domain.usecase.ObserveEstablishmentsUseCase
import br.com.sprena.test.MainDispatcherEnv
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModeratorsViewModelTest {
    private val env = MainDispatcherEnv()

    @BeforeTest fun setUp() = env.install()

    @AfterTest fun tearDown() = env.uninstall()

    private fun vm(
        establishments: FakeEstablishmentRepo,
        members: FakeMembersRepo,
    ) = ModeratorsViewModel(
        observeEstablishments = ObserveEstablishmentsUseCase(establishments),
        observeMembers = ObserveEstablishmentMembersUseCase(members),
    )

    @Test
    fun `seleciona o primeiro estabelecimento sozinho`() =
        runTest {
            val ests = FakeEstablishmentRepo()
            ests.all.value = Result.success(listOf(establishment("e1", "Arena"), establishment("e2")))
            val members = FakeMembersRepo(mapOf("e1" to Result.success(listOf(membership("u1")))))

            val model = vm(ests, members)
            advanceUntilIdle()

            // Abrir a tela sem nada selecionado obrigaria um toque extra para ver qualquer coisa.
            assertEquals("e1", model.state.value.selectedEstablishmentId)
            assertEquals(1, model.state.value.members.size)
        }

    @Test
    fun `trocar de estabelecimento recarrega os membros`() =
        runTest {
            val ests = FakeEstablishmentRepo()
            ests.all.value = Result.success(listOf(establishment("e1"), establishment("e2")))
            val members =
                FakeMembersRepo(
                    mapOf(
                        "e1" to Result.success(listOf(membership("u1", "e1"))),
                        "e2" to Result.success(listOf(membership("u2", "e2"), membership("u3", "e2"))),
                    ),
                )
            val model = vm(ests, members)
            advanceUntilIdle()

            model.handleIntent(ModeratorsIntent.EstablishmentSelected("e2"))
            advanceUntilIdle()

            assertEquals("e2", model.state.value.selectedEstablishmentId)
            assertEquals(2, model.state.value.members.size)
            assertTrue("e2" in members.requestedEstablishments)
        }

    @Test
    fun `mostra todos os papeis, nao so moderadores`() =
        runTest {
            val ests = FakeEstablishmentRepo()
            ests.all.value = Result.success(listOf(establishment("e1")))
            val members =
                FakeMembersRepo(
                    mapOf(
                        "e1" to
                            Result.success(
                                listOf(
                                    membership("u1", role = MemberRole.USER),
                                    membership("u2", role = MemberRole.MOD),
                                    membership("u3", role = MemberRole.CLIENT),
                                ),
                            ),
                    ),
                )

            val model = vm(ests, members)
            advanceUntilIdle()

            // Quem administra precisa enxergar tudo que alcança o estabelecimento — inclusive
            // um vínculo que não deveria estar ali. Ordenado por papel, do mais poderoso.
            assertEquals(
                listOf(MemberRole.MOD, MemberRole.CLIENT, MemberRole.USER),
                model.state.value.members
                    .map { it.role },
            )
        }

    @Test
    fun `sem estabelecimento cadastrado nao tenta ler membros`() =
        runTest {
            val ests = FakeEstablishmentRepo()
            ests.all.value = Result.success(emptyList())
            val members = FakeMembersRepo()

            val model = vm(ests, members)
            advanceUntilIdle()

            assertNull(model.state.value.selectedEstablishmentId)
            assertTrue(members.requestedEstablishments.isEmpty())
            assertTrue(
                model.state.value.establishments
                    .isEmpty(),
            )
        }

    @Test
    fun `falha ao listar estabelecimentos vira erro`() =
        runTest {
            val ests = FakeEstablishmentRepo()
            ests.all.value = Result.failure(RuntimeException("offline"))

            val model = vm(ests, FakeMembersRepo())
            advanceUntilIdle()

            assertNotNull(model.state.value.error)
            assertTrue(
                !model.state.value.error!!
                    .contains("offline"),
            )
        }

    @Test
    fun `falha ao ler membros vira erro sem apagar a lista de estabelecimentos`() =
        runTest {
            val ests = FakeEstablishmentRepo()
            ests.all.value = Result.success(listOf(establishment("e1", "Arena")))
            val members = FakeMembersRepo(mapOf("e1" to Result.failure(RuntimeException("denied"))))

            val model = vm(ests, members)
            advanceUntilIdle()

            assertNotNull(model.state.value.membersError)
            // O seletor precisa continuar utilizável para o ADM tentar outro estabelecimento.
            assertEquals(1, model.state.value.establishments.size)
        }

    @Test
    fun `membro sem nome cai no uid abreviado`() =
        runTest {
            val ests = FakeEstablishmentRepo()
            ests.all.value = Result.success(listOf(establishment("e1")))
            val members =
                FakeMembersRepo(
                    mapOf(
                        "e1" to
                            Result.success(
                                listOf(
                                    membership("abcdefghij123456", displayName = null),
                                    membership("outro", displayName = "Maria"),
                                ),
                            ),
                    ),
                )

            val model = vm(ests, members)
            advanceUntilIdle()

            // Vínculos semeados à mão antes de F1.7.3b não têm displayName. Melhor um
            // identificador curto que um espaço vazio na lista.
            val nomes =
                model.state.value.members
                    .map { it.label }
            assertTrue(nomes.any { it == "Maria" })
            assertTrue(nomes.any { it.startsWith("abcdefgh") && it.length <= 12 }, "foi: $nomes")
        }
}
