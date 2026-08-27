package br.com.sprena.presentation.core.navigation

import br.com.sprena.shared.auth.domain.model.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabMatrixTest {
    @Test
    fun `ADM gere pela Config e nao ve a lista de clientes esportivos`() {
        val tabs = tabsFor(UserRole.ADM, hasEstablishment = true)

        assertEquals(
            listOf(BottomTab.EVENTOS, BottomTab.BAR, BottomTab.FINANCIAL, BottomTab.CONFIG),
            tabs,
        )
        // A Home é o cadastro de clientes de UM estabelecimento; o ADM opera acima disso.
        assertFalse(BottomTab.HOME in tabs)
    }

    @Test
    fun `MOD tem todas as abas operacionais e o Perfil`() {
        assertEquals(
            listOf(
                BottomTab.HOME,
                BottomTab.EVENTOS,
                BottomTab.BAR,
                BottomTab.FINANCIAL,
                BottomTab.PROFILE,
            ),
            tabsFor(UserRole.MOD, hasEstablishment = true),
        )
    }

    @Test
    fun `CLIENT nao tem Financeiro`() {
        val tabs = tabsFor(UserRole.CLIENT, hasEstablishment = true)

        assertEquals(
            listOf(BottomTab.HOME, BottomTab.EVENTOS, BottomTab.BAR, BottomTab.PROFILE),
            tabs,
        )
        // Ocultar é decisão de UI; quem nega de verdade é a rule de `transactions`.
        assertFalse(BottomTab.FINANCIAL in tabs)
    }

    @Test
    fun `USER ve apenas comandas, eventos e o proprio perfil`() {
        val tabs = tabsFor(UserRole.USER, hasEstablishment = true)

        assertEquals(listOf(BottomTab.BAR, BottomTab.EVENTOS, BottomTab.PROFILE), tabs)
        // Comandas é a primeira aba — é a "home" do frequentador.
        assertEquals(BottomTab.BAR, tabs.first())
        assertFalse(BottomTab.HOME in tabs)
        assertFalse(BottomTab.FINANCIAL in tabs)
    }

    @Test
    fun `sem estabelecimento vinculado nao ha aba nenhuma`() {
        for (role in listOf(UserRole.MOD, UserRole.CLIENT, UserRole.USER)) {
            assertTrue(
                tabsFor(role, hasEstablishment = false).isEmpty(),
                "$role sem estabelecimento deveria cair na tela de erro",
            )
        }
    }

    @Test
    fun `ADM nunca fica sem abas`() {
        // Ele precisa alcançar a Config para criar o primeiro estabelecimento. Barrá-lo aqui
        // seria um impasse: o único ADM veria "contate um ADM".
        assertEquals(
            tabsFor(UserRole.ADM, hasEstablishment = true),
            tabsFor(UserRole.ADM, hasEstablishment = false),
        )
    }

    @Test
    fun `todo papel alcanca os direitos do titular`() {
        // LGPD art. 18: exportar dados e excluir a conta precisam estar ao alcance de todos.
        // O ADM chega lá pela Config (que hospeda "Meus dados"), os demais pelo Perfil.
        // Se algum papel ficar sem as duas portas, a tela de exclusão vira inalcançável —
        // e é justamente o botão que a review da Play Store testa.
        for (role in UserRole.entries) {
            val tabs = tabsFor(role, hasEstablishment = true)
            assertTrue(
                BottomTab.PROFILE in tabs || BottomTab.CONFIG in tabs,
                "$role ficou sem acesso aos próprios dados",
            )
        }
    }

    @Test
    fun `a primeira aba e sempre um destino valido`() {
        // `BottomNavState.current` passa a ser derivado da lista; se ela mudar de ordem, o
        // destino inicial muda junto, e nenhum papel pode começar numa aba que não tem.
        for (role in UserRole.entries) {
            val tabs = tabsFor(role, hasEstablishment = true)
            assertTrue(tabs.isNotEmpty(), "$role ficou sem abas")
            assertTrue(tabs.first() in tabs)
        }
    }
}
