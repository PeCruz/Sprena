package br.com.sprena.presentation.core.navigation

import br.com.sprena.shared.core.mvi.UiIntent

sealed interface BottomNavIntent : UiIntent {
    data class TabSelected(
        val tab: BottomTab,
    ) : BottomNavIntent

    /**
     * As abas que o papel atual enxerga, recalculadas sempre que o contexto muda — troca de
     * estabelecimento ativo, vínculo concedido ou removido pelo ADM.
     */
    data class TabsResolved(
        val tabs: List<BottomTab>,
    ) : BottomNavIntent
}
