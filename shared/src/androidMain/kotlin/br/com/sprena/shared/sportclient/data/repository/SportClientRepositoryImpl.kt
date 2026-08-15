package br.com.sprena.shared.sportclient.data.repository

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.core.logger.pii.PiiMasker
import br.com.sprena.shared.sportclient.data.dto.SportClientDto
import br.com.sprena.shared.sportclient.domain.model.SportClientModel
import br.com.sprena.shared.sportclient.domain.repository.SportClientRepository
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Clientes esportivos em `establishments/{establishmentId}/sport_clients` (F1.7.2).
 *
 * Antes a coleção era global (`sport_clients`) e a rule dava leitura a qualquer conta
 * autenticada — o que significava CPF e telefone de todos os clientes visíveis para
 * qualquer login. A mudança de caminho é o que permitiu fechar aquela rule antes de o
 * cadastro ser aberto em F1.7.3.
 *
 * Erros são logados com PII mascarado e devolvidos como `Result.failure`.
 */
class SportClientRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val logger: Logger,
) : SportClientRepository {
    private fun collectionOf(establishmentId: String): CollectionReference =
        firestore
            .collection(ESTABLISHMENTS)
            .document(establishmentId)
            .collection(COLLECTION_NAME)

    override fun observeAll(establishmentId: String): Flow<Result<List<SportClientModel>>> =
        collectionOf(establishmentId)
            .snapshots()
            .map { snapshot ->
                Result.success(
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(SportClientDto::class.java)?.toDomain(doc.id)
                    },
                )
            }.catch { e ->
                logger.error(TAG, "observeAll firestore stream failed", e)
                emit(Result.failure(e))
            }

    override suspend fun getById(
        establishmentId: String,
        id: String,
    ): Result<SportClientModel?> =
        runCatching {
            val doc = collectionOf(establishmentId).document(id).get().await()
            doc.toObject(SportClientDto::class.java)?.toDomain(doc.id)
        }.onFailure { e -> logger.error(TAG, "getById failed id=$id", e) }

    override suspend fun add(
        establishmentId: String,
        client: SportClientModel,
    ): Result<String> =
        runCatching {
            collectionOf(establishmentId).add(SportClientDto.fromDomain(client)).await().id
        }.onFailure { e ->
            logger.error(
                TAG,
                "add failed cpf=${PiiMasker.cpf(client.cpf)} phone=${PiiMasker.phone(client.phone)}",
                e,
            )
        }

    override suspend fun update(
        establishmentId: String,
        client: SportClientModel,
    ): Result<Unit> =
        runCatching {
            collectionOf(establishmentId)
                .document(client.id)
                .set(SportClientDto.fromDomain(client))
                .await()
            Unit
        }.onFailure { e ->
            logger.error(TAG, "update failed id=${client.id} cpf=${PiiMasker.cpf(client.cpf)}", e)
        }

    override suspend fun delete(
        establishmentId: String,
        id: String,
    ): Result<Unit> =
        runCatching {
            collectionOf(establishmentId).document(id).delete().await()
            Unit
        }.onFailure { e -> logger.error(TAG, "delete failed id=$id", e) }

    companion object {
        const val ESTABLISHMENTS = "establishments"
        const val COLLECTION_NAME = "sport_clients"
        private const val TAG = "SportClientRepo"
    }
}
