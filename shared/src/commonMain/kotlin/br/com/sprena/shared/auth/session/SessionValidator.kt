package br.com.sprena.shared.auth.session

/**
 * Validação de TTL de sessão.
 *
 * Default: 24h (8.64e7 ms). Sessão expira quando `now - lastLogin >= ttl`.
 * Também trata "clock skew" — se lastLogin é maior que now, considera expirada
 * (defesa contra device com data trocada).
 */
object SessionValidator {
    const val DEFAULT_TTL_MILLIS: Long = 24L * 60L * 60L * 1000L

    fun isExpired(
        lastLoginEpochMillis: Long,
        nowEpochMillis: Long,
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
    ): Boolean {
        if (lastLoginEpochMillis > nowEpochMillis) return true
        return (nowEpochMillis - lastLoginEpochMillis) >= ttlMillis
    }
}
