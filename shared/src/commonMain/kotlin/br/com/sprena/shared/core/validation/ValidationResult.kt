package br.com.sprena.shared.core.validation

/**
 * Resultado imutável de uma validação de campo/regra de negócio.
 *
 * Uso típico:
 * ```kotlin
 * val r = TaskValidator.validateName("Comprar remo")
 * if (!r.isValid) showError(r.errorMessage)
 * ```
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
) {
    companion object {
        val Valid = ValidationResult(isValid = true)

        fun invalid(message: String): ValidationResult = ValidationResult(isValid = false, errorMessage = message)
    }
}
