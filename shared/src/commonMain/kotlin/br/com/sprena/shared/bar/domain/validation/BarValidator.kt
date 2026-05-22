package br.com.sprena.shared.bar.domain.validation

import br.com.sprena.shared.core.validation.ValidationResult

object BarValidator {
    const val NAME_MAX_LENGTH: Int = 100
    const val NICKNAME_MAX_LENGTH: Int = 50
    const val PHONE_MIN_LENGTH: Int = 10
    const val PHONE_MAX_LENGTH: Int = 15
    const val CPF_LENGTH: Int = 11
    const val EMAIL_MAX_LENGTH: Int = 150
    const val ITEM_NAME_MAX_LENGTH: Int = 100
    const val ITEM_MAX_PRICE_CENTS: Long = 100_000_00L // R$ 100.000,00

    fun validateClientName(name: String): ValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Nome é obrigatório")
            name.length > NAME_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $NAME_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }
    }

    fun validateNickname(nickname: String?): ValidationResult =
        when {
            nickname == null || nickname.isEmpty() -> ValidationResult.Valid
            nickname.length > NICKNAME_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $NICKNAME_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }

    fun validatePhone(phone: String): ValidationResult {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.isEmpty() -> ValidationResult.invalid("Telefone é obrigatório")
            digits.length < PHONE_MIN_LENGTH ->
                ValidationResult.invalid("Telefone deve ter pelo menos $PHONE_MIN_LENGTH dígitos")
            digits.length > PHONE_MAX_LENGTH ->
                ValidationResult.invalid("Telefone deve ter no máximo $PHONE_MAX_LENGTH dígitos")
            else -> ValidationResult.Valid
        }
    }

    fun validateCpf(cpf: String): ValidationResult {
        val digits = cpf.filter { it.isDigit() }
        return when {
            digits.isEmpty() -> ValidationResult.invalid("CPF é obrigatório")
            digits.length != CPF_LENGTH ->
                ValidationResult.invalid("CPF deve ter $CPF_LENGTH dígitos")
            else -> ValidationResult.Valid
        }
    }

    fun validateEmail(email: String?): ValidationResult =
        when {
            email == null || email.isEmpty() -> ValidationResult.Valid
            email.length > EMAIL_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $EMAIL_MAX_LENGTH caracteres")
            !email.contains("@") || !email.contains(".") ->
                ValidationResult.invalid("E-mail inválido")
            else -> ValidationResult.Valid
        }

    fun validateItemName(name: String): ValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Nome do item é obrigatório")
            name.length > ITEM_NAME_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $ITEM_NAME_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }
    }

    fun validateItemPrice(priceCents: Long?): ValidationResult =
        when {
            priceCents == null -> ValidationResult.invalid("Preço é obrigatório")
            priceCents <= 0 -> ValidationResult.invalid("Preço deve ser maior que zero")
            priceCents > ITEM_MAX_PRICE_CENTS ->
                ValidationResult.invalid("Preço máximo é R\$ 100.000,00")
            else -> ValidationResult.Valid
        }
}
