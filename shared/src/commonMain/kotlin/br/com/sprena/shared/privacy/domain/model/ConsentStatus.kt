package br.com.sprena.shared.privacy.domain.model

/**
 * Decisão do gate de consentimento.
 *
 * [Unavailable] existe porque "não deu para saber" não é "pode entrar": o gate é
 * fail-closed e trata falha de leitura como bloqueio com retry, não como aceite.
 */
sealed interface ConsentStatus {
    data object Granted : ConsentStatus

    data class Required(
        val reason: Reason,
    ) : ConsentStatus

    data class Unavailable(
        val message: String,
    ) : ConsentStatus

    enum class Reason { MISSING, OUTDATED }
}
