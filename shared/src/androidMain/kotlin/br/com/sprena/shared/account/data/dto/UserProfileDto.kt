package br.com.sprena.shared.account.data.dto

import br.com.sprena.shared.account.domain.model.UserProfile
import br.com.sprena.shared.auth.domain.model.UserRole
import br.com.sprena.shared.core.logger.Logger
import br.com.sprena.shared.sportclient.domain.validation.SportModality
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Mapeia os dois documentos que compõem o perfil (F1.6a).
 *
 * Toda leitura é tolerante a campo ausente: `user_profiles/{uid}` não tem backfill, e
 * mesmo `users/{uid}` pode ter sido provisionado antes de um campo existir.
 */
object UserProfileDto {
    /**
     * @param users snapshot de `users/{uid}` — a fonte da identidade operacional.
     * @param profile snapshot de `user_profiles/{uid}`, ou `null` se o doc não existe.
     *
     * Devolve `null` quando a role não é reconhecida: um valor fora do enum significa
     * provisionamento inválido, e tratar como CLIENT daria acesso que ninguém concedeu.
     */
    fun fromSnapshots(
        uid: String,
        email: String,
        users: DocumentSnapshot,
        profile: DocumentSnapshot?,
        logger: Logger,
    ): UserProfile? {
        val role = parseRole(users.getString(FIELD_ROLE), logger) ?: return null

        return UserProfile(
            uid = uid,
            email = email,
            role = role,
            name = users.getString(FIELD_NAME)?.takeIf { it.isNotBlank() },
            apelido = profile?.getString(FIELD_APELIDO)?.takeIf { it.isNotBlank() },
            cpf = profile?.getString(FIELD_CPF)?.takeIf { it.isNotBlank() },
            phone = profile?.getString(FIELD_PHONE)?.takeIf { it.isNotBlank() },
            modalities = parseModalities(profile, logger),
        )
    }

    private fun parseRole(
        raw: String?,
        logger: Logger,
    ): UserRole? =
        runCatching { UserRole.valueOf(raw.orEmpty().uppercase()) }
            .onFailure { logger.warn(TAG, "role desconhecida no doc de users", it) }
            .getOrNull()

    /** Valor desconhecido é descartado com warn — não derruba o perfil inteiro. */
    private fun parseModalities(
        profile: DocumentSnapshot?,
        logger: Logger,
    ): List<SportModality> {
        val raw = profile?.get(FIELD_MODALITIES) as? List<*> ?: return emptyList()
        return raw.mapNotNull { item ->
            runCatching { SportModality.valueOf(item.toString().uppercase()) }
                .onFailure { logger.warn(TAG, "modalidade desconhecida descartada", it) }
                .getOrNull()
        }
    }

    private const val TAG = "UserProfileDto"
    private const val FIELD_ROLE = "role"
    private const val FIELD_NAME = "name"
    private const val FIELD_APELIDO = "apelido"
    private const val FIELD_CPF = "cpf"
    private const val FIELD_PHONE = "phone"
    private const val FIELD_MODALITIES = "modalities"
}
