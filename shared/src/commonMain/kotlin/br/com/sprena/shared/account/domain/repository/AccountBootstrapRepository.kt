package br.com.sprena.shared.account.domain.repository

/**
 * Cria `users/{uid}` no primeiro acesso, via Cloud Function.
 *
 * Existe porque `users` é `write: if false` nas rules: se o cliente pudesse criar o próprio
 * documento de papel, criaria com `role: 'ADM'`. A função devolve o papel real — que **não** é
 * necessariamente `USER`, já que chamar de novo numa conta existente devolve o papel dela sem
 * tocar no documento.
 */
interface AccountBootstrapRepository {
    /** O papel da conta depois do bootstrap, ou `failure` se não foi possível prepará-la. */
    suspend fun bootstrap(): Result<String>
}
