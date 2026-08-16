package br.com.sprena.shared.establishment.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * O estabelecimento ativo do seletor global, persistido em `user_settings/{uid}`.
 *
 * Fica no Firestore, e não em DataStore, porque o requisito é que a escolha siga o login
 * e não o aparelho. É a única coleção que o próprio dono escreve livremente, o que só é
 * seguro por causa de uma invariante anotada em `firestore.rules`: **nenhuma rule lê
 * `user_settings`**. Apontar o contexto para um estabelecimento alheio é permitido e
 * inútil — todo acesso continua barrado por `isMemberOf(estId)`, que vem do path.
 *
 * Portanto o valor daqui é preferência de UI, nunca autorização. Quem consome precisa
 * conferir se o id ainda está entre os vínculos do usuário: o ADM pode ter desligado a
 * pessoa depois da última escolha.
 */
interface ActiveEstablishmentRepository {
    /** `null` quando nunca houve escolha, ou quando o contexto foi limpo. */
    fun observe(): Flow<Result<String?>>

    suspend fun set(establishmentId: String?): Result<Unit>
}
