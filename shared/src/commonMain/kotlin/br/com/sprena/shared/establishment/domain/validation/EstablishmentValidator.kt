package br.com.sprena.shared.establishment.domain.validation

import br.com.sprena.shared.core.validation.ValidationResult

private const val NAME_MAX_LENGTH = 80
private const val RAZAO_SOCIAL_MAX_LENGTH = 120
private const val EMAIL_MAX_LENGTH = 120
private const val PHONE_MIN_DIGITS = 10
private const val PHONE_MAX_DIGITS = 11

private val EMAIL_REGEX = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]{2,}$""")

/**
 * Campos do estabelecimento.
 *
 * Os limites espelham de propósito os da rule de `establishments` em `firestore.rules`.
 * Quando divergem, o usuário preenche um formulário que a tela aceita e o servidor recusa
 * com `PERMISSION_DENIED` — um erro que não diz qual campo causou o problema. Se um limite
 * mudar aqui, precisa mudar lá.
 *
 * Nome, CNPJ, telefone e e-mail são obrigatórios; razão social e endereço, opcionais.
 */
object EstablishmentValidator {
    fun validateName(name: String): ValidationResult =
        when {
            name.isBlank() -> ValidationResult.invalid("Nome é obrigatório")
            name.trim().length > NAME_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $NAME_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }

    fun validateCnpj(cnpj: String): ValidationResult = CnpjValidator.validate(cnpj)

    fun validateRazaoSocial(razaoSocial: String?): ValidationResult =
        when {
            razaoSocial.isNullOrBlank() -> ValidationResult.Valid
            razaoSocial.trim().length > RAZAO_SOCIAL_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $RAZAO_SOCIAL_MAX_LENGTH caracteres")
            else -> ValidationResult.Valid
        }

    fun validatePhone(phone: String): ValidationResult {
        val digits = phone.filter { it.isDigit() }
        return when {
            phone.isBlank() -> ValidationResult.invalid("Telefone é obrigatório")
            digits.length !in PHONE_MIN_DIGITS..PHONE_MAX_DIGITS ->
                ValidationResult.invalid("Telefone deve ter DDD + 8 ou 9 dígitos")
            else -> ValidationResult.Valid
        }
    }

    fun validateEmail(email: String): ValidationResult =
        when {
            email.isBlank() -> ValidationResult.invalid("E-mail é obrigatório")
            email.trim().length > EMAIL_MAX_LENGTH ->
                ValidationResult.invalid("Máximo de $EMAIL_MAX_LENGTH caracteres")
            !EMAIL_REGEX.matches(email.trim()) -> ValidationResult.invalid("E-mail inválido")
            else -> ValidationResult.Valid
        }
}
