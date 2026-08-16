package br.com.sprena.shared.establishment.domain.repository

import br.com.sprena.shared.establishment.domain.model.Establishment
import kotlinx.coroutines.flow.Flow

/**
 * Acesso aos estabelecimentos.
 *
 * [observeAll] só funciona para ADM: a rule dá `list` apenas a ele. Um membro comum
 * descobre onde trabalha pelo próprio vínculo (`MembershipRepository.observeMine`) e lê
 * cada estabelecimento por [observeById] — dois caminhos distintos de propósito, porque
 * o motor de rules não sabe provar que uma query devolveu só o que o requisitante pode ver.
 */
interface EstablishmentRepository {
    /** Todos os estabelecimentos. Falha com `PERMISSION_DENIED` para quem não é ADM. */
    fun observeAll(): Flow<Result<List<Establishment>>>

    /** `null` quando o documento não existe. Falha de rede e negativa de acesso são `failure`. */
    fun observeById(id: String): Flow<Result<Establishment?>>

    suspend fun getById(id: String): Result<Establishment?>

    /**
     * `true` quando já existe estabelecimento com este CNPJ (só dígitos).
     *
     * Consultado **antes** de [create] para poder dizer "CNPJ já cadastrado". Sem esta
     * leitura, a colisão chegaria como o mesmo `PERMISSION_DENIED` de "não é ADM", já que
     * `cnpj_index` nega `update` — e o ADM veria um "erro ao salvar" sem saber que basta
     * procurar o estabelecimento que já existe.
     *
     * É uma checagem consultiva, não a garantia: quem garante a unicidade é o `create`
     * sobre id existente dentro de [create], que falha mesmo se dois cadastros correrem juntos.
     */
    suspend fun isCnpjTaken(cnpjDigits: String): Result<Boolean>

    /**
     * Cria o estabelecimento **e** a entrada de unicidade em `cnpj_index`, no mesmo batch.
     * Falha quando o CNPJ já existe — é o `create` sobre id existente que garante isso.
     */
    suspend fun create(establishment: Establishment): Result<String>

    /** Não altera o CNPJ: mudar a chave de unicidade exigiria mover a entrada do índice. */
    suspend fun update(establishment: Establishment): Result<Unit>

    /** "Excluir" na UI. Não há delete: apagar deixaria membros e comandas órfãos. */
    suspend fun setActive(
        id: String,
        active: Boolean,
    ): Result<Unit>
}
