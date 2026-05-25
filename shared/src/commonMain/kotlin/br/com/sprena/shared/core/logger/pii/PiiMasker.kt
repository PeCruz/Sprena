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
    /**
     * CPF: preserva apenas os 2 últimos dígitos. Aceita formatado ou não.
     * Ex.: "123.456.789-90" -> "***.***.***-90"
     */
    fun cpf(value: String?): String? {
        if (value == null) return null
        if (value.isEmpty()) return ""
        val digits = value.filter { it.isDigit() }
        if (digits.length < 11) return "***"
        val last2 = digits.takeLast(2)
        return "***.***.***-$last2"
    }

    /**
     * Telefone: preserva DDD (2 primeiros dígitos) e os 2 últimos.
     * Aceita 10 ou 11 dígitos com qualquer formatação.
     */
    fun phone(value: String?): String? {
        if (value == null) return null
        if (value.isEmpty()) return ""
        val digits = value.filter { it.isDigit() }
        if (digits.length !in 10..11) return "***"
        val ddd = digits.take(2)
        val last2 = digits.takeLast(2)
        val middleStars = "*".repeat(digits.length - 4)
        return "($ddd)$middleStars-$last2"
    }

    /**
     * Email: preserva 1 char do local + domínio inteiro.
     * Ex.: "pedro@gmail.com" -> "p***@gmail.com"
     */
    fun email(value: String?): String? {
        if (value == null) return null
        if (value.isEmpty()) return ""
        val atIndex = value.indexOf('@')
        if (atIndex <= 0) return "***"
        val first = value[0]
        val domain = value.substring(atIndex)
        return if (atIndex == 1) "*$domain" else "$first***$domain"
    }
}
