package br.com.sprena.shared.sportclient.domain.repository

import br.com.sprena.shared.sportclient.domain.model.SportClientModel
import kotlinx.coroutines.flow.Flow

/**
 * Contrato do repositório de SportClients.
 *
 * A partir de F1.7.2 os clientes vivem em `establishments/{establishmentId}/sport_clients`,
 * e não mais numa coleção global. O `establishmentId` vai em cada chamada, em vez de no
 * construtor, porque o estabelecimento ativo muda em tempo de execução pelo seletor global —
 * um repositório amarrado a um tenant na construção precisaria ser recriado a cada troca.
 *
 * Devolve `Result` desde F1.7.2. Antes lançava exceção, seguindo o padrão de F0; a troca
 * foi feita agora porque esta interface **ainda não tem nenhum consumidor** — o
 * `SportClientViewModel` guarda os clientes em memória e nunca chegou a injetar o
 * repositório. Ligá-lo é F1.7.3, quando o contexto ativo existir na UI, e é bem mais
 * simples ligá-lo a um contrato que já está no formato definitivo.
 */
interface SportClientRepository {
    /** Observa os clientes de um estabelecimento em tempo real. */
    fun observeAll(establishmentId: String): Flow<Result<List<SportClientModel>>>

    /** `null` quando o documento não existe. */
    suspend fun getById(
        establishmentId: String,
        id: String,
    ): Result<SportClientModel?>

    /** Adiciona um cliente ao estabelecimento. Devolve o id gerado. */
    suspend fun add(
        establishmentId: String,
        client: SportClientModel,
    ): Result<String>

    suspend fun update(
        establishmentId: String,
        client: SportClientModel,
    ): Result<Unit>

    suspend fun delete(
        establishmentId: String,
        id: String,
    ): Result<Unit>
}
