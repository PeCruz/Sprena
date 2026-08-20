package br.com.sprena.shared.establishment.domain.usecase

import br.com.sprena.shared.establishment.domain.repository.EstablishmentRepository

/**
 * Liga e desliga um estabelecimento.
 *
 * É o "excluir" do produto: não existe delete, porque apagar o documento deixaria membros,
 * comandas e lançamentos órfãos sob um path que ninguém mais consegue ler. Desativado, o
 * estabelecimento some da barra de quem trabalha nele e continua recuperável.
 */
class SetEstablishmentActiveUseCase(
    private val repository: EstablishmentRepository,
) {
    suspend operator fun invoke(
        id: String,
        active: Boolean,
    ): Result<Unit> = repository.setActive(id, active)
}
