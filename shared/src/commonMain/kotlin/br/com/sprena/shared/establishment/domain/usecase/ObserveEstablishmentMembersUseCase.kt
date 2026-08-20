package br.com.sprena.shared.establishment.domain.usecase

import br.com.sprena.shared.establishment.domain.model.Membership
import br.com.sprena.shared.establishment.domain.repository.MembershipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Quem está vinculado a um estabelecimento.
 *
 * Devolve todos os papéis, não só os moderadores: quem administra precisa enxergar tudo que
 * alcança aquele estabelecimento — inclusive um vínculo que não deveria estar ali.
 *
 * Ordena por papel (MOD, CLIENT, USER) e depois por nome, para que a lista não mude de ordem
 * a cada emissão do Firestore. Vínculos desligados ficam no fim.
 */
class ObserveEstablishmentMembersUseCase(
    private val repository: MembershipRepository,
) {
    operator fun invoke(establishmentId: String): Flow<Result<List<Membership>>> =
        repository.observeMembers(establishmentId).map { result ->
            result.map { members ->
                members.sortedWith(
                    compareBy<Membership> { !it.active }
                        .thenBy { it.role.ordinal }
                        .thenBy { (it.displayName ?: it.uid).lowercase() },
                )
            }
        }
}
