package br.com.sprena.shared.account.domain.repository

import br.com.sprena.shared.account.domain.model.ProfilePatch
import br.com.sprena.shared.account.domain.model.UserProfile

/**
 * Acesso ao perfil do próprio titular.
 *
 * A implementação junta `users/{uid}` e `user_profiles/{uid}` internamente — o use case
 * não conhece coleção. Isso é o que permite mudar o layout no Firestore (F1.7, quando
 * `establishmentIds` entrar) sem tocar em domínio nem em apresentação.
 */
interface UserProfileRepository {
    /**
     * `null` quando `users/{uid}` não existe: conta não provisionada, ou já excluída e o
     * token ainda em cache. Falha de rede é `Result.failure`, e os dois casos são
     * distinguidos de propósito — só um deles significa "sua conta não está autorizada".
     */
    suspend fun current(uid: String): Result<UserProfile?>

    /** Escreve **apenas** em `user_profiles/{uid}`. Cria o doc se ainda não existir. */
    suspend fun save(
        uid: String,
        patch: ProfilePatch,
    ): Result<Unit>
}
