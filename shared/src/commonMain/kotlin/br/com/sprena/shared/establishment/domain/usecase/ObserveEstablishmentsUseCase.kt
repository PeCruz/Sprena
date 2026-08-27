package br.com.sprena.shared.establishment.domain.usecase

import br.com.sprena.shared.establishment.domain.model.Establishment
import br.com.sprena.shared.establishment.domain.repository.EstablishmentRepository
import kotlinx.coroutines.flow.Flow

/**
 * Todos os estabelecimentos, para a tela de administração.
 *
 * Exclusivo do ADM: a rule dá `list` apenas a ele, então para qualquer outro papel o fluxo
 * emite `failure` com `PERMISSION_DENIED`. Quem consome precisa tratar isso como erro
 * legível — uma lista vazia diria "não há estabelecimentos", que é outra coisa.
 *
 * Um membro comum não passa por aqui: ele descobre onde trabalha por
 * [ObserveMyEstablishmentsUseCase], que lê o próprio vínculo.
 */
class ObserveEstablishmentsUseCase(
    private val repository: EstablishmentRepository,
) {
    operator fun invoke(): Flow<Result<List<Establishment>>> = repository.observeAll()
}
