package br.com.sprena.shared.menu.domain.validation

import br.com.sprena.shared.core.validation.ValidationResult

object MenuValidator {
    const val NAME_MAX_LENGTH: Int = 100
    const val DESCRIPTION_MAX_LENGTH: Int = 500
    const val MAX_PRICE_CENTS: Long = 100_000_00L // R$ 100.000,00

    fun validateMenuItemName(name: String): ValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Nome do item é obrigatório")
            name.length > NAME_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $NAME_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }
    }

    fun validateMenuItemPrice(priceCents: Long?): ValidationResult =
        when {
            priceCents == null -> ValidationResult.invalid("Preço é obrigatório")
            priceCents <= 0 -> ValidationResult.invalid("Preço deve ser maior que zero")
            priceCents > MAX_PRICE_CENTS ->
                ValidationResult.invalid("Preço máximo é R\$ 100.000,00")
            else -> ValidationResult.Valid
        }

    fun validateMenuItemDescription(description: String?): ValidationResult =
        when {
            description == null || description.isEmpty() -> ValidationResult.Valid
            description.length > DESCRIPTION_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $DESCRIPTION_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }
}
