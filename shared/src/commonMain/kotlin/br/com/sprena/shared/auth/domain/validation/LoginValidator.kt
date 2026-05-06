package br.com.sprena.shared.auth.domain.validation

import br.com.sprena.shared.core.validation.ValidationResult

object LoginValidator {

    const val USERNAME_MIN_LENGTH: Int = 3
    const val USERNAME_MAX_LENGTH: Int = 8
    const val PASSWORD_LENGTH: Int = 6

    fun validateUsername(username: String): ValidationResult {
        val trimmed = username.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Usuário é obrigatório")
            trimmed.length < USERNAME_MIN_LENGTH ->
                ValidationResult.invalid("Mínimo de $USERNAME_MIN_LENGTH caracteres")
            trimmed.length > USERNAME_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $USERNAME_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }
    }

    fun validatePassword(password: String): ValidationResult {
        val trimmed = password.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Senha é obrigatória")
            !trimmed.all { it.isDigit() } ->
                ValidationResult.invalid("Senha deve conter apenas dígitos numéricos")
            trimmed.length != PASSWORD_LENGTH ->
                ValidationResult.invalid("Senha deve ter exatamente $PASSWORD_LENGTH dígitos")
            else -> ValidationResult.Valid
        }
    }
}
