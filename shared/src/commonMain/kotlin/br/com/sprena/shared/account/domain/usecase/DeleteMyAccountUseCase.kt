package br.com.sprena.shared.account.domain.usecase

import br.com.sprena.shared.account.domain.model.AccountDeletionResult
import br.com.sprena.shared.account.domain.repository.AccountDeletionRepository
import br.com.sprena.shared.auth.domain.repository.AuthRepository
import br.com.sprena.shared.auth.session.SessionStore
import br.com.sprena.shared.core.logger.Logger

/**
 * Exclui a conta do titular (LGPD art. 18, VI + exigência da Play Store).
 *
 * **A ordem é a regra:** o callable roda primeiro, e só depois vêm `signOut()` e
 * `sessionStore.clear()`. Invertido, o ID token morreria antes de o backend poder
 * validá-lo e a exclusão falharia sempre — com a sessão já destruída e o titular sem
 * caminho de volta. Há teste específico travando isso.
 *
 * Em [AccountDeletionResult.Failed] a sessão fica **intacta**: nada foi apagado, e o
 * titular precisa poder tentar de novo sem relogar.
 */
class DeleteMyAccountUseCase(
    private val deletionRepository: AccountDeletionRepository,
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
    private val logger: Logger,
) {
    suspend operator fun invoke(): AccountDeletionResult {
        val result = deletionRepository.deleteMyAccount()

        when (result) {
            is AccountDeletionResult.Deleted -> {
                logger.info(TAG, "conta excluida com sucesso")
                clearLocalSession()
            }

            // O backend recusou o token: ele já não vale, então manter a sessão local só
            // deixaria o titular preso numa tela que não funciona.
            is AccountDeletionResult.SessionExpired -> {
                logger.warn(TAG, "token recusado na exclusao; limpando sessao local")
                clearLocalSession()
            }

            is AccountDeletionResult.Failed ->
                logger.warn(TAG, "exclusao falhou; sessao preservada para retry")
        }

        return result
    }

    private suspend fun clearLocalSession() {
        authRepository.signOut()
        sessionStore.clear()
    }

    private companion object {
        const val TAG = "DeleteMyAccount"
    }
}
