package br.com.sprena.shared.establishment.domain.model

import br.com.sprena.shared.auth.domain.model.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun vinculo(
    estId: String,
    role: MemberRole = MemberRole.CLIENT,
    active: Boolean = true,
) = Membership(establishmentId = estId, uid = "u1", role = role, active = active)

class TenantContextTest {
    @Test
    fun `ADM tem papel efetivo ADM e nao precisa de vinculo`() {
        val ctx = TenantContext(UserRole.ADM, memberships = emptyList(), activeEstablishmentId = null)

        assertEquals(UserRole.ADM, ctx.effectiveRole)
        // Sem isto, o unico ADM do sistema cairia na tela de "procure um ADM" antes de
        // conseguir criar o primeiro estabelecimento — um impasse de inicializacao.
        assertTrue(ctx.hasEstablishment)
    }

    @Test
    fun `ADM continua ADM mesmo vinculado como CLIENT em algum lugar`() {
        val ctx =
            TenantContext(
                globalRole = UserRole.ADM,
                memberships = listOf(vinculo("e1", MemberRole.CLIENT)),
                activeEstablishmentId = "e1",
            )

        // O papel global vence o do tenant. O contrario permitiria rebaixar um ADM
        // escrevendo um member doc — e `members` existe justamente para nao decidir isso.
        assertEquals(UserRole.ADM, ctx.effectiveRole)
    }

    @Test
    fun `papel efetivo vem do vinculo no estabelecimento ativo`() {
        val ctx =
            TenantContext(
                globalRole = UserRole.USER,
                memberships = listOf(vinculo("e1", MemberRole.MOD), vinculo("e2", MemberRole.CLIENT)),
                activeEstablishmentId = "e2",
            )

        assertEquals(UserRole.CLIENT, ctx.effectiveRole)
        assertEquals("e2", ctx.activeMembership?.establishmentId)
    }

    @Test
    fun `com um unico vinculo dispensa escolha explicita`() {
        val ctx =
            TenantContext(
                globalRole = UserRole.USER,
                memberships = listOf(vinculo("e1", MemberRole.MOD)),
                activeEstablishmentId = null,
            )

        // Quem tem um lugar so nunca deveria precisar abrir um seletor de um item.
        assertEquals(UserRole.MOD, ctx.effectiveRole)
        assertEquals("e1", ctx.activeMembership?.establishmentId)
    }

    @Test
    fun `com varios vinculos e nenhum escolhido nao adivinha`() {
        val ctx =
            TenantContext(
                globalRole = UserRole.USER,
                memberships = listOf(vinculo("e1", MemberRole.MOD), vinculo("e2", MemberRole.CLIENT)),
                activeEstablishmentId = null,
            )

        // Escolher por conta propria daria ao usuario poderes de MOD num lugar que ele nao
        // pediu para abrir. Sem escolha, o papel efetivo cai para o mais restrito.
        assertNull(ctx.activeMembership)
        assertEquals(UserRole.USER, ctx.effectiveRole)
        assertTrue(ctx.hasEstablishment)
    }

    @Test
    fun `id ativo que nao corresponde a vinculo algum e ignorado`() {
        val ctx =
            TenantContext(
                globalRole = UserRole.USER,
                memberships = listOf(vinculo("e1", MemberRole.MOD)),
                activeEstablishmentId = "estabelecimento_removido",
            )

        // `user_settings` e escrito pelo cliente e nenhuma rule o le, entao o id guardado
        // ali pode apontar para qualquer coisa — inclusive para um vinculo ja desligado.
        // Cai no unico vinculo valido em vez de conceder o papel do id invalido.
        assertEquals("e1", ctx.activeMembership?.establishmentId)
        assertEquals(UserRole.MOD, ctx.effectiveRole)
    }

    @Test
    fun `vinculo desligado nao conta`() {
        val ctx =
            TenantContext(
                globalRole = UserRole.USER,
                memberships = listOf(vinculo("e1", MemberRole.MOD, active = false)),
                activeEstablishmentId = "e1",
            )

        assertNull(ctx.activeMembership)
        assertEquals(UserRole.USER, ctx.effectiveRole)
        assertFalse(ctx.hasEstablishment)
    }

    @Test
    fun `sem vinculo algum e sem ser ADM nao tem estabelecimento`() {
        val ctx = TenantContext(UserRole.USER, memberships = emptyList(), activeEstablishmentId = null)

        assertFalse(ctx.hasEstablishment)
        assertEquals(UserRole.USER, ctx.effectiveRole)
    }

    @Test
    fun `papel legado global nao concede acesso sem vinculo`() {
        // Contas MOD e CLIENT provisionadas a mao antes de F1.7 continuam existindo. O papel
        // global delas nao vale nada sozinho: sem vinculo, o efetivo cai para USER, e a
        // pessoa vai para a tela de "sem estabelecimento" ate um ADM vincula-la.
        for (legado in listOf(UserRole.MOD, UserRole.CLIENT)) {
            val ctx = TenantContext(legado, memberships = emptyList(), activeEstablishmentId = null)
            assertEquals(UserRole.USER, ctx.effectiveRole, "papel legado $legado nao pode valer sozinho")
            assertFalse(ctx.hasEstablishment)
        }
    }
}
