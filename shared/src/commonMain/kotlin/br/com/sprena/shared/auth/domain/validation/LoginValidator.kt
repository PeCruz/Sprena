package br.com.sprena.shared.auth.domain.validation

import br.com.sprena.shared.core.validation.ValidationResult

object LoginValidator {
    const val EMAIL_MAX_LENGTH: Int = 254
    const val PASSWORD_MIN_LENGTH: Int = 6

    // Regex simples — não pretende cobrir todo o RFC 5322, apenas o caso comum.
    private val EMAIL_REGEX = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

    fun validateEmail(value: String): ValidationResult {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Email é obrigatório")
            trimmed.length > EMAIL_MAX_LENGTH -> ValidationResult.invalid("Email muito longo")
            !EMAIL_REGEX.matches(trimmed) -> ValidationResult.invalid("Email inválido")
            else -> ValidationResult.Valid
        }
    }

    fun validatePassword(value: String): ValidationResult =
        when {
            value.isEmpty() -> ValidationResult.invalid("Senha é obrigatória")
            value != value.trim() -> ValidationResult.invalid("Senha não pode começar ou terminar com espaço")
            value.length < PASSWORD_MIN_LENGTH ->
                ValidationResult.invalid("Senha deve ter no mínimo $PASSWORD_MIN_LENGTH caracteres")
            else -> ValidationResult.Valid
        }
}
