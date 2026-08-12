package br.com.sprena.shared.privacy.data.repository

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.privacy.data.dto.ConsentDto
import br.com.sprena.shared.privacy.domain.model.ConsentRecord
import br.com.sprena.shared.privacy.domain.repository.ConsentRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Persistência do aceite em `user_consents/{uid}`.
 *
 * A gravação é um batch: o doc corrente (sobrescrito a cada nova versão) e um doc
 * em `history/{policyVersion}` que nunca é alterado — as rules de F1.5 negam
 * update e delete nele.
 */
class FirestoreConsentRepository(
    private val firestore: FirebaseFirestore,
    private val appVersion: String,
    private val logger: Logger,
) : ConsentRepository {
    override suspend fun current(uid: String): Result<ConsentRecord?> =
        runCatching {
            val snapshot =
                firestore
                    .collection(COLLECTION)
                    .document(uid)
                    .get()
                    .await()
            if (snapshot.exists()) ConsentDto.fromSnapshot(snapshot) else null
        }.onFailure { logger.warn(TAG, "read failed uid=$uid", it) }

    override suspend fun accept(
        uid: String,
        policyVersion: String,
    ): Result<Unit> =
        runCatching {
            val root = firestore.collection(COLLECTION).document(uid)
            val history = root.collection(HISTORY).document(policyVersion)
            val batch = firestore.batch()
            batch.set(
                root,
                mapOf(
                    "uid" to uid,
                    "policyVersion" to policyVersion,
                    "acceptedAt" to FieldValue.serverTimestamp(),
                    "appVersion" to appVersion,
                ),
            )
            batch.set(
                history,
                mapOf(
                    "policyVersion" to policyVersion,
                    "acceptedAt" to FieldValue.serverTimestamp(),
                ),
            )
            batch.commit().await()
            Unit
        }.onFailure { logger.warn(TAG, "write failed uid=$uid", it) }

    private companion object {
        const val COLLECTION = "user_consents"
        const val HISTORY = "history"
        const val TAG = "ConsentRepo"
    }
}
