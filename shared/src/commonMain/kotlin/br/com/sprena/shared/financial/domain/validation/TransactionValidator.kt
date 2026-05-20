package br.com.sprena.shared.financial.domain.validation

import br.com.sprena.shared.core.validation.ValidationResult

object TransactionValidator {
    const val NAME_MAX_LENGTH: Int = 50
    const val PERSON_NAME_MAX_LENGTH: Int = 50
    const val DESCRIPTION_MAX_LENGTH: Int = 3000

    fun validateName(name: String): ValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Nome é obrigatório")
            name.length > NAME_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $NAME_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }
    }

    fun validatePersonName(name: String): ValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Nome é obrigatório")
            name.length > PERSON_NAME_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $PERSON_NAME_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }
    }

    fun validateDescription(description: String?): ValidationResult =
        when {
            description == null || description.isEmpty() -> ValidationResult.Valid
            description.length > DESCRIPTION_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $DESCRIPTION_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }
}
