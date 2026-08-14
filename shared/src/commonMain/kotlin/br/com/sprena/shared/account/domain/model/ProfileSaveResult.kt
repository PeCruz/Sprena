package br.com.sprena.shared.account.domain.model

import br.com.sprena.shared.core.validation.ValidationResult

/**
 * Desfecho da gravação do perfil.
 *
 * [Invalid] carrega os erros por campo em vez de uma mensagem única porque a tela de
 * perfil marca o campo problemático — uma string só obrigaria a UI a adivinhar qual.
 */
sealed interface ProfileSaveResult {
    data object Saved : ProfileSaveResult

    data class Invalid(
        val cpf: ValidationResult = ValidationResult.Valid,
        val phone: ValidationResult = ValidationResult.Valid,
    ) : ProfileSaveResult

    data class Failed(
        val message: String,
    ) : ProfileSaveResult
}
