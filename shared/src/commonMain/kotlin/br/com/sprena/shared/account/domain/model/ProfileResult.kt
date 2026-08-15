package br.com.sprena.shared.account.domain.model

/**
 * Resultado da leitura do próprio perfil.
 *
 * Fail-closed, no mesmo molde de [br.com.sprena.shared.privacy.domain.model.ConsentStatus]:
 * qualquer falha vira [Unavailable] com retry na UI. Nunca existe "perfil parcial" —
 * uma tela que diz "estes são os dados que temos sobre você" não pode mostrar meia verdade.
 */
sealed interface ProfileResult {
    data class Loaded(
        val profile: UserProfile,
    ) : ProfileResult

    data class Unavailable(
        val message: String,
    ) : ProfileResult
}
