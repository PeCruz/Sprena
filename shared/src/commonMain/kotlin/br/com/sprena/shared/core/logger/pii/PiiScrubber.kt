package br.com.sprena.shared.core.logger.pii

/**
 * Defense-in-depth: aplicado pela impl de `Logger` antes de emitir QUALQUER mensagem.
 *
 * Cobre os padrões mais comuns de PII que podem escapar de stack traces, mensagens
 * de exceção do Firestore, ou logs ad-hoc que esqueceram de usar [PiiMasker].
 *
 * NÃO substitui [PiiMasker] — é a última linha de defesa, não a primeira.
 */
object PiiScrubber {
    // CPF formatado: 123.456.789-90
    private val cpfFormattedRegex = Regex("""\d{3}\.\d{3}\.\d{3}-\d{2}""")

    // CPF cru após keyword "cpf" (qualquer caixa): cpf=12345678900 ou cpf: 12345678900
    private val cpfRawAfterKeywordRegex = Regex("""(?i)(cpf\s*[:=]\s*)\d{11}""")

    // Email RFC-simplificado
    private val emailRegex = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

    // Password após keyword (password=, senha=, etc.) até espaço/fim
    private val passwordAfterKeywordRegex = Regex("""(?i)(password|senha|pwd)\s*[:=]\s*\S+""")

    fun scrub(value: String?): String? =
        when {
            value == null -> null
            value.isEmpty() -> ""
            else -> scrubNonEmpty(value)
        }

    private fun scrubNonEmpty(value: String): String {
        var result = value
        result = cpfFormattedRegex.replace(result, "***.***.***-**")
        result = cpfRawAfterKeywordRegex.replace(result, "$1***********")
        result = emailRegex.replace(result, "***@***")
        result =
            passwordAfterKeywordRegex.replace(result) { match ->
                val keyword = match.value.substringBefore('=').substringBefore(':')
                val sep = if ('=' in match.value) '=' else ':'
                "$keyword$sep***"
            }
        return result
    }
}
