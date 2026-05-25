package br.com.sprena.shared.sportclient.data.repository

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.core.logger.pii.PiiMasker
import br.com.sprena.shared.sportclient.data.dto.SportClientDto
import br.com.sprena.shared.sportclient.domain.model.SportClientModel
import br.com.sprena.shared.sportclient.domain.repository.SportClientRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Implementação do [SportClientRepository] usando Firebase Firestore.
 *
 * Coleção Firestore: `sport_clients`. Cada documento mapeia 1:1 com [SportClientDto].
 * Erros são logados via [logger] (com PII mascarado) e re-lançados — ViewModel decide UX.
 */
class SportClientRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val logger: Logger,
) : SportClientRepository {
    private val collection get() = firestore.collection(COLLECTION_NAME)

    override fun observeAll(): Flow<List<SportClientModel>> =
        collection
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(SportClientDto::class.java)?.toDomain(doc.id)
                }
            }.catch { e ->
                logger.error(TAG, "observeAll firestore stream failed", e)
                throw e
            }

    override suspend fun getById(id: String): SportClientModel? =
        runCatching {
            val doc = collection.document(id).get().await()
            doc.toObject(SportClientDto::class.java)?.toDomain(doc.id)
        }.onFailure { e ->
            logger.error(TAG, "getById failed id=$id", e)
        }.getOrThrow()

    override suspend fun add(client: SportClientModel): String =
        runCatching {
            val dto = SportClientDto.fromDomain(client)
            collection.add(dto).await().id
        }.onFailure { e ->
            logger.error(
                TAG,
                "add failed cpf=${PiiMasker.cpf(client.cpf)} phone=${PiiMasker.phone(client.phone)}",
                e,
            )
        }.getOrThrow()

    override suspend fun update(client: SportClientModel) {
        runCatching {
            val dto = SportClientDto.fromDomain(client)
            collection.document(client.id).set(dto).await()
        }.onFailure { e ->
            logger.error(TAG, "update failed id=${client.id} cpf=${PiiMasker.cpf(client.cpf)}", e)
        }.getOrThrow()
    }

    override suspend fun delete(id: String) {
        runCatching {
            collection.document(id).delete().await()
        }.onFailure { e ->
            logger.error(TAG, "delete failed id=$id", e)
        }.getOrThrow()
    }

    companion object {
        const val COLLECTION_NAME = "sport_clients"
        private const val TAG = "SportClientRepo"
    }
}
