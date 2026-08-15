package br.com.sprena.shared.privacy.domain.repository

import br.com.sprena.shared.privacy.domain.model.ConsentRecord

/**
 * Persistência do aceite de política.
 *
 * `Result<ConsentRecord?>` separa as três situações que o gate precisa distinguir:
 * aceitou (`success(record)`), nunca aceitou (`success(null)`) e não deu para
 * saber (`failure`).
 */
interface ConsentRepository {
    suspend fun current(uid: String): Result<ConsentRecord?>

    suspend fun accept(
        uid: String,
        policyVersion: String,
    ): Result<Unit>

    /**
     * Trilha append-only de aceites, do mais antigo para o mais recente.
     *
     * Usado na exportação de dados (F1.6a, LGPD art. 18 V): o titular tem direito de
     * saber a que versões da política ele consentiu, e não só à vigente. As rules de
     * F1.5 já permitem ler o próprio `history`.
     */
    suspend fun history(uid: String): Result<List<ConsentRecord>>
}
