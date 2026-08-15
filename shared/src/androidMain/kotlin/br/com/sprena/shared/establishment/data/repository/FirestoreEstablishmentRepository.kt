package br.com.sprena.shared.establishment.data.repository

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.establishment.data.dto.EstablishmentDto
import br.com.sprena.shared.establishment.domain.model.Establishment
import br.com.sprena.shared.establishment.domain.repository.EstablishmentRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Estabelecimentos em `establishments/{id}`, com a unicidade de CNPJ em `cnpj_index/{cnpj}`.
 *
 * Devolve `Result` em vez de lançar — o padrão de `UserProfileRepository`, e não o de
 * `SportClientRepositoryImpl`, que é anterior.
 */
class FirestoreEstablishmentRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val logger: Logger,
) : EstablishmentRepository {
    private val collection get() = firestore.collection(ESTABLISHMENTS)

    override fun observeAll(): Flow<Result<List<Establishment>>> =
        collection
            .snapshots()
            .map { snapshot ->
                Result.success(
                    snapshot.documents
                        .mapNotNull { EstablishmentDto.fromSnapshot(it) }
                        .sortedBy { it.name.lowercase() },
                )
            }.catch { error ->
                logger.warn(TAG, "observeAll failed", error)
                emit(Result.failure(error))
            }

    override fun observeById(id: String): Flow<Result<Establishment?>> =
        collection
            .document(id)
            .snapshots()
            .map { Result.success(EstablishmentDto.fromSnapshot(it)) }
            .catch { error ->
                logger.warn(TAG, "observeById failed", error)
                emit(Result.failure(error))
            }

    override suspend fun getById(id: String): Result<Establishment?> =
        runCatching {
            EstablishmentDto.fromSnapshot(collection.document(id).get().await())
        }.onFailure { logger.warn(TAG, "getById failed", it) }

    override suspend fun isCnpjTaken(cnpjDigits: String): Result<Boolean> =
        runCatching {
            firestore
                .collection(CNPJ_INDEX)
                .document(cnpjDigits)
                .get()
                .await()
                .exists()
        }.onFailure { logger.warn(TAG, "cnpj lookup failed", it) }

    override suspend fun create(establishment: Establishment): Result<String> =
        runCatching {
            val uid = auth.currentUser?.uid
            val ref = collection.document()
            val indexRef = firestore.collection(CNPJ_INDEX).document(establishment.cnpj)

            // Batch: o estabelecimento e a entrada de unicidade nascem juntos ou nenhum
            // dos dois nasce. A rule de `cnpj_index` nega `update`, então se o CNPJ já
            // estiver tomado a segunda escrita é recusada e o batch inteiro é desfeito —
            // é isso que impede o duplicado numa corrida entre dois cadastros.
            firestore
                .batch()
                .apply {
                    set(ref, EstablishmentDto.toMap(establishment, createdBy = uid))
                    set(
                        indexRef,
                        mapOf(
                            FIELD_ESTABLISHMENT_ID to ref.id,
                            EstablishmentDto.FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                        ),
                    )
                }.commit()
                .await()

            ref.id
        }.onFailure { logger.warn(TAG, "create failed", it) }

    override suspend fun update(establishment: Establishment): Result<Unit> =
        runCatching {
            // Sem `createdBy`: reenviá-lo sobrescreveria a origem do registro, e a rule não
            // tem como distinguir criação de edição para proteger o campo.
            collection
                .document(establishment.id)
                .set(EstablishmentDto.toMap(establishment))
                .await()
            Unit
        }.onFailure { logger.warn(TAG, "update failed", it) }

    override suspend fun setActive(
        id: String,
        active: Boolean,
    ): Result<Unit> =
        runCatching {
            // `update` parcial é seguro aqui: no update, `request.resource.data` que a rule
            // valida é o estado FINAL do documento, então os demais campos continuam vindo
            // do que já está gravado e passam por `validEstablishment()` normalmente.
            collection
                .document(id)
                .update(
                    mapOf(
                        EstablishmentDto.FIELD_ACTIVE to active,
                        EstablishmentDto.FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    ),
                ).await()
            Unit
        }.onFailure { logger.warn(TAG, "setActive failed", it) }

    private companion object {
        const val ESTABLISHMENTS = "establishments"
        const val CNPJ_INDEX = "cnpj_index"
        const val FIELD_ESTABLISHMENT_ID = "establishmentId"
        const val TAG = "EstablishmentRepo"
    }
}
