package br.com.sprena.shared.core.validation

private const val CPF_LENGTH = 11
private const val CPF_BASE_LENGTH = 9
private const val FIRST_WEIGHT_DV1 = 10
private const val FIRST_WEIGHT_DV2 = 11
private const val MODULUS = 11
private const val NO_REMAINDER_THRESHOLD = 2

/**
 * CPF com verificação de dígito verificador.
 *
 * Existe separado de [br.com.sprena.shared.account.domain.validation.ProfileValidator] e de
 * `SportClientValidator` porque o papel do CPF é diferente em cada um. Naqueles dois ele é
 * um dado autodeclarado, e contar 11 dígitos basta: um CPF errado ali incomoda o próprio
 * titular e mais ninguém.
 *
 * Aqui o CPF é **chave de vínculo** (F1.7.5): é por ele que um pré-cadastro feito pelo
 * CLIENT encontra a conta da pessoa no primeiro login. Um dígito trocado não erra por
 * pouco — cria um vínculo pendente que ninguém nunca reclama, ou que alguém errado
 * reclama. O dígito verificador é a defesa mais barata contra isso, e é também o primeiro
 * filtro antes de gastar rate limit e leitura na callable de claim.
 */
object CpfValidator {
    /** Só os dígitos — é esta forma que vira entrada do HMAC e chave de busca. */
    fun digits(raw: String): String = raw.filter { it.isDigit() }

    fun isValid(raw: String): Boolean {
        val d = digits(raw)
        if (d.length != CPF_LENGTH) return false
        // Sequências repetidas passam na aritmética (111.111.111-11 fecha os dois dígitos),
        // então precisam de recusa explícita.
        if (d.all { it == d[0] }) return false
        return d[CPF_BASE_LENGTH] == checkDigit(d, CPF_BASE_LENGTH, FIRST_WEIGHT_DV1) &&
            d[CPF_BASE_LENGTH + 1] == checkDigit(d, CPF_BASE_LENGTH + 1, FIRST_WEIGHT_DV2)
    }

    fun validate(raw: String): ValidationResult =
        when {
            raw.isBlank() -> ValidationResult.invalid("CPF é obrigatório")
            digits(raw).length != CPF_LENGTH -> ValidationResult.invalid("CPF deve ter $CPF_LENGTH dígitos")
            !isValid(raw) -> ValidationResult.invalid("CPF inválido")
            else -> ValidationResult.Valid
        }

    private fun checkDigit(
        digits: String,
        upTo: Int,
        firstWeight: Int,
    ): Char {
        var sum = 0
        for (i in 0 until upTo) {
            sum += (digits[i] - '0') * (firstWeight - i)
        }
        val remainder = sum % MODULUS
        return if (remainder < NO_REMAINDER_THRESHOLD) '0' else '0' + (MODULUS - remainder)
    }
}
