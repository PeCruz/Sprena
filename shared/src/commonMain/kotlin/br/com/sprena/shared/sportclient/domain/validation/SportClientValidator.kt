package br.com.sprena.shared.sportclient.domain.validation

import br.com.sprena.shared.core.validation.ValidationResult

/**
 * Validação dos campos do cliente de esportes.
 *
 * - Nome: obrigatório, max 100 chars
 * - CPF: obrigatório, exatamente 11 dígitos (aceita formatação)
 * - Telefone: obrigatório, 10-15 dígitos (aceita formatação)
 * - Modalidade: obrigatória (FUTEVOLEI, BEACH_TENNIS, VOLEI)
 * - Frequência: obrigatória, 1~4
 * - Pagamento: obrigatório (WELLHUB, TOTALPASS, CASH)
 * - Valor em dinheiro: obrigatório (>= 0) para TODOS os métodos
 * - Mês Pagamento: obrigatório, formato MM/YYYY
 */
object SportClientValidator {
    const val NAME_MAX_LENGTH: Int = 100
    const val APELIDO_MAX_LENGTH: Int = 50
    const val CPF_LENGTH: Int = 11
    const val PHONE_MIN_LENGTH: Int = 10
    const val PHONE_MAX_LENGTH: Int = 15
    const val MAX_CASH_CENTS: Long = 100_000_00L // R$ 100.000,00

    fun validateName(name: String): ValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Nome é obrigatório")
            name.length > NAME_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $NAME_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }
    }

    fun validateApelido(apelido: String?): ValidationResult {
        if (apelido.isNullOrBlank()) return ValidationResult.Valid
        return when {
            apelido.length > APELIDO_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $APELIDO_MAX_LENGTH caracteres")
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

    fun validatePhone(phone: String): ValidationResult {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.isEmpty() -> ValidationResult.invalid("Telefone é obrigatório")
            digits.length < PHONE_MIN_LENGTH ->
                ValidationResult.invalid("Mínimo de $PHONE_MIN_LENGTH dígitos")
            digits.length > PHONE_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $PHONE_MAX_LENGTH dígitos")
            else -> ValidationResult.Valid
        }
    }

    fun validateModalidade(modalities: List<SportModality>?): ValidationResult =
        when {
            modalities.isNullOrEmpty() -> ValidationResult.invalid("Selecione ao menos uma modalidade")
            else -> ValidationResult.Valid
        }

    fun validateAttendance(attendance: Int?): ValidationResult =
        when {
            attendance == null -> ValidationResult.invalid("Frequência é obrigatória")
            attendance !in 1..4 -> ValidationResult.invalid("Frequência deve ser de 1 a 4")
            else -> ValidationResult.Valid
        }

    fun validatePaymentMethod(method: PaymentMethod?): ValidationResult =
        when (method) {
            null -> ValidationResult.invalid("Método de pagamento é obrigatório")
            else -> ValidationResult.Valid
        }

    fun validateCashAmount(
        amountCents: Long?,
        method: PaymentMethod,
    ): ValidationResult {
        // Valor em dinheiro é obrigatório (>= 0) para TODOS os métodos de pagamento
        return when {
            amountCents == null ->
                ValidationResult.invalid("Valor em dinheiro é obrigatório")
            amountCents < 0L ->
                ValidationResult.invalid("Valor não pode ser negativo")
            amountCents > MAX_CASH_CENTS ->
                ValidationResult.invalid("Valor máximo é R\$ 100.000,00")
            else -> ValidationResult.Valid
        }
    }

    fun validateLastPaymentMonth(month: String?): ValidationResult {
        if (month.isNullOrBlank()) {
            return ValidationResult.invalid("Mês de pagamento é obrigatório")
        }
        val regex = Regex("""^(\d{2})/(\d{4})$""")
        val match =
            regex.matchEntire(month)
                ?: return ValidationResult.invalid("Formato inválido — use MM/AAAA")
        val mm = match.groupValues[1].toIntOrNull() ?: 0
        if (mm !in 1..12) {
            return ValidationResult.invalid("Formato inválido — use MM/AAAA")
        }
        return ValidationResult.Valid
    }
}
