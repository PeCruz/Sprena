package br.com.sprena.shared.account.data.repository

import br.com.sprena.shared.account.domain.model.AccountDeletionResult
import br.com.sprena.shared.account.domain.repository.AccountDeletionRepository
import br.com.sprena.shared.core.logger.Logger
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Dispara o callable `deleteMyAccount` (F1.6a).
 *
 * A chamada vai **sem payload**: o uid é derivado do token pelo backend, e o callable
 * rejeita qualquer chave em `request.data`. Mandar o uid daqui seria oferecer ao cliente
 * exatamente o parâmetro que permitiria excluir a conta de outra pessoa.
 *
 * Nenhuma `FirebaseFunctionsException` cruza para `commonMain` — a tradução acontece
 * aqui, em [mapAccountDeletionError] (restrição 13 do CLAUDE.md).
 */
class FunctionsAccountDeletionRepository(
    private val functions: FirebaseFunctions,
    private val logger: Logger,
) : AccountDeletionRepository {
    override suspend fun deleteMyAccount(): AccountDeletionResult =
        runCatching {
            functions.getHttpsCallable(CALLABLE).call().await()
            AccountDeletionResult.Deleted
        }.getOrElse { error ->
            logger.warn(TAG, "deleteMyAccount falhou${deletionDiagnostics(error)}", error)
            mapAccountDeletionError(error)
        }

    private companion object {
        const val CALLABLE = "deleteMyAccount"
        const val TAG = "AccountDeletionRepo"
    }
}
