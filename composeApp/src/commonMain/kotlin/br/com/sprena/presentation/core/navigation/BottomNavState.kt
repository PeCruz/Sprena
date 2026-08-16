package br.com.sprena.presentation.core.navigation

import br.com.sprena.shared.core.mvi.UiState

/**
 * Abas da barra inferior de navegação.
 *
 * [PROFILE] era `SETTINGS` até F1.6a. A aba passou a ser "Meus dados" — direitos do titular —
 * e Configurações virou uma linha dentro dela, na rota standalone que já existia.
 *
 * [CONFIG] entrou em F1.7.3 e é exclusiva do ADM, ocupando o lugar de [PROFILE] para ele. Além
 * de Estabelecimentos e Moderadores, precisa hospedar "Meus dados": o ADM também é titular, e
 * sem essa porta a exportação e a exclusão de conta (F1.6a) ficariam inalcançáveis para ele.
 * `TabMatrixTest` tem um caso que quebra se algum papel perder as duas.
 */
enum class BottomTab { HOME, EVENTOS, BAR, FINANCIAL, PROFILE, CONFIG }

/**
 * A barra deixou de ser fixa em F1.7.3: [tabs] vem de `tabsFor(papel, temEstabelecimento)`.
 *
 * Nasce vazia porque o papel efetivo depende de leituras assíncronas (sessão, vínculos,
 * estabelecimento ativo). Enquanto [current] for `null` não há barra a desenhar — e uma lista
 * vazia depois da resolução não é carregamento, é o sinal de "sem estabelecimento vinculado".
 * Quem consome precisa distinguir os dois, e é para isso que [resolved] existe.
 */
data class BottomNavState(
    val tabs: List<BottomTab> = emptyList(),
    val current: BottomTab? = null,
    val resolved: Boolean = false,
) : UiState {
    /** `true` quando a resolução terminou e não sobrou aba nenhuma. */
    val isWithoutEstablishment: Boolean
        get() = resolved && tabs.isEmpty()
}
