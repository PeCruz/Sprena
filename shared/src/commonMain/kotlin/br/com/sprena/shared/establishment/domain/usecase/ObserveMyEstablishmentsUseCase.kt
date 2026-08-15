package br.com.sprena.shared.establishment.domain.usecase

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.establishment.domain.model.MyEstablishment
import br.com.sprena.shared.establishment.domain.repository.EstablishmentRepository
import br.com.sprena.shared.establishment.domain.repository.MembershipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Os estabelecimentos onde o usuário atual tem vínculo ativo, com o papel de cada um.
 *
 * É o que alimenta o seletor global e a decisão "esta pessoa tem estabelecimento?" que
 * separa o app da tela de "sem estabelecimento vinculado".
 *
 * São duas leituras porque as rules impõem dois caminhos: `collectionGroup('members')`
 * devolve os vínculos (e só os do próprio usuário), e cada estabelecimento é lido pelo id.
 * Não existe um `list` de estabelecimentos filtrado por membro — o motor de rules não sabe
 * provar que uma query devolveu apenas o permitido, então esse caminho é do ADM apenas.
 */
class ObserveMyEstablishmentsUseCase(
    private val memberships: MembershipRepository,
    private val establishments: EstablishmentRepository,
    private val logger: Logger,
) {
    operator fun invoke(): Flow<Result<List<MyEstablishment>>> =
        memberships.observeMine().map { result ->
            result.mapCatching { list ->
                list
                    .filter { it.active }
                    .mapNotNull { membership -> resolve(membership.establishmentId)?.let { it to membership.role } }
                    .map { (establishment, role) -> MyEstablishment(establishment, role) }
                    .sortedBy { it.establishment.name.lowercase() }
            }
        }

    /**
     * `null` descarta o vínculo da lista, sem derrubar os demais.
     *
     * Vale tanto para estabelecimento inexistente (resto de dado de um vínculo órfão)
     * quanto para falha de leitura de um item. Propagar qualquer um dos dois deixaria a
     * pessoa sem acesso aos estabelecimentos válidos por causa de um que ela nem usa.
     * A falha que **é** propagada é a da leitura dos vínculos: sem ela não dá para
     * distinguir "nenhum estabelecimento" de "não consegui ler", e mostrar a tela de
     * "procure um ADM" seria mandar o usuário atrás de um problema que não existe.
     */
    private suspend fun resolve(id: String) =
        establishments
            .getById(id)
            .onFailure { logger.warn(TAG, "establishment read failed", it) }
            .getOrNull()
            ?.takeIf { it.active }

    private companion object {
        const val TAG = "ObserveMyEstablishments"
    }
}
