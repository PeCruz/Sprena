package br.com.sprena.presentation.establishment.moderators

import br.com.sprena.shared.core.mvi.UiState
import br.com.sprena.shared.establishment.domain.model.Establishment
import br.com.sprena.shared.establishment.domain.model.MemberRole
import br.com.sprena.shared.establishment.domain.model.Membership

/** Quantos caracteres do uid sobrevivem quando não há nome — o suficiente para distinguir. */
private const val UID_LABEL_LENGTH = 8

/**
 * Uma linha da lista de membros.
 *
 * [label] resolve a ausência de nome aqui, e não na tela, para poder ser testado: os vínculos
 * criados à mão pelo Console antes de F1.7.3b não têm `displayName`, e um espaço em branco na
 * lista seria pior que um identificador curto.
 */
data class MemberRow(
    val uid: String,
    val role: MemberRole,
    val active: Boolean,
    val displayName: String?,
) {
    val label: String
        get() = displayName ?: uid.take(UID_LABEL_LENGTH)

    companion object {
        fun from(membership: Membership): MemberRow =
            MemberRow(
                uid = membership.uid,
                role = membership.role,
                active = membership.active,
                displayName = membership.displayName,
            )
    }
}

/**
 * Membros de um estabelecimento (ADM).
 *
 * [error] e [membersError] são separados de propósito: falhar ao listar estabelecimentos
 * derruba a tela inteira, mas falhar ao ler os membros de um deles precisa deixar o seletor
 * utilizável para o ADM tentar outro.
 */
data class ModeratorsState(
    val establishments: List<Establishment> = emptyList(),
    val selectedEstablishmentId: String? = null,
    val members: List<MemberRow> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMembers: Boolean = false,
    val error: String? = null,
    val membersError: String? = null,
) : UiState {
    val selectedEstablishment: Establishment?
        get() = establishments.firstOrNull { it.id == selectedEstablishmentId }

    val hasNoEstablishments: Boolean
        get() = !isLoading && error == null && establishments.isEmpty()
}
