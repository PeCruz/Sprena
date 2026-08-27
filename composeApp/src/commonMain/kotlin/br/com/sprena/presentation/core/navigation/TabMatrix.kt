package br.com.sprena.presentation.core.navigation

import br.com.sprena.shared.auth.domain.model.UserRole

/**
 * Quais abas cada papel enxerga (F1.7.3).
 *
 * Função pura e sem dependência de Compose para poder ser testada por asserção direta — a
 * matriz de permissões é a regra de produto mais fácil de quebrar sem perceber ao mexer na
 * barra de navegação, que até aqui era uma lista de itens escritos à mão.
 *
 * **Isto não é autorização.** Esconder uma aba não protege nada: quem chamar o Firestore
 * direto passa pelas rules, não por esta lista. O Financeiro do CLIENT, por exemplo, é negado
 * na rule de `transactions`; aqui ele apenas some da barra, para não oferecer um caminho que
 * termina em erro.
 */
fun tabsFor(
    role: UserRole,
    hasEstablishment: Boolean,
): List<BottomTab> =
    when {
        // O ADM nunca fica sem abas: ele precisa alcançar a Config para criar o primeiro
        // estabelecimento, e barrá-lo aqui seria um impasse — o único ADM do sistema veria
        // "contate um ADM".
        role == UserRole.ADM ->
            listOf(BottomTab.EVENTOS, BottomTab.BAR, BottomTab.FINANCIAL, BottomTab.CONFIG)

        // Lista vazia é o sinal para a tela de "sem estabelecimento vinculado".
        !hasEstablishment -> emptyList()

        role == UserRole.MOD ->
            listOf(
                BottomTab.HOME,
                BottomTab.EVENTOS,
                BottomTab.BAR,
                BottomTab.FINANCIAL,
                BottomTab.PROFILE,
            )

        role == UserRole.CLIENT ->
            listOf(BottomTab.HOME, BottomTab.EVENTOS, BottomTab.BAR, BottomTab.PROFILE)

        // Comandas primeiro: é a "home" de quem só frequenta o lugar.
        else -> listOf(BottomTab.BAR, BottomTab.EVENTOS, BottomTab.PROFILE)
    }
