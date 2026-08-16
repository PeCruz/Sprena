package br.com.sprena.shared.establishment.domain.usecase

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.establishment.domain.repository.ActiveEstablishmentRepository
import br.com.sprena.shared.establishment.domain.repository.MembershipRepository
import kotlinx.coroutines.flow.first

/**
 * Troca o estabelecimento ativo do seletor global.
 *
 * A conferência de vínculo aqui **não** é barreira de segurança — as rules já negam tudo
 * no estabelecimento alheio, e `user_settings` é justamente a coleção que nenhuma rule lê,
 * então gravar um id qualquer ali é inofensivo. Ela existe para o app não entrar num
 * estado em que todas as abas respondem "sem permissão" sem que nada na tela explique por
 * quê, o que acontece se o ADM desligar o vínculo entre uma escolha e outra.
 *
 * Limpar o contexto (`null`) não passa por essa conferência: é a saída de quem já está num
 * estado inválido, e exigir leitura de vínculos justamente aí travaria o único caminho de
 * volta quando a rede está fora.
 */
class SelectActiveEstablishmentUseCase(
    private val memberships: MembershipRepository,
    private val activeEstablishment: ActiveEstablishmentRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(establishmentId: String?): Result<Unit> =
        if (establishmentId == null) {
            activeEstablishment.set(null)
        } else {
            memberships
                .observeMine()
                .first()
                .onFailure { logger.warn(TAG, "membership read failed", it) }
                .mapCatching { mine ->
                    check(mine.any { it.establishmentId == establishmentId && it.active }) {
                        NOT_A_MEMBER_MESSAGE
                    }
                }.fold(
                    onSuccess = { activeEstablishment.set(establishmentId) },
                    onFailure = { Result.failure(it) },
                )
        }

    private companion object {
        const val TAG = "SelectActiveEstablishment"
        const val NOT_A_MEMBER_MESSAGE = "Você não tem acesso a este estabelecimento."
    }
}
