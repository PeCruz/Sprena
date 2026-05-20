package br.com.sprena.shared.sportclient.data.repository

import br.com.sprena.shared.sportclient.data.dto.SportClientDto
import br.com.sprena.shared.sportclient.domain.model.SportClientModel
import br.com.sprena.shared.sportclient.domain.repository.SportClientRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Implementação do [SportClientRepository] usando Firebase Firestore.
 *
 * Coleção Firestore: `sport_clients`
 *
 * Cada documento mapeia 1:1 com [SportClientDto].
 * O ID do documento é gerado automaticamente pelo Firestore no [add].
 */
class SportClientRepositoryImpl(
    private val firestore: FirebaseFirestore,
) : SportClientRepository {
    private val collection get() = firestore.collection(COLLECTION_NAME)

    override fun observeAll(): Flow<List<SportClientModel>> =
        collection.snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(SportClientDto::class.java)?.toDomain(doc.id)
            }
        }

    override suspend fun getById(id: String): SportClientModel? {
        val doc = collection.document(id).get().await()
        return doc.toObject(SportClientDto::class.java)?.toDomain(doc.id)
    }

    override suspend fun add(client: SportClientModel): String {
        val dto = SportClientDto.fromDomain(client)
        val docRef = collection.add(dto).await()
        return docRef.id
    }

    override suspend fun update(client: SportClientModel) {
        val dto = SportClientDto.fromDomain(client)
        collection.document(client.id).set(dto).await()
    }

    override suspend fun delete(id: String) {
        collection.document(id).delete().await()
    }

    companion object {
        const val COLLECTION_NAME = "sport_clients"
    }
}
