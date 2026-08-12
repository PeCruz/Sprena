package br.com.sprena.shared.core.privacy

private const val CPF_DIGITS = 11
private const val FULLY_MASKED = "***.***.***-**"

/**
 * Mascara um CPF para exibição: `12345678900` → `***.***.789-00`.
 *
 * Aceita entrada crua ou já pontuada — só os dígitos importam. Qualquer entrada
 * que não normalize para exatamente 11 dígitos vira máscara completa: entrada
 * malformada não pode vazar dígito parcial.
 */
fun maskCpf(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.length != CPF_DIGITS) return FULLY_MASKED
    return "***.***.${digits.substring(6, 9)}-${digits.substring(9, 11)}"
}

/**
 * Formata um CPF completo para exibição: `12345678900` → `123.456.789-00`.
 *
 * Usado só quando a revelação foi autorizada (ADM/MOD). Entrada que não tenha
 * 11 dígitos volta como veio — formatar lixo esconderia o problema.
 */
fun formatCpf(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.length != CPF_DIGITS) return raw
    return "${digits.substring(0, 3)}.${digits.substring(3, 6)}.${digits.substring(6, 9)}-${digits.substring(9, 11)}"
}
