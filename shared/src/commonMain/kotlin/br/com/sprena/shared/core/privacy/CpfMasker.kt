package br.com.sprena.shared.core.privacy

private const val CPF_DIGITS = 11
private const val FULLY_MASKED = "***.***.***-**"

// CPF tem o formato XXX.XXX.XXX-YY: 3 blocos de 3 dígitos + 2 dígitos verificadores (DV).
// Os índices abaixo demarcam onde cada bloco começa/termina dentro da string de 11 dígitos.
private const val PRIMEIRO_BLOCO_INICIO = 0
private const val PRIMEIRO_BLOCO_FIM = 3
private const val SEGUNDO_BLOCO_FIM = 6
private const val TERCEIRO_BLOCO_FIM = 9
private const val DIGITO_VERIFICADOR_FIM = CPF_DIGITS

/**
 * Mascara um CPF para exibição: `12345678900` → `***.***.789-00`.
 *
 * Aceita entrada crua ou já pontuada — só os dígitos importam. Qualquer entrada
 * que não normalize para exatamente 11 dígitos vira máscara completa: entrada
 * malformada não pode vazar dígito parcial.
 *
 * Não confundir com [br.com.sprena.shared.core.logger.pii.PiiMasker.cpf], que mascara CPF com
 * outra regra (preserva só os 2 últimos dígitos) e serve para logs, não para exibição em tela.
 */
fun maskCpf(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.length != CPF_DIGITS) return FULLY_MASKED
    val terceiroBloco = digits.substring(SEGUNDO_BLOCO_FIM, TERCEIRO_BLOCO_FIM)
    val digitoVerificador = digits.substring(TERCEIRO_BLOCO_FIM, DIGITO_VERIFICADOR_FIM)
    return "***.***.$terceiroBloco-$digitoVerificador"
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
    val primeiroBloco = digits.substring(PRIMEIRO_BLOCO_INICIO, PRIMEIRO_BLOCO_FIM)
    val segundoBloco = digits.substring(PRIMEIRO_BLOCO_FIM, SEGUNDO_BLOCO_FIM)
    val terceiroBloco = digits.substring(SEGUNDO_BLOCO_FIM, TERCEIRO_BLOCO_FIM)
    val digitoVerificador = digits.substring(TERCEIRO_BLOCO_FIM, DIGITO_VERIFICADOR_FIM)
    return "$primeiroBloco.$segundoBloco.$terceiroBloco-$digitoVerificador"
}
