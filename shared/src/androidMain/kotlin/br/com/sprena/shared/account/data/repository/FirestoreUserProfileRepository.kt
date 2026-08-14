package br.com.sprena.shared.account.data.repository

import br.com.sprena.shared.account.data.dto.UserProfileDto
import br.com.sprena.shared.account.domain.model.ProfilePatch
import br.com.sprena.shared.account.domain.model.UserProfile
import br.com.sprena.shared.account.domain.repository.UserProfileRepository
import br.com.sprena.shared.core.logger.Logger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Perfil do titular, juntando `users/{uid}` e `user_profiles/{uid}` (F1.6a).
 *
 * As duas leituras ficam aqui, e não no use case, para que o domínio não conheça
 * coleção — quando F1.7 mudar o layout, nada acima desta classe muda.
 *
 * O e-mail vem do Firebase Auth, não do doc de `users`: lá ele é informativo e pode
 * estar desatualizado em relação à credencial de login (o runbook, Parte B, já registra
 * que o app não lê aquele campo em runtime).
 */
class FirestoreUserProfileRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val logger: Logger,
) : UserProfileRepository {
    override suspend fun current(uid: String): Result<UserProfile?> =
        runCatching {
            val users =
                firestore
                    .collection(USERS)
                    .document(uid)
                    .get()
                    .await()
            // Sem doc em `users` a conta não está autorizada — não adianta ler o sidecar.
            if (!users.exists()) {
                null
            } else {
                val profile =
                    firestore
                        .collection(PROFILES)
                        .document(uid)
                        .get()
                        .await()
                UserProfileDto.fromSnapshots(
                    uid = uid,
                    email = auth.currentUser?.email.orEmpty(),
                    users = users,
                    profile = profile.takeIf { it.exists() },
                    logger = logger,
                )
            }
        }.onFailure { logger.warn(TAG, "read failed", it) }

    override suspend fun save(
        uid: String,
        patch: ProfilePatch,
    ): Result<Unit> =
        runCatching {
            // `set` sem merge: o payload é o estado final completo do doc, que é o que a
            // rule valida com keys().hasOnly(). Merge deixaria campos órfãos de versões
            // anteriores fora da allowlist e a escrita passaria a ser negada.
            firestore
                .collection(PROFILES)
                .document(uid)
                .set(
                    mapOf(
                        FIELD_APELIDO to patch.apelido.orEmpty(),
                        FIELD_CPF to patch.cpf.orEmpty(),
                        FIELD_PHONE to patch.phone.orEmpty(),
                        FIELD_MODALITIES to patch.modalities.map { it.name },
                        FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    ),
                ).await()
            Unit
        }.onFailure { logger.warn(TAG, "save failed", it) }

    private companion object {
        const val USERS = "users"
        const val PROFILES = "user_profiles"
        const val TAG = "UserProfileRepo"
        const val FIELD_APELIDO = "apelido"
        const val FIELD_CPF = "cpf"
        const val FIELD_PHONE = "phone"
        const val FIELD_MODALITIES = "modalities"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}
