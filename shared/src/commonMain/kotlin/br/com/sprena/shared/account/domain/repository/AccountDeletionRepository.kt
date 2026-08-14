package br.com.sprena.shared.account.domain.repository

import br.com.sprena.shared.account.domain.model.AccountDeletionResult

/**
 * Dispara a exclusão da própria conta.
 *
 * A implementação chama a Cloud Function `deleteMyAccount`. O uid **nunca** vai no
 * payload — ele é derivado do token pelo backend. Um uid no corpo seria a escalada de
 * privilégio óbvia (excluir a conta de outra pessoa), e por isso o callable rejeita
 * qualquer chave em `request.data`.
 */
interface AccountDeletionRepository {
    suspend fun deleteMyAccount(): AccountDeletionResult
}
