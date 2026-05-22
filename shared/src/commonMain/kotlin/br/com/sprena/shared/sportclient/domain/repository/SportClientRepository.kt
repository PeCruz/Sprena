package br.com.sprena.shared.sportclient.domain.repository

import br.com.sprena.shared.sportclient.domain.model.SportClientModel
import kotlinx.coroutines.flow.Flow

/**
 * Contrato do repositório de SportClients.
 *
 * A implementação concreta usa Firebase Firestore (Android)
 * e é injetada via Koin no platformModule / shared DI.
 */
interface SportClientRepository {
    /**
     * Observa todos os clientes em tempo real.
     * Emite uma nova lista sempre que houver mudanças no Firestore.
     */
    fun observeAll(): Flow<List<SportClientModel>>

    /**
     * Busca um cliente pelo ID.
     */
    suspend fun getById(id: String): SportClientModel?

    /**
     * Adiciona um novo cliente. Retorna o ID gerado.
     */
    suspend fun add(client: SportClientModel): String

    /**
     * Atualiza um cliente existente.
     */
    suspend fun update(client: SportClientModel)

    /**
     * Remove um cliente pelo ID.
     */
    suspend fun delete(id: String)
}
