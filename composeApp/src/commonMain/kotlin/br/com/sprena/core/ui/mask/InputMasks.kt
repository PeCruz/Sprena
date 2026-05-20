package br.com.sprena.core.ui.mask

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Máscara de telefone brasileiro: (XX) XXXXX-XXXX
 * Aceita até 11 dígitos. Se tiver 10 dígitos: (XX) XXXX-XXXX.
 */
class PhoneMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(11)
        val masked = buildPhoneMask(digits)

        val offsetMapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    val clampedOffset = offset.coerceIn(0, digits.length)
                    return mapOriginalToTransformed(clampedOffset, digits.length)
                }

                override fun transformedToOriginal(offset: Int): Int =
                    mapTransformedToOriginal(offset, masked, digits.length)
            }

        return TransformedText(AnnotatedString(masked), offsetMapping)
    }
}

private fun buildPhoneMask(digits: String): String {
    val sb = StringBuilder()
    for (i in digits.indices) {
        when {
            i == 0 -> sb.append("(")
            i == 2 -> sb.append(") ")
            i == 7 && digits.length == 11 -> sb.append("-")
            i == 6 && digits.length <= 10 -> sb.append("-")
        }
        sb.append(digits[i])
    }
    return sb.toString()
}

private fun mapOriginalToTransformed(
    offset: Int,
    totalDigits: Int,
): Int {
    // Map digit index → position in masked string
    // (XX) XXXXX-XXXX  for 11 digits
    // (XX) XXXX-XXXX   for 10 digits
    if (offset == 0) return 0
    var pos = 0
    var digitsSeen = 0
    val masked =
        if (totalDigits <= 10) {
            buildPhoneMask("0".repeat(totalDigits.coerceAtLeast(1)))
        } else {
            buildPhoneMask("0".repeat(11))
        }
    for (c in masked) {
        if (digitsSeen == offset) return pos
        if (c.isDigit()) digitsSeen++
        pos++
    }
    return pos
}

private fun mapTransformedToOriginal(
    offset: Int,
    masked: String,
    totalDigits: Int,
): Int {
    var digitsSeen = 0
    for (i in 0 until offset.coerceAtMost(masked.length)) {
        if (masked[i].isDigit()) digitsSeen++
    }
    return digitsSeen.coerceAtMost(totalDigits)
}

/**
 * Máscara de CPF: XXX.XXX.XXX-XX
 * Aceita exatamente 11 dígitos.
 */
class CpfMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(11)
        val masked = buildCpfMask(digits)

        val offsetMapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    val clampedOffset = offset.coerceIn(0, digits.length)
                    return cpfOriginalToTransformed(clampedOffset)
                }

                override fun transformedToOriginal(offset: Int): Int =
                    cpfTransformedToOriginal(offset, masked, digits.length)
            }

        return TransformedText(AnnotatedString(masked), offsetMapping)
    }
}

private fun buildCpfMask(digits: String): String {
    val sb = StringBuilder()
    for (i in digits.indices) {
        when (i) {
            3, 6 -> sb.append(".")
            9 -> sb.append("-")
        }
        sb.append(digits[i])
    }
    return sb.toString()
}

private fun cpfOriginalToTransformed(offset: Int): Int {
    // Digit positions:  0 1 2 . 3 4 5 . 6 7 8 - 9 10
    // Transformed pos:  0 1 2 3 4 5 6 7 8 9 10 11 12 13
    return when {
        offset <= 3 -> offset
        offset <= 6 -> offset + 1
        offset <= 9 -> offset + 2
        else -> offset + 3
    }
}

private fun cpfTransformedToOriginal(
    offset: Int,
    masked: String,
    totalDigits: Int,
): Int {
    var digitsSeen = 0
    for (i in 0 until offset.coerceAtMost(masked.length)) {
        if (masked[i].isDigit()) digitsSeen++
    }
    return digitsSeen.coerceAtMost(totalDigits)
}

/**
 * Máscara de mês/ano: MM/AAAA
 * Aceita até 6 dígitos. Insere "/" após os 2 primeiros.
 * Exemplo: "03" → "03/", "032026" → "03/2026"
 */
class MonthYearMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(6)
        val masked = buildMonthYearMask(digits)

        val offsetMapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    val clamped = offset.coerceIn(0, digits.length)
                    return if (clamped <= 2) clamped else clamped + 1
                }

                override fun transformedToOriginal(offset: Int): Int =
                    monthYearTransformedToOriginal(offset, masked, digits.length)
            }

        return TransformedText(AnnotatedString(masked), offsetMapping)
    }
}

private fun buildMonthYearMask(digits: String): String {
    val sb = StringBuilder()
    for (i in digits.indices) {
        if (i == 2) sb.append("/")
        sb.append(digits[i])
    }
    return sb.toString()
}

private fun monthYearTransformedToOriginal(
    offset: Int,
    masked: String,
    totalDigits: Int,
): Int {
    var digitsSeen = 0
    for (i in 0 until offset.coerceAtMost(masked.length)) {
        if (masked[i].isDigit()) digitsSeen++
    }
    return digitsSeen.coerceAtMost(totalDigits)
}

/**
 * Converte dígitos crus de mês/ano para formato MM/AAAA (para validação).
 * "042026" → "04/2026"
 */
fun formatMonthYearDigits(digits: String): String {
    if (digits.length < 6) return digits
    return "${digits.substring(0, 2)}/${digits.substring(2, 6)}"
}

/**
 * Máscara de moeda brasileira (centavos).
 * O valor armazenado é uma string de dígitos representando centavos.
 * Exibe: 1→0,01 / 12→0,12 / 123→1,23 / 1234→12,34 / 12345→123,45
 */
class CurrencyMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val formatted = formatCurrencyDigits(digits)

        val offsetMapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = formatted.length

                override fun transformedToOriginal(offset: Int): Int = digits.length
            }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

/**
 * Formata dígitos como moeda: "1" → "0,01", "12" → "0,12", "123" → "1,23"
 */
fun formatCurrencyDigits(digits: String): String {
    if (digits.isEmpty()) return ""
    val cents = digits.toLongOrNull() ?: 0L
    val reais = cents / 100
    val centavos = cents % 100
    return "$reais,${centavos.toString().padStart(2, '0')}"
}

/**
 * Converte string de dígitos (centavos) para Long.
 */
fun parseCurrencyDigits(digits: String): Long? {
    if (digits.isEmpty()) return null
    return digits.toLongOrNull()
}

/**
 * Converte Long de centavos para string de dígitos pura (para o campo de input).
 */
fun centsToDigitString(cents: Long): String = cents.toString()

/**
 * Filtra input para conter apenas dígitos (para uso com masks).
 */
fun filterDigitsOnly(
    input: String,
    maxLength: Int,
): String = input.filter { it.isDigit() }.take(maxLength)
