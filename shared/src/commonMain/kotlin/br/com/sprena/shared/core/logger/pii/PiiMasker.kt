package br.com.sprena.shared.core.logger.pii

/**
 * Masking explícito de PII para uso em call sites.
 *
 * Usar quando QUEM LOGA conhece o tipo do campo. Exemplo:
 * ```
 * logger.info("AuthRepo", "login ok para usuario=${PiiMasker.email(user.email)}")
 * ```
 *
 * Para defense-in-depth (regex sweep antes de emitir), ver [PiiScrubber].
 */
object PiiMasker {
    private const val CPF_DIGIT_COUNT = 11
    private const val PHONE_MIN_DIGITS = 10
    private const val PHONE_MAX_DIGITS = 11
    private const val PRESERVED_EDGES = 4

    /**
     * CPF: preserva apenas os 2 últimos dígitos. Aceita formatado ou não.
     * Ex.: "123.456.789-90" -> "***.***.***-90"
     */
    fun cpf(value: String?): String? =
        when {
            value == null -> null
            value.isEmpty() -> ""
            else -> {
                val digits = value.filter { it.isDigit() }
                if (digits.length < CPF_DIGIT_COUNT) {
                    "***"
                } else {
                    "***.***.***-${digits.takeLast(2)}"
                }
            }
        }

    /**
     * Telefone: preserva DDD (2 primeiros dígitos) e os 2 últimos.
     * Aceita 10 ou 11 dígitos com qualquer formatação.
     */
    fun phone(value: String?): String? =
        when {
            value == null -> null
            value.isEmpty() -> ""
            else -> {
                val digits = value.filter { it.isDigit() }
                if (digits.length !in PHONE_MIN_DIGITS..PHONE_MAX_DIGITS) {
                    "***"
                } else {
                    val ddd = digits.take(2)
                    val last2 = digits.takeLast(2)
                    val middleStars = "*".repeat(digits.length - PRESERVED_EDGES)
                    "($ddd)$middleStars-$last2"
                }
            }
        }

    /**
     * Email: preserva 1 char do local + domínio inteiro.
     * Ex.: "pedro@gmail.com" -> "p***@gmail.com"
     */
    fun email(value: String?): String? =
        when {
            value == null -> null
            value.isEmpty() -> ""
            else -> {
                val atIndex = value.indexOf('@')
                when {
                    atIndex <= 0 -> "***"
                    atIndex == 1 -> "*${value.substring(atIndex)}"
                    else -> "${value[0]}***${value.substring(atIndex)}"
                }
            }
        }
}
