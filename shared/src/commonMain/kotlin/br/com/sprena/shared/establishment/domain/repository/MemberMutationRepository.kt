package br.com.sprena.shared.establishment.domain.repository

import br.com.sprena.shared.establishment.domain.model.MemberLinkResult
import br.com.sprena.shared.establishment.domain.model.MemberRole

/**
 * Mutação do grafo de vínculos — tudo passa por Cloud Function.
 *
 * Separado de [MembershipRepository], que só lê. A divisão espelha as rules: `members` é
 * `write: if false`, então leitura vai direto ao Firestore e escrita não tem como ir. Juntar
 * os dois num repositório só sugeriria que existe um caminho de escrita pelo cliente.
 */
interface MemberMutationRepository {
    /**
     * O único caminho de vinculação. [cpf] pode vir formatado; a normalização e a validação
     * de dígito verificador acontecem no servidor, que é quem não confia no cliente.
     */
    suspend fun linkByCpf(
        establishmentId: String,
        cpf: String,
        name: String,
        role: MemberRole,
    ): MemberLinkResult

    suspend fun setRole(
        establishmentId: String,
        targetUid: String,
        role: MemberRole,
    ): Result<Unit>

    suspend fun remove(
        establishmentId: String,
        targetUid: String,
    ): Result<Unit>

    /** Sair por conta própria. Não pede permissão a ninguém — é o remédio de quem foi
     *  vinculado sem pedir. */
    suspend fun leave(establishmentId: String): Result<Unit>
}
