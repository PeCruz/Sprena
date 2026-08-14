package br.com.sprena.shared.core.time

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_DAY = 86_400L

// Deslocamento entre a era civil usada pelo algoritmo (começa em 0000-03-01) e a epoch
// Unix, e o número de dias num ciclo de 400 anos. Constantes do algoritmo
// `civil_from_days` de Howard Hinnant, que é exato para qualquer data proléptica.
private const val DAYS_SHIFT_TO_ERA = 719_468L
private const val DAYS_PER_ERA = 146_097L
private const val LAST_DAY_OF_ERA = 146_096L
private const val DAYS_PER_4_YEARS = 1_460L
private const val DAYS_PER_CENTURY = 36_524L
private const val DAYS_PER_YEAR = 365L
private const val YEARS_PER_ERA = 400L
private const val YEAR_DIGITS = 4
private const val LEAP_CYCLE = 4L
private const val CENTURY = 100L
private const val DAYS_PER_5_MONTHS = 153L
private const val MONTH_NUMERATOR = 5L
private const val MONTH_OFFSET = 2L
private const val MONTHS_PER_YEAR = 12L
private const val MARCH = 3L
private const val FEBRUARY = 2L

/** Acima deste mês deslocado, o ano civil já virou — daí o desconto de 12. */
private const val MONTH_PIVOT = 10L

/**
 * Formata um instante em ISO-8601 UTC: `1786040525000` → `"2026-08-14T18:22:05Z"`.
 *
 * Precisão de segundo — milissegundos são descartados. O destinatário é o titular lendo
 * o próprio arquivo de exportação (F1.6a), não uma máquina que precise de sub-segundo.
 *
 * Escrito à mão em vez de trazer `kotlinx-datetime`: o escopo é só formatação em UTC,
 * sem parsing e sem fuso, e a dependência não se pagaria.
 */
fun toIso8601Utc(epochMillis: Long): String {
    val totalSeconds = epochMillis.floorDiv(MILLIS_PER_SECOND)
    val days = totalSeconds.floorDiv(SECONDS_PER_DAY)
    val secondOfDay = totalSeconds.mod(SECONDS_PER_DAY)

    val (year, month, day) = civilFromDays(days)
    val hour = secondOfDay / SECONDS_PER_HOUR
    val minute = (secondOfDay % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val second = secondOfDay % SECONDS_PER_MINUTE

    return buildString {
        append(year.toString().padStart(YEAR_DIGITS, '0'))
        append('-')
        append(pad2(month))
        append('-')
        append(pad2(day))
        append('T')
        append(pad2(hour))
        append(':')
        append(pad2(minute))
        append(':')
        append(pad2(second))
        append('Z')
    }
}

private fun pad2(value: Long): String = value.toString().padStart(2, '0')

/** Converte dias desde a epoch Unix em (ano, mês, dia) do calendário gregoriano. */
private fun civilFromDays(days: Long): Triple<Long, Long, Long> {
    val z = days + DAYS_SHIFT_TO_ERA
    val era = z.floorDiv(DAYS_PER_ERA)
    val dayOfEra = z - era * DAYS_PER_ERA
    val yearOfEra =
        (dayOfEra - dayOfEra / DAYS_PER_4_YEARS + dayOfEra / DAYS_PER_CENTURY - dayOfEra / LAST_DAY_OF_ERA) /
            DAYS_PER_YEAR
    val dayOfYear =
        dayOfEra - (DAYS_PER_YEAR * yearOfEra + yearOfEra / LEAP_CYCLE - yearOfEra / CENTURY)
    // O algoritmo trabalha com o ano começando em março, então o mês vem deslocado: a
    // razão 153/5 traduz "dia do ano deslocado" em mês, porque um bloco de 5 meses
    // consecutivos sempre soma 153 dias nesse calendário rotacionado.
    val shiftedMonth = (MONTH_NUMERATOR * dayOfYear + MONTH_OFFSET) / DAYS_PER_5_MONTHS
    val day = dayOfYear - (DAYS_PER_5_MONTHS * shiftedMonth + MONTH_OFFSET) / MONTH_NUMERATOR + 1
    val month = shiftedMonth + if (shiftedMonth < MONTH_PIVOT) MARCH else MARCH - MONTHS_PER_YEAR
    val year = yearOfEra + era * YEARS_PER_ERA + if (month <= FEBRUARY) 1 else 0
    return Triple(year, month, day)
}
