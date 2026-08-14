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
}
