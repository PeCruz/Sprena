package br.com.sprena.shared.establishment.data.repository

import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.establishment.domain.repository.ActiveEstablishmentRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Contexto ativo do seletor global, em `user_settings/{uid}`.
 *
 * Fica no Firestore, e não em DataStore, porque a escolha precisa seguir o login e não o
 * aparelho — é o mesmo requisito que motivou F1.7 inteira.
 */
class FirestoreActiveEstablishmentRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val logger: Logger,
) : ActiveEstablishmentRepository {
    override fun observe(): Flow<Result<String?>> {
        val uid =
            auth.currentUser?.uid
                ?: return flowOf(Result.failure(IllegalStateException(NOT_SIGNED_IN)))

        return firestore
            .collection(USER_SETTINGS)
            .document(uid)
            .snapshots()
            // Documento ausente é estado legítimo — significa "nunca escolheu", e precisa
            // chegar como sucesso com `null` para a UI conseguir distinguir isso de falha.
            .map { Result.success(it.getString(FIELD_ACTIVE_ESTABLISHMENT_ID)) }
            .catch { error ->
                logger.warn(TAG, "observe failed", error)
                emit(Result.failure(error))
            }
    }

    override suspend fun set(establishmentId: String?): Result<Unit> =
        runCatching {
            val uid = auth.currentUser?.uid ?: error(NOT_SIGNED_IN)
            // `set` sem merge: o payload é o estado final, que é o que a rule valida com
            // keys().hasOnly(). Merge deixaria para trás campos de versões futuras fora da
            // allowlist e a escrita passaria a ser negada sem motivo aparente.
            firestore
                .collection(USER_SETTINGS)
                .document(uid)
                .set(
                    mapOf(
                        FIELD_ACTIVE_ESTABLISHMENT_ID to establishmentId,
                        FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    ),
                ).await()
            Unit
        }.onFailure { logger.warn(TAG, "set failed", it) }

    private companion object {
        const val USER_SETTINGS = "user_settings"
        const val FIELD_ACTIVE_ESTABLISHMENT_ID = "activeEstablishmentId"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val TAG = "ActiveEstablishmentRepo"
        const val NOT_SIGNED_IN = "Sessão expirada. Entre novamente."
    }
}
