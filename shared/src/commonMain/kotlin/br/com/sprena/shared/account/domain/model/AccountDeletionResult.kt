package br.com.sprena.shared.account.domain.model

/**
 * Desfecho da exclusão de conta (LGPD art. 18, VI).
 *
 * Cada variante tem um comportamento de UI distinto — por isso são três e não um
 * `Result<Unit>`: [Deleted] navega para o login, [SessionExpired] também navega mas
 * sem afirmar que a conta foi apagada, e [Failed] mantém o diálogo aberto com o erro.
 */
sealed interface AccountDeletionResult {
    data object Deleted : AccountDeletionResult

    /** O backend recusou o token. A conta pode não ter sido tocada — não afirmar exclusão. */
    data object SessionExpired : AccountDeletionResult

    data class Failed(
        val message: String,
    ) : AccountDeletionResult
}
