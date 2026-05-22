package br.com.sprena.shared.financial.domain.validation

import br.com.sprena.shared.core.validation.ValidationResult

object BrlMonetaryFormatter {
    fun parseToCents(raw: String): Long? {
        val cleaned =
            raw
                .replace("R$", "")
                .replace("R\$", "")
                .replace(" ", "")
                .replace(".", "")
                .replace(",", ".")
                .trim()
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        return (value * 100).toLong()
    }

    fun formatCents(cents: Long): String {
        val reais = cents / 100
        val centavos = (cents % 100).let { if (it < 0) -it else it }
        val reaisStr =
            reais
                .toString()
                .reversed()
                .chunked(3)
                .joinToString(".")
                .reversed()
        return "R$ $reaisStr,${centavos.toString().padStart(2, '0')}"
    }

    fun validate(raw: String): ValidationResult {
        val cleaned =
            raw
                .replace("R$", "")
                .replace("R\$", "")
                .replace(" ", "")
                .trim()
        if (cleaned.isEmpty()) {
            return ValidationResult.invalid("Valor é obrigatório")
        }
        val cents = parseToCents(raw)
        return when {
            cents == null -> ValidationResult.invalid("Formato inválido")
            cents <= 0L -> ValidationResult.invalid("Valor deve ser maior que zero")
            else -> ValidationResult.Valid
        }
    }
}
