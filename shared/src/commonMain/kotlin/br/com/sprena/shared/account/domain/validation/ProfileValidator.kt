package br.com.sprena.shared.account.domain.validation

import br.com.sprena.shared.core.validation.ValidationResult

private const val CPF_DIGITS = 11
private const val PHONE_MIN_DIGITS = 10
private const val PHONE_MAX_DIGITS = 11

/**
 * Validação dos campos autodeclarados do perfil (F1.6a).
 *
 * Todos os campos são **opcionais**: em branco significa "não informado", que é um
 * estado legítimo — o titular não é obrigado a fornecer CPF para usar o app. O que não
 * se aceita é valor preenchido e malformado, que viraria máscara completa na exibição e
 * deixaria o titular sem entender por que o dado sumiu.
 */
object ProfileValidator {
    fun validateCpf(raw: String): ValidationResult {
        val digits = raw.filter { it.isDigit() }
        return when {
            raw.isBlank() -> ValidationResult.Valid
            digits.length != CPF_DIGITS -> ValidationResult.invalid("CPF deve ter 11 dígitos")
            else -> ValidationResult.Valid
        }
    }

    fun validatePhone(raw: String): ValidationResult {
        val digits = raw.filter { it.isDigit() }
        return when {
            raw.isBlank() -> ValidationResult.Valid
            digits.length !in PHONE_MIN_DIGITS..PHONE_MAX_DIGITS ->
                ValidationResult.invalid("Telefone deve ter DDD + 8 ou 9 dígitos")
            else -> ValidationResult.Valid
        }
    }
}
