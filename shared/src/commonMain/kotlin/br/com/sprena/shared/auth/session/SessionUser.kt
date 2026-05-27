package br.com.sprena.shared.auth.session

import br.com.sprena.shared.auth.domain.model.UserRole

/**
 * Snapshot da sessão persistido localmente (cifrado).
 *
 * Não inclui token Firebase — o SDK persiste por conta própria.
 * Inclui [lastLoginEpochMillis] para validação de TTL (24h).
 */
data class SessionUser(
    val uid: String,
    val email: String,
    val role: UserRole,
    val lastLoginEpochMillis: Long,
)
