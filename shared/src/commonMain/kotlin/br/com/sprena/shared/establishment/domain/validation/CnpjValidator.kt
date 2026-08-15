package br.com.sprena.shared.establishment.domain.validation

import br.com.sprena.shared.core.validation.ValidationResult

private const val CNPJ_LENGTH = 14
private const val MODULUS = 11
private const val NO_REMAINDER_THRESHOLD = 2

/** Pesos do 1º dígito verificador, aplicados aos 12 primeiros dígitos. */
private val WEIGHTS_DV1 = intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)

/** Pesos do 2º, aplicados aos 13 primeiros (os 12 da base mais o DV1 já calculado). */
private val WEIGHTS_DV2 = intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)

/**
 * CNPJ com verificação de dígito verificador.
 *
 * Os 14 dígitos viram o id do documento em `cnpj_index`, e é o `create` sobre um id já
 * existente que garante a unicidade do estabelecimento — não há transação nem callable no
 * caminho. Isso torna o dígito verificador mais que higiene de formulário: um CNPJ
 * digitado errado não colide com o correto, então o mesmo estabelecimento entraria duas
 * vezes sem que a unicidade percebesse.
 *
 * A rule de `establishments` só consegue checar a forma (`^[0-9]{14}$`); aritmética não
 * existe no motor de rules. Esta é a única camada que fecha essa lacuna.
 */
object CnpjValidator {
    /** Só os dígitos — é esta forma que vai para o id de `cnpj_index` e para o Firestore. */
    fun digits(raw: String): String = raw.filter { it.isDigit() }

    fun isValid(raw: String): Boolean {
        val d = digits(raw)
        if (d.length != CNPJ_LENGTH) return false
        if (d.all { it == d[0] }) return false
        return d[WEIGHTS_DV1.size] == checkDigit(d, WEIGHTS_DV1) &&
            d[WEIGHTS_DV2.size] == checkDigit(d, WEIGHTS_DV2)
    }

    fun validate(raw: String): ValidationResult =
        when {
            raw.isBlank() -> ValidationResult.invalid("CNPJ é obrigatório")
            digits(raw).length != CNPJ_LENGTH -> ValidationResult.invalid("CNPJ deve ter $CNPJ_LENGTH dígitos")
            !isValid(raw) -> ValidationResult.invalid("CNPJ inválido")
            else -> ValidationResult.Valid
        }

    private fun checkDigit(
        digits: String,
        weights: IntArray,
    ): Char {
        var sum = 0
        for (i in weights.indices) {
            sum += (digits[i] - '0') * weights[i]
        }
        val remainder = sum % MODULUS
        return if (remainder < NO_REMAINDER_THRESHOLD) '0' else '0' + (MODULUS - remainder)
    }
}
