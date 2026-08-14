package br.com.sprena.shared.core.privacy

private const val PHONE_MIN_DIGITS = 10
private const val PHONE_MAX_DIGITS = 11
private const val FULLY_MASKED_PHONE = "(**) *****-****"

// Telefone brasileiro sem DDI: 2 dígitos de DDD + 8 (fixo) ou 9 (celular) do número.
// Os 4 últimos são preservados na máscara; o miolo entre DDD e sufixo vira asterisco.
private const val DDD_LENGTH = 2
private const val PRESERVED_SUFFIX = 4

/**
 * Mascara um telefone para exibição: `11987654321` → `(11) *****-4321`.
 *
 * Aceita entrada crua ou já pontuada — só os dígitos importam. Qualquer entrada
 * que não normalize para 10 ou 11 dígitos vira máscara completa: entrada
 * malformada não pode vazar dígito parcial. DDI não é suportado — o campo guarda
 * DDD + número, então `+5511987654321` (13 dígitos) mascara tudo.
 *
 * Não confundir com [br.com.sprena.shared.core.logger.pii.PiiMasker.phone], que preserva só os
 * 2 últimos dígitos e serve para logs, não para exibição em tela.
 */
fun maskPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.length !in PHONE_MIN_DIGITS..PHONE_MAX_DIGITS) return FULLY_MASKED_PHONE
    val ddd = digits.take(DDD_LENGTH)
    val sufixo = digits.takeLast(PRESERVED_SUFFIX)
    val miolo = "*".repeat(digits.length - DDD_LENGTH - PRESERVED_SUFFIX)
    return "($ddd) $miolo-$sufixo"
}

/**
 * Formata um telefone completo para exibição: `11987654321` → `(11) 98765-4321`.
 *
 * Usado só quando a revelação foi autorizada. Entrada que não tenha 10 ou 11
 * dígitos volta como veio — formatar lixo esconderia o problema.
 */
fun formatPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.length !in PHONE_MIN_DIGITS..PHONE_MAX_DIGITS) return raw
    val ddd = digits.take(DDD_LENGTH)
    val sufixo = digits.takeLast(PRESERVED_SUFFIX)
    val prefixo = digits.substring(DDD_LENGTH, digits.length - PRESERVED_SUFFIX)
    return "($ddd) $prefixo-$sufixo"
}
