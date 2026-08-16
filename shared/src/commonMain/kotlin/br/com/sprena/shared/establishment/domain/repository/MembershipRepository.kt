package br.com.sprena.shared.establishment.domain.repository

import br.com.sprena.shared.establishment.domain.model.Membership
import kotlinx.coroutines.flow.Flow

/**
 * Leitura do grafo de vínculos.
 *
 * Só leitura, e assim deve continuar: `members` é `write: if false` nas rules, e toda
 * mutação passa pelas callables de F1.7.3. Acrescentar um `save` aqui seria escrever um
 * método que o servidor nega por definição.
 */
interface MembershipRepository {
    /**
     * Os vínculos do usuário autenticado, via
     * `collectionGroup('members').where('uid','==',me)`.
     *
     * Custa zero `get()` de rule e é a única leitura que atravessa estabelecimentos —
     * daí precisar do índice de escopo collection group em `firestore.indexes.json`.
     * Sem ele a query falha com `FAILED_PRECONDITION`, e só em produção: o emulador cria
     * índice sozinho.
     */
    fun observeMine(): Flow<Result<List<Membership>>>

    /** Membros de um estabelecimento. Exige ser membro dele (ou ADM). */
    fun observeMembers(establishmentId: String): Flow<Result<List<Membership>>>
}
