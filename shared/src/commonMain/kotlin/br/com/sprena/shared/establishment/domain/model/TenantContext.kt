package br.com.sprena.shared.establishment.domain.model

import br.com.sprena.shared.auth.domain.model.UserRole

/**
 * O que o app precisa saber para decidir o que mostrar: quem é a pessoa globalmente, onde ela
 * tem vínculo, e em qual estabelecimento está operando agora.
 *
 * Isto é **conveniência de UI, não autorização**. Quem autoriza são as rules, que releem o
 * grafo de membros a cada requisição. Um [effectiveRole] mais generoso do que a realidade não
 * concede nada — só produz uma tela que falha ao carregar. É por isso que a resolução abaixo
 * pode ser tolerante com dados inconsistentes sem abrir brecha.
 */
data class TenantContext(
    val globalRole: UserRole,
    val memberships: List<Membership>,
    val activeEstablishmentId: String?,
) {
    val isAdm: Boolean
        get() = globalRole == UserRole.ADM

    private val activeMemberships: List<Membership>
        get() = memberships.filter { it.active }

    /**
     * O vínculo em uso.
     *
     * Cai no único vínculo ativo quando não há escolha explícita — quem trabalha num lugar só
     * nunca deveria abrir um seletor de um item. Com dois ou mais e nenhum escolhido, devolve
     * `null` em vez de adivinhar: escolher sozinho poderia dar a alguém poderes de MOD num
     * lugar que ela não pediu para abrir.
     *
     * O id vem de `user_settings`, que o cliente escreve e nenhuma rule lê, então pode apontar
     * para um estabelecimento onde o vínculo já foi desligado. Nesse caso ele é ignorado.
     */
    val activeMembership: Membership?
        get() =
            activeMemberships.firstOrNull { it.establishmentId == activeEstablishmentId }
                ?: activeMemberships.singleOrNull()

    /**
     * O papel que a UI deve usar.
     *
     * [UserRole.ADM] global vence qualquer vínculo: o contrário permitiria rebaixar um
     * administrador escrevendo um member doc, e `members` existe justamente para não decidir
     * isso. Sem vínculo resolvido, cai para [UserRole.USER] — o papel mais restrito.
     *
     * Os papéis globais legados [UserRole.MOD] e [UserRole.CLIENT] (contas provisionadas à mão
     * antes de F1.7) **não** são considerados: hoje esses papéis valem por estabelecimento, e
     * honrar o valor global daria acesso a quem não tem vínculo nenhum.
     */
    val effectiveRole: UserRole
        get() =
            when {
                isAdm -> UserRole.ADM
                else -> activeMembership?.role?.toUserRole() ?: UserRole.USER
            }

    /**
     * `false` leva à tela de "sem estabelecimento vinculado".
     *
     * Sempre `true` para o ADM, mesmo sem vínculo: ele precisa alcançar a tela de
     * estabelecimentos para criar o primeiro, e barrá-lo ali seria um impasse de inicialização
     * — o único ADM do sistema veria "procure um ADM".
     */
    val hasEstablishment: Boolean
        get() = isAdm || activeMemberships.isNotEmpty()
}

private fun MemberRole.toUserRole(): UserRole =
    when (this) {
        MemberRole.MOD -> UserRole.MOD
        MemberRole.CLIENT -> UserRole.CLIENT
        MemberRole.USER -> UserRole.USER
    }
