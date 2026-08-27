package br.com.sprena.shared.account.data.repository

import br.com.sprena.shared.account.domain.repository.AccountBootstrapRepository
import br.com.sprena.shared.core.logger.Logger
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Dispara o callable `bootstrapAccount` (F1.7.3d).
 *
 * A chamada vai **sem payload**: o uid é derivado do token pelo backend, e o callable rejeita
 * qualquer chave em `request.data`. Mandar o uid daqui seria oferecer ao cliente exatamente o
 * parâmetro que permitiria criar o documento de papel de outra pessoa.
 *
 * O papel devolvido **não** é necessariamente `USER`: numa conta que já existe, a função lê e
 * devolve o papel real sem tocar no documento. Quem chama precisa usar o valor devolvido, e
 * não assumir `USER` — assumir transformaria um ADM em usuário comum na sessão seguinte.
 */
class FunctionsAccountBootstrapRepository(
    private val functions: FirebaseFunctions,
    private val logger: Logger,
) : AccountBootstrapRepository {
    override suspend fun bootstrap(): Result<String> =
        runCatching {
            val response = functions.getHttpsCallable(CALLABLE).call().await()

            @Suppress("UNCHECKED_CAST")
            val data = response.data as? Map<String, Any?>
            data?.get("role") as? String
                ?: error("resposta sem papel")
        }.onFailure { error ->
            logger.warn(TAG, "bootstrapAccount falhou", error)
        }

    private companion object {
        const val CALLABLE = "bootstrapAccount"
        const val TAG = "AccountBootstrapRepo"
    }
}
