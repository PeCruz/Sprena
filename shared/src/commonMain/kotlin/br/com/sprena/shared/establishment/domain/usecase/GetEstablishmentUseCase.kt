package br.com.sprena.shared.establishment.domain.usecase

import br.com.sprena.shared.establishment.domain.model.Establishment
import br.com.sprena.shared.establishment.domain.repository.EstablishmentRepository

/**
 * Um estabelecimento pelo id, para preencher o formulário de edição.
 *
 * `null` quando o documento não existe — o que é diferente de falha de leitura, e a tela
 * precisa dos dois: um manda de volta para a lista, o outro oferece tentar de novo.
 */
class GetEstablishmentUseCase(
    private val repository: EstablishmentRepository,
) {
    suspend operator fun invoke(id: String): Result<Establishment?> = repository.getById(id)
}
